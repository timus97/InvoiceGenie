package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.GetInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceVersionUseCase;
import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter: Invoice CRUD + Lifecycle operations.
 * Swagger UI at /q/swagger-ui/
 */
@Path("/api/v1/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Invoices", description = "Invoice CRUD and lifecycle operations")
public class InvoiceResource {

    private final IssueInvoiceUseCase issueInvoiceUseCase;
    private final GetInvoiceUseCase getInvoiceUseCase;
    private final ListInvoicesUseCase listInvoicesUseCase;
    private final InvoiceLifecycleUseCase lifecycleUseCase;
    private final ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase;
    private final InvoiceVersionUseCase invoiceVersionUseCase;

    public InvoiceResource(IssueInvoiceUseCase issueInvoiceUseCase,
                           GetInvoiceUseCase getInvoiceUseCase,
                           ListInvoicesUseCase listInvoicesUseCase,
                           InvoiceLifecycleUseCase lifecycleUseCase,
                           ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase,
                           InvoiceVersionUseCase invoiceVersionUseCase) {
        this.issueInvoiceUseCase = issueInvoiceUseCase;
        this.getInvoiceUseCase = getInvoiceUseCase;
        this.listInvoicesUseCase = listInvoicesUseCase;
        this.lifecycleUseCase = lifecycleUseCase;
        this.applyInvoicePaymentUseCase = applyInvoicePaymentUseCase;
        this.invoiceVersionUseCase = invoiceVersionUseCase;
    }

    // ==================== CREATE ====================

