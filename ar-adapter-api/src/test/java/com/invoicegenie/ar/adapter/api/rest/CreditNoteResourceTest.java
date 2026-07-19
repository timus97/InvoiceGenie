package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.CreditNoteUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.ar.domain.service.CreditNoteService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CreditNoteResource")
@ExtendWith(MockitoExtension.class)
class CreditNoteResourceTest {

    @Mock private CreditNoteUseCase creditNoteUseCase;
    private CreditNoteResource resource;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        resource = new CreditNoteResource(creditNoteUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CreditNote sample() {
        return new CreditNote(UUID.randomUUID(), "CN-1", new CustomerId(UUID.randomUUID()),
                Money.of("10", "USD"), CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, null, "desc");
    }

    @Test
    @DisplayName("generate success")
    void generateOk() {
        CreditNote cn = sample();
        when(creditNoteUseCase.generateEarlyPaymentDiscount(eq(tenantId), any(), any(), any()))
                .thenReturn(new CreditNoteService.CreditNoteResult(cn, true, "ok"));
        var dto = new CreditNoteResource.GenerateCreditNoteDto(
                UUID.randomUUID().toString(), new BigDecimal("10"), "USD", UUID.randomUUID().toString());
        assertEquals(201, resource.generateEarlyPaymentDiscount(dto).getStatus());
    }

    @Test
    @DisplayName("generate failure")
    void generateFail() {
        when(creditNoteUseCase.generateEarlyPaymentDiscount(eq(tenantId), any(), any(), any()))
                .thenReturn(new CreditNoteService.CreditNoteResult(null, false, "no"));
        var dto = new CreditNoteResource.GenerateCreditNoteDto(
                UUID.randomUUID().toString(), new BigDecimal("10"), "USD", null);
        assertEquals(400, resource.generateEarlyPaymentDiscount(dto).getStatus());
    }

    @Test
    @DisplayName("get found")
    void getOk() {
        CreditNote cn = sample();
        when(creditNoteUseCase.get(eq(tenantId), eq(cn.getId()))).thenReturn(Optional.of(cn));
        assertEquals(200, resource.getCreditNote(cn.getId().toString()).getStatus());
    }

    @Test
    @DisplayName("get missing")
    void getMissing() {
        when(creditNoteUseCase.get(eq(tenantId), any())).thenReturn(Optional.empty());
        assertEquals(404, resource.getCreditNote(UUID.randomUUID().toString()).getStatus());
    }

    @Test
    @DisplayName("list ok")
    void listOk() {
        when(creditNoteUseCase.list(eq(tenantId), isNull()))
                .thenReturn(CreditNoteUseCase.ListResult.ok(List.of(sample())));
        assertEquals(200, resource.listCreditNotes(null).getStatus());
    }

    @Test
    @DisplayName("apply missing")
    void applyMissing() {
        when(creditNoteUseCase.apply(eq(tenantId), any(), any())).thenReturn(Optional.empty());
        var dto = new CreditNoteResource.ApplyCreditNoteDto(UUID.randomUUID().toString());
        assertEquals(404, resource.applyCreditNote(UUID.randomUUID().toString(), dto).getStatus());
    }
}