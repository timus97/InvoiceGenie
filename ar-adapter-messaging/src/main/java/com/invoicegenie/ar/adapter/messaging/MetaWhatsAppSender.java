package com.invoicegenie.ar.adapter.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicegenie.ar.application.port.outbound.WhatsAppSender;
import com.invoicegenie.ar.application.service.NotificationDestinationValidator;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplate;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Meta WhatsApp Cloud API sender (PP-002). Fail-closed without token/phone-number-id.
 */
@ApplicationScoped
@Typed(MetaWhatsAppSender.class)
public class MetaWhatsAppSender implements WhatsAppSender {

    private static final Logger LOG = Logger.getLogger(MetaWhatsAppSender.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "invoicegenie.notifications.whatsapp-meta.access-token", defaultValue = "none")
    String accessToken;

    @ConfigProperty(name = "invoicegenie.notifications.whatsapp-meta.phone-number-id", defaultValue = "none")
    String phoneNumberId;

    @ConfigProperty(name = "invoicegenie.notifications.whatsapp-meta.api-version", defaultValue = "v18.0")
    String apiVersion;

    @ConfigProperty(name = "invoicegenie.notifications.whatsapp-meta.language-code", defaultValue = "en")
    String languageCode;

    @Inject
    Instance<MetaGraphHttpClient> httpClientInstance;

    @Inject
    Instance<NotificationTemplateRepository> templateRepository;

    /** Optional override for unit tests. */
    MetaGraphHttpClient httpClientOverride;

    @Override
    public SendResult send(Notification notification) {
        if (!isConfigured(accessToken) || !isConfigured(phoneNumberId)) {
            return SendResult.fail(
                    "WhatsApp Meta not configured (access-token / phone-number-id)", null);
        }

        String to = normalizePhone(notification.getDestination());
        if (to == null) {
            return SendResult.fail("Invalid WhatsApp destination", null);
        }

        MetaGraphHttpClient client = resolveClient();
        if (client == null) {
            return SendResult.fail("Meta Graph HTTP client unavailable", null);
        }

        try {
            String templateName = resolveTemplateName(notification);
            String bodyJson = buildPayload(to, templateName, notification);
            String url = String.format("https://graph.facebook.com/%s/%s/messages",
                    apiVersion != null ? apiVersion.trim() : "v18.0",
                    phoneNumberId.trim());

            MetaGraphHttpClient.HttpResult result = client.postJson(url, accessToken.trim(), bodyJson);
            if (result.statusCode() >= 200 && result.statusCode() < 300) {
                String messageId = extractMessageId(result.body());
                LOG.infof("[WHATSAPP-META] sent id=%s to=%s providerMsgId=%s",
                        notification.getId(),
                        NotificationDestinationValidator.mask(to),
                        messageId);
                return SendResult.ok(messageId != null ? messageId : "meta-" + notification.getId());
            }
            LOG.warnf("[WHATSAPP-META] send failed id=%s status=%d body=%s",
                    notification.getId(), result.statusCode(), truncate(result.body()));
            return SendResult.fail(
                    "Meta API HTTP " + result.statusCode() + ": " + truncate(result.body()),
                    result.statusCode());
        } catch (Exception e) {
            LOG.warnf(e, "[WHATSAPP-META] send error id=%s to=%s",
                    notification.getId(),
                    NotificationDestinationValidator.mask(to));
            return SendResult.fail("Meta WhatsApp send failed: " + e.getMessage(), null);
        }
    }

    private String resolveTemplateName(Notification notification) {
        if (notification.getTemplateId() != null
                && templateRepository != null
                && templateRepository.isResolvable()) {
            return templateRepository.get().findById(notification.getTemplateId())
                    .map(NotificationTemplate::getWhatsappTemplateName)
                    .filter(n -> n != null && !n.isBlank())
                    .orElseGet(() -> defaultTemplateName(notification));
        }
        return defaultTemplateName(notification);
    }

    private static String defaultTemplateName(Notification notification) {
        if (notification.getEventType() == null) {
            return "invoice_issued_en";
        }
        return switch (notification.getEventType()) {
            case INVOICE_ISSUED -> "invoice_issued_en";
            case PAYMENT_REMINDER -> "payment_reminder_en";
            case DUNNING_NOTICE -> "dunning_notice_en";
            case STATEMENT_SEND -> "statement_send_en";
        };
    }

    private String buildPayload(String to, String templateName, Notification notification) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("to", to);
        root.put("type", "template");

        ObjectNode template = root.putObject("template");
        template.put("name", templateName != null ? templateName : "invoice_issued_en");
        ObjectNode language = template.putObject("language");
        language.put("code", languageCode != null && !languageCode.isBlank() ? languageCode : "en");

        // Pass rendered body (and subject if present) as body text parameters when useful.
        ArrayNode components = template.putArray("components");
        ObjectNode bodyComponent = components.addObject();
        bodyComponent.put("type", "body");
        ArrayNode parameters = bodyComponent.putArray("parameters");
        String bodyText = notification.getBody();
        if (bodyText == null || bodyText.isBlank()) {
            bodyText = notification.getSubject() != null ? notification.getSubject() : "Notification";
        }
        // Meta templates often have multiple variables; send single body blob as first param.
        // Operators should align approved template variables with invoice_number etc.
        // For robustness we also support a text-message fallback when template name is "text".
        if ("text".equalsIgnoreCase(templateName)) {
            root.put("type", "text");
            root.remove("template");
            ObjectNode text = root.putObject("text");
            text.put("body", bodyText);
            return MAPPER.writeValueAsString(root);
        }

        // Prefer structured params if body looks short enough for a single variable template
        ObjectNode param = parameters.addObject();
        param.put("type", "text");
        // Meta rejects very long params; keep under 1024
        param.put("text", bodyText.length() > 1000 ? bodyText.substring(0, 1000) : bodyText);

        return MAPPER.writeValueAsString(root);
    }

    private static String extractMessageId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            JsonNode messages = node.get("messages");
            if (messages != null && messages.isArray() && messages.size() > 0) {
                JsonNode id = messages.get(0).get("id");
                if (id != null && !id.isNull()) {
                    return id.asText();
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
        return null;
    }

    private MetaGraphHttpClient resolveClient() {
        if (httpClientOverride != null) {
            return httpClientOverride;
        }
        if (httpClientInstance != null && httpClientInstance.isResolvable()) {
            return httpClientInstance.get();
        }
        return null;
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !"none".equalsIgnoreCase(value.trim());
    }

    private static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.trim().replaceAll("\\D", "");
        if (digits.length() < 8 || digits.length() > 15) {
            return null;
        }
        return digits; // Meta expects digits without +
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
