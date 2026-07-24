package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.ar.domain.model.fx.ExchangeRateRepository;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain service: multi-currency conversion using tenant exchange rates.
 */
public class CurrencyConversionService {

    private final ExchangeRateRepository exchangeRateRepository;

    public CurrencyConversionService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = Objects.requireNonNull(exchangeRateRepository);
    }

    /**
     * Convert money to {@code toCurrency} using the latest rate on or before {@code asOf}.
     * Same-currency is a no-op. Throws if no rate is available.
     */
    public Money convert(TenantId tenantId, Money amount, String toCurrency, LocalDate asOf) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(toCurrency, "toCurrency");
        String target = toCurrency.trim().toUpperCase();
        if (amount.getCurrencyCode().equals(target)) {
            return amount;
        }
        LocalDate date = asOf != null ? asOf : LocalDate.now();

        Optional<ExchangeRate> direct = exchangeRateRepository.findLatest(
                tenantId, amount.getCurrencyCode(), target, date);
        if (direct.isPresent()) {
            return amount.convert(direct.get().getRate(), target);
        }

        // Try inverse rate
        Optional<ExchangeRate> inverse = exchangeRateRepository.findLatest(
                tenantId, target, amount.getCurrencyCode(), date);
        if (inverse.isPresent()) {
            BigDecimal inv = BigDecimal.ONE.divide(inverse.get().getRate(), 8, RoundingMode.HALF_UP);
            return amount.convert(inv, target);
        }

        throw new IllegalArgumentException(
                "No exchange rate for " + amount.getCurrencyCode() + " → " + target + " as of " + date);
    }

    public Optional<ExchangeRate> findRate(TenantId tenantId, String from, String to, LocalDate asOf) {
        return exchangeRateRepository.findLatest(tenantId, from, to, asOf != null ? asOf : LocalDate.now());
    }
}
