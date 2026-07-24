package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.TenantUseCase;
import com.invoicegenie.ar.domain.model.ledger.ChartOfAccountsRepository;
import com.invoicegenie.ar.domain.model.tenant.Tenant;
import com.invoicegenie.ar.domain.model.tenant.TenantRepository;
import com.invoicegenie.ar.domain.model.tenant.TenantStatus;
import com.invoicegenie.shared.domain.UuidV7;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Application service: tenant registry CRUD / lifecycle.
 * On create, seeds system chart-of-accounts rows when COA persistence is available (STORY-020).
 */
public class TenantManagementService implements TenantUseCase {

    private static final Logger LOG = Logger.getLogger(TenantManagementService.class.getName());

    private final TenantRepository tenantRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;

    public TenantManagementService(TenantRepository tenantRepository) {
        this(tenantRepository, null);
    }

    public TenantManagementService(TenantRepository tenantRepository,
                                   ChartOfAccountsRepository chartOfAccountsRepository) {
        this.tenantRepository = tenantRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
    }

    @Override
    public Tenant create(CreateTenantCommand command) {
        if (tenantRepository.existsByCode(command.code())) {
            throw new IllegalArgumentException("Tenant code already exists: " + command.code());
        }
        Tenant tenant = new Tenant(
                UuidV7.generate(),
                command.code(),
                command.name(),
                command.baseCurrency() != null ? command.baseCurrency() : "USD",
                TenantStatus.ACTIVE,
                command.settingsJson() != null ? command.settingsJson() : "{}",
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        tenantRepository.save(tenant);
        seedChartOfAccounts(tenant.getId());
        return tenant;
    }

    private void seedChartOfAccounts(UUID tenantId) {
        if (chartOfAccountsRepository == null) {
            return;
        }
        try {
            int n = chartOfAccountsRepository.seedSystemAccounts(tenantId);
            if (n > 0) {
                LOG.info("Seeded " + n + " system accounts for new tenant " + tenantId);
            }
        } catch (Exception e) {
            // Do not fail tenant create if COA table is missing or partially migrated
            LOG.warning("COA seed failed for tenant " + tenantId + ": " + e.getMessage());
        }
    }

    @Override
    public Optional<Tenant> get(UUID id) {
        return tenantRepository.findById(id);
    }

    @Override
    public Optional<Tenant> getByCode(String code) {
        return tenantRepository.findByCode(code);
    }

    @Override
    public List<Tenant> list(String status) {
        if (status == null || status.isBlank()) {
            return tenantRepository.findAll();
        }
        try {
            return tenantRepository.findByStatus(TenantStatus.valueOf(status.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    @Override
    public Optional<Tenant> update(UUID id, UpdateTenantCommand command) {
        return tenantRepository.findById(id).map(t -> {
            if (command.name() != null && !command.name().isBlank()) {
                t.rename(command.name());
            }
            if (command.baseCurrency() != null && !command.baseCurrency().isBlank()) {
                t.setBaseCurrency(command.baseCurrency());
            }
            if (command.settingsJson() != null) {
                t.setSettingsJson(command.settingsJson());
            }
            tenantRepository.save(t);
            return t;
        });
    }

    @Override
    public Optional<Tenant> activate(UUID id) {
        return tenantRepository.findById(id).map(t -> {
            t.activate();
            tenantRepository.save(t);
            return t;
        });
    }

    @Override
    public Optional<Tenant> suspend(UUID id) {
        return tenantRepository.findById(id).map(t -> {
            t.suspend();
            tenantRepository.save(t);
            return t;
        });
    }
}