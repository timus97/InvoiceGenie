package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: customer notification preferences.
 */
public interface NotificationPreferenceRepository {

    void save(NotificationPreference preference);

    Optional<NotificationPreference> findByCustomerAndChannel(TenantId tenantId, CustomerId customerId,
                                                              NotificationChannel channel);

    List<NotificationPreference> findByCustomer(TenantId tenantId, CustomerId customerId);

    Optional<NotificationPreference> findById(TenantId tenantId, UUID id);
}
