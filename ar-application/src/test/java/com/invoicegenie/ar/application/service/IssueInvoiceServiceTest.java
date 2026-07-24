package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.exception.CustomerNotInvoiceableException;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@DisplayName("IssueInvoiceService")
@ExtendWith(MockitoExtension.class)
class IssueInvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private IdGenerator idGenerator;
    @Mock private EventPublisher eventPublisher;
    @Mock private AuditRepository auditRepository;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private IdempotencyStore idempotencyStore;
    @Mock private InvoiceVersionRepository invoiceVersionRepository;

    private IssueInvoiceService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new IssueInvoiceService(invoiceRepository, customerRepository, idGenerator, eventPublisher,
                auditRepository, new LedgerService(), ledgerRepository, idempotencyStore, invoiceVersionRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    private void stubCustomerExists() {
        when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                .thenReturn(Optional.of(new Customer(customerId, "CUST001", "Acme", "USD")));
        // open balance for credit check
        lenient().when(invoiceRepository.findOpenByTenantAndCustomer(eq(tenantId), eq(customerId)))
                .thenReturn(List.of());
    }

    @Nested
    @DisplayName("Issue Invoice")
    class IssueInvoice {

        @Test
        @DisplayName("should issue invoice with single line and post ledger")
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
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
            verify(ledgerRepository).saveAll(eq(tenantId), entriesCaptor.capture());
            assertEquals(2, entriesCaptor.getValue().size());
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
            verify(ledgerRepository).saveAll(eq(tenantId), any());
        }

        @Test
        @DisplayName("should honor idempotency key and return cached invoice id")
        void shouldHonorIdempotencyKey() {
            InvoiceId expectedId = InvoiceId.generate();
            when(idGenerator.newInvoiceId()).thenReturn(expectedId);
            stubCustomerExists();
            when(idempotencyStore.find(eq(tenantId), eq("invoice:key-1"))).thenReturn(Optional.empty());

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-003", customerId.getValue().toString(), "Acme", "USD", LocalDate.now(),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("50.00")))
            );

            InvoiceId first = service.issue(tenantId, command, "key-1");
            assertEquals(expectedId, first);
            verify(idempotencyStore).put(eq(tenantId), eq("invoice:key-1"), anyString(), eq(expectedId.getValue().toString()));
            verify(invoiceRepository, times(1)).save(eq(tenantId), any());
        }

        @Test
        @DisplayName("should return cached id when idempotency key hits")
        void shouldReturnCachedOnIdempotencyHit() {
            InvoiceId cachedId = InvoiceId.generate();
            stubCustomerExists();
            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-004", customerId.getValue().toString(), "Acme", "USD", LocalDate.now(),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("50.00")))
            );

            when(idGenerator.newInvoiceId()).thenReturn(cachedId);
            when(idempotencyStore.find(eq(tenantId), eq("invoice:key-2"))).thenReturn(Optional.empty());
            service.issue(tenantId, command, "key-2");

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(idempotencyStore).put(eq(tenantId), eq("invoice:key-2"), hashCaptor.capture(), anyString());
            String hash = hashCaptor.getValue();

            when(idempotencyStore.find(eq(tenantId), eq("invoice:key-2")))
                    .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                            "invoice:key-2", hash, cachedId.getValue().toString(), java.time.Instant.now())));

            InvoiceId second = service.issue(tenantId, command, "key-2");
            assertEquals(cachedId, second);
            verify(invoiceRepository, times(1)).save(eq(tenantId), any());
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

        @Test
        @DisplayName("should create pure DRAFT without ledger or InvoiceIssued")
        void shouldCreateDraftWithoutLedger() {
            InvoiceId expectedId = InvoiceId.generate();
            when(idGenerator.newInvoiceId()).thenReturn(expectedId);
            stubCustomerExists();

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-DRAFT", customerId.getValue().toString(), "Acme", "USD", LocalDate.now().plusDays(30),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("75.00"))),
                    false
            );

            InvoiceId result = service.issue(tenantId, command);

            assertEquals(expectedId, result);
            verify(invoiceRepository).save(eq(tenantId), any());
            verify(auditRepository).save(eq(tenantId), any());
            verify(ledgerRepository, never()).saveAll(any(), any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should reject issue for blocked customer")
        void shouldRejectBlockedCustomer() {
            Customer blocked = new Customer(customerId, "CUST001", "Acme", "USD");
            blocked.block();
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(blocked));

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-BLOCK", customerId.getValue().toString(), "Acme", "USD", LocalDate.now(),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("50.00")))
            );

            assertThrows(CustomerNotInvoiceableException.class, () -> service.issue(tenantId, command));
            verify(invoiceRepository, never()).save(any(), any());
        }

        @Test
        @DisplayName("should reject issue when credit limit exceeded")
        void shouldRejectCreditLimitExceeded() {
            Customer limited = new Customer(customerId, "CUST001", "Acme", "USD");
            limited.setCreditLimit(new BigDecimal("100.00"));
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(limited));
            when(invoiceRepository.findOpenByTenantAndCustomer(eq(tenantId), eq(customerId)))
                    .thenReturn(List.of());

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-CL", customerId.getValue().toString(), "Acme", "USD", LocalDate.now(),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("500.00")))
            );

            assertThrows(CustomerNotInvoiceableException.class, () -> service.issue(tenantId, command));
            verify(invoiceRepository, never()).save(any(), any());
        }
    }
}
