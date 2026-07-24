package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryLog;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookSubscription;
import com.invoicegenie.shared.domain.TenantId;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Delivers outbox domain events to active HTTP webhook subscriptions (STORY-009).
 *
 * <p>HMAC signature, SSRF URL checks, timeout, exponential backoff retries, delivery log.
 */
@ApplicationScoped
public class WebhookDispatcher {

    private static final Logger LOG = Logger.getLogger(WebhookDispatcher.class);

    @Inject
    WebhookRepository webhookRepository;

    @Inject
    WebhookDeliveryRepository deliveryRepository;

    @ConfigProperty(name = "webhook.delivery.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "webhook.delivery.timeout-ms", defaultValue = "5000")
    long timeoutMs;

    @ConfigProperty(name = "webhook.delivery.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "webhook.delivery.retry-batch", defaultValue = "50")
    int retryBatch;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Fan-out an outbox entry to matching active subscriptions for its tenant.
     */
    @Transactional
    public void dispatch(OutboxEntry entry) {
        if (!enabled || entry == null) {
            return;
        }
        TenantId tenantId = entry.getTenantId();
        List<WebhookSubscription> subs = webhookRepository.findActiveByTenant(tenantId);
        for (WebhookSubscription sub : subs) {
            if (!sub.matches(entry.getEventType())) {
                continue;
            }
            WebhookDeliveryLog log = WebhookDeliveryLog.start(
                    tenantId, sub.getId(), entry.getId(), entry.getEventType(), sub.getUrl(), entry.getPayload());
            deliverWithLog(log, sub);
        }
    }

    @Scheduled(every = "${webhook.delivery.retry-interval:30s}", delayed = "20s")
    @Transactional
    public void processRetries() {
        if (!enabled) {
            return;
        }
        try {
            List<WebhookDeliveryLog> due = deliveryRepository.findDueRetries(Instant.now(), retryBatch);
            for (WebhookDeliveryLog log : due) {
                retryDelivery(log);
            }
        } catch (Exception e) {
            LOG.debugf("Webhook retry pass skipped: %s", e.getMessage());
        }
    }

    private void retryDelivery(WebhookDeliveryLog existing) {
        try {
            var subOpt = webhookRepository.findById(existing.getTenantId(), existing.getSubscriptionId());
            if (subOpt.isEmpty() || !subOpt.get().isActive()) {
                existing.markDead(existing.getAttemptCount(), null, "subscription inactive");
                deliveryRepository.save(existing);
                return;
            }
            deliverWithLog(existing, subOpt.get());
        } catch (Exception e) {
            LOG.warnf(e, "Webhook retry failed for delivery %s", existing.getId());
        }
    }

    void deliverWithLog(WebhookDeliveryLog log, WebhookSubscription sub) {
        int attempt = log.getAttemptCount() + 1;
        try {
            SsrfUrlValidator.validateHttpUrl(sub.getUrl());
        } catch (IllegalArgumentException ssrf) {
            log.markBlockedSsrf(ssrf.getMessage());
            deliveryRepository.save(log);
            LOG.warnf("Webhook SSRF blocked subscription=%s url=%s: %s",
                    sub.getId(), sub.getUrl(), ssrf.getMessage());
            return;
        }

        String body = log.getPayload() == null ? "{}" : log.getPayload();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(sub.getUrl()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "InvoiceGenie-Webhook/1.0")
                    .header("X-InvoiceGenie-Event", log.getEventType())
                    .header("X-InvoiceGenie-Delivery", log.getId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            String sig = WebhookSignature.sign(sub.getSecret(), body);
            if (!sig.isEmpty()) {
                builder.header(WebhookSignature.HEADER, sig);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            String snippet = response.body();
            if (code >= 200 && code < 300) {
                log.markSuccess(code, snippet);
                deliveryRepository.save(log);
                LOG.infof("Webhook delivered event=%s subscription=%s status=%d",
                        log.getEventType(), sub.getId(), code);
                return;
            }
            scheduleOrDead(log, attempt, code, "HTTP " + code + ": " + truncate(snippet));
        } catch (Exception e) {
            scheduleOrDead(log, attempt, null, e.getMessage());
        }
    }

    private void scheduleOrDead(WebhookDeliveryLog log, int attempt, Integer httpStatus, String error) {
        if (attempt >= maxAttempts) {
            log.markDead(attempt, httpStatus, error);
            deliveryRepository.save(log);
            LOG.warnf("Webhook DEAD after %d attempts event=%s: %s", attempt, log.getEventType(), error);
            return;
        }
        long backoffSec = (long) Math.min(3600, Math.pow(2, attempt) * 5L);
        Instant next = Instant.now().plusSeconds(backoffSec);
        log.markRetry(attempt, httpStatus, error, next);
        deliveryRepository.save(log);
        LOG.infof("Webhook RETRY attempt=%d next=%s event=%s: %s",
                attempt, next, log.getEventType(), error);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}