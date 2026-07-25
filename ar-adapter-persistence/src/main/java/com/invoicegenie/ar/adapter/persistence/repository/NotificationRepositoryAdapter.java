package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.NotificationEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationSkipReason;
import com.invoicegenie.ar.domain.model.notification.NotificationStatus;
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
public class NotificationRepositoryAdapter implements NotificationRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(Notification notification) {
        em.merge(toEntity(notification));
    }

    @Override
    public Optional<Notification> findById(TenantId tenantId, UUID id) {
        NotificationEntity e = em.find(NotificationEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(e));
    }

    @Override
    public Optional<Notification> findByIdempotencyKey(TenantId tenantId, String idempotencyKey) {
        List<NotificationEntity> rows = em.createQuery(
                        "SELECT n FROM NotificationEntity n WHERE n.tenantId = :tid AND n.idempotencyKey = :key",
                        NotificationEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("key", idempotencyKey)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDomain(rows.get(0)));
    }

    @Override
    public List<Notification> findByTenant(TenantId tenantId, int limit) {
        return em.createQuery(
                        "SELECT n FROM NotificationEntity n WHERE n.tenantId = :tid ORDER BY n.createdAt DESC",
                        NotificationEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setMaxResults(Math.min(Math.max(limit, 1), 500))
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    public List<Notification> findByInvoice(TenantId tenantId, InvoiceId invoiceId, int limit) {
        return em.createQuery(
                        "SELECT n FROM NotificationEntity n WHERE n.tenantId = :tid AND n.invoiceId = :iid ORDER BY n.createdAt DESC",
                        NotificationEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("iid", invoiceId.getValue())
                .setMaxResults(Math.min(Math.max(limit, 1), 500))
                .getResultStream().map(this::toDomain).toList();
    }

    /**
     * Cross-tenant poll. Table owner (ar) bypasses RLS unless FORCE ROW LEVEL SECURITY.
     * See V11 migration header comments.
     */
    @Override
    public List<Notification> findDue(Instant now, int limit) {
        return em.createQuery(
                        "SELECT n FROM NotificationEntity n WHERE n.status IN ('PENDING', 'QUEUED') "
                                + "AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now) "
                                + "ORDER BY n.createdAt ASC",
                        NotificationEntity.class)
                .setParameter("now", now)
                .setMaxResults(Math.min(Math.max(limit, 1), 500))
                .getResultStream().map(this::toDomain).toList();
    }

    /**
     * Claim due rows with FOR UPDATE SKIP LOCKED and mark SENDING (QA-NOTIFY-004/012).
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Notification> claimDue(Instant now, int limit) {
        int lim = Math.min(Math.max(limit, 1), 500);
        List<NotificationEntity> rows = em.createNativeQuery(
                        "SELECT * FROM ar_notification n "
                                + "WHERE n.status IN ('PENDING', 'QUEUED') "
                                + "AND (n.next_attempt_at IS NULL OR n.next_attempt_at <= :now) "
                                + "ORDER BY n.created_at ASC "
                                + "FOR UPDATE SKIP LOCKED "
                                + "LIMIT :lim",
                        NotificationEntity.class)
                .setParameter("now", now)
                .setParameter("lim", lim)
                .getResultList();
        Instant ts = Instant.now();
        for (NotificationEntity e : rows) {
            e.setStatus(NotificationStatus.SENDING.name());
            e.setUpdatedAt(ts);
            em.merge(e);
        }
        em.flush();
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        NotificationEntity e = em.find(NotificationEntity.class, id);
        if (e != null && e.getTenantId().equals(tenantId.getValue())) {
            em.remove(e);
        }
    }

    private NotificationEntity toEntity(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.setId(n.getId());
        e.setTenantId(n.getTenantId().getValue());
        e.setCustomerId(n.getCustomerId() != null ? n.getCustomerId().getValue() : null);
        e.setInvoiceId(n.getInvoiceId() != null ? n.getInvoiceId().getValue() : null);
        e.setEventType(n.getEventType().name());
        e.setChannel(n.getChannel().name());
        e.setStatus(n.getStatus().name());
        e.setIdempotencyKey(n.getIdempotencyKey());
        e.setDestination(n.getDestination());
        e.setSubject(n.getSubject());
        e.setBody(n.getBody());
        e.setTemplateId(n.getTemplateId());
        e.setSkipReason(n.getSkipReason() != null ? n.getSkipReason().name() : null);
        e.setErrorMessage(n.getErrorMessage());
        e.setAttemptCount(n.getAttemptCount());
        e.setMaxAttempts(n.getMaxAttempts());
        e.setNextAttemptAt(n.getNextAttemptAt());
        e.setSentAt(n.getSentAt());
        e.setProviderMessageId(n.getProviderMessageId());
        e.setMetadataJson(n.getMetadataJson());
        e.setCreatedAt(n.getCreatedAt());
        e.setUpdatedAt(n.getUpdatedAt());
        return e;
    }

    private Notification toDomain(NotificationEntity e) {
        return new Notification(
                e.getId(),
                TenantId.of(e.getTenantId()),
                e.getCustomerId() != null ? CustomerId.of(e.getCustomerId()) : null,
                e.getInvoiceId() != null ? InvoiceId.of(e.getInvoiceId()) : null,
                NotificationEventType.valueOf(e.getEventType()),
                NotificationChannel.valueOf(e.getChannel()),
                NotificationStatus.valueOf(e.getStatus()),
                e.getIdempotencyKey(),
                e.getDestination(),
                e.getSubject(),
                e.getBody(),
                e.getTemplateId(),
                e.getSkipReason() != null ? NotificationSkipReason.valueOf(e.getSkipReason()) : null,
                e.getErrorMessage(),
                e.getAttemptCount(),
                e.getMaxAttempts(),
                e.getNextAttemptAt(),
                e.getSentAt(),
                e.getProviderMessageId(),
                e.getMetadataJson(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
