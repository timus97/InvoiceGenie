package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.RequireRoles;
import com.invoicegenie.ar.application.port.inbound.PostingPeriodUseCase;
import com.invoicegenie.ar.domain.model.period.PostingPeriod;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin API: AR posting periods open/close (PP-025).
 */
@Path("/api/v1/posting-periods")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Posting Periods", description = "AR period close MVP")
public class PostingPeriodResource {

    private final PostingPeriodUseCase postingPeriodUseCase;

    @Inject
    public PostingPeriodResource(PostingPeriodUseCase postingPeriodUseCase) {
        this.postingPeriodUseCase = postingPeriodUseCase;
    }

    @GET
    @RequireRoles({ArRoles.TENANT_ADMIN, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR})
    @Operation(summary = "List posting periods for tenant")
    public Response list() {
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(postingPeriodUseCase.list(tenantId).stream()
                .map(this::toDto).collect(Collectors.toList())).build();
    }

    @POST
    @RequireRoles({ArRoles.TENANT_ADMIN, ArRoles.AR_CONTROLLER})
    @Operation(summary = "Open a new posting period")
    public Response open(OpenPeriodDto dto) {
        try {
            if (dto == null || dto.periodStart() == null || dto.periodEnd() == null) {
                return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR",
                        "periodStart and periodEnd are required")).build();
            }
            var tenantId = TenantContext.getCurrentTenant();
            PostingPeriod period = postingPeriodUseCase.open(tenantId, dto.periodStart(), dto.periodEnd(), dto.notes());
            return Response.status(201).entity(toDto(period)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(new ErrorResponse("STATE_ERROR", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/close")
    @RequireRoles({ArRoles.TENANT_ADMIN, ArRoles.AR_CONTROLLER})
    @Operation(summary = "Close a posting period")
    public Response close(@PathParam("id") String id, ClosePeriodDto dto) {
        try {
            UUID uuid = UUID.fromString(id);
            var tenantId = TenantContext.getCurrentTenant();
            String closedBy = dto != null ? dto.closedBy() : null;
            return postingPeriodUseCase.close(tenantId, uuid, closedBy)
                    .map(p -> Response.ok(toDto(p)).build())
                    .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Period not found")).build());
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(new ErrorResponse("STATE_ERROR", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/reopen")
    @RequireRoles({ArRoles.TENANT_ADMIN, ArRoles.AR_CONTROLLER})
    @Operation(summary = "Reopen a closed posting period")
    public Response reopen(@PathParam("id") String id) {
        try {
            UUID uuid = UUID.fromString(id);
            var tenantId = TenantContext.getCurrentTenant();
            return postingPeriodUseCase.reopen(tenantId, uuid)
                    .map(p -> Response.ok(toDto(p)).build())
                    .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Period not found")).build());
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(new ErrorResponse("STATE_ERROR", e.getMessage())).build();
        }
    }

    private PeriodDto toDto(PostingPeriod p) {
        return new PeriodDto(
                p.getId().toString(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.getStatus().name(),
                p.getClosedAt() != null ? p.getClosedAt().toString() : null,
                p.getClosedBy(),
                p.getNotes(),
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null
        );
    }

    public record OpenPeriodDto(LocalDate periodStart, LocalDate periodEnd, String notes) {}
    public record ClosePeriodDto(String closedBy) {}
    public record PeriodDto(String id, LocalDate periodStart, LocalDate periodEnd, String status,
                            String closedAt, String closedBy, String notes, String createdAt) {}
}
