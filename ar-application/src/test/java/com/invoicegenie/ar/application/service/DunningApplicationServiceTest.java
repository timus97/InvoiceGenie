package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.DunningUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.DunningNotice;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DunningApplicationService")
@ExtendWith(MockitoExtension.class)
class DunningApplicationServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private EventPublisher eventPublisher;

    private DunningApplicationService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new DunningApplicationService(invoiceRepository, eventPublisher,
                DunningPolicy.defaults());
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("emits DunningNotice for invoices past level thresholds")
    void emitsNotices() {
        Invoice inv = new Invoice(InvoiceId.generate(), "INV-D1", customerId, "C",
                "USD", LocalDate.now().minusDays(100), LocalDate.now().minusDays(45), List.of());
        inv.addLine(new InvoiceLine(1, "Svc", Money.of("200.00", "USD")));
        inv.issue();

        when(invoiceRepository.findOpenByTenant(tenantId)).thenReturn(List.of(inv));

        DunningUseCase.DunningRunResult result = service.runForTenant(tenantId, LocalDate.now());

        assertEquals(1, result.invoicesScanned());
        assertEquals(1, result.noticesEmitted());
        verify(eventPublisher).publish(any(DunningNotice.class));
    }

    @Test
    @DisplayName("level mapping uses policy thresholds")
    void policyLevels() {
        DunningPolicy policy = DunningPolicy.parse(true, "15,45");
        assertEquals(0, policy.levelFor(10));
        assertEquals(1, policy.levelFor(15));
        assertEquals(1, policy.levelFor(44));
        assertEquals(2, policy.levelFor(45));
        assertEquals(2, policy.levelFor(100));
    }

    @Test
    @DisplayName("disabled policy emits nothing")
    void disabled() {
        service = new DunningApplicationService(invoiceRepository, eventPublisher,
                new DunningPolicy(false, List.of(30, 60, 90)));
        DunningUseCase.DunningRunResult result = service.runForTenant(tenantId, LocalDate.now());
        assertEquals(0, result.noticesEmitted());
        verifyNoInteractions(eventPublisher);
    }
}