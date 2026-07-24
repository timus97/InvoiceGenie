package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.AuditQueryUseCase;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/audit")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Audit", description = "Audit log query and export")
public class AuditResource {

    private final AuditQueryUseCase auditQueryUseCase;

    public AuditResource(AuditQueryUseCase auditQueryUseCase) {
        this.auditQueryUseCase = auditQueryUseCase;
    }

    @GET
    @Operation(summary = "List recent audit entries")
    public Response list(@QueryParam("limit") @DefaultValue("100") int limit) {
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(auditQueryUseCase.listRecent(tenantId, limit).stream()
                .map(this::toDto).collect(Collectors.toList())).build();
    }

    @GET
    @Path("/entity/{entityType}/{entityId}")
    @Operation(summary = "List audit entries for an entity")
    public Response forEntity(@PathParam("entityType") String entityType, @PathParam("entityId") String entityId) {
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(auditQueryUseCase.listForEntity(tenantId, entityType, UUID.fromString(entityId)).stream()
                .map(this::toDto).collect(Collectors.toList())).build();
    }

    @GET
    @Path("/export")
    @Produces("text/csv")
    @Operation(summary = "Export audit log as CSV")
    public Response exportCsv(@QueryParam("limit") @DefaultValue("500") int limit) {
        var tenantId = TenantContext.getCurrentTenant();
        String csv = auditQueryUseCase.exportCsv(tenantId, limit);
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"audit-export.csv\"")
                .build();
    }

    private AuditDto toDto(AuditEntry e) {
        return new AuditDto(
                e.getId().toString(),
                e.getEntityType(),
                e.getEntityId() != null ? e.getEntityId().toString() : null,
                e.getEntityRef(),
                e.getAction(),
                e.getActorType(),
                e.getBeforeState(),
                e.getAfterState(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null
        );
    }

    public record AuditDto(String id, String entityType, String entityId, String entityRef, String action,
                           String actorType, String beforeState, String afterState, String createdAt) {}
}