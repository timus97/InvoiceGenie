package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

/**
 * REST adapter: maps HTTP to use cases. No business logic; tenant from TenantContext.
 */
@Path("/api/v1/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    private final IssueInvoiceUseCase issueInvoiceUseCase;

    public InvoiceResource(IssueInvoiceUseCase issueInvoiceUseCase) {
        this.issueInvoiceUseCase = issueInvoiceUseCase;
    }

    @POST
    public Response create(InvoiceCreateDto dto) {
        var command = new IssueInvoiceUseCase.IssueInvoiceCommand(
                dto.invoiceNumber(),
                dto.customerRef(),
                dto.currencyCode(),
                dto.dueDate(),
                dto.lines().stream()
                        .map(l -> new IssueInvoiceUseCase.IssueInvoiceCommand.LineItem(l.description(), l.amount()))
                        .toList()
        );
        var tenantId = TenantContext.getCurrentTenant();
        var id = issueInvoiceUseCase.issue(tenantId, command);
        return Response.created(URI.create("/api/v1/invoices/" + id.getValue())).entity(new InvoiceIdDto(id.getValue().toString())).build();
    }

    public record InvoiceCreateDto(String invoiceNumber, String customerRef, String currencyCode, java.time.LocalDate dueDate, List<LineDto> lines) {
        public record LineDto(String description, java.math.BigDecimal amount) {}
    }

    public record InvoiceIdDto(String id) {}
}
