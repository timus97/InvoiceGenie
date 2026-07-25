package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;

/**
 * Outbound port: tenant notification policy.
 */
public interface NotificationPolicyRepository {

    void save(NotificationPolicy policy);

    Optional<NotificationPolicy> findByTenant(TenantId tenantId);
}
