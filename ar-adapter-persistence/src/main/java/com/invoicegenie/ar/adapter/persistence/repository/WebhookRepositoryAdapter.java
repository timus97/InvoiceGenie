package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.WebhookEntity;
import com.invoicegenie.ar.domain.model.webhook.WebhookRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookSubscription;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WebhookRepositoryAdapter implements WebhookRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, WebhookSubscription subscription) {
        WebhookEntity e = new WebhookEntity();
        e.setId(subscription.getId());
        e.setTenantId(tenantId.getValue());
        e.setUrl(subscription.getUrl());
        e.setSecret(subscription.getSecret());
        e.setEventTypes(subscription.getEventTypes());
        e.setActive(subscription.isActive());
        e.setCreatedAt(subscription.getCreatedAt());
        e.setUpdatedAt(subscription.getUpdatedAt());
        em.merge(e);
    }

    @Override
    public Optional<WebhookSubscription> findById(TenantId tenantId, UUID id) {
        WebhookEntity e = em.find(WebhookEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(e));
    }

    @Override
    public List<WebhookSubscription> findAllByTenant(TenantId tenantId) {
        return em.createQuery(
                        "SELECT w FROM WebhookEntity w WHERE w.tenantId = :tid ORDER BY w.createdAt DESC",
                        WebhookEntity.class)
                .setParameter("tid", tenantId.getValue())
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    public List<WebhookSubscription> findActiveByTenant(TenantId tenantId) {
        return em.createQuery(
                        "SELECT w FROM WebhookEntity w WHERE w.tenantId = :tid AND w.active = true",
                        WebhookEntity.class)
                .setParameter("tid", tenantId.getValue())
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        WebhookEntity e = em.find(WebhookEntity.class, id);
        if (e != null && e.getTenantId().equals(tenantId.getValue())) {
            em.remove(e);
        }
    }

    private WebhookSubscription toDomain(WebhookEntity e) {
        return new WebhookSubscription(e.getId(), e.getUrl(), e.getSecret(), e.getEventTypes(),
                e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
    }
}