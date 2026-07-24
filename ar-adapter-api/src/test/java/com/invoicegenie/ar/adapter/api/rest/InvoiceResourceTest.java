package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.GetInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceVersionUseCase;
import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("InvoiceResource")
@ExtendWith(MockitoExtension.class)
class InvoiceResourceTest {

    @Mock private IssueInvoiceUseCase issueInvoiceUseCase;
    @Mock private GetInvoiceUseCase getInvoiceUseCase;
    @Mock private ListInvoicesUseCase listInvoicesUseCase;
    @Mock private InvoiceLifecycleUseCase lifecycleUseCase;
    @Mock private ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase;
    @Mock InvoiceVersionUseCase invoiceVersionUseCase;

    private InvoiceResource resource;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        resource = new InvoiceResource(
                issueInvoiceUseCase,
                getInvoiceUseCase,
                listInvoicesUseCase,
                lifecycleUseCase,
                applyInvoicePaymentUseCase,
                invoiceVersionUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice sampleInvoice(InvoiceId id) {
        Invoice inv = new Invoice(id, "INV-1", null, "CUST", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30),
                List.of(new InvoiceLine(1, "Svc", Money.of("100.00", "USD"))));
        inv.issue();
        return inv;
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        void missingNumber() {
            var dto = new InvoiceResource.InvoiceCreateDto(null, UUID.randomUUID().toString(), null, "USD",
                    LocalDate.now(), List.of(new InvoiceResource.LineDto(1, "x", new BigDecimal("1"))), null);
            assertEquals(400, resource.create(null, dto).getStatus());
        }

        @Test
        void missingCustomer() {
            var dto = new InvoiceResource.InvoiceCreateDto("INV", null, null, "USD",
                    LocalDate.now(), List.of(new InvoiceResource.LineDto(1, "x", new BigDecimal("1"))), null);
            assertEquals(400, resource.create(null, dto).getStatus());
        }

        @Test
        void emptyLines() {
            var dto = new InvoiceResource.InvoiceCreateDto("INV", UUID.randomUUID().toString(), null, "USD",
                    LocalDate.now(), List.of(), null);
            assertEquals(400, resource.create(null, dto).getStatus());
        }

        @Test
        void success() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(issueInvoiceUseCase.issue(eq(tenantId), any(), isNull())).thenReturn(id);
            var dto = new InvoiceResource.InvoiceCreateDto("INV-1", UUID.randomUUID().toString(), "Acme", "USD",
                    LocalDate.now().plusDays(10),
                    List.of(new InvoiceResource.LineDto(1, "Svc", new BigDecimal("100.00"))), null);
            Response r = resource.create(null, dto);
            assertEquals(201, r.getStatus());
            assertTrue(r.getLocation().toString().contains(id.getValue().toString()));
        }

        @Test
        void withIdempotencyKey() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(issueInvoiceUseCase.issue(eq(tenantId), any(), eq("key-1"))).thenReturn(id);
            var dto = new InvoiceResource.InvoiceCreateDto("INV-1", UUID.randomUUID().toString(), null, "USD",
                    LocalDate.now().plusDays(10),
                    List.of(new InvoiceResource.LineDto(1, "Svc", new BigDecimal("100.00"))), true);
            assertEquals(201, resource.create("key-1", dto).getStatus());
        }

        @Test
        void draftCreate() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(issueInvoiceUseCase.issue(eq(tenantId), any(), isNull())).thenReturn(id);
            var dto = new InvoiceResource.InvoiceCreateDto("INV-DRAFT", UUID.randomUUID().toString(), null, "USD",
                    LocalDate.now().plusDays(10),
                    List.of(new InvoiceResource.LineDto(1, "Svc", new BigDecimal("50.00"))), false);
            assertEquals(201, resource.create(null, dto).getStatus());
        }
    }

    @Nested
    @DisplayName("get/list")
    class Read {
        @Test
        void getFound() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(getInvoiceUseCase.get(tenantId, id)).thenReturn(Optional.of(sampleInvoice(id)));
            assertEquals(200, resource.get(id.getValue().toString()).getStatus());
        }

        @Test
        void getMissing() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(getInvoiceUseCase.get(tenantId, id)).thenReturn(Optional.empty());
            assertEquals(404, resource.get(id.getValue().toString()).getStatus());
        }

        @Test
        void list() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(listInvoicesUseCase.list(eq(tenantId), eq(20), isNull(), isNull()))
                    .thenReturn(new ListInvoicesUseCase.PageResult(List.of(sampleInvoice(id)), Optional.empty(), 1));
            assertEquals(200, resource.list(20, null, null).getStatus());
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {
        @Test
        void issueOk() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(lifecycleUseCase.issue(tenantId, id)).thenReturn(Optional.of(sampleInvoice(id)));
            assertEquals(200, resource.issue(id.getValue().toString()).getStatus());
        }

        @Test
        void issueMissing() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(lifecycleUseCase.issue(tenantId, id)).thenReturn(Optional.empty());
            assertEquals(404, resource.issue(id.getValue().toString()).getStatus());
        }

        @Test
        void overdue() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(lifecycleUseCase.markOverdue(eq(tenantId), eq(id), any())).thenReturn(Optional.of(sampleInvoice(id)));
            assertEquals(200, resource.markOverdue(id.getValue().toString(), "").getStatus());
        }

        @Test
        void writeOff() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(lifecycleUseCase.writeOff(tenantId, id, "bad debt")).thenReturn(Optional.of(sampleInvoice(id)));
            assertEquals(200, resource.writeOff(id.getValue().toString(), new InvoiceResource.WriteOffDto("bad debt")).getStatus());
        }

        @Test
        void updateDueDate() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            LocalDate d = LocalDate.now().plusDays(60);
            when(lifecycleUseCase.updateDueDate(tenantId, id, d)).thenReturn(Optional.of(sampleInvoice(id)));
            assertEquals(200, resource.updateDueDate(id.getValue().toString(), new InvoiceResource.DueDateDto(d)).getStatus());
        }
    }

    @Nested
    @DisplayName("payment")
    class Payment {
        @Test
        void fullPay() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(applyInvoicePaymentUseCase.apply(eq(tenantId), eq(id), any()))
                    .thenReturn(Optional.of(sampleInvoice(id)));
            assertEquals(200, resource.applyPayment(id.getValue().toString(), new InvoiceResource.PaymentDto(true, null)).getStatus());
        }

        @Test
        void missing() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(applyInvoicePaymentUseCase.apply(eq(tenantId), eq(id), any())).thenReturn(Optional.empty());
            assertEquals(404, resource.applyPayment(id.getValue().toString(), new InvoiceResource.PaymentDto(true, null)).getStatus());
        }

        @Test
        void nullBody() {
            InvoiceId id = InvoiceId.of(UUID.randomUUID());
            when(applyInvoicePaymentUseCase.apply(eq(tenantId), eq(id), any()))
                    .thenReturn(Optional.of(sampleInvoice(id)));
            assertEquals(200, resource.applyPayment(id.getValue().toString(), null).getStatus());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {
        @Test
        void methodNotAllowed() {
            assertEquals(405, resource.delete().getStatus());
        }
    }
}