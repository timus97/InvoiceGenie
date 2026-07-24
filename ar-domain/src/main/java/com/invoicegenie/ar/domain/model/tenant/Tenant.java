package com.invoicegenie.ar.domain.model.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant aggregate — organization that owns all AR data.
 *
 * <p>Stored in {@code ar_tenant}. Not multi-tenant-scoped itself (platform registry).
 */
public final class Tenant {

    private final UUID id;
    private final String code;
    private String name;
    private String baseCurrency;
    private TenantStatus status;
    private String settingsJson;
    private final Instant createdAt;
    private Instant updatedAt;

    public Tenant(UUID id, String code, String name, String baseCurrency) {
        this(id, code, name, baseCurrency, TenantStatus.ACTIVE, "{}", Instant.now(), Instant.now());
    }

    public Tenant(UUID id, String code, String name, String baseCurrency, TenantStatus status,
                  String settingsJson, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.baseCurrency = baseCurrency == null || baseCurrency.isBlank() ? "USD" : baseCurrency.trim().toUpperCase();
        if (this.baseCurrency.length() != 3) {
            throw new IllegalArgumentException("baseCurrency must be ISO 4217");
        }
        this.status = status == null ? TenantStatus.ACTIVE : status;
        this.settingsJson = settingsJson == null || settingsJson.isBlank() ? "{}" : settingsJson;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = newName.trim();
        touch();
    }

    public void setBaseCurrency(String currency) {
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("baseCurrency must be ISO 4217");
        }
        this.baseCurrency = currency.trim().toUpperCase();
        touch();
    }

    public void activate() {
        this.status = TenantStatus.ACTIVE;
        touch();
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
        touch();
    }

    public void setSettingsJson(String settingsJson) {
        this.settingsJson = settingsJson == null || settingsJson.isBlank() ? "{}" : settingsJson;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getBaseCurrency() { return baseCurrency; }
    public TenantStatus getStatus() { return status; }
    public String getSettingsJson() { return settingsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
