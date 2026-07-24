package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.RequireRoles;
import com.invoicegenie.ar.application.port.inbound.DunningUseCase;
import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST: customer statements and dunning job trigger (STORY-015).
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Statements", description = "Customer statements and dunning")
public class StatementResource {

    private final StatementUseCase statementUseCase;
    private final DunningUseCase dunningUseCase;

    @Inject
    public StatementResource(StatementUseCase statementUseCase, DunningUseCase dunningUseCase) {
        this.statementUseCase = statementUseCase;
        this.dunningUseCase = dunningUseCase;
    }

    @GET
    @Path("/customers/{customerId}/statement")
    @Operation(summary = "Generate customer open-item statement as-of date (JSON or CSV)")
    public Response customerStatement(
            @PathParam("customerId") String customerId,
            @QueryParam("asOf") LocalDate asOf,
            @QueryParam("format") @DefaultValue("json") String format) {
        var tenantId = TenantContext.getCurrentTenant();
        return statementUseCase.generate(tenantId, CustomerId.of(UUID.fromString(customerId)), asOf)
                .map(s -> {
                    if ("csv".equalsIgnoreCase(format)) {
                        String csv = statementUseCase.toCsv(s);
                        return Response.ok(csv)
                                .type("text/csv")
                                .header("Content-Disposition",
                                        "attachment; filename=\"statement-" + customerId + ".csv\"")
                                .build();
                    }
                    return Response.ok(toDto(s)).build();
                })
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Customer not found")).build());
    }

    @POST
    @Path("/dunning/run")
    @RequireRoles({ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN})
    @Operation(summary = "Run dunning scan for current tenant (emits DunningNotice outbox events)")
    public Response runDunning(@QueryParam("asOf") LocalDate asOf) {
        var tenantId = TenantContext.getCurrentTenant();
        var result = dunningUseCase.runForTenant(tenantId, asOf);
        return Response.ok(new DunningRunDto(
                result.invoicesScanned(),
                result.noticesEmitted(),
                result.invoiceIds()
        )).build();
    }

    private StatementDto toDto(StatementUseCase.CustomerStatement s) {
        return new StatementDto(
                s.customerId(),
                s.customerCode(),
                s.customerName(),
                s.asOfDate(),
                s.openItems().stream().map(i -> new OpenItemDto(
                        i.invoiceId(), i.invoiceNumber(), i.issueDate(), i.dueDate(),
                        i.daysPastDue(), i.total(), i.amountPaid(), i.balanceDue(),
                        i.currency(), i.status()
                )).toList(),
                s.totalBalance(),
                s.currency()
        );
    }

    public record StatementDto(
            String customerId,
            String customerCode,
            String customerName,
            LocalDate asOfDate,
            List<OpenItemDto> openItems,
            BigDecimal totalBalance,
            String currency
    ) {}

    public record OpenItemDto(
            String invoiceId,
            String invoiceNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            int daysPastDue,
            BigDecimal total,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            String currency,
            String status
    ) {}

    public record DunningRunDto(int invoicesScanned, int noticesEmitted, List<String> invoiceIds) {}
}