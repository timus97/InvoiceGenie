package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.WebhookUseCase;
import com.invoicegenie.ar.domain.model.webhook.WebhookSubscription;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Webhooks", description = "Customer webhook subscriptions")
public class WebhookResource {

    private final WebhookUseCase webhookUseCase;

    public WebhookResource(WebhookUseCase webhookUseCase) {
        this.webhookUseCase = webhookUseCase;
    }

    @POST
    @Operation(summary = "Create webhook subscription")
    public Response create(CreateWebhookDto dto) {
        try {
            var tenantId = TenantContext.getCurrentTenant();
            WebhookSubscription sub = webhookUseCase.create(tenantId,
                    new WebhookUseCase.CreateWebhookCommand(dto.url(), dto.secret(), dto.eventTypes()));
            return Response.status(201).entity(toDto(sub)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @GET
    @Operation(summary = "List webhooks")
    public Response list() {
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(webhookUseCase.list(tenantId).stream().map(this::toDto).collect(Collectors.toList())).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        return webhookUseCase.get(tenantId, UUID.fromString(id))
                .map(s -> Response.ok(toDto(s)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Webhook not found")).build());
    }

    @POST
    @Path("/{id}/deactivate")
    public Response deactivate(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        return webhookUseCase.deactivate(tenantId, UUID.fromString(id))
                .map(s -> Response.ok(toDto(s)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Webhook not found")).build());
    }

    @POST
    @Path("/{id}/activate")
    public Response activate(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        return webhookUseCase.activate(tenantId, UUID.fromString(id))
                .map(s -> Response.ok(toDto(s)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Webhook not found")).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        if (!webhookUseCase.delete(tenantId, UUID.fromString(id))) {
            return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Webhook not found")).build();
        }
        return Response.noContent().build();
    }

    private WebhookDto toDto(WebhookSubscription s) {
        return new WebhookDto(s.getId().toString(), s.getUrl(), s.getEventTypes(), s.isActive(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
    }

    public record CreateWebhookDto(String url, String secret, String eventTypes) {}
    public record WebhookDto(String id, String url, String eventTypes, boolean active, String createdAt) {}
}