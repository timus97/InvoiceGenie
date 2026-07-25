package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.NotificationPreferenceUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationPreference;
import com.invoicegenie.ar.domain.model.notification.NotificationPreferenceRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.List;

/**
 * Application service: customer notification preferences.
 */
public class NotificationPreferenceApplicationService implements NotificationPreferenceUseCase {

    private final NotificationPreferenceRepository preferenceRepository;
    private final CustomerRepository customerRepository;

    public NotificationPreferenceApplicationService(NotificationPreferenceRepository preferenceRepository,
                                                    CustomerRepository customerRepository) {
        this.preferenceRepository = preferenceRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public List<NotificationPreference> list(TenantId tenantId, CustomerId customerId) {
        ensureCustomer(tenantId, customerId);
        return preferenceRepository.findByCustomer(tenantId, customerId);
    }

    @Override
    public List<NotificationPreference> upsert(TenantId tenantId, CustomerId customerId,
                                               List<PreferenceUpdate> updates) {
        ensureCustomer(tenantId, customerId);
        if (updates == null) {
            return preferenceRepository.findByCustomer(tenantId, customerId);
        }
        for (PreferenceUpdate u : updates) {
            if (u == null || u.channel() == null) {
                continue;
            }
            if (u.destinationOverride() != null && !u.destinationOverride().isBlank()) {
                NotificationDestinationValidator.validateOrThrow(u.channel(), u.destinationOverride());
            }
            NotificationPreference pref = preferenceRepository
                    .findByCustomerAndChannel(tenantId, customerId, u.channel())
                    .orElseGet(() -> NotificationPreference.create(tenantId, customerId, u.channel(), true, null));
            pref.setEnabled(u.enabled());
            if (u.destinationOverride() != null) {
                pref.setDestinationOverride(u.destinationOverride().isBlank() ? null : u.destinationOverride().trim());
            }
            preferenceRepository.save(pref);
        }
        return preferenceRepository.findByCustomer(tenantId, customerId);
    }

    private void ensureCustomer(TenantId tenantId, CustomerId customerId) {
        if (customerRepository.findByTenantAndId(tenantId, customerId).isEmpty()) {
            throw new IllegalArgumentException("CUSTOMER_NOT_FOUND");
        }
    }
}