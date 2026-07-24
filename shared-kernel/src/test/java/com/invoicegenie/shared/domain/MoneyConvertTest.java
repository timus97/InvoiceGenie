package com.invoicegenie.shared.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MoneyConvertTest {

    @Test
    void convertMultipliesRate() {
        Money m = Money.of("10.00", "EUR").convert(new BigDecimal("1.5"), "USD");
        assertEquals("USD", m.getCurrencyCode());
        assertEquals(0, m.getAmount().compareTo(new BigDecimal("15.00")));
    }

    @Test
    void rejectsNonPositiveRate() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of("1", "USD").convert(BigDecimal.ZERO, "EUR"));
    }
}