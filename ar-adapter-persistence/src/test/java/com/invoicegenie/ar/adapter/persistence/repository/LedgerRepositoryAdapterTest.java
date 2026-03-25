package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LedgerRepositoryAdapterTest {

    private LedgerRepositoryAdapter adapter;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        adapter = new LedgerRepositoryAdapter();
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Test
    void save_StoresEntry() {
        LedgerEntry entry = createLedgerEntry();
        
        adapter.save(tenantId, entry);
        
        Optional<LedgerEntry> found = adapter.findByTenantAndId(tenantId, entry.getId());
        assertTrue(found.isPresent());
        assertEquals(entry.getId(), found.get().getId());
    }

    @Test
    void saveAll_StoresMultipleEntries() {
        LedgerEntry entry1 = createLedgerEntry();
        LedgerEntry entry2 = createLedgerEntry();
        
        adapter.saveAll(tenantId, List.of(entry1, entry2));
        
        Optional<LedgerEntry> found1 = adapter.findByTenantAndId(tenantId, entry1.getId());
        Optional<LedgerEntry> found2 = adapter.findByTenantAndId(tenantId, entry2.getId());
        
        assertTrue(found1.isPresent());
        assertTrue(found2.isPresent());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        Optional<LedgerEntry> result = adapter.findByTenantAndId(tenantId, UUID.randomUUID());
        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_ForDifferentTenant() {
        LedgerEntry entry = createLedgerEntry();
        adapter.save(tenantId, entry);
        
        TenantId differentTenant = TenantId.of(UUID.randomUUID());
        Optional<LedgerEntry> result = adapter.findByTenantAndId(differentTenant, entry.getId());
        
        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndTransactionId_ReturnsEntries() {
        UUID transactionId = UUID.randomUUID();
        LedgerEntry entry1 = createLedgerEntryWithTransaction(transactionId);
        LedgerEntry entry2 = createLedgerEntryWithTransaction(transactionId);
        
        adapter.saveAll(tenantId, List.of(entry1, entry2));
        
        List<LedgerEntry> result = adapter.findByTenantAndTransactionId(tenantId, transactionId);
        
        assertEquals(2, result.size());
    }

    @Test
    void findByTenantAndTransactionId_ReturnsEmpty_WhenNotFound() {
        List<LedgerEntry> result = adapter.findByTenantAndTransactionId(tenantId, UUID.randomUUID());
        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndReference_ReturnsEntries() {
        UUID referenceId = UUID.randomUUID();
        LedgerEntry entry = createLedgerEntryWithReference("Invoice", referenceId);
        
        adapter.save(tenantId, entry);
        
        List<LedgerEntry> result = adapter.findByTenantAndReference(tenantId, "Invoice", referenceId);
        
        assertEquals(1, result.size());
    }

    @Test
    void findByTenantAndReference_ReturnsEmpty_WhenReferenceTypeMismatch() {
        UUID referenceId = UUID.randomUUID();
        LedgerEntry entry = createLedgerEntryWithReference("Invoice", referenceId);
        
        adapter.save(tenantId, entry);
        
        List<LedgerEntry> result = adapter.findByTenantAndReference(tenantId, "Payment", referenceId);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndReference_ReturnsEmpty_WhenReferenceIdMismatch() {
        UUID referenceId = UUID.randomUUID();
        LedgerEntry entry = createLedgerEntryWithReference("Invoice", referenceId);
        
        adapter.save(tenantId, entry);
        
        List<LedgerEntry> result = adapter.findByTenantAndReference(tenantId, "Invoice", UUID.randomUUID());
        
        assertTrue(result.isEmpty());
    }

    @Test
    void getAccountBalance_ReturnsZero_WhenNoEntries() {
        BigDecimal balance = adapter.getAccountBalance(tenantId, Account.AR, "USD");
        assertEquals(BigDecimal.ZERO, balance);
    }

    @Test
    void getAccountBalance_CalculatesBalanceCorrectly() {
        // Create debit entry (increases AR)
        LedgerEntry debit = new LedgerEntry(
            Account.AR,
            Money.of("100.00", "USD"),
            EntryType.DEBIT,
            "Invoice",
            UUID.randomUUID(),
            "Invoice",
            UUID.randomUUID()
        );
        
        // Create credit entry (decreases AR)
        LedgerEntry credit = new LedgerEntry(
            Account.AR,
            Money.of("30.00", "USD"),
            EntryType.CREDIT,
            "Payment",
            UUID.randomUUID(),
            "Payment",
            UUID.randomUUID()
        );
        
        adapter.saveAll(tenantId, List.of(debit, credit));
        
        BigDecimal balance = adapter.getAccountBalance(tenantId, Account.AR, "USD");
        
        // Balance should be 100 - 30 = 70
        assertEquals(0, new BigDecimal("70.00").compareTo(balance));
    }

    @Test
    void getAccountBalance_FiltersByCurrency() {
        LedgerEntry usdEntry = new LedgerEntry(
            Account.AR,
            Money.of("100.00", "USD"),
            EntryType.DEBIT,
            "Invoice",
            UUID.randomUUID(),
            "Invoice",
            UUID.randomUUID()
        );
        
        LedgerEntry eurEntry = new LedgerEntry(
            Account.AR,
            Money.of("100.00", "EUR"),
            EntryType.DEBIT,
            "Invoice",
            UUID.randomUUID(),
            "Invoice",
            UUID.randomUUID()
        );
        
        adapter.saveAll(tenantId, List.of(usdEntry, eurEntry));
        
        BigDecimal usdBalance = adapter.getAccountBalance(tenantId, Account.AR, "USD");
        BigDecimal eurBalance = adapter.getAccountBalance(tenantId, Account.AR, "EUR");
        
        assertEquals(0, new BigDecimal("100.00").compareTo(usdBalance));
        assertEquals(0, new BigDecimal("100.00").compareTo(eurBalance));
    }

    @Test
    void getAccountBalance_FiltersByAccount() {
        LedgerEntry arEntry = new LedgerEntry(
            Account.AR,
            Money.of("100.00", "USD"),
            EntryType.DEBIT,
            "Invoice",
            UUID.randomUUID(),
            "Invoice",
            UUID.randomUUID()
        );
        
        LedgerEntry revenueEntry = new LedgerEntry(
            Account.REVENUE,
            Money.of("100.00", "USD"),
            EntryType.CREDIT,
            "Invoice",
            UUID.randomUUID(),
            "Invoice",
            UUID.randomUUID()
        );
        
        adapter.saveAll(tenantId, List.of(arEntry, revenueEntry));
        
        BigDecimal arBalance = adapter.getAccountBalance(tenantId, Account.AR, "USD");
        BigDecimal revenueBalance = adapter.getAccountBalance(tenantId, Account.REVENUE, "USD");
        
        assertEquals(0, new BigDecimal("100.00").compareTo(arBalance));
        assertEquals(0, new BigDecimal("-100.00").compareTo(revenueBalance));
    }

    private LedgerEntry createLedgerEntry() {
        return new LedgerEntry(
            Account.AR,
            Money.of("100.00", "USD"),
            EntryType.DEBIT,
            "Invoice",
            UUID.randomUUID(),
            "Invoice",
            UUID.randomUUID()
        );
    }

    private LedgerEntry createLedgerEntryWithTransaction(UUID transactionId) {
        return new LedgerEntry(
            Account.AR,
            Money.of("50.00", "USD"),
            EntryType.DEBIT,
            "Invoice",
            transactionId,
            "Invoice",
            UUID.randomUUID()
        );
    }

    private LedgerEntry createLedgerEntryWithReference(String referenceType, UUID referenceId) {
        return new LedgerEntry(
            Account.AR,
            Money.of("75.00", "USD"),
            EntryType.DEBIT,
            referenceType,
            UUID.randomUUID(),
            referenceType,
            referenceId
        );
    }
}
