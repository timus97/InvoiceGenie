package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.NotificationPreferenceEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationPreference;
import com.invoicegenie.ar.domain.model.notification.NotificationPreferenceRepository;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NotificationPreferenceRepositoryAdapter implements NotificationPreferenceRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(NotificationPreference preference) {
        NotificationPreferenceEntity e = new NotificationPreferenceEntity();
        e.setId(preference.getId());
        e.setTenantId(preference.getTenantId().getValue());
        e.setCustomerId(preference.getCustomerId().getValue());
        e.setChannel(preference.getChannel().name());
        e.setEnabled(preference.isEnabled());
        e.setOptedOutAt(preference.getOptedOutAt());
        e.setDestinationOverride(preference.getDestinationOverride());
        e.setCreatedAt(preference.getCreatedAt());
        e.setUpdatedAt(preference.getUpdatedAt());
        em.merge(e);
    }

    @Override
    public Optional<NotificationPreference> findByCustomerAndChannel(TenantId tenantId, CustomerId customerId,
                                                                     NotificationChannel channel) {
        List<NotificationPreferenceEntity> rows = em.createQuery(
                        "SELECT p FROM NotificationPreferenceEntity p WHERE p.tenantId = :tid "
                                + "AND p.customerId = :cid AND p.channel = :ch",
                        NotificationPreferenceEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("cid", customerId.getValue())
                .setParameter("ch", channel.name())
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDomain(rows.get(0)));
    }

    @Override
    public List<NotificationPreference> findByCustomer(TenantId tenantId, CustomerId customerId) {
        return em.createQuery(
                        "SELECT p FROM NotificationPreferenceEntity p WHERE p.tenantId = :tid AND p.customerId = :cid",
                        NotificationPreferenceEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("cid", customerId.getValue())
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    public Optional<NotificationPreference> findById(TenantId tenantId, UUID id) {
        NotificationPreferenceEntity e = em.find(NotificationPreferenceEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(e));
    }

    private NotificationPreference toDomain(NotificationPreferenceEntity e) {
        return new NotificationPreference(
                e.getId(),
                TenantId.of(e.getTenantId()),
                CustomerId.of(e.getCustomerId()),
                NotificationChannel.valueOf(e.getChannel()),
                e.isEnabled(),
                e.getOptedOutAt(),
                e.getDestinationOverride(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
