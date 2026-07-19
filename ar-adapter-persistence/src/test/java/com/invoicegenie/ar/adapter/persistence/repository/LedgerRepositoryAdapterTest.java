package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.LedgerEntryEntity;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LedgerRepositoryAdapterTest {

    private LedgerRepositoryAdapter adapter;
    private EntityManager em;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        adapter = new LedgerRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Test
    void save_MergesEntity() {
        adapter.save(tenantId, createLedgerEntry());
        verify(em).merge(any(LedgerEntryEntity.class));
    }

    @Test
    void saveAll_MergesAllEntries() {
        adapter.saveAll(tenantId, List.of(createLedgerEntry(), createLedgerEntry()));
        verify(em, times(2)).merge(any(LedgerEntryEntity.class));
    }

    @Test
    void findByTenantAndId_ReturnsEntry_WhenFoundAndTenantMatches() {
        LedgerEntry entry = createLedgerEntry();
        when(em.find(LedgerEntryEntity.class, entry.getId())).thenReturn(toEntity(entry, tenantId.getValue()));

        Optional<LedgerEntry> found = adapter.findByTenantAndId(tenantId, entry.getId());
        assertTrue(found.isPresent());
        assertEquals(entry.getId(), found.get().getId());
        assertEquals(Account.AR, found.get().getAccount());
        assertEquals(EntryType.DEBIT, found.get().getEntryType());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(em.find(LedgerEntryEntity.class, id)).thenReturn(null);
        assertTrue(adapter.findByTenantAndId(tenantId, id).isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_ForDifferentTenant() {
        LedgerEntry entry = createLedgerEntry();
        when(em.find(LedgerEntryEntity.class, entry.getId())).thenReturn(toEntity(entry, UUID.randomUUID()));
        assertTrue(adapter.findByTenantAndId(tenantId, entry.getId()).isEmpty());
    }

    @Test
    void findByTenantAndTransactionId_ReturnsEntries() {
        UUID txId = UUID.randomUUID();
        LedgerEntry entry = createLedgerEntryWithTransaction(txId);
        TypedQuery<LedgerEntryEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(LedgerEntryEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(Stream.of(toEntity(entry, tenantId.getValue())));

        List<LedgerEntry> result = adapter.findByTenantAndTransactionId(tenantId, txId);
        assertEquals(1, result.size());
        assertEquals(txId, result.get(0).getTransactionId());
    }

    @Test
    void findByTenantAndReference_ReturnsEntries() {
        UUID refId = UUID.randomUUID();
        LedgerEntry entry = createLedgerEntryWithReference("INVOICE", refId);
        TypedQuery<LedgerEntryEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(LedgerEntryEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(Stream.of(toEntity(entry, tenantId.getValue())));

        assertEquals(1, adapter.findByTenantAndReference(tenantId, "INVOICE", refId).size());
    }

    @Test
    void getAccountBalance_CalculatesBalanceCorrectly() {
        LedgerEntry debit = new LedgerEntry(Account.AR, Money.of("100.00", "USD"), EntryType.DEBIT,
                "Invoice", UUID.randomUUID(), "INVOICE", UUID.randomUUID());
        LedgerEntry credit = new LedgerEntry(Account.AR, Money.of("30.00", "USD"), EntryType.CREDIT,
                "Payment", UUID.randomUUID(), "PAYMENT", UUID.randomUUID());
        TypedQuery<LedgerEntryEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(LedgerEntryEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(
                toEntity(debit, tenantId.getValue()), toEntity(credit, tenantId.getValue())));

        BigDecimal balance = adapter.getAccountBalance(tenantId, Account.AR, "USD");
        assertEquals(0, new BigDecimal("70.00").compareTo(balance));
    }

    @Test
    void getAccountBalance_ReturnsZero_WhenNoEntries() {
        TypedQuery<LedgerEntryEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(LedgerEntryEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        assertEquals(BigDecimal.ZERO, adapter.getAccountBalance(tenantId, Account.AR, "USD"));
    }

    private LedgerEntry createLedgerEntry() {
        return new LedgerEntry(Account.AR, Money.of("100.00", "USD"), EntryType.DEBIT, "Invoice",
                UUID.randomUUID(), "INVOICE", UUID.randomUUID());
    }

    private LedgerEntry createLedgerEntryWithTransaction(UUID transactionId) {
        return new LedgerEntry(Account.AR, Money.of("50.00", "USD"), EntryType.DEBIT, "Invoice",
                transactionId, "INVOICE", UUID.randomUUID());
    }

    private LedgerEntry createLedgerEntryWithReference(String referenceType, UUID referenceId) {
        return new LedgerEntry(Account.AR, Money.of("75.00", "USD"), EntryType.DEBIT, referenceType,
                UUID.randomUUID(), referenceType, referenceId);
    }

    private LedgerEntryEntity toEntity(LedgerEntry entry, UUID tenant) {
        LedgerEntryEntity e = new LedgerEntryEntity();
        e.setId(entry.getId());
        e.setTenantId(tenant);
        e.setEntryNumber("LE-test");
        e.setEntryDate(java.time.LocalDate.now());
        e.setPostingDate(java.time.LocalDate.now());
        e.setAccount(entry.getAccount().name());
        e.setCurrency(entry.getAmount().getCurrencyCode());
        e.setDescription(entry.getDescription());
        e.setTransactionId(entry.getTransactionId());
        e.setReferenceType(entry.getReferenceType());
        e.setReferenceId(entry.getReferenceId());
        e.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now());
        if (entry.getEntryType() == EntryType.DEBIT) {
            e.setDebit(entry.getAmount().getAmount());
            e.setCredit(BigDecimal.ZERO.setScale(2));
        } else {
            e.setDebit(BigDecimal.ZERO.setScale(2));
            e.setCredit(entry.getAmount().getAmount());
        }
        return e;
    }
}
