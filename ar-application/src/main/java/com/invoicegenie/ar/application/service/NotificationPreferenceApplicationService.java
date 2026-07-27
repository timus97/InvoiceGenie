package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.NotificationPreferenceUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationPreference;
import com.invoicegenie.ar.domain.model.notification.NotificationPreferenceRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;

import java.util.List;
import java.util.Optional;

/**
 * Application service: customer notification preferences + public unsubscribe.
 */
public class NotificationPreferenceApplicationService implements NotificationPreferenceUseCase {

    private final NotificationPreferenceRepository preferenceRepository;
    private final CustomerRepository customerRepository;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final AuditRepository auditRepository;

    public NotificationPreferenceApplicationService(NotificationPreferenceRepository preferenceRepository,
                                                    CustomerRepository customerRepository) {
        this(preferenceRepository, customerRepository, null, null);
    }

    public NotificationPreferenceApplicationService(NotificationPreferenceRepository preferenceRepository,
                                                    CustomerRepository customerRepository,
                                                    UnsubscribeTokenService unsubscribeTokenService,
                                                    AuditRepository auditRepository) {
        this.preferenceRepository = preferenceRepository;
        this.customerRepository = customerRepository;
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.auditRepository = auditRepository;
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
            String before = snapshot(pref);
            pref.setEnabled(u.enabled());
            if (u.destinationOverride() != null) {
                pref.setDestinationOverride(u.destinationOverride().isBlank() ? null : u.destinationOverride().trim());
            }
            preferenceRepository.save(pref);
            if (auditRepository != null) {
                auditRepository.save(tenantId, AuditEntry.update(
                        tenantId, "NOTIFICATION_PREFERENCE", pref.getId(),
                        customerId.getValue().toString() + ":" + pref.getChannel().name(),
                        null, before, snapshot(pref)));
            }
        }
        return preferenceRepository.findByCustomer(tenantId, customerId);
    }

    @Override
    public Optional<NotificationPreference> unsubscribeByToken(String token) {
        if (unsubscribeTokenService == null) {
            return Optional.empty();
        }
        Optional<UnsubscribeTokenService.TokenClaims> claims = unsubscribeTokenService.parse(token);
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        UnsubscribeTokenService.TokenClaims c = claims.get();
        // Bind tenant for RLS during public call
        TenantContext.setCurrentTenant(c.tenantId());
        try {
            NotificationPreference pref = preferenceRepository
                    .findByCustomerAndChannel(c.tenantId(), c.customerId(), c.channel())
                    .orElseGet(() -> NotificationPreference.create(
                            c.tenantId(), c.customerId(), c.channel(), true, null));
            pref.setEnabled(false);
            preferenceRepository.save(pref);
            if (auditRepository != null) {
                auditRepository.save(c.tenantId(), AuditEntry.update(
                        c.tenantId(), "NOTIFICATION_PREFERENCE", pref.getId(),
                        "unsubscribe:" + c.channel().name(),
                        null, null, snapshot(pref)));
            }
            return Optional.of(pref);
        } finally {
            TenantContext.clear();
        }
    }

    private void ensureCustomer(TenantId tenantId, CustomerId customerId) {
        if (customerRepository.findByTenantAndId(tenantId, customerId).isEmpty()) {
            throw new IllegalArgumentException("CUSTOMER_NOT_FOUND");
        }
    }

    private static String snapshot(NotificationPreference p) {
        return "{\"channel\":\"" + p.getChannel() + "\",\"enabled\":" + p.isEnabled()
                + ",\"optedOutAt\":" + (p.getOptedOutAt() != null ? "\"" + p.getOptedOutAt() + "\"" : "null")
                + "}";
    }
}
