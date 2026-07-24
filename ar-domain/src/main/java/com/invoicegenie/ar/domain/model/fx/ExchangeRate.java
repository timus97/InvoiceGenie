package com.invoicegenie.ar.domain.model.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Exchange rate between two currencies effective on a date (tenant-scoped).
 */
public final class ExchangeRate {

    private final UUID id;
    private final String fromCurrency;
    private final String toCurrency;
    private final BigDecimal rate;
    private final LocalDate effectiveDate;
    private final String source;

    public ExchangeRate(UUID id, String fromCurrency, String toCurrency, BigDecimal rate,
                        LocalDate effectiveDate, String source) {
        this.id = Objects.requireNonNull(id, "id");
        this.fromCurrency = requireCurrency(fromCurrency, "fromCurrency");
        this.toCurrency = requireCurrency(toCurrency, "toCurrency");
        if (this.fromCurrency.equals(this.toCurrency)) {
            throw new IllegalArgumentException("fromCurrency and toCurrency must differ");
        }
        this.rate = Objects.requireNonNull(rate, "rate");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        this.effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate");
        this.source = source;
    }

    private static String requireCurrency(String c, String field) {
        if (c == null || c.isBlank() || c.trim().length() != 3) {
            throw new IllegalArgumentException(field + " must be ISO 4217");
        }
        return c.trim().toUpperCase();
    }

    public UUID getId() { return id; }
    public String getFromCurrency() { return fromCurrency; }
    public String getToCurrency() { return toCurrency; }
    public BigDecimal getRate() { return rate; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getSource() { return source; }
}
