package com.invoicegenie.shared.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object for monetary amounts. Immutable; scale fixed for currency.
 */
public final class Money {

    private final BigDecimal amount;
    private final String currencyCode;

    public Money(BigDecimal amount, String currencyCode) {
        this.amount = Objects.requireNonNull(amount, "amount must not be null")
                .setScale(2, java.math.RoundingMode.HALF_UP);
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        if (currencyCode.length() != 3) {
            throw new IllegalArgumentException("currencyCode must be ISO 4217 (3 letters)");
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), currencyCode);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currencyCode);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currencyCode);
    }

    private void requireSameCurrency(Money other) {
        if (!currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Cannot operate on different currencies: " + currencyCode + " vs " + other.currencyCode);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 && currencyCode.equals(money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currencyCode);
    }
}
