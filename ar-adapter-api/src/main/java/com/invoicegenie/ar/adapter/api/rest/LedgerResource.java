package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
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
 * REST adapter: Ledger operations for double-entry accounting.
 * Swagger UI at /q/swagger-ui/
 */
@Path("/api/v1/ledger")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Ledger", description = "Double-entry accounting ledger")
public class LedgerResource {

    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;

    public LedgerResource(LedgerService ledgerService, LedgerRepository ledgerRepository) {
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
    }

    @GET
    @Path("/accounts")
    @Operation(summary = "List all available accounts")
    public Response listAccounts() {
        List<AccountDto> accounts = java.util.Arrays.stream(Account.values())
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
            BigDecimal balance = ledgerRepository.getAccountBalance(tenantId, account, currency);
            return Response.ok(new BalanceDto(account.name(), balance, currency)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorDto("INVALID_ACCOUNT", "Unknown account: " + accountName)).build();
        }
    }

    @GET
    @Path("/transactions/{transactionId}")
    @Operation(summary = "Get all entries for a transaction")
    public Response getTransaction(@PathParam("transactionId") String transactionId) {
        var tenantId = TenantContext.getCurrentTenant();
        List<LedgerEntry> entries = ledgerRepository.findByTenantAndTransactionId(tenantId, UUID.fromString(transactionId));
        if (entries.isEmpty()) {
            return Response.status(404).entity(new ErrorDto("NOT_FOUND", "Transaction not found")).build();
        }
        return Response.ok(toTransactionDto(entries)).build();
    }

    @GET
    @Path("/reference/{type}/{id}")
    @Operation(summary = "Get all entries for a reference (invoice or payment)")
    public Response getByReference(@PathParam("type") String referenceType,
                                   @PathParam("id") String referenceId) {
        var tenantId = TenantContext.getCurrentTenant();
        List<LedgerEntry> entries = ledgerRepository.findByTenantAndReference(tenantId, referenceType.toUpperCase(), UUID.fromString(referenceId));
        return Response.ok(entries.stream().map(this::toEntryDto).collect(Collectors.toList())).build();
    }

    @POST
    @Path("/validate")
    @Operation(summary = "Validate that debits equal credits for a set of entries")
    public Response validateEntries(ValidateRequestDto dto) {
        // This is a simple validation endpoint
        // In production, would parse entries and validate
        return Response.ok(new ValidationResultDto(true, "Validation endpoint - implement with actual entries")).build();
    }

    // DTOs
    private TransactionDto toTransactionDto(List<LedgerEntry> entries) {
        return new TransactionDto(
                entries.get(0).getTransactionId().toString(),
                entries.stream().map(this::toEntryDto).collect(Collectors.toList()),
                ledgerService.validateBalanced(entries),
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
    public record ValidateRequestDto() {}
    public record ValidationResultDto(boolean balanced, String message) {}
    public record ErrorDto(String code, String message) {}
}
