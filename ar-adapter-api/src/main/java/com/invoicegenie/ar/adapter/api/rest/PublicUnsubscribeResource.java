package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.NotificationPreferenceUseCase;
import com.invoicegenie.ar.application.service.NotificationRateLimiter;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Public unsubscribe endpoint (PP-011) — no authentication required.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Public", description = "Unauthenticated public endpoints")
public class PublicUnsubscribeResource {

    private final NotificationPreferenceUseCase preferenceUseCase;
    private final NotificationRateLimiter rateLimiter;

    @Inject
    public PublicUnsubscribeResource(NotificationPreferenceUseCase preferenceUseCase,
                                     NotificationRateLimiter rateLimiter) {
        this.preferenceUseCase = preferenceUseCase;
        this.rateLimiter = rateLimiter;
    }

    @GET
    @Path("/unsubscribe")
    @Operation(summary = "Unsubscribe from notifications via signed token (GET)")
    public Response unsubscribeGet(@QueryParam("token") String token) {
        return unsubscribe(token);
    }

    @POST
    @Path("/unsubscribe")
    @Operation(summary = "Unsubscribe from notifications via signed token (POST)")
    public Response unsubscribePost(@QueryParam("token") String token) {
        return unsubscribe(token);
    }

    private Response unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "token is required")).build();
        }
        try {
            // Rate-limit by a fixed public key (pilot) — uses demo tenant id as bucket
            rateLimiter.checkOrThrow(TenantId.of(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")));
        } catch (IllegalStateException e) {
            return Response.status(429).entity(new ErrorResponse("RATE_LIMITED", e.getMessage())).build();
        }
        return preferenceUseCase.unsubscribeByToken(token.trim())
                .map(p -> Response.ok(new UnsubscribeResultDto(
                        true,
                        p.getChannel().name(),
                        p.getCustomerId().getValue().toString(),
                        "You have been unsubscribed from " + p.getChannel().name() + " notifications"
                )).build())
                .orElse(Response.status(400).entity(new ErrorResponse("INVALID_TOKEN",
                        "Token is invalid or expired")).build());
    }

    public record UnsubscribeResultDto(boolean success, String channel, String customerId, String message) {}
}
