package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.StatementGenerated;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("StatementApplicationService")
@ExtendWith(MockitoExtension.class)
class StatementApplicationServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private EventPublisher eventPublisher;

    private StatementApplicationService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new StatementApplicationService(customerRepository, invoiceRepository, eventPublisher);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("generates open-item statement JSON model and CSV + event")
    void generateStatement() {
        Customer customer = new Customer(customerId, "C001", "Acme Corp", "USD");
        Invoice inv = new Invoice(InvoiceId.generate(), "INV-S1", customerId, "Acme",
                "USD", LocalDate.now().minusDays(40), LocalDate.now().minusDays(10), List.of());
        inv.addLine(new InvoiceLine(1, "Work", Money.of("500.00", "USD")));
        inv.issue();

        when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));
        when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of(inv));

        Optional<StatementUseCase.CustomerStatement> result =
                service.generate(tenantId, customerId, LocalDate.now());

        assertTrue(result.isPresent());
        assertEquals(1, result.get().openItems().size());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.get().totalBalance()));
        String csv = service.toCsv(result.get());
        assertTrue(csv.contains("invoiceNumber"));
        assertTrue(csv.contains("INV-S1"));
        verify(eventPublisher).publish(any(StatementGenerated.class));
    }

    @Test
    @DisplayName("returns empty when customer missing")
    void missingCustomer() {
        when(customerRepository.findByTenantAndId(any(), any())).thenReturn(Optional.empty());
        assertTrue(service.generate(tenantId, customerId, LocalDate.now()).isEmpty());
    }
}