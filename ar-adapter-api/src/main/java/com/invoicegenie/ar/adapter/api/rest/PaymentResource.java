package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentQueryUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentReversalUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST adapter: Payment create, list, get, reverse/refund, allocation.
 */
@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payments", description = "Payment allocation operations")
public class PaymentResource {

    private final PaymentAllocationUseCase allocationUseCase;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final PaymentQueryUseCase paymentQueryUseCase;
    private final PaymentReversalUseCase paymentReversalUseCase;

    @jakarta.inject.Inject
    public PaymentResource(PaymentAllocationUseCase allocationUseCase,
                           RecordPaymentUseCase recordPaymentUseCase,
                           PaymentQueryUseCase paymentQueryUseCase,
                           PaymentReversalUseCase paymentReversalUseCase) {
        this.allocationUseCase = allocationUseCase;
        this.recordPaymentUseCase = recordPaymentUseCase;
        this.paymentQueryUseCase = paymentQueryUseCase;
        this.paymentReversalUseCase = paymentReversalUseCase;
    }

    /** Test helper constructor. */
    public PaymentResource(PaymentAllocationUseCase allocationUseCase,
                           RecordPaymentUseCase recordPaymentUseCase) {
        this(allocationUseCase, recordPaymentUseCase, null, null);
    }

