package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.tenant.Tenant;
import com.invoicegenie.ar.domain.model.tenant.TenantStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: platform tenant registry (onboarding).
 */
public interface TenantUseCase {

    Tenant create(CreateTenantCommand command);

    Optional<Tenant> get(UUID id);

    Optional<Tenant> getByCode(String code);

    List<Tenant> list(String status);

    Optional<Tenant> update(UUID id, UpdateTenantCommand command);

    Optional<Tenant> activate(UUID id);

    Optional<Tenant> suspend(UUID id);

    record CreateTenantCommand(String code, String name, String baseCurrency, String settingsJson) {
        public CreateTenantCommand {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        }
    }

    record UpdateTenantCommand(String name, String baseCurrency, String settingsJson) {}
}