package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.WebhookDeliveryEntity;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryLog;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryStatus;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WebhookDeliveryRepositoryAdapter implements WebhookDeliveryRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(WebhookDeliveryLog log) {
        WebhookDeliveryEntity e = new WebhookDeliveryEntity();
        e.setId(log.getId());
        e.setTenantId(log.getTenantId().getValue());
        e.setSubscriptionId(log.getSubscriptionId());
        e.setOutboxId(log.getOutboxId());
        e.setEventType(log.getEventType());
        e.setUrl(log.getUrl());
        e.setPayload(log.getPayload());
        e.setStatus(log.getStatus().name());
        e.setAttemptCount(log.getAttemptCount());
        e.setHttpStatus(log.getHttpStatus());
        e.setResponseSnippet(log.getResponseSnippet());
        e.setErrorMessage(log.getErrorMessage());
        e.setNextAttemptAt(log.getNextAttemptAt());
        e.setCreatedAt(log.getCreatedAt());
        e.setUpdatedAt(log.getUpdatedAt());
        em.merge(e);
    }

    @Override
    public Optional<WebhookDeliveryLog> findById(UUID id) {
        WebhookDeliveryEntity e = em.find(WebhookDeliveryEntity.class, id);
        return e == null ? Optional.empty() : Optional.of(toDomain(e));
    }

    @Override
    public List<WebhookDeliveryLog> findDueRetries(Instant now, int limit) {
        return em.createQuery(
                        "SELECT d FROM WebhookDeliveryEntity d WHERE d.status = 'RETRY' AND d.nextAttemptAt <= :now ORDER BY d.nextAttemptAt ASC",
                        WebhookDeliveryEntity.class)
                .setParameter("now", now)
                .setMaxResults(Math.min(Math.max(limit, 1), 500))
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    public List<WebhookDeliveryLog> findRecentByTenant(TenantId tenantId, int limit) {
        return em.createQuery(
                        "SELECT d FROM WebhookDeliveryEntity d WHERE d.tenantId = :tid ORDER BY d.createdAt DESC",
                        WebhookDeliveryEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setMaxResults(Math.min(Math.max(limit, 1), 500))
                .getResultStream().map(this::toDomain).toList();
    }

    private WebhookDeliveryLog toDomain(WebhookDeliveryEntity e) {
        return new WebhookDeliveryLog(
                e.getId(),
                TenantId.of(e.getTenantId()),
                e.getSubscriptionId(),
                e.getOutboxId(),
                e.getEventType(),
                e.getUrl(),
                e.getPayload(),
                WebhookDeliveryStatus.valueOf(e.getStatus()),
                e.getAttemptCount(),
                e.getHttpStatus(),
                e.getResponseSnippet(),
                e.getErrorMessage(),
                e.getNextAttemptAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}