package com.invoicegenie.ar.domain.model.fx;

import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: exchange rate persistence.
 */
public interface ExchangeRateRepository {

    void save(TenantId tenantId, ExchangeRate rate);

    Optional<ExchangeRate> findById(TenantId tenantId, UUID id);

    /**
     * Latest rate on or before {@code asOf} for the currency pair.
     */
    Optional<ExchangeRate> findLatest(TenantId tenantId, String fromCurrency, String toCurrency, LocalDate asOf);

    List<ExchangeRate> findAllByTenant(TenantId tenantId);

    void delete(TenantId tenantId, UUID id);
}
