package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.ar.domain.model.fx.ExchangeRateRepository;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    @Mock ExchangeRateRepository repo;

    @Test
    void sameCurrencyNoOp() {
        var svc = new CurrencyConversionService(repo);
        TenantId tid = TenantId.of(UUID.randomUUID());
        Money m = Money.of("10.00", "USD");
        assertEquals(m, svc.convert(tid, m, "USD", LocalDate.now()));
    }

    @Test
    void convertsWithDirectRate() {
        var svc = new CurrencyConversionService(repo);
        TenantId tid = TenantId.of(UUID.randomUUID());
        when(repo.findLatest(eq(tid), eq("EUR"), eq("USD"), any()))
                .thenReturn(Optional.of(new ExchangeRate(UUID.randomUUID(), "EUR", "USD",
                        new BigDecimal("2.00"), LocalDate.now(), "T")));
        Money result = svc.convert(tid, Money.of("10.00", "EUR"), "USD", LocalDate.now());
        assertEquals("USD", result.getCurrencyCode());
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("20.00")));
    }
}