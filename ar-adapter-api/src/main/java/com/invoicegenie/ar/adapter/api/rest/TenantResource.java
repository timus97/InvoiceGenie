package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.TenantUseCase;
import com.invoicegenie.ar.domain.model.tenant.Tenant;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Platform tenant registry API (onboarding new organizations).
 */
@Path("/api/v1/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tenants", description = "Tenant registry / onboarding")
public class TenantResource {

    private final TenantUseCase tenantUseCase;

    public TenantResource(TenantUseCase tenantUseCase) {
        this.tenantUseCase = tenantUseCase;
    }

    @POST
    @Operation(summary = "Create a tenant")
    public Response create(CreateTenantDto dto) {
        try {
            Tenant t = tenantUseCase.create(new TenantUseCase.CreateTenantCommand(
                    dto.code(), dto.name(), dto.baseCurrency(), dto.settingsJson()));
            return Response.status(201).entity(toDto(t)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @GET
    @Operation(summary = "List tenants")
    public Response list(@QueryParam("status") String status) {
        try {
            return Response.ok(tenantUseCase.list(status).stream().map(this::toDto).collect(Collectors.toList())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("INVALID_STATUS", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get tenant by id")
    public Response get(@PathParam("id") String id) {
        return tenantUseCase.get(UUID.fromString(id))
                .map(t -> Response.ok(toDto(t)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Tenant not found")).build());
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update tenant")
    public Response update(@PathParam("id") String id, UpdateTenantDto dto) {
        try {
            return tenantUseCase.update(UUID.fromString(id),
                            new TenantUseCase.UpdateTenantCommand(dto.name(), dto.baseCurrency(), dto.settingsJson()))
                    .map(t -> Response.ok(toDto(t)).build())
                    .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Tenant not found")).build());
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/activate")
    @Operation(summary = "Activate tenant")
    public Response activate(@PathParam("id") String id) {
        return tenantUseCase.activate(UUID.fromString(id))
                .map(t -> Response.ok(toDto(t)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Tenant not found")).build());
    }

    @POST
    @Path("/{id}/suspend")
    @Operation(summary = "Suspend tenant")
    public Response suspend(@PathParam("id") String id) {
        return tenantUseCase.suspend(UUID.fromString(id))
                .map(t -> Response.ok(toDto(t)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Tenant not found")).build());
    }

    private TenantDto toDto(Tenant t) {
        return new TenantDto(
                t.getId().toString(),
                t.getCode(),
                t.getName(),
                t.getBaseCurrency(),
                t.getStatus().name(),
                t.getSettingsJson(),
                t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null
        );
    }

    public record CreateTenantDto(String code, String name, String baseCurrency, String settingsJson) {}
    public record UpdateTenantDto(String name, String baseCurrency, String settingsJson) {}
    public record TenantDto(String id, String code, String name, String baseCurrency, String status,
                            String settingsJson, String createdAt, String updatedAt) {}
}