    @POST
    @Operation(summary = "Create an invoice",
            description = "By default creates and issues immediately (backward compatible). "
                    + "Set issueImmediately=false to create a pure DRAFT with no ledger posting.")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Invoice created"),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "409", description = "Idempotency key conflict")
    })
    public Response create(
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            InvoiceCreateDto dto) {

        if (dto.invoiceNumber() == null || dto.invoiceNumber().isBlank()) {
            return error(400, "invoiceNumber required");
        }
        if (dto.customerId() == null || dto.customerId().isBlank()) {
            return error(400, "customerId required");
        }
        if (dto.lines() == null || dto.lines().isEmpty()) {
            return error(400, "at least one line required");
        }

        // customerRef optional — defaults to customerId in the command compact constructor
        String customerRef = dto.customerRef() != null && !dto.customerRef().isBlank()
                ? dto.customerRef()
                : dto.customerId();

        boolean issueImmediately = dto.issueImmediately() == null || dto.issueImmediately();

        var command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                dto.invoiceNumber(),
                dto.customerId(),
                customerRef,
                dto.currencyCode() != null ? dto.currencyCode() : "USD",
                dto.dueDate(),
                dto.lines().stream()
                        .map(l -> new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem(l.description(), l.amount()))
                        .toList(),
                issueImmediately
        );
        var tenantId = TenantContext.getCurrentTenant();
        var id = issueInvoiceUseCase.issue(tenantId, command, idempotencyKey);
        return Response.created(URI.create("/api/v1/invoices/" + id.getValue()))
                .entity(new InvoiceIdDto(id.getValue().toString()))
                .build();
    }

    // ==================== READ ====================

    @GET
    @Path("/{id}")
    @Operation(summary = "Get invoice by ID")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Invoice found"),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public Response get(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceId = InvoiceId.of(UUID.fromString(id));
        return getInvoiceUseCase.get(tenantId, invoiceId)
                .map(inv -> Response.ok(toDto(inv)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Invoice not found")).build());
    }

    @GET
    @Operation(summary = "List invoices with pagination and filtering")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Page of invoices")
    })
    public Response list(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("cursor") String cursor,
            @QueryParam("status") InvoiceStatus status) {

        var tenantId = TenantContext.getCurrentTenant();
        var result = listInvoicesUseCase.list(tenantId, limit, cursor, status);

        return Response.ok(new PageDto(
                result.items().stream().map(this::toDto).toList(),
                result.nextCursor().orElse(null),
                result.total()
        )).build();
    }

    // ==================== LIFECYCLE ====================

    @POST
    @Path("/{id}/issue")
    @Operation(summary = "Issue invoice (DRAFT → ISSUED)")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Issued"),
        @APIResponse(responseCode = "400", description = "Invalid transition"),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public Response issue(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceId = InvoiceId.of(UUID.fromString(id));
        return lifecycleUseCase.issue(tenantId, invoiceId)
                .map(inv -> Response.ok(toDto(inv)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Invoice not found")).build());
    }

    @POST
    @Path("/{id}/overdue")
    @Operation(summary = "Mark invoice as overdue")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Marked overdue"),
        @APIResponse(responseCode = "400", description = "Not past due or invalid state"),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public Response markOverdue(@PathParam("id") String id, @QueryParam("today") @DefaultValue("") String todayStr) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceId = InvoiceId.of(UUID.fromString(id));
        var today = todayStr.isBlank() ? LocalDate.now() : LocalDate.parse(todayStr);
        return lifecycleUseCase.markOverdue(tenantId, invoiceId, today)
                .map(inv -> Response.ok(toDto(inv)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Invoice not found")).build());
    }

    @POST
    @Path("/{id}/writeoff")
    @Operation(summary = "Write off invoice (OVERDUE → WRITTEN_OFF)")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Written off"),
        @APIResponse(responseCode = "400", description = "Invalid state or missing reason"),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public Response writeOff(@PathParam("id") String id, WriteOffDto dto) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceId = InvoiceId.of(UUID.fromString(id));
        return lifecycleUseCase.writeOff(tenantId, invoiceId, dto.reason())
                .map(inv -> Response.ok(toDto(inv)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Invoice not found")).build());
    }

    @POST
    @Path("/{id}/payment")
    @Operation(summary = "Apply payment to invoice (creates Payment + allocates)",
            description = "Creates a real Payment and allocates it to this invoice. "
                    + "Use fullyPaid=true (or omit amount) to pay remaining balance; "
                    + "or provide amount for a partial payment.")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Payment applied"),
        @APIResponse(responseCode = "400", description = "Invalid state or amount"),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public Response applyPayment(@PathParam("id") String id, PaymentDto dto) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceId = InvoiceId.of(UUID.fromString(id));
        PaymentDto body = dto != null ? dto : new PaymentDto(null, null);
        var command = new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(body.amount(), body.fullyPaid());
        return applyInvoicePaymentUseCase.apply(tenantId, invoiceId, command)
                .map(inv -> Response.ok(toDto(inv)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Invoice not found")).build());
    }

    @PATCH
    @Path("/{id}/due-date")
    @Operation(summary = "Update due date (DRAFT only)")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Updated"),
        @APIResponse(responseCode = "400", description = "Invalid date or state"),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public Response updateDueDate(@PathParam("id") String id, DueDateDto dto) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceId = InvoiceId.of(UUID.fromString(id));
        return lifecycleUseCase.updateDueDate(tenantId, invoiceId, dto.dueDate())
                .map(inv -> Response.ok(toDto(inv)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Invoice not found")).build());
    }

    // ==================== DELETE (soft via status) ====================

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete invoice (not allowed - returns 405)")
    public Response delete() {
        return Response.status(405).entity(new ErrorDto("METHOD_NOT_ALLOWED", "Use write-off for terminal state")).build();
    }

    // ==================== DTOs & Helpers ====================

    private InvoiceDto toDto(Invoice inv) {
        return new InvoiceDto(
                inv.getId().getValue().toString(),
                inv.getInvoiceNumber(),
                inv.getCustomerId() != null ? inv.getCustomerId().getValue().toString() : null,
                inv.getCustomerRef(),
                inv.getCurrencyCode(),
                inv.getIssueDate(),
                inv.getDueDate(),
                inv.getStatus().name(),
                inv.getTotal().getAmount(),
                inv.getIssuedAt(),
                inv.getWrittenOffAt(),
                inv.getVersion(),
                inv.getLines().stream().map(l -> new LineDto(l.getSequence(), l.getDescription(), l.getLineTotal().getAmount())).toList()
        );
    }

    private Response error(int status, String message) {
        return Response.status(status).entity(new ErrorDto("VALIDATION_ERROR", message)).build();
    }

    // Records
    /**
     * @param issueImmediately null or true → issue immediately (default); false → pure DRAFT
     */
    public record InvoiceCreateDto(
            String invoiceNumber,
            String customerId,
            String customerRef,
            String currencyCode,
            LocalDate dueDate,
            List<LineDto> lines,
            Boolean issueImmediately
    ) {}
    public record LineDto(int sequence, String description, BigDecimal amount) {}
    public record InvoiceIdDto(String id) {}
    public record InvoiceDto(String id, String invoiceNumber, String customerId, String customerRef, String currencyCode, LocalDate issueDate, LocalDate dueDate, String status, BigDecimal total, java.time.Instant issuedAt, java.time.Instant writtenOffAt, long version, List<LineDto> lines) {}
    public record PageDto(List<InvoiceDto> items, String nextCursor, long total) {}
    public record WriteOffDto(String reason) {}
    /** amount optional when fullyPaid is true; fullyPaid defaults to true when amount is omitted. */
    public record PaymentDto(Boolean fullyPaid, BigDecimal amount) {}
    public record DueDateDto(LocalDate dueDate) {}
    public record ErrorDto(String code, String message) {}
    @GET
    @Path("/{id}/versions")
    @Operation(summary = "List invoice version snapshots")
    public Response listVersions(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceId = InvoiceId.of(UUID.fromString(id));
        var versions = invoiceVersionUseCase.list(tenantId, invoiceId).stream()
                .map(v -> new InvoiceVersionDto(
                        v.getId().toString(),
                        v.getInvoiceId().getValue().toString(),
                        v.getVersion(),
                        v.getSnapshotJson(),
                        v.getChangeReason(),
                        v.getCreatedAt() != null ? v.getCreatedAt().toString() : null))
                .toList();
        return Response.ok(versions).build();
    }

    public record InvoiceVersionDto(String id, String invoiceId, long version, String snapshot,
                                    String changeReason, String createdAt) {}
}
