package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST adapter: Payment allocation operations.
 * Swagger UI at /q/swagger-ui/
 */
@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payments", description = "Payment allocation operations")
public class PaymentResource {

    private final PaymentAllocationUseCase allocationUseCase;

    public PaymentResource(PaymentAllocationUseCase allocationUseCase) {
        this.allocationUseCase = allocationUseCase;
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
        
        var requests = dto.allocations().stream()
                .map(a -> new PaymentAllocationUseCase.ManualAllocationRequest(
                        InvoiceId.of(UUID.fromString(a.invoiceId())),
                        Money.of(a.amount().toString(), "USD"), // Assuming USD for now
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

    // DTOs
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
}
