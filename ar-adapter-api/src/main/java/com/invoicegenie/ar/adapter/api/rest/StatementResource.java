package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.RequireRoles;
import com.invoicegenie.ar.application.port.inbound.DunningUseCase;
import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.application.service.NotificationEnqueueService;
import com.invoicegenie.ar.application.service.NotificationRateLimiter;
import com.invoicegenie.ar.application.service.PdfDocumentService;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.enterprise.inject.Instance;
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
import java.util.Optional;
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
    private final PdfDocumentService pdfDocumentService;
    private final NotificationEnqueueService enqueueService;
    private final CustomerRepository customerRepository;
    private final NotificationRateLimiter rateLimiter;
    private final Instance<AuditRepository> auditRepository;

    @Inject
    public StatementResource(StatementUseCase statementUseCase,
                             DunningUseCase dunningUseCase,
                             PdfDocumentService pdfDocumentService,
                             NotificationEnqueueService enqueueService,
                             CustomerRepository customerRepository,
                             NotificationRateLimiter rateLimiter,
                             Instance<AuditRepository> auditRepository) {
        this.statementUseCase = statementUseCase;
        this.dunningUseCase = dunningUseCase;
        this.pdfDocumentService = pdfDocumentService;
        this.enqueueService = enqueueService;
        this.customerRepository = customerRepository;
        this.rateLimiter = rateLimiter;
        this.auditRepository = auditRepository;
    }

    @GET
    @Path("/customers/{customerId}/statement")
    @Operation(summary = "Generate customer open-item statement as-of date (JSON, CSV, or PDF)")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN})
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
                    if ("pdf".equalsIgnoreCase(format)) {
                        byte[] pdf = pdfDocumentService.generateStatementPdf(s);
                        return Response.ok(pdf)
                                .type("application/pdf")
                                .header("Content-Disposition",
                                        "attachment; filename=\"statement-" + customerId + ".pdf\"")
                                .build();
                    }
                    return Response.ok(toDto(s)).build();
                })
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Customer not found")).build());
    }

    @POST
    @Path("/customers/{customerId}/statement/send")
    @Operation(summary = "Enqueue statement email (with optional PDF attachment) (PP-013)")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN})
    public Response sendStatement(@PathParam("customerId") String customerId,
                                  @QueryParam("asOf") LocalDate asOf,
                                  @QueryParam("attachPdf") @DefaultValue("true") boolean attachPdf) {
        try {
            UUID uuid = UUID.fromString(customerId);
            var tenantId = TenantContext.getCurrentTenant();
            rateLimiter.checkOrThrow(tenantId);
            CustomerId cid = CustomerId.of(uuid);
            Optional<Customer> customerOpt = customerRepository.findByTenantAndId(tenantId, cid);
            if (customerOpt.isEmpty()) {
                return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Customer not found")).build();
            }
            Optional<StatementUseCase.CustomerStatement> statementOpt =
                    statementUseCase.generate(tenantId, cid, asOf);
            if (statementOpt.isEmpty()) {
                return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Customer not found")).build();
            }
            Notification n = enqueueService.enqueueStatementSend(
                    tenantId, customerOpt.get(), statementOpt.get(), attachPdf);
            if (auditRepository.isResolvable()) {
                auditRepository.get().save(tenantId, AuditEntry.create(
                        tenantId, "NOTIFICATION_SEND", cid.getValue(),
                        "STATEMENT_SEND", null,
                        "{\"notificationId\":\"" + n.getId() + "\",\"status\":\"" + n.getStatus() + "\"}"));
            }
            return Response.status(202).entity(new StatementSendDto(
                    n.getId().toString(),
                    n.getStatus().name(),
                    n.getChannel().name(),
                    n.getEventType().name(),
                    n.getDestination(),
                    n.getSkipReason() != null ? n.getSkipReason().name() : null
            )).build();
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("RATE_LIMITED")) {
                return Response.status(429).entity(new ErrorResponse("RATE_LIMITED", e.getMessage())).build();
            }
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
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

    public record StatementSendDto(String notificationId, String status, String channel,
                                   String eventType, String destination, String skipReason) {}
}
