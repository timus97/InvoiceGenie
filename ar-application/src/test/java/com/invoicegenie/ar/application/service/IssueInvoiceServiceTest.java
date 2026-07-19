package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("IssueInvoiceService")
@ExtendWith(MockitoExtension.class)
class IssueInvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AuditRepository auditRepository;

    private IssueInvoiceService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new IssueInvoiceService(invoiceRepository, customerRepository, idGenerator, eventPublisher, auditRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    private void stubCustomerExists() {
        when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                .thenReturn(Optional.of(new Customer(customerId, "CUST001", "Acme", "USD")));
    }

    @Nested
    @DisplayName("Issue Invoice")
    class IssueInvoice {

        @Test
        @DisplayName("should issue invoice with single line")
        void shouldIssueInvoiceWithSingleLine() {
            InvoiceId expectedId = InvoiceId.generate();
            when(idGenerator.newInvoiceId()).thenReturn(expectedId);
            stubCustomerExists();

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-001", customerId.getValue().toString(), "Acme", "USD", LocalDate.now(),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("100.00")))
            );

            InvoiceId result = service.issue(tenantId, command);

            assertEquals(expectedId, result);
            verify(invoiceRepository).save(eq(tenantId), any());
            verify(auditRepository).save(eq(tenantId), any());
            verify(eventPublisher).publish(any(InvoiceIssued.class));
        }

        @Test
        @DisplayName("should issue invoice with multiple lines")
        void shouldIssueInvoiceWithMultipleLines() {
            InvoiceId expectedId = InvoiceId.generate();
            when(idGenerator.newInvoiceId()).thenReturn(expectedId);
            stubCustomerExists();

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-002", customerId.getValue().toString(), "Acme", "USD", LocalDate.now(),
                    List.of(
                            new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service A", new BigDecimal("100.00")),
                            new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service B", new BigDecimal("200.00"))
                    )
            );

            InvoiceId result = service.issue(tenantId, command);

            assertEquals(expectedId, result);
            verify(invoiceRepository).save(eq(tenantId), any());
            verify(auditRepository).save(eq(tenantId), any());
        }

        @Test
        @DisplayName("should reject when customer does not exist")
        void shouldRejectMissingCustomer() {
            when(customerRepository.findByTenantAndId(eq(tenantId), any())).thenReturn(Optional.empty());

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-003", customerId.getValue().toString(), null, "USD", LocalDate.now(),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("50.00")))
            );

            assertThrows(IllegalArgumentException.class, () -> service.issue(tenantId, command));
            verify(invoiceRepository, never()).save(any(), any());
        }
    }
}
