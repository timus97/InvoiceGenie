package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST adapter: Cheque processing operations.
 * Swagger UI at /q/swagger-ui/
 */
@Path("/api/v1/cheques")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cheques", description = "Cheque processing system")
public class ChequeResource {

    private final ChequeService chequeService;
    private final ChequeRepository chequeRepository;
    private final InvoiceLifecycleUseCase invoiceLifecycleUseCase;

    public ChequeResource(ChequeService chequeService, ChequeRepository chequeRepository,
                          InvoiceLifecycleUseCase invoiceLifecycleUseCase) {
        this.chequeService = chequeService;
        this.chequeRepository = chequeRepository;
        this.invoiceLifecycleUseCase = invoiceLifecycleUseCase;
    }

    @POST
    @Operation(summary = "Create a new cheque (RECEIVED state)")
    public Response createCheque(CreateChequeDto dto) {
        try {
            var tenantId = TenantContext.getCurrentTenant();
            UUID chequeId = UUID.randomUUID();
            
            Cheque cheque = new Cheque(
                    chequeId,
                    dto.chequeNumber(),
                    new com.invoicegenie.ar.domain.model.customer.CustomerId(UUID.fromString(dto.customerId())),
                    Money.of(dto.amount().toString(), dto.currencyCode() != null ? dto.currencyCode() : "USD"),
                    dto.bankName(),
                    dto.bankBranch(),
                    dto.chequeDate(),
                    dto.notes()
            );
            
            chequeRepository.save(tenantId, cheque);
            return Response.status(201).entity(toDto(cheque)).build();
        } catch (Exception e) {
            return Response.status(400).entity(new ErrorDto("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/deposit")
    @Operation(summary = "Deposit cheque to bank (RECEIVED → DEPOSITED)")
    public Response depositCheque(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var chequeId = UUID.fromString(id);
        
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    var result = chequeService.deposit(tenantId, cheque);
                    if (result.success()) {
                        chequeRepository.save(tenantId, cheque);
                        return Response.ok(toDto(cheque)).build();
                    } else {
                        return Response.status(400).entity(new ErrorDto("INVALID_STATE", result.message())).build();
                    }
                })
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Cheque not found")).build());
    }

    @POST
    @Path("/{id}/clear")
    @Operation(summary = "Clear cheque (DEPOSITED → CLEARED)")
    public Response clearCheque(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var chequeId = UUID.fromString(id);
        
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    var result = chequeService.clear(tenantId, cheque);
                    if (result.success()) {
                        chequeRepository.save(tenantId, cheque);
                        return Response.ok(new ClearResultDto(toDto(cheque), 
                                result.ledgerEntries().stream().map(this::toEntryDto).collect(Collectors.toList())))
                                .build();
                    } else {
                        return Response.status(400).entity(new ErrorDto("INVALID_STATE", result.message())).build();
                    }
                })
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Cheque not found")).build());
    }

    @POST
    @Path("/{id}/bounce")
    @Operation(summary = "Bounce cheque (DEPOSITED → BOUNCED)")
    public Response bounceCheque(@PathParam("id") String id, BounceDto dto) {
        var tenantId = TenantContext.getCurrentTenant();
        var chequeId = UUID.fromString(id);
        
        if (dto.reason() == null || dto.reason().isBlank()) {
            return Response.status(400).entity(new ErrorDto("VALIDATION_ERROR", "Bounce reason is required")).build();
        }
        
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    var result = chequeService.bounce(tenantId, cheque, dto.reason());
                    if (result.success()) {
                        chequeRepository.save(tenantId, cheque);
                        
                        // Reopen affected invoices
                        List<String> reopenedInvoices = new ArrayList<>();
                        for (UUID invoiceId : result.affectedInvoiceIds()) {
                            invoiceLifecycleUseCase.reopen(tenantId, new InvoiceId(invoiceId), 
                                    "Cheque " + cheque.getChequeNumber() + " bounced: " + dto.reason());
                            reopenedInvoices.add(invoiceId.toString());
                        }
                        
                        return Response.ok(new BounceResultDto(toDto(cheque),
                                result.reverseEntries().stream().map(this::toEntryDto).collect(Collectors.toList()),
                                result.affectedInvoiceIds()))
                                .build();
                    } else {
                        return Response.status(400).entity(new ErrorDto("INVALID_STATE", result.message())).build();
                    }
                })
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Cheque not found")).build());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get cheque by ID")
    public Response getCheque(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var chequeId = UUID.fromString(id);
        
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> Response.ok(toDto(cheque)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Cheque not found")).build());
    }

    @GET
    @Operation(summary = "List cheques with optional status filter")
    public Response listCheques(@QueryParam("status") String status) {
        var tenantId = TenantContext.getCurrentTenant();
        List<Cheque> cheques;
        
        if (status != null) {
            try {
                ChequeStatus chequeStatus = ChequeStatus.valueOf(status.toUpperCase());
                cheques = chequeRepository.findByTenantAndStatus(tenantId, chequeStatus);
            } catch (IllegalArgumentException e) {
                return Response.status(400).entity(new ErrorDto("INVALID_STATUS", "Unknown status: " + status)).build();
            }
        } else {
            // Return all - simplified, in production would have pagination
            cheques = List.of();
        }
        
        return Response.ok(cheques.stream().map(this::toDto).collect(Collectors.toList())).build();
    }

    // DTOs
    private ChequeDto toDto(Cheque cheque) {
        return new ChequeDto(
                cheque.getId().toString(),
                cheque.getChequeNumber(),
                cheque.getCustomerId().getValue().toString(),
                cheque.getAmount().getAmount(),
                cheque.getAmount().getCurrencyCode(),
                cheque.getBankName(),
                cheque.getBankBranch(),
                cheque.getChequeDate(),
                cheque.getReceivedDate(),
                cheque.getDepositedDate(),
                cheque.getClearedDate(),
                cheque.getBouncedDate(),
                cheque.getBounceReason(),
                cheque.getStatus().name(),
                cheque.getPaymentId() != null ? cheque.getPaymentId().toString() : null,
                cheque.getAllocatedInvoiceIds().stream().map(UUID::toString).collect(Collectors.toList()),
                cheque.getNotes()
        );
    }

    private EntryDto toEntryDto(LedgerEntry entry) {
        return new EntryDto(
                entry.getId().toString(),
                entry.getAccount().name(),
                entry.getAmount().getAmount(),
                entry.getEntryType().name(),
                entry.getDescription()
        );
    }

    public record CreateChequeDto(String chequeNumber, String customerId, BigDecimal amount,
            String currencyCode, String bankName, String bankBranch, LocalDate chequeDate, String notes) {}
    public record ChequeDto(String id, String chequeNumber, String customerId, BigDecimal amount,
            String currencyCode, String bankName, String bankBranch, LocalDate chequeDate,
            LocalDate receivedDate, LocalDate depositedDate, LocalDate clearedDate,
            LocalDate bouncedDate, String bounceReason, String status, String paymentId,
            List<String> allocatedInvoiceIds, String notes) {}
    public record BounceDto(String reason) {}
    public record ClearResultDto(ChequeDto cheque, List<EntryDto> ledgerEntries) {}
    public record BounceResultDto(ChequeDto cheque, List<EntryDto> reverseEntries, List<UUID> affectedInvoices) {}
    public record EntryDto(String id, String account, BigDecimal amount, String entryType, String description) {}
    public record ErrorDto(String code, String message) {}
}
