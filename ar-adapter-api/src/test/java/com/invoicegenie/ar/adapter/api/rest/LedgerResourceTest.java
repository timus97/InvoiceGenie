package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.LedgerQueryUseCase;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LedgerResource")
@ExtendWith(MockitoExtension.class)
class LedgerResourceTest {

    @Mock private LedgerQueryUseCase ledgerQueryUseCase;
    private LedgerResource resource;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        resource = new LedgerResource(ledgerQueryUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("listAccounts")
    void listAccounts() {
        when(ledgerQueryUseCase.listAccounts()).thenReturn(List.of(Account.AR));
        assertEquals(200, resource.listAccounts().getStatus());
    }

    @Test
    @DisplayName("getAccountBalance ok")
    void balanceOk() {
        when(ledgerQueryUseCase.getAccountBalance(eq(tenantId), eq(Account.AR), eq("USD")))
                .thenReturn(new BigDecimal("10.00"));
        assertEquals(200, resource.getAccountBalance("AR", "USD").getStatus());
    }

    @Test
    @DisplayName("getAccountBalance invalid account")
    void balanceBad() {
        assertEquals(400, resource.getAccountBalance("NOPE", "USD").getStatus());
    }

    @Test
    @DisplayName("getTransaction not found")
    void txMissing() {
        when(ledgerQueryUseCase.getTransaction(eq(tenantId), any())).thenReturn(List.of());
        assertEquals(404, resource.getTransaction(UUID.randomUUID().toString()).getStatus());
    }

    @Test
    @DisplayName("getTransaction found")
    void txFound() {
        UUID tx = UUID.randomUUID();
        LedgerEntry e = new LedgerEntry(Account.AR, Money.of("10", "USD"),
                EntryType.DEBIT, "desc", tx, "INVOICE", UUID.randomUUID());
        when(ledgerQueryUseCase.getTransaction(eq(tenantId), eq(tx))).thenReturn(List.of(e));
        when(ledgerQueryUseCase.validateBalanced(any())).thenReturn(true);
        assertEquals(200, resource.getTransaction(tx.toString()).getStatus());
    }

    @Test
    @DisplayName("getByReference")
    void byRef() {
        when(ledgerQueryUseCase.getByReference(eq(tenantId), eq("INVOICE"), any())).thenReturn(List.of());
        assertEquals(200, resource.getByReference("invoice", UUID.randomUUID().toString()).getStatus());
    }

    @Test
    @DisplayName("validate")
    void validate() {
        assertEquals(200, resource.validateEntries(new LedgerResource.ValidateRequestDto()).getStatus());
    }
}