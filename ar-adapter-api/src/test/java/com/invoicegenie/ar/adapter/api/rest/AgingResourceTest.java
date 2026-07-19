package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.AgingUseCase;
import com.invoicegenie.ar.domain.model.invoice.AgingReport;
import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AgingResource")
@ExtendWith(MockitoExtension.class)
class AgingResourceTest {

    @Mock private AgingUseCase agingUseCase;
    private AgingResource resource;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        resource = new AgingResource(agingUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getBuckets returns labels")
    void buckets() {
        assertEquals(200, resource.getBuckets().getStatus());
    }

    @Test
    @DisplayName("getAgingReport success")
    void reportOk() {
        AgingReport report = new AgingReport(LocalDate.now(), "USD");
        AgingReport.AgingSummary summary = AgingReport.AgingSummary.from(report);
        when(agingUseCase.getReport(eq(tenantId), any()))
                .thenReturn(new AgingService.AgingReportResult(report, summary, true, "ok"));

        Response r = resource.getAgingReport(null);
        assertEquals(200, r.getStatus());
    }

    @Test
    @DisplayName("getAgingReport failure")
    void reportFail() {
        when(agingUseCase.getReport(eq(tenantId), any()))
                .thenReturn(new AgingService.AgingReportResult(null, null, false, "boom"));
        assertEquals(500, resource.getAgingReport(null).getStatus());
    }

    @Test
    @DisplayName("calculateDiscount success")
    void discount() {
        UUID invId = UUID.randomUUID();
        when(agingUseCase.calculateEarlyPaymentDiscount(any(), any(), any(), any()))
                .thenReturn(new AgingService.EarlyPaymentDiscountResult(
                        invId, Money.of("100", "USD"), Money.of("2", "USD"),
                        Money.of("98", "USD"), true, "ok"));

        var dto = new AgingResource.DiscountRequestDto(new BigDecimal("100"), "USD",
                LocalDate.now().plusDays(30), LocalDate.now());
        assertEquals(200, resource.calculateDiscount(dto).getStatus());
    }
}