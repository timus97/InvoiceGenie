package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.RequireRoles;
import com.invoicegenie.ar.application.port.inbound.NotificationPreferenceUseCase;
import com.invoicegenie.ar.application.port.inbound.NotificationPolicyUseCase;
import com.invoicegenie.ar.application.port.inbound.NotificationUseCase;
import com.invoicegenie.ar.application.service.NotificationRateLimiter;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationAttempt;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationPreference;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notifications", description = "Customer notifications (Email + WhatsApp)")
public class NotificationResource {

    private final NotificationUseCase notificationUseCase;
    private final NotificationPreferenceUseCase preferenceUseCase;
    private final NotificationPolicyUseCase policyUseCase;
    private final NotificationRateLimiter rateLimiter;

    @Inject
    public NotificationResource(NotificationUseCase notificationUseCase,
                                NotificationPreferenceUseCase preferenceUseCase,
                                NotificationPolicyUseCase policyUseCase,
                                NotificationRateLimiter rateLimiter) {
        this.notificationUseCase = notificationUseCase;
        this.preferenceUseCase = preferenceUseCase;
        this.policyUseCase = policyUseCase;
        this.rateLimiter = rateLimiter;
    }

    @GET
    @Path("/notifications")
    @Operation(summary = "List recent notifications for tenant (cursor pagination PP-023)")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN})
    public Response list(@QueryParam("limit") @DefaultValue("100") int limit,
                         @QueryParam("cursor") String cursor,
                         @QueryParam("status") String status,
                         @QueryParam("eventType") String eventType) {
        var tenantId = TenantContext.getCurrentTenant();
        var page = notificationUseCase.list(tenantId, limit, cursor);
        var stream = page.items().stream();
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            stream = stream.filter(n -> n.getStatus().name().equals(s));
        }
        if (eventType != null && !eventType.isBlank()) {
            String e = eventType.trim().toUpperCase();
            stream = stream.filter(n -> n.getEventType().name().equals(e));
        }
        List<NotificationDto> items = stream.map(this::toDto).collect(Collectors.toList());
        String next = page.nextCursor().orElse(null);
        Response.ResponseBuilder rb = Response.ok(new NotificationPageDto(items, next, items.size()));
        if (next != null && !next.isBlank()) {
            rb.header("X-Next-Cursor", next);
        }
        return rb.build();
    }

    @GET
    @Path("/notifications/metrics")
    @Operation(summary = "Notification metrics by status/channel/eventType (PP-015)")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN})
    public Response metrics(@QueryParam("days") @DefaultValue("7") int days) {
        var tenantId = TenantContext.getCurrentTenant();
        NotificationUseCase.MetricsResult m = notificationUseCase.metrics(tenantId, days);
        return Response.ok(new MetricsDto(
                m.days(),
                m.total(),
                m.byStatus().stream().map(b -> new MetricBucketDto(b.key(), b.count())).toList(),
                m.byChannel().stream().map(b -> new MetricBucketDto(b.key(), b.count())).toList(),
                m.byEventType().stream().map(b -> new MetricBucketDto(b.key(), b.count())).toList(),
                m.details().stream().map(d -> new MetricDetailDto(d.status(), d.channel(), d.eventType(), d.count())).toList()
        )).build();
    }

    @POST
    @Path("/notifications/templates/preview")
    @Operation(summary = "Preview rendered notification template (no send) (PP-014)")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN})
    public Response preview(PreviewRequestDto dto) {
        try {
            if (dto == null) {
                return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "body required")).build();
            }
            NotificationEventType eventType = dto.eventType() != null && !dto.eventType().isBlank()
                    ? NotificationEventType.valueOf(dto.eventType().trim().toUpperCase())
                    : NotificationEventType.INVOICE_ISSUED;
            NotificationChannel channel = dto.channel() != null && !dto.channel().isBlank()
                    ? NotificationChannel.valueOf(dto.channel().trim().toUpperCase())
                    : NotificationChannel.EMAIL;
            Map<String, String> vars = dto.variables() != null ? dto.variables() : Map.of();
            var tenantId = TenantContext.getCurrentTenant();
            NotificationUseCase.PreviewResult r = notificationUseCase.preview(tenantId, eventType, channel, vars);
            return Response.ok(new PreviewResponseDto(r.eventType(), r.channel(), r.subject(), r.body(), r.templateId())).build();
        } catch (IllegalArgumentException e) {
            if ("NO_TEMPLATE".equals(e.getMessage())) {
                return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "No active template")).build();
            }
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @GET
    @Path("/notifications/{id}")
    @Operation(summary = "Get notification by id")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN})
    public Response get(@PathParam("id") String id) {
        UUID uuid = parseUuid(id);
        if (uuid == null) {
            return badUuid();
        }
        var tenantId = TenantContext.getCurrentTenant();
        return notificationUseCase.get(tenantId, uuid)
                .map(n -> Response.ok(toDto(n)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Notification not found")).build());
    }

    @GET
    @Path("/notifications/{id}/attempts")
    @Operation(summary = "List delivery attempts for a notification")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN})
    public Response attempts(@PathParam("id") String id) {
        UUID uuid = parseUuid(id);
        if (uuid == null) {
            return badUuid();
        }
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(notificationUseCase.listAttempts(tenantId, uuid).stream()
                .map(this::toAttemptDto).collect(Collectors.toList())).build();
    }

    @GET
    @Path("/invoices/{invoiceId}/notifications")
    @Operation(summary = "List notifications for an invoice")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN})
    public Response byInvoice(@PathParam("invoiceId") String invoiceId,
                              @QueryParam("limit") @DefaultValue("50") int limit) {
        UUID uuid = parseUuid(invoiceId);
        if (uuid == null) {
            return badUuid();
        }
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(notificationUseCase.listByInvoice(tenantId, InvoiceId.of(uuid), limit)
                .stream().map(this::toDto).collect(Collectors.toList())).build();
    }

    @POST
    @Path("/notifications/send")
    @Operation(summary = "Manually enqueue notification(s) for an invoice")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN})
    public Response send(SendNotificationDto dto) {
        try {
            if (dto == null || dto.invoiceId() == null || dto.invoiceId().isBlank()) {
                return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "invoiceId is required")).build();
            }
            UUID invoiceUuid = parseUuid(dto.invoiceId());
            if (invoiceUuid == null) {
                return badUuid();
            }
            var tenantId = TenantContext.getCurrentTenant();
            rateLimiter.checkOrThrow(tenantId);
            NotificationEventType eventType = dto.eventType() != null && !dto.eventType().isBlank()
                    ? NotificationEventType.valueOf(dto.eventType().trim().toUpperCase())
                    : NotificationEventType.INVOICE_ISSUED;
            List<NotificationChannel> channels = parseChannels(dto.channels());
            boolean force = dto.force() != null && dto.force();
            List<Notification> results = notificationUseCase.sendForInvoice(
                    tenantId, InvoiceId.of(invoiceUuid), eventType, channels, force);
            return Response.status(202).entity(results.stream().map(this::toDto).collect(Collectors.toList())).build();
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("RATE_LIMITED")) {
                return Response.status(429).entity(new ErrorResponse("RATE_LIMITED", e.getMessage())).build();
            }
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            if ("INVOICE_NOT_FOUND".equals(e.getMessage())) {
                return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Invoice not found")).build();
            }
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @GET
    @Path("/customers/{customerId}/notification-preferences")
    @Operation(summary = "Get customer notification preferences")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN})
    public Response getPreferences(@PathParam("customerId") String customerId) {
        UUID uuid = parseUuid(customerId);
        if (uuid == null) {
            return badUuid();
        }
        try {
            var tenantId = TenantContext.getCurrentTenant();
            return Response.ok(preferenceUseCase.list(tenantId, CustomerId.of(uuid)).stream()
                    .map(this::toPrefDto).collect(Collectors.toList())).build();
        } catch (IllegalArgumentException e) {
            if ("CUSTOMER_NOT_FOUND".equals(e.getMessage())) {
                return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Customer not found")).build();
            }
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/customers/{customerId}/notification-preferences")
    @Operation(summary = "Upsert customer notification preferences")
    @RequireRoles({ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN})
    public Response putPreferences(@PathParam("customerId") String customerId, List<PreferenceDto> body) {
        UUID uuid = parseUuid(customerId);
        if (uuid == null) {
            return badUuid();
        }
        try {
            var tenantId = TenantContext.getCurrentTenant();
            List<NotificationPreferenceUseCase.PreferenceUpdate> updates = new ArrayList<>();
            if (body != null) {
                for (PreferenceDto p : body) {
                    updates.add(new NotificationPreferenceUseCase.PreferenceUpdate(
                            NotificationChannel.valueOf(p.channel().trim().toUpperCase()),
                            p.enabled(),
                            p.destinationOverride()
                    ));
                }
            }
            return Response.ok(preferenceUseCase.upsert(tenantId, CustomerId.of(uuid), updates)
                    .stream().map(this::toPrefDto).collect(Collectors.toList())).build();
        } catch (IllegalArgumentException e) {
            if ("CUSTOMER_NOT_FOUND".equals(e.getMessage())) {
                return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Customer not found")).build();
            }
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @GET
    @Path("/notification-policy")
    @Operation(summary = "Get tenant notification policy")
    @RequireRoles({ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN, ArRoles.AR_AUDITOR})
    public Response getPolicy() {
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(toPolicyDto(policyUseCase.getOrDefault(tenantId))).build();
    }

    @PUT
    @Path("/notification-policy")
    @Operation(summary = "Update tenant notification policy")
    @RequireRoles({ArRoles.TENANT_ADMIN})
    public Response putPolicy(PolicyDto dto) {
        try {
            if (dto == null) {
                return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "body required")).build();
            }
            var tenantId = TenantContext.getCurrentTenant();
            NotificationPolicy updated = policyUseCase.update(tenantId, new NotificationPolicyUseCase.UpdatePolicyCommand(
                    dto.enabled(),
                    dto.emailEnabled(),
                    dto.whatsappEnabled(),
                    dto.autoSendOnIssue(),
                    dto.preDueReminderEnabled(),
                    dto.preDueDays(),
                    dto.dunningNoticeEnabled(),
                    dto.channelsInvoiceIssued(),
                    dto.channelsPaymentReminder(),
                    dto.channelsDunningNotice(),
                    dto.quietHoursStart(),
                    dto.quietHoursEnd(),
                    dto.timezone(),
                    dto.attachPdfOnIssue()
            ));
            return Response.ok(toPolicyDto(updated)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Response badUuid() {
        return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "Invalid UUID")).build();
    }

    private List<NotificationChannel> parseChannels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<NotificationChannel> list = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                list.add(NotificationChannel.valueOf(s.trim().toUpperCase()));
            }
        }
        return list;
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId().toString(),
                n.getCustomerId() != null ? n.getCustomerId().getValue().toString() : null,
                n.getInvoiceId() != null ? n.getInvoiceId().getValue().toString() : null,
                n.getEventType().name(),
                n.getChannel().name(),
                n.getStatus().name(),
                n.getIdempotencyKey(),
                n.getDestination(),
                n.getSubject(),
                n.getSkipReason() != null ? n.getSkipReason().name() : null,
                n.getErrorMessage(),
                n.getAttemptCount(),
                n.getProviderMessageId(),
                n.getSentAt() != null ? n.getSentAt().toString() : null,
                n.getCreatedAt() != null ? n.getCreatedAt().toString() : null,
                n.getUpdatedAt() != null ? n.getUpdatedAt().toString() : null
        );
    }

    private AttemptDto toAttemptDto(NotificationAttempt a) {
        return new AttemptDto(
                a.getId().toString(),
                a.getNotificationId().toString(),
                a.getAttemptNumber(),
                a.getStatus().name(),
                a.getProvider(),
                a.getProviderMessageId(),
                a.getHttpStatus(),
                a.getErrorMessage(),
                a.getAttemptedAt() != null ? a.getAttemptedAt().toString() : null
        );
    }

    private PreferenceDto toPrefDto(NotificationPreference p) {
        return new PreferenceDto(
                p.getChannel().name(),
                p.isEnabled(),
                p.getDestinationOverride(),
                p.getOptedOutAt() != null ? p.getOptedOutAt().toString() : null
        );
    }

    private PolicyDto toPolicyDto(NotificationPolicy p) {
        return new PolicyDto(
                p.isEnabled(),
                p.isEmailEnabled(),
                p.isWhatsappEnabled(),
                p.isAutoSendOnIssue(),
                p.isPreDueReminderEnabled(),
                p.getPreDueDays(),
                p.isDunningNoticeEnabled(),
                p.getChannelsInvoiceIssued(),
                p.getChannelsPaymentReminder(),
                p.getChannelsDunningNotice(),
                p.getQuietHoursStart(),
                p.getQuietHoursEnd(),
                p.getTimezone(),
                p.isAttachPdfOnIssue()
        );
    }

    public record SendNotificationDto(String invoiceId, String eventType, List<String> channels, Boolean force) {}
    public record NotificationPageDto(List<NotificationDto> items, String nextCursor, int count) {}
    public record NotificationDto(String id, String customerId, String invoiceId, String eventType, String channel,
                                  String status, String idempotencyKey, String destination, String subject,
                                  String skipReason, String errorMessage, int attemptCount,
                                  String providerMessageId, String sentAt, String createdAt, String updatedAt) {}
    public record AttemptDto(String id, String notificationId, int attemptNumber, String status, String provider,
                             String providerMessageId, Integer httpStatus, String errorMessage, String attemptedAt) {}
    public record PreferenceDto(String channel, boolean enabled, String destinationOverride, String optedOutAt) {}
    public record PolicyDto(boolean enabled, boolean emailEnabled, boolean whatsappEnabled, boolean autoSendOnIssue,
                            boolean preDueReminderEnabled, int preDueDays, boolean dunningNoticeEnabled,
                            String channelsInvoiceIssued, String channelsPaymentReminder, String channelsDunningNotice,
                            Integer quietHoursStart, Integer quietHoursEnd, String timezone, boolean attachPdfOnIssue) {}
    public record PreviewRequestDto(String eventType, String channel, Map<String, String> variables) {}
    public record PreviewResponseDto(String eventType, String channel, String subject, String body, String templateId) {}
    public record MetricsDto(int days, long total, List<MetricBucketDto> byStatus, List<MetricBucketDto> byChannel,
                             List<MetricBucketDto> byEventType, List<MetricDetailDto> details) {}
    public record MetricBucketDto(String key, long count) {}
    public record MetricDetailDto(String status, String channel, String eventType, long count) {}
}
