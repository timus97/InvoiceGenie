package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("LedgerQueryService")
@ExtendWith(MockitoExtension.class)
class LedgerQueryServiceTest {

    @Mock private LedgerRepository ledgerRepository;
    private LedgerService ledgerService;
    private LedgerQueryService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService();
        service = new LedgerQueryService(ledgerService, ledgerRepository);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("should list all accounts")
    void shouldListAccounts() {
        List<Account> accounts = service.listAccounts();
        assertFalse(accounts.isEmpty());
        assertTrue(accounts.contains(Account.AR));
        assertTrue(accounts.contains(Account.BANK));
    }

    @Test
    @DisplayName("should get account balance from repository")
    void shouldGetBalance() {
        when(ledgerRepository.getAccountBalance(tenantId, Account.AR, "USD"))
                .thenReturn(new BigDecimal("1500.00"));

        BigDecimal balance = service.getAccountBalance(tenantId, Account.AR, "USD");

        assertEquals(0, new BigDecimal("1500.00").compareTo(balance));
        verify(ledgerRepository).getAccountBalance(eq(tenantId), eq(Account.AR), eq("USD"));
    }

    @Test
    @DisplayName("should get transaction entries")
    void shouldGetTransaction() {
        UUID txId = UUID.randomUUID();
        when(ledgerRepository.findByTenantAndTransactionId(tenantId, txId)).thenReturn(List.of());

        assertTrue(service.getTransaction(tenantId, txId).isEmpty());
    }

    @Test
    @DisplayName("should get entries by reference")
    void shouldGetByReference() {
        UUID refId = UUID.randomUUID();
        when(ledgerRepository.findByTenantAndReference(tenantId, "INVOICE", refId)).thenReturn(List.of());

        assertTrue(service.getByReference(tenantId, "INVOICE", refId).isEmpty());
    }

    @Nested
    @DisplayName("validateBalanced")
    class Validate {
        @Test
        @DisplayName("should validate balanced entries")
        void shouldValidateBalanced() {
            UUID txId = UUID.randomUUID();
            List<LedgerEntry> entries = List.of(
                    new LedgerEntry(Account.AR, Money.of("100.00", "USD"), EntryType.DEBIT,
                            "dr", txId, "INVOICE", UUID.randomUUID()),
                    new LedgerEntry(Account.REVENUE, Money.of("100.00", "USD"), EntryType.CREDIT,
                            "cr", txId, "INVOICE", UUID.randomUUID())
            );

            assertTrue(service.validateBalanced(entries));
        }

        @Test
        @DisplayName("should reject unbalanced entries")
        void shouldRejectUnbalanced() {
            UUID txId = UUID.randomUUID();
            List<LedgerEntry> entries = List.of(
                    new LedgerEntry(Account.AR, Money.of("100.00", "USD"), EntryType.DEBIT,
                            "dr", txId, "INVOICE", UUID.randomUUID())
            );

            assertFalse(service.validateBalanced(entries));
        }
    }
}
