package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.ExchangeRateUseCase;
import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/exchange-rates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Exchange Rates", description = "Multi-currency rates and conversion")
public class ExchangeRateResource {

    private final ExchangeRateUseCase exchangeRateUseCase;

    public ExchangeRateResource(ExchangeRateUseCase exchangeRateUseCase) {
        this.exchangeRateUseCase = exchangeRateUseCase;
    }

    @POST
    @Operation(summary = "Create exchange rate")
    public Response create(CreateRateDto dto) {
        try {
            var tenantId = TenantContext.getCurrentTenant();
            ExchangeRate rate = exchangeRateUseCase.create(tenantId, new ExchangeRateUseCase.CreateRateCommand(
                    dto.fromCurrency(), dto.toCurrency(), dto.rate(),
                    dto.effectiveDate() != null ? dto.effectiveDate() : LocalDate.now(),
                    dto.source()));
            return Response.status(201).entity(toDto(rate)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", e.getMessage())).build();
        }
    }

    @GET
    @Operation(summary = "List exchange rates for tenant")
    public Response list() {
        var tenantId = TenantContext.getCurrentTenant();
        return Response.ok(exchangeRateUseCase.list(tenantId).stream().map(this::toDto).collect(Collectors.toList())).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get exchange rate by id")
    public Response get(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        return exchangeRateUseCase.get(tenantId, UUID.fromString(id))
                .map(r -> Response.ok(toDto(r)).build())
                .orElse(Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Rate not found")).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete exchange rate")
    public Response delete(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        exchangeRateUseCase.delete(tenantId, UUID.fromString(id));
        return Response.noContent().build();
    }

    @POST
    @Path("/convert")
    @Operation(summary = "Convert amount using stored rates")
    public Response convert(ConvertDto dto) {
        try {
            var tenantId = TenantContext.getCurrentTenant();
            Money result = exchangeRateUseCase.convert(tenantId, new ExchangeRateUseCase.ConvertCommand(
                    dto.amount(), dto.fromCurrency(), dto.toCurrency(),
                    dto.asOf() != null ? dto.asOf() : LocalDate.now()));
            return Response.ok(new ConvertedDto(
                    result.getAmount(), result.getCurrencyCode(),
                    dto.fromCurrency(), dto.amount())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse("CONVERSION_ERROR", e.getMessage())).build();
        }
    }

    private RateDto toDto(ExchangeRate r) {
        return new RateDto(r.getId().toString(), r.getFromCurrency(), r.getToCurrency(),
                r.getRate(), r.getEffectiveDate().toString(), r.getSource());
    }

    public record CreateRateDto(String fromCurrency, String toCurrency, BigDecimal rate,
                                LocalDate effectiveDate, String source) {}
    public record ConvertDto(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate asOf) {}
    public record RateDto(String id, String fromCurrency, String toCurrency, BigDecimal rate,
                          String effectiveDate, String source) {}
    public record ConvertedDto(BigDecimal amount, String currencyCode, String fromCurrency, BigDecimal originalAmount) {}
}