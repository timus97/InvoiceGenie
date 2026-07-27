package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.NotificationPolicyEntity;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class NotificationPolicyRepositoryAdapter implements NotificationPolicyRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(NotificationPolicy policy) {
        NotificationPolicyEntity e = new NotificationPolicyEntity();
        e.setId(policy.getId());
        e.setTenantId(policy.getTenantId().getValue());
        e.setEnabled(policy.isEnabled());
        e.setEmailEnabled(policy.isEmailEnabled());
        e.setWhatsappEnabled(policy.isWhatsappEnabled());
        e.setAutoSendOnIssue(policy.isAutoSendOnIssue());
        e.setPreDueReminderEnabled(policy.isPreDueReminderEnabled());
        e.setPreDueDays(policy.getPreDueDays());
        e.setDunningNoticeEnabled(policy.isDunningNoticeEnabled());
        e.setChannelsInvoiceIssued(policy.getChannelsInvoiceIssued());
        e.setChannelsPaymentReminder(policy.getChannelsPaymentReminder());
        e.setChannelsDunningNotice(policy.getChannelsDunningNotice());
        e.setQuietHoursStart(policy.getQuietHoursStart());
        e.setQuietHoursEnd(policy.getQuietHoursEnd());
        e.setTimezone(policy.getTimezone() != null ? policy.getTimezone() : "UTC");
        e.setAttachPdfOnIssue(policy.isAttachPdfOnIssue());
        e.setCreatedAt(policy.getCreatedAt());
        e.setUpdatedAt(policy.getUpdatedAt());
        em.merge(e);
    }

    @Override
    public Optional<NotificationPolicy> findByTenant(TenantId tenantId) {
        List<NotificationPolicyEntity> rows = em.createQuery(
                        "SELECT p FROM NotificationPolicyEntity p WHERE p.tenantId = :tid",
                        NotificationPolicyEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDomain(rows.get(0)));
    }

    private NotificationPolicy toDomain(NotificationPolicyEntity e) {
        return new NotificationPolicy(
                e.getId(),
                TenantId.of(e.getTenantId()),
                e.isEnabled(),
                e.isEmailEnabled(),
                e.isWhatsappEnabled(),
                e.isAutoSendOnIssue(),
                e.isPreDueReminderEnabled(),
                e.getPreDueDays(),
                e.isDunningNoticeEnabled(),
                e.getChannelsInvoiceIssued(),
                e.getChannelsPaymentReminder(),
                e.getChannelsDunningNotice(),
                e.getQuietHoursStart(),
                e.getQuietHoursEnd(),
                e.getTimezone(),
                e.isAttachPdfOnIssue(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
