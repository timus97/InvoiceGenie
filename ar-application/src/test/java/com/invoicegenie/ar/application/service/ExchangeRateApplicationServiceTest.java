package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ExchangeRateUseCase;
import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.ar.domain.model.fx.ExchangeRateRepository;
import com.invoicegenie.ar.domain.service.CurrencyConversionService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ExchangeRateApplicationService")
@ExtendWith(MockitoExtension.class)
class ExchangeRateApplicationServiceTest {

    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private CurrencyConversionService conversionService;

    private ExchangeRateApplicationService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateApplicationService(exchangeRateRepository, conversionService);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("create saves rate")
    void create() {
        ExchangeRate rate = service.create(tenantId, new ExchangeRateUseCase.CreateRateCommand(
                "USD", "EUR", new BigDecimal("0.92"), LocalDate.now(), "ECB"));

        assertEquals("USD", rate.getFromCurrency());
        assertEquals("EUR", rate.getToCurrency());
        verify(exchangeRateRepository).save(eq(tenantId), any(ExchangeRate.class));
    }

    @Test
    @DisplayName("list and get and delete")
    void listGetDelete() {
        UUID id = UUID.randomUUID();
        when(exchangeRateRepository.findAllByTenant(tenantId)).thenReturn(List.of());
        when(exchangeRateRepository.findById(tenantId, id)).thenReturn(Optional.empty());

        assertTrue(service.list(tenantId).isEmpty());
        assertTrue(service.get(tenantId, id).isEmpty());
        service.delete(tenantId, id);
        verify(exchangeRateRepository).delete(tenantId, id);
    }

    @Test
    @DisplayName("convert delegates to CurrencyConversionService")
    void convert() {
        when(conversionService.convert(eq(tenantId), any(Money.class), eq("EUR"), any()))
                .thenReturn(Money.of("92.00", "EUR"));

        Money result = service.convert(tenantId, new ExchangeRateUseCase.ConvertCommand(
                new BigDecimal("100"), "usd", "EUR", LocalDate.now()));

        assertEquals(0, new BigDecimal("92.00").compareTo(result.getAmount()));
        verify(conversionService).convert(eq(tenantId), argThat(m ->
                "USD".equals(m.getCurrencyCode()) && m.getAmount().compareTo(new BigDecimal("100")) == 0),
                eq("EUR"), any());
    }
}