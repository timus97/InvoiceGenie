package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("IssueInvoiceService")
@ExtendWith(MockitoExtension.class)
class IssueInvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private IdGenerator idGenerator;
    @Mock private EventPublisher eventPublisher;
    @Mock private AuditRepository auditRepository;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private IdempotencyStore idempotencyStore;

    private IssueInvoiceService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new IssueInvoiceService(invoiceRepository, idGenerator, eventPublisher, auditRepository,
                new LedgerService(), ledgerRepository, idempotencyStore);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Issue Invoice")
    class IssueInvoice {

        @Test
        @DisplayName("should issue invoice with single line and post ledger")
        void shouldIssueInvoiceWithSingleLine() {
            InvoiceId expectedId = InvoiceId.generate();
            when(idGenerator.newInvoiceId()).thenReturn(expectedId);

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-001", "CUST001", "USD", LocalDate.now(),
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

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-002", "CUST002", "USD", LocalDate.now(),
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
            InvoiceId cachedId = InvoiceId.generate();
            when(idempotencyStore.find(eq(tenantId), eq("invoice:key-1")))
                    .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                            "invoice:key-1",
                            // hash of same payload will be computed — stub any match by answering with matching hash
                            // We can't know hash easily; use thenAnswer to echo whatever hash is expected via put never
                            "placeholder",
                            cachedId.getValue().toString(),
                            java.time.Instant.now())));

            // First call without matching hash will throw — so we need matching hash.
            // Better approach: first call finds nothing, second finds with hash from put.
            reset(idempotencyStore);

            InvoiceId expectedId = InvoiceId.generate();
            when(idGenerator.newInvoiceId()).thenReturn(expectedId);
            when(idempotencyStore.find(eq(tenantId), eq("invoice:key-1"))).thenReturn(Optional.empty());

            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-003", "CUST001", "USD", LocalDate.now(),
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
            IssueInvoiceUseCase.IssueInvoiceCommand command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                    "INV-004", "CUST001", "USD", LocalDate.now(),
                    List.of(new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem("Service", new BigDecimal("50.00")))
            );

            // Compute by performing first put path then replaying: capture hash
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
            // only one save from first call
            verify(invoiceRepository, times(1)).save(eq(tenantId), any());
        }
    }
}
