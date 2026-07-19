package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.CreditNoteUseCase;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST adapter: Credit note operations.
 */
@Path("/api/v1/credit-notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Credit Notes", description = "Credit notes for discounts and adjustments")
public class CreditNoteResource {

    private final CreditNoteUseCase creditNoteUseCase;

    public CreditNoteResource(CreditNoteUseCase creditNoteUseCase) {
        this.creditNoteUseCase = creditNoteUseCase;
    }

    @POST
    @Operation(summary = "Generate credit note for early payment discount")
    public Response generateEarlyPaymentDiscount(GenerateCreditNoteDto dto) {
        var tenantId = TenantContext.getCurrentTenant();

        try {
            var customerId = UUID.fromString(dto.customerId());
            var discountAmount = Money.of(dto.discountAmount().toString(),
                    dto.currencyCode() != null ? dto.currencyCode() : "USD");
            var referenceInvoiceId = dto.referenceInvoiceId() != null ?
                    UUID.fromString(dto.referenceInvoiceId()) : null;

            var result = creditNoteUseCase.generateEarlyPaymentDiscount(
                    tenantId, customerId, discountAmount, referenceInvoiceId);

            if (result.success()) {
                return Response.status(201).entity(toDto(result.creditNote())).build();
            } else {
                return Response.status(400).entity(new ErrorResponse("ERROR", result.message())).build();
            }
        } catch (Exception e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/apply")
    @Operation(summary = "Apply credit note to a payment")
    public Response applyCreditNote(@PathParam("id") String id, ApplyCreditNoteDto dto) {
        var tenantId = TenantContext.getCurrentTenant();
        var creditNoteId = UUID.fromString(id);

        try {
            return creditNoteUseCase.apply(tenantId, creditNoteId, UUID.fromString(dto.paymentId()))
                    .map(creditNote -> Response.ok(new ApplyResultDto(toDto(creditNote),
                            "Credit note applied successfully")).build())
                    .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Credit note not found")).build());
        } catch (Exception e) {
            return Response.status(400).entity(new ErrorResponse("INVALID_STATE", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get credit note by ID")
    public Response getCreditNote(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var creditNoteId = UUID.fromString(id);

        return creditNoteUseCase.get(tenantId, creditNoteId)
                .map(creditNote -> Response.ok(toDto(creditNote)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Credit note not found")).build());
    }

    @GET
    @Operation(summary = "List credit notes")
    public Response listCreditNotes(@QueryParam("status") String status) {
        var tenantId = TenantContext.getCurrentTenant();
        var result = creditNoteUseCase.list(tenantId, status);

        if (!result.success()) {
            return Response.status(400).entity(new ErrorResponse("INVALID_STATUS", result.errorMessage())).build();
        }

        return Response.ok(result.creditNotes().stream().map(this::toDto).collect(Collectors.toList())).build();
    }

    private CreditNoteDto toDto(CreditNote creditNote) {
        return new CreditNoteDto(
                creditNote.getId().toString(),
                creditNote.getCreditNoteNumber(),
                creditNote.getCustomerId().getValue().toString(),
                creditNote.getAmount().getAmount(),
                creditNote.getAmount().getCurrencyCode(),
                creditNote.getType().name(),
                creditNote.getReferenceInvoiceId() != null ? creditNote.getReferenceInvoiceId().toString() : null,
                creditNote.getDescription(),
                creditNote.getStatus().name(),
                creditNote.getIssueDate(),
                creditNote.getAppliedDate(),
                creditNote.getExpiryDate(),
                creditNote.getAppliedToPaymentId() != null ? creditNote.getAppliedToPaymentId().toString() : null,
                creditNote.getNotes()
        );
    }

    public record GenerateCreditNoteDto(String customerId, BigDecimal discountAmount,
            String currencyCode, String referenceInvoiceId) {}

    public record CreditNoteDto(String id, String creditNoteNumber, String customerId, BigDecimal amount,
            String currencyCode, String type, String referenceInvoiceId, String description,
            String status, java.time.LocalDate issueDate, java.time.LocalDate appliedDate,
            java.time.LocalDate expiryDate, String appliedToPaymentId, String notes) {}

    public record ApplyCreditNoteDto(String paymentId) {}

    public record ApplyResultDto(CreditNoteDto creditNote, String message) {}
}
