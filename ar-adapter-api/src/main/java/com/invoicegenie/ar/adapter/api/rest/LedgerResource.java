package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.LedgerQueryUseCase;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
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
 * REST adapter: Ledger operations for double-entry accounting.
 * Swagger UI at /q/swagger-ui/
 */
@Path("/api/v1/ledger")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Ledger", description = "Double-entry accounting ledger")
public class LedgerResource {

    private final LedgerQueryUseCase ledgerQueryUseCase;

    public LedgerResource(LedgerQueryUseCase ledgerQueryUseCase) {
        this.ledgerQueryUseCase = ledgerQueryUseCase;
    }

    @GET
    @Path("/accounts")
    @Operation(summary = "List all available accounts")
    public Response listAccounts() {
        List<AccountDto> accounts = ledgerQueryUseCase.listAccounts().stream()
                .map(a -> new AccountDto(a.name(), a.getDisplayName(), a.getType().name()))
                .collect(Collectors.toList());
        return Response.ok(accounts).build();
    }

    @GET
    @Path("/balance/{account}")
    @Operation(summary = "Get balance for an account")
    public Response getAccountBalance(@PathParam("account") String accountName,
                                       @QueryParam("currency") @DefaultValue("USD") String currency) {
        try {
            Account account = Account.valueOf(accountName.toUpperCase());
            var tenantId = TenantContext.getCurrentTenant();
            BigDecimal balance = ledgerQueryUseCase.getAccountBalance(tenantId, account, currency);
            return Response.ok(new BalanceDto(account.name(), balance, currency)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("INVALID_ACCOUNT", "Unknown account: " + accountName)).build();
        }
    }

    @GET
    @Path("/transactions/{transactionId}")
    @Operation(summary = "Get all entries for a transaction")
    public Response getTransaction(@PathParam("transactionId") String transactionId) {
        var tenantId = TenantContext.getCurrentTenant();
        List<LedgerEntry> entries = ledgerQueryUseCase.getTransaction(tenantId, UUID.fromString(transactionId));
        if (entries.isEmpty()) {
            return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Transaction not found")).build();
        }
        return Response.ok(toTransactionDto(entries)).build();
    }

    @GET
    @Path("/reference/{type}/{id}")
    @Operation(summary = "Get all entries for a reference (invoice or payment)")
    public Response getByReference(@PathParam("type") String referenceType,
                                   @PathParam("id") String referenceId) {
        var tenantId = TenantContext.getCurrentTenant();
        List<LedgerEntry> entries = ledgerQueryUseCase.getByReference(
                tenantId, referenceType.toUpperCase(), UUID.fromString(referenceId));
        return Response.ok(entries.stream().map(this::toEntryDto).collect(Collectors.toList())).build();
    }

    @POST
    @Path("/validate")
    @Operation(summary = "Validate that debits equal credits for a transaction or reference")
    public Response validateEntries(ValidateRequestDto dto) {
        if (dto == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "request body required"))
                    .build();
        }

        var tenantId = TenantContext.getCurrentTenant();
        List<LedgerEntry> entries;

        if (dto.transactionId() != null && !dto.transactionId().isBlank()) {
            entries = ledgerQueryUseCase.getTransaction(tenantId, UUID.fromString(dto.transactionId()));
        } else if (dto.referenceType() != null && !dto.referenceType().isBlank()
                && dto.referenceId() != null && !dto.referenceId().isBlank()) {
            entries = ledgerQueryUseCase.getByReference(
                    tenantId, dto.referenceType().toUpperCase(), UUID.fromString(dto.referenceId()));
        } else {
            return Response.status(400)
                    .entity(new ErrorResponse("VALIDATION_ERROR",
                            "Provide transactionId or referenceType+referenceId"))
                    .build();
        }

        if (entries.isEmpty()) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "No ledger entries found for the given criteria"))
                    .build();
        }

        boolean balanced = ledgerQueryUseCase.validateBalanced(entries);
        BigDecimal totalDebit = entries.stream()
                .filter(e -> e.getEntryType() == com.invoicegenie.ar.domain.model.ledger.EntryType.DEBIT)
                .map(e -> e.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream()
                .filter(e -> e.getEntryType() == com.invoicegenie.ar.domain.model.ledger.EntryType.CREDIT)
                .map(e -> e.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Response.ok(new ValidationResultDto(
                balanced,
                balanced ? "Debits equal credits" : "Debits do not equal credits",
                entries.size(),
                totalDebit,
                totalCredit
        )).build();
    }

    private TransactionDto toTransactionDto(List<LedgerEntry> entries) {
        return new TransactionDto(
                entries.get(0).getTransactionId().toString(),
                entries.stream().map(this::toEntryDto).collect(Collectors.toList()),
                ledgerQueryUseCase.validateBalanced(entries),
                entries.get(0).getCreatedAt()
        );
    }

    private EntryDto toEntryDto(LedgerEntry entry) {
        return new EntryDto(
                entry.getId().toString(),
                entry.getAccount().name(),
                entry.getAmount().getAmount(),
                entry.getEntryType().name(),
                entry.getDescription(),
                entry.getTransactionId().toString(),
                entry.getReferenceType(),
                entry.getReferenceId() != null ? entry.getReferenceId().toString() : null,
                entry.getCreatedAt()
        );
    }

    public record AccountDto(String code, String name, String type) {}
    public record BalanceDto(String account, BigDecimal balance, String currency) {}
    public record TransactionDto(String transactionId, List<EntryDto> entries, boolean balanced, java.time.Instant createdAt) {}
    public record EntryDto(String id, String account, BigDecimal amount, String entryType,
                          String description, String transactionId, String referenceType,
                          String referenceId, java.time.Instant createdAt) {}
    public record ValidateRequestDto(String transactionId, String referenceType, String referenceId) {}
    public record ValidationResultDto(
            boolean balanced,
            String message,
            int entryCount,
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {}
}
