package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.NotificationSuppressionEntity;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppression;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppressionRepository;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class NotificationSuppressionRepositoryAdapter implements NotificationSuppressionRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(NotificationSuppression suppression) {
        NotificationSuppressionEntity e = new NotificationSuppressionEntity();
        e.setId(suppression.getId());
        e.setTenantId(suppression.getTenantId().getValue());
        e.setChannel(suppression.getChannel().name());
        e.setDestinationNormalized(suppression.getDestinationNormalized());
        e.setDestinationHash(suppression.getDestinationHash());
        e.setReason(suppression.getReason());
        e.setProvider(suppression.getProvider());
        e.setCreatedAt(suppression.getCreatedAt());
        em.merge(e);
    }

    @Override
    public Optional<NotificationSuppression> findByTenantChannelAndHash(TenantId tenantId,
                                                                        NotificationChannel channel,
                                                                        String destinationHash) {
        List<NotificationSuppressionEntity> rows = em.createQuery(
                        "SELECT s FROM NotificationSuppressionEntity s WHERE s.tenantId = :tid "
                                + "AND s.channel = :ch AND s.destinationHash = :hash",
                        NotificationSuppressionEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("ch", channel.name())
                .setParameter("hash", destinationHash)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDomain(rows.get(0)));
    }

    @Override
    public boolean isSuppressed(TenantId tenantId, NotificationChannel channel, String destinationHash) {
        Long count = em.createQuery(
                        "SELECT COUNT(s) FROM NotificationSuppressionEntity s WHERE s.tenantId = :tid "
                                + "AND s.channel = :ch AND s.destinationHash = :hash",
                        Long.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("ch", channel.name())
                .setParameter("hash", destinationHash)
                .getSingleResult();
        return count != null && count > 0;
    }

    private NotificationSuppression toDomain(NotificationSuppressionEntity e) {
        return new NotificationSuppression(
                e.getId(),
                TenantId.of(e.getTenantId()),
                NotificationChannel.valueOf(e.getChannel()),
                e.getDestinationNormalized(),
                e.getDestinationHash(),
                e.getReason(),
                e.getProvider(),
                e.getCreatedAt()
        );
    }
}
