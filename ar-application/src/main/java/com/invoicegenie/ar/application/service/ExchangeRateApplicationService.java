package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ExchangeRateUseCase;
import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.ar.domain.model.fx.ExchangeRateRepository;
import com.invoicegenie.ar.domain.service.CurrencyConversionService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.domain.UuidV7;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: exchange rates + conversion.
 */
public class ExchangeRateApplicationService implements ExchangeRateUseCase {

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyConversionService conversionService;

    public ExchangeRateApplicationService(ExchangeRateRepository exchangeRateRepository,
                                          CurrencyConversionService conversionService) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.conversionService = conversionService;
    }

    @Override
    public ExchangeRate create(TenantId tenantId, CreateRateCommand command) {
        ExchangeRate rate = new ExchangeRate(
                UuidV7.generate(),
                command.fromCurrency(),
                command.toCurrency(),
                command.rate(),
                command.effectiveDate(),
                command.source()
        );
        exchangeRateRepository.save(tenantId, rate);
        return rate;
    }

    @Override
    public List<ExchangeRate> list(TenantId tenantId) {
        return exchangeRateRepository.findAllByTenant(tenantId);
    }

    @Override
    public Optional<ExchangeRate> get(TenantId tenantId, UUID id) {
        return exchangeRateRepository.findById(tenantId, id);
    }

    @Override
    public void delete(TenantId tenantId, UUID id) {
        exchangeRateRepository.delete(tenantId, id);
    }

    @Override
    public Money convert(TenantId tenantId, ConvertCommand command) {
        Money source = Money.of(command.amount(), command.fromCurrency().trim().toUpperCase());
        return conversionService.convert(tenantId, source, command.toCurrency(), command.asOf());
    }
}