package com.invoicegenie.ar.adapter.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.service.NotificationSuppressionService;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppression;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/**
 * Provider delivery webhooks for bounce / complaint → suppression (PP-003).
 *
 * <p>Secured with shared secret header {@code X-Provider-Webhook-Secret}.
 * Public (no tenant JWT) — tenant comes from payload or header {@code X-Tenant-Id}.
 */
@Path("/api/v1/notifications/provider-webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notification Provider Webhooks", description = "Bounce / complaint ingest for suppressions")
public class ProviderWebhookResource {

    private static final Logger LOG = Logger.getLogger(ProviderWebhookResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NotificationSuppressionService suppressionService;

    @ConfigProperty(name = "invoicegenie.notifications.provider-webhook-secret", defaultValue = "none")
    String webhookSecret;

    @Inject
    public ProviderWebhookResource(NotificationSuppressionService suppressionService) {
        this.suppressionService = suppressionService;
    }

    @POST
    @Path("/{provider}")
    @Operation(summary = "Ingest provider bounce/complaint webhook (ses|smtp-generic|meta)")
    public Response ingest(@PathParam("provider") String provider,
                           @HeaderParam("X-Provider-Webhook-Secret") String secret,
                           @HeaderParam("X-Tenant-Id") String tenantHeader,
                           String rawBody) {
        if (!isSecretValid(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("UNAUTHORIZED", "Invalid or missing X-Provider-Webhook-Secret"))
                    .build();
        }

        String providerNorm = provider != null ? provider.trim().toLowerCase(Locale.ROOT) : "";
        if (!providerNorm.equals("ses") && !providerNorm.equals("smtp-generic") && !providerNorm.equals("meta")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_PROVIDER", "provider must be ses, smtp-generic, or meta"))
                    .build();
        }

        try {
            JsonNode root = MAPPER.readTree(rawBody != null ? rawBody : "{}");
            // SNS envelope unwrap
            if (root.has("Message") && root.has("Type")) {
                String message = root.path("Message").asText(null);
                if (message != null && !message.isBlank()) {
                    root = MAPPER.readTree(message);
                }
            }

            String eventType = firstText(root, "notificationType", "type", "eventType", "event");
            if (eventType == null) {
                eventType = firstText(root.path("eventType"), "type");
            }
            boolean actionable = isActionable(eventType, root);
            if (!actionable) {
                return Response.ok(new WebhookAckDto("ignored", null, eventType)).build();
            }

            TenantId tenantId = resolveTenant(root, tenantHeader);
            if (tenantId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("TENANT_REQUIRED",
                                "tenantId required in body or X-Tenant-Id header"))
                        .build();
            }

            NotificationChannel channel = resolveChannel(providerNorm, root);
            String destination = resolveDestination(root, channel);
            if (destination == null || destination.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("DESTINATION_REQUIRED",
                                "destination / email / phone required in payload"))
                        .build();
            }

            String reason = firstText(root, "reason", "bounceType", "complaintFeedbackType");
            if (reason == null) {
                reason = eventType != null ? eventType : "SUPPRESSED";
            }

            NotificationSuppression s = suppressionService.suppress(
                    tenantId, channel, destination, reason, providerNorm);
            LOG.infof("Suppression recorded provider=%s channel=%s tenant=%s id=%s",
                    providerNorm, channel, tenantId, s.getId());
            return Response.ok(new WebhookAckDto("suppressed", s.getId().toString(), reason)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_PAYLOAD", e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.warnf(e, "Provider webhook parse/handle failed provider=%s", providerNorm);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_PAYLOAD", "Unable to parse provider webhook"))
                    .build();
        }
    }

    private boolean isSecretValid(String provided) {
        if (!isConfigured(webhookSecret)) {
            // Fail closed when secret not configured
            return false;
        }
        if (provided == null || provided.isBlank()) {
            return false;
        }
        return constantTimeEquals(webhookSecret.trim(), provided.trim());
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !"none".equalsIgnoreCase(value.trim());
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            // still compare to reduce timing signal on length-only path somewhat
            return MessageDigest.isEqual(left, left) && false;
        }
        return MessageDigest.isEqual(left, right);
    }

    private static boolean isActionable(String eventType, JsonNode root) {
        if (eventType != null) {
            String t = eventType.trim().toLowerCase(Locale.ROOT);
            if (t.contains("bounce") || t.contains("complaint") || t.contains("suppress")
                    || "hard_bounce".equals(t) || "permanent".equals(t)) {
                return true;
            }
        }
        // SES nested bounce object
        if (root.has("bounce") || root.has("complaint")) {
            return true;
        }
        // Explicit flag
        if (root.path("suppress").asBoolean(false)) {
            return true;
        }
        // Minimal payload with destination + reason only
        return root.has("destination") || root.has("email") || root.has("phone");
    }

    private static TenantId resolveTenant(JsonNode root, String tenantHeader) {
        String tid = firstText(root, "tenantId", "tenant_id");
        if (tid == null || tid.isBlank()) {
            tid = tenantHeader;
        }
        if (tid == null || tid.isBlank()) {
            return null;
        }
        try {
            return TenantId.of(UUID.fromString(tid.trim()));
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid tenantId");
        }
    }

    private static NotificationChannel resolveChannel(String provider, JsonNode root) {
        String ch = firstText(root, "channel");
        if (ch != null) {
            try {
                return NotificationChannel.valueOf(ch.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                // fall through
            }
        }
        if ("meta".equals(provider)) {
            return NotificationChannel.WHATSAPP;
        }
        return NotificationChannel.EMAIL;
    }

    private static String resolveDestination(JsonNode root, NotificationChannel channel) {
        String dest = firstText(root, "destination", "email", "phone", "msisdn", "recipient");
        if (dest != null) {
            return dest;
        }
        // SES bounce recipients
        JsonNode bounced = root.path("bounce").path("bouncedRecipients");
        if (bounced.isArray() && bounced.size() > 0) {
            return firstText(bounced.get(0), "emailAddress", "email");
        }
        JsonNode complained = root.path("complaint").path("complainedRecipients");
        if (complained.isArray() && complained.size() > 0) {
            return firstText(complained.get(0), "emailAddress", "email");
        }
        // Meta status recipient_id
        dest = firstText(root, "recipient_id", "wa_id");
        if (dest != null) {
            return dest;
        }
        JsonNode statuses = root.path("entry").isArray() && root.path("entry").size() > 0
                ? root.path("entry").get(0).path("changes")
                : null;
        // keep simple for MVP
        return dest;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual() && fields.length == 0) {
            return node.asText();
        }
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull() && v.isValueNode()) {
                String s = v.asText();
                if (s != null && !s.isBlank()) {
                    return s.trim();
                }
            }
        }
        return null;
    }

    public record WebhookAckDto(String status, String suppressionId, String reason) {}
}
