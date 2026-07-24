package com.invoicegenie.ar.domain.model.fx;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExchangeRateTest {

    @Test
    void createsRate() {
        ExchangeRate r = new ExchangeRate(UUID.randomUUID(), "eur", "usd",
                new BigDecimal("1.1"), LocalDate.now(), "ECB");
        assertEquals("EUR", r.getFromCurrency());
        assertEquals("USD", r.getToCurrency());
    }

    @Test
    void rejectsSameCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExchangeRate(UUID.randomUUID(), "USD", "USD", BigDecimal.ONE, LocalDate.now(), null));
    }

    @Test
    void rejectsNonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExchangeRate(UUID.randomUUID(), "USD", "EUR", BigDecimal.ZERO, LocalDate.now(), null));
    }
}