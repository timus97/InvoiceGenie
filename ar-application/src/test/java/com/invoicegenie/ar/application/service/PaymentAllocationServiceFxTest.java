package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.ar.domain.model.fx.ExchangeRateRepository;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.service.CurrencyConversionService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PaymentAllocationService FX (PP-024)")
@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceFxTest {

    @Mock PaymentRepository paymentRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock EventPublisher eventPublisher;
    @Mock IdempotencyStore idempotencyStore;
    @Mock ExchangeRateRepository exchangeRateRepository;

    TenantId tenantId;
    CustomerId customerId;
    PaymentId paymentId;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
        paymentId = PaymentId.generate();
    }

    @Test
    @DisplayName("rejects currency mismatch when allowFx=false")
    void rejectsMismatchWhenDisabled() {
        PaymentAllocationService service = new PaymentAllocationService(
                paymentRepository, invoiceRepository, eventPublisher, idempotencyStore, null, false);
        Payment payment = new Payment(paymentId, "PAY-EUR", customerId, Money.of("100.00", "EUR"),
                LocalDate.now(), PaymentMethod.BANK_TRANSFER);
        Invoice invoice = new Invoice(InvoiceId.generate(), "INV-USD", null, "C1", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Svc", Money.of("100.00", "USD")));
        invoice.issue();

        when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findByTenantAndId(eq(tenantId), eq(invoice.getId()))).thenReturn(Optional.of(invoice));

        var result = service.manualAllocate(tenantId, paymentId, List.of(
                new PaymentAllocationUseCase.ManualAllocationRequest(
                        invoice.getId(), Money.of("100.00", "EUR"), "x")),
                UUID.randomUUID(), null);

        assertTrue(result.isPresent());
        assertTrue(result.get().hasErrors());
        assertTrue(result.get().errors().get(0).contains("Currency mismatch"));
        assertEquals(0, result.get().allocations().size());
        verify(paymentRepository, never()).save(any(), any());
    }

    @Test
    @DisplayName("allows currency mismatch when allowFx=true with rate")
    void allowsMismatchWhenEnabled() {
        CurrencyConversionService conversion = new CurrencyConversionService(exchangeRateRepository);
        PaymentAllocationService service = new PaymentAllocationService(
                paymentRepository, invoiceRepository, eventPublisher, idempotencyStore, conversion, true);

        Payment payment = new Payment(paymentId, "PAY-EUR", customerId, Money.of("100.00", "EUR"),
                LocalDate.now(), PaymentMethod.BANK_TRANSFER);
        Invoice invoice = new Invoice(InvoiceId.generate(), "INV-USD", null, "C1", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Svc", Money.of("110.00", "USD")));
        invoice.issue();

        when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findByTenantAndId(eq(tenantId), eq(invoice.getId()))).thenReturn(Optional.of(invoice));
        // 1 EUR = 1.10 USD
        when(exchangeRateRepository.findLatest(eq(tenantId), eq("EUR"), eq("USD"), any()))
                .thenReturn(Optional.of(new ExchangeRate(UUID.randomUUID(), "EUR", "USD",
                        new BigDecimal("1.10000000"), LocalDate.now(), "TEST")));
        // also may need inverse for balance conversion path
        when(exchangeRateRepository.findLatest(eq(tenantId), eq("USD"), eq("EUR"), any()))
                .thenReturn(Optional.of(new ExchangeRate(UUID.randomUUID(), "USD", "EUR",
                        new BigDecimal("0.90909091"), LocalDate.now(), "TEST")));

        var result = service.manualAllocate(tenantId, paymentId, List.of(
                new PaymentAllocationUseCase.ManualAllocationRequest(
                        invoice.getId(), Money.of("100.00", "EUR"), "fx")),
                UUID.randomUUID(), null);

        assertTrue(result.isPresent());
        assertFalse(result.get().hasErrors(), () -> result.get().errors().toString());
        assertEquals(1, result.get().allocations().size());
        assertEquals("EUR", result.get().allocations().get(0).amount().getCurrencyCode());
        verify(paymentRepository, atLeastOnce()).save(eq(tenantId), any());
        verify(invoiceRepository, atLeastOnce()).save(eq(tenantId), any());
    }
}
