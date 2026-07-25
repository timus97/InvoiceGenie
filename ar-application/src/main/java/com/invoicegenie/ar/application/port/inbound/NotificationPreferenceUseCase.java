package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationPreference;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;

/**
 * Inbound port: customer notification preferences.
 */
public interface NotificationPreferenceUseCase {

    List<NotificationPreference> list(TenantId tenantId, CustomerId customerId);

    List<NotificationPreference> upsert(TenantId tenantId, CustomerId customerId,
                                        List<PreferenceUpdate> updates);

    record PreferenceUpdate(NotificationChannel channel, boolean enabled, String destinationOverride) {}
}
