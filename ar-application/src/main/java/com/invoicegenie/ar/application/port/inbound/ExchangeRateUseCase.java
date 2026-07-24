package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: exchange rates and currency conversion.
 */
public interface ExchangeRateUseCase {

    ExchangeRate create(TenantId tenantId, CreateRateCommand command);

    List<ExchangeRate> list(TenantId tenantId);

    Optional<ExchangeRate> get(TenantId tenantId, UUID id);

    void delete(TenantId tenantId, UUID id);

    Money convert(TenantId tenantId, ConvertCommand command);

    record CreateRateCommand(String fromCurrency, String toCurrency, BigDecimal rate,
                             LocalDate effectiveDate, String source) {
        public CreateRateCommand {
            if (fromCurrency == null || fromCurrency.isBlank()) throw new IllegalArgumentException("fromCurrency required");
            if (toCurrency == null || toCurrency.isBlank()) throw new IllegalArgumentException("toCurrency required");
            if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("rate must be positive");
            if (effectiveDate == null) throw new IllegalArgumentException("effectiveDate required");
        }
    }

    record ConvertCommand(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate asOf) {
        public ConvertCommand {
            if (amount == null) throw new IllegalArgumentException("amount required");
            if (fromCurrency == null || fromCurrency.isBlank()) throw new IllegalArgumentException("fromCurrency required");
            if (toCurrency == null || toCurrency.isBlank()) throw new IllegalArgumentException("toCurrency required");
        }
    }
}