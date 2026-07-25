package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.NotificationTemplateEntity;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplate;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRepository;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NotificationTemplateRepositoryAdapter implements NotificationTemplateRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(NotificationTemplate template) {
        NotificationTemplateEntity e = toEntity(template);
        em.merge(e);
    }

    @Override
    public Optional<NotificationTemplate> findById(UUID id) {
        NotificationTemplateEntity e = em.find(NotificationTemplateEntity.class, id);
        return e == null ? Optional.empty() : Optional.of(toDomain(e));
    }

    @Override
    public Optional<NotificationTemplate> findActive(TenantId tenantId, NotificationEventType eventType,
                                                     NotificationChannel channel, String locale) {
        String loc = locale == null || locale.isBlank() ? "en" : locale;
        // Tenant-specific first
        List<NotificationTemplateEntity> tenantRows = em.createQuery(
                        "SELECT t FROM NotificationTemplateEntity t WHERE t.tenantId = :tid "
                                + "AND t.eventType = :et AND t.channel = :ch AND t.locale = :loc AND t.active = true",
                        NotificationTemplateEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("et", eventType.name())
                .setParameter("ch", channel.name())
                .setParameter("loc", loc)
                .setMaxResults(1)
                .getResultList();
        if (!tenantRows.isEmpty()) {
            return Optional.of(toDomain(tenantRows.get(0)));
        }
        // System template (tenant_id null)
        List<NotificationTemplateEntity> systemRows = em.createQuery(
                        "SELECT t FROM NotificationTemplateEntity t WHERE t.tenantId IS NULL "
                                + "AND t.eventType = :et AND t.channel = :ch AND t.locale = :loc AND t.active = true",
                        NotificationTemplateEntity.class)
                .setParameter("et", eventType.name())
                .setParameter("ch", channel.name())
                .setParameter("loc", loc)
                .setMaxResults(1)
                .getResultList();
        if (systemRows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toDomain(systemRows.get(0)));
    }

    private NotificationTemplateEntity toEntity(NotificationTemplate t) {
        NotificationTemplateEntity e = new NotificationTemplateEntity();
        e.setId(t.getId());
        e.setTenantId(t.getTenantId() != null ? t.getTenantId().getValue() : null);
        e.setEventType(t.getEventType().name());
        e.setChannel(t.getChannel().name());
        e.setLocale(t.getLocale());
        e.setSubject(t.getSubject());
        e.setBody(t.getBody());
        e.setWhatsappTemplateName(t.getWhatsappTemplateName());
        e.setActive(t.isActive());
        e.setCreatedAt(t.getCreatedAt());
        e.setUpdatedAt(t.getUpdatedAt());
        return e;
    }

    private NotificationTemplate toDomain(NotificationTemplateEntity e) {
        return new NotificationTemplate(
                e.getId(),
                e.getTenantId() != null ? TenantId.of(e.getTenantId()) : null,
                NotificationEventType.valueOf(e.getEventType()),
                NotificationChannel.valueOf(e.getChannel()),
                e.getLocale(),
                e.getSubject(),
                e.getBody(),
                e.getWhatsappTemplateName(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