    @POST
    @Operation(summary = "Create/record a new payment received from a customer")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Payment created"),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "409", description = "Idempotency conflict or duplicate payment number")
    })
    public Response create(
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            CreatePaymentRequestDto dto) {
        var tenantId = TenantContext.getCurrentTenant();

        var command = new RecordPaymentUseCase.RecordPaymentCommand(
                dto.paymentNumber(),
                dto.customerId(),
                dto.amount(),
                dto.currencyCode(),
                dto.paymentDate(),
                dto.method(),
                dto.reference(),
                dto.notes()
        );

        var paymentId = recordPaymentUseCase.record(tenantId, command, idempotencyKey);
        return Response.status(201)
                .entity(new PaymentCreatedDto(paymentId.getValue().toString(), dto.paymentNumber()))
                .build();
    }

    @GET
    @Operation(summary = "List payments with optional filters")
    public Response list(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("fromDate") LocalDate fromDate,
            @QueryParam("toDate") LocalDate toDate,
            @QueryParam("unallocatedOnly") @DefaultValue("false") boolean unallocatedOnly,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        if (paymentQueryUseCase == null) {
            return Response.status(501).entity(new ErrorDto("NOT_IMPLEMENTED", "Payment query not wired")).build();
        }
        var tenantId = TenantContext.getCurrentTenant();
        PaymentStatus st = null;
        if (status != null && !status.isBlank()) {
            st = PaymentStatus.valueOf(status.toUpperCase());
        }
        UUID cust = customerId != null && !customerId.isBlank() ? UUID.fromString(customerId) : null;
        var result = paymentQueryUseCase.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                cust, st, fromDate, toDate, unallocatedOnly, limit));
        return Response.ok(new PaymentListDto(
                result.items().stream().map(this::toPaymentDto).toList(),
                result.count()
        )).build();
    }

    @GET
    @Path("/{paymentId}")
    @Operation(summary = "Get payment by id with allocations summary")
    public Response get(@PathParam("paymentId") String paymentId) {
        if (paymentQueryUseCase == null) {
            return Response.status(501).entity(new ErrorDto("NOT_IMPLEMENTED", "Payment query not wired")).build();
        }
        var tenantId = TenantContext.getCurrentTenant();
        return paymentQueryUseCase.get(tenantId, PaymentId.of(UUID.fromString(paymentId)))
                .map(p -> Response.ok(toPaymentDto(p)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Payment not found")).build());
    }

    @POST
    @Path("/{paymentId}/reverse")
    @Operation(summary = "Reverse a RECEIVED payment (unwind allocations + ledger)")
    public Response reverse(
            @PathParam("paymentId") String paymentId,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            ReasonDto dto) {
        if (paymentReversalUseCase == null) {
            return Response.status(501).entity(new ErrorDto("NOT_IMPLEMENTED", "Reversal not wired")).build();
        }
        var tenantId = TenantContext.getCurrentTenant();
        String reason = dto != null ? dto.reason() : null;
        return paymentReversalUseCase.reverse(tenantId, PaymentId.of(UUID.fromString(paymentId)), reason, idempotencyKey)
                .map(r -> Response.ok(new ReversalDto(r.paymentId().getValue().toString(), r.newStatus(),
                        r.affectedInvoiceIds().stream().map(UUID::toString).toList(), r.message())).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Payment not found")).build());
    }

    @POST
    @Path("/{paymentId}/refund")
    @Operation(summary = "Refund a RECEIVED payment (unwind allocations + ledger)")
    public Response refund(
            @PathParam("paymentId") String paymentId,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            ReasonDto dto) {
        if (paymentReversalUseCase == null) {
            return Response.status(501).entity(new ErrorDto("NOT_IMPLEMENTED", "Refund not wired")).build();
        }
        var tenantId = TenantContext.getCurrentTenant();
        String reason = dto != null ? dto.reason() : null;
        return paymentReversalUseCase.refund(tenantId, PaymentId.of(UUID.fromString(paymentId)), reason, idempotencyKey)
                .map(r -> Response.ok(new ReversalDto(r.paymentId().getValue().toString(), r.newStatus(),
                        r.affectedInvoiceIds().stream().map(UUID::toString).toList(), r.message())).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Payment not found")).build());
    }

    @POST
    @Path("/{paymentId}/allocate/fifo")
    @Operation(summary = "Auto-allocate payment to invoices using FIFO (oldest first)")
    public Response autoAllocateFIFO(
            @PathParam("paymentId") String paymentId,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            AllocationRequestDto dto) {
        
        var tenantId = TenantContext.getCurrentTenant();
        var paymentIdObj = PaymentId.of(UUID.fromString(paymentId));
        var allocatedBy = dto.allocatedBy() != null ? UUID.fromString(dto.allocatedBy()) : UUID.randomUUID();
        
        return allocationUseCase.autoAllocateFIFO(tenantId, paymentIdObj, allocatedBy, idempotencyKey)
                .map(result -> Response.ok(toResultDto(result)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Payment not found")).build());
    }

    @POST
    @Path("/{paymentId}/allocate/manual")
    @Operation(summary = "Manually allocate payment to specific invoices")
    public Response manualAllocate(
            @PathParam("paymentId") String paymentId,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            ManualAllocationDto dto) {
        
        var tenantId = TenantContext.getCurrentTenant();
        var paymentIdObj = PaymentId.of(UUID.fromString(paymentId));
        var allocatedBy = dto.allocatedBy() != null ? UUID.fromString(dto.allocatedBy()) : UUID.randomUUID();

        // Currency is corrected in PaymentAllocationService to the payment's currency
        var requests = dto.allocations().stream()
                .map(a -> new PaymentAllocationUseCase.ManualAllocationRequest(
                        InvoiceId.of(UUID.fromString(a.invoiceId())),
                        Money.of(a.amount().toString(), "USD"),
                        a.notes()))
                .toList();
        
        return allocationUseCase.manualAllocate(tenantId, paymentIdObj, requests, allocatedBy, idempotencyKey)
                .map(result -> Response.ok(toResultDto(result)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Payment not found")).build());
    }

    @GET
    @Path("/{paymentId}/allocations")
    @Operation(summary = "Get all allocations for a payment")
    public Response getAllocations(@PathParam("paymentId") String paymentId) {
        var tenantId = TenantContext.getCurrentTenant();
        var paymentIdObj = PaymentId.of(UUID.fromString(paymentId));
        
        return allocationUseCase.getAllocations(tenantId, paymentIdObj)
                .map(result -> Response.ok(toResultDto(result)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Payment not found")).build());
    }

    @GET
    @Path("/invoices/{invoiceId}/allocations")
    @Operation(summary = "Get all allocations for an invoice")
    public Response getInvoiceAllocations(@PathParam("invoiceId") String invoiceId) {
        var tenantId = TenantContext.getCurrentTenant();
        var invoiceIdObj = InvoiceId.of(UUID.fromString(invoiceId));
        
        var allocations = allocationUseCase.getInvoiceAllocations(tenantId, invoiceIdObj);
        return Response.ok(new InvoiceAllocationsDto(
                invoiceId,
                allocations.stream()
                        .map(a -> new AllocationDetailDto(a.invoiceId().getValue().toString(), 
                                a.amount().getAmount(), a.allocationId().toString()))
                        .toList()
        )).build();
    }

    private PaymentDto toPaymentDto(Payment p) {
        return new PaymentDto(
                p.getId().getValue().toString(),
                p.getPaymentNumber(),
                p.getCustomerId().getValue().toString(),
                p.getAmount().getAmount(),
                p.getAmount().getCurrencyCode(),
                p.getAmountUnallocated().getAmount(),
                p.getPaymentDate(),
                p.getMethod().name(),
                p.getReference(),
                p.getNotes(),
                p.getStatus().name(),
                p.getVersion(),
                p.getAllocations().stream()
                        .map(a -> new AllocationDetailDto(
                                a.getInvoiceId().getValue().toString(),
                                a.getAmount().getAmount(),
                                a.getId().toString()))
                        .toList()
        );
    }

    private AllocationResultDto toResultDto(PaymentAllocationUseCase.AllocationResult result) {
        return new AllocationResultDto(
                result.paymentId().getValue().toString(),
                result.allocations().stream()
                        .map(a -> new AllocationDetailDto(
                                a.invoiceId().getValue().toString(),
                                a.amount().getAmount(),
                                a.allocationId().toString()))
                        .toList(),
                result.totalAllocated().getAmount(),
                result.remainingUnallocated().getAmount(),
                result.errors(),
                result.paymentVersion(),
                result.isFullyAllocated()
        );
    }

    public record AllocationRequestDto(String allocatedBy) {}
    public record ManualAllocationDto(String allocatedBy, List<ManualAllocationItemDto> allocations) {}
    public record ManualAllocationItemDto(String invoiceId, BigDecimal amount, String notes) {}
    public record AllocationResultDto(String paymentId, List<AllocationDetailDto> allocations, 
            BigDecimal totalAllocated, BigDecimal remainingUnallocated, 
            List<String> errors, long version, boolean fullyAllocated) {}
    public record AllocationDetailDto(String invoiceId, BigDecimal amount, String allocationId) {}
    public record InvoiceAllocationsDto(String invoiceId, List<AllocationDetailDto> allocations) {}
    public record ErrorDto(String code, String message) {}
    
    public record CreatePaymentRequestDto(
            String paymentNumber,
            String customerId,
            BigDecimal amount,
            String currencyCode,
            java.time.LocalDate paymentDate,
            PaymentMethod method,
            String reference,
            String notes
    ) {}
    
    public record PaymentCreatedDto(String id, String paymentNumber) {}
    public record PaymentDto(
            String id,
            String paymentNumber,
            String customerId,
            BigDecimal amount,
            String currencyCode,
            BigDecimal amountUnallocated,
            LocalDate paymentDate,
            String method,
            String reference,
            String notes,
            String status,
            long version,
            List<AllocationDetailDto> allocations
    ) {}
    public record PaymentListDto(List<PaymentDto> items, int count) {}
    public record ReasonDto(String reason) {}
    public record ReversalDto(String paymentId, String status, List<String> affectedInvoiceIds, String message) {}
}
