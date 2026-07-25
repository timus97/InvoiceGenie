package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.NotificationAttemptEntity;
import com.invoicegenie.ar.domain.model.notification.NotificationAttempt;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptStatus;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NotificationAttemptRepositoryAdapter implements NotificationAttemptRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(NotificationAttempt attempt) {
        NotificationAttemptEntity e = new NotificationAttemptEntity();
        e.setId(attempt.getId());
        e.setTenantId(attempt.getTenantId().getValue());
        e.setNotificationId(attempt.getNotificationId());
        e.setAttemptNumber(attempt.getAttemptNumber());
        e.setStatus(attempt.getStatus().name());
        e.setProvider(attempt.getProvider());
        e.setProviderMessageId(attempt.getProviderMessageId());
        e.setHttpStatus(attempt.getHttpStatus());
        e.setErrorMessage(attempt.getErrorMessage());
        e.setResponseSnippet(attempt.getResponseSnippet());
        e.setAttemptedAt(attempt.getAttemptedAt());
        em.merge(e);
    }

    @Override
    public List<NotificationAttempt> findByNotification(TenantId tenantId, UUID notificationId) {
        return em.createQuery(
                        "SELECT a FROM NotificationAttemptEntity a WHERE a.tenantId = :tid "
                                + "AND a.notificationId = :nid ORDER BY a.attemptNumber ASC",
                        NotificationAttemptEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("nid", notificationId)
                .getResultStream().map(this::toDomain).toList();
    }

    private NotificationAttempt toDomain(NotificationAttemptEntity e) {
        return new NotificationAttempt(
                e.getId(),
                TenantId.of(e.getTenantId()),
                e.getNotificationId(),
                e.getAttemptNumber(),
                NotificationAttemptStatus.valueOf(e.getStatus()),
                e.getProvider(),
                e.getProviderMessageId(),
                e.getHttpStatus(),
                e.getErrorMessage(),
                e.getResponseSnippet(),
                e.getAttemptedAt()
        );
    }
}
