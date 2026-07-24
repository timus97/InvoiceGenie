package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.ChequeUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
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

@DisplayName("ChequeResource")
@ExtendWith(MockitoExtension.class)
class ChequeResourceTest {

    @Mock private ChequeUseCase chequeUseCase;
    private ChequeResource resource;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        resource = new ChequeResource(chequeUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Cheque sample() {
        return new Cheque(UUID.randomUUID(), "CHQ-1", new CustomerId(UUID.randomUUID()),
                Money.of("100", "USD"), "Bank", "Br", LocalDate.now(), null);
    }

    @Test
    @DisplayName("create ok")
    void create() {
        Cheque c = sample();
        when(chequeUseCase.create(eq(tenantId), any())).thenReturn(c);
        var dto = new ChequeResource.CreateChequeDto("CHQ-1", c.getCustomerId().getValue().toString(),
                new BigDecimal("100"), "USD", "Bank", "Br", LocalDate.now(), null);
        assertEquals(201, resource.createCheque(dto).getStatus());
    }

    @Test
    @DisplayName("get found")
    void getOk() {
        Cheque c = sample();
        when(chequeUseCase.get(eq(tenantId), eq(c.getId()))).thenReturn(Optional.of(c));
        assertEquals(200, resource.getCheque(c.getId().toString()).getStatus());
    }

    @Test
    @DisplayName("get missing")
    void getMissing() {
        when(chequeUseCase.get(eq(tenantId), any())).thenReturn(Optional.empty());
        assertEquals(404, resource.getCheque(UUID.randomUUID().toString()).getStatus());
    }

    @Test
    @DisplayName("deposit success")
    void deposit() {
        Cheque c = sample();
        when(chequeUseCase.deposit(eq(tenantId), eq(c.getId())))
                .thenReturn(Optional.of(new ChequeService.DepositResult(c, true, "ok")));
        assertEquals(200, resource.depositCheque(c.getId().toString()).getStatus());
    }

    @Test
    @DisplayName("clear success")
    void clear() {
        Cheque c = sample();
        when(chequeUseCase.clear(eq(tenantId), eq(c.getId()), isNull()))
                .thenReturn(Optional.of(new ChequeService.ClearResult(c, UUID.randomUUID(), List.of(), true, "ok")));
        assertEquals(200, resource.clearCheque(c.getId().toString(), null).getStatus());
    }

    @Test
    @DisplayName("bounce success")
    void bounce() {
        Cheque c = sample();
        when(chequeUseCase.bounce(eq(tenantId), eq(c.getId()), anyString()))
                .thenReturn(Optional.of(new ChequeService.BounceResult(c, List.of(), List.of(), true, "ok")));
        assertEquals(200, resource.bounceCheque(c.getId().toString(), new ChequeResource.BounceDto("NSF")).getStatus());
    }

    @Test
    @DisplayName("bounce requires reason")
    void bounceReason() {
        assertEquals(400, resource.bounceCheque(UUID.randomUUID().toString(), new ChequeResource.BounceDto("")).getStatus());
    }

    @Test
    @DisplayName("list ok")
    void list() {
        when(chequeUseCase.list(eq(tenantId), isNull())).thenReturn(ChequeUseCase.ListResult.ok(List.of(sample())));
        assertEquals(200, resource.listCheques(null).getStatus());
    }
}