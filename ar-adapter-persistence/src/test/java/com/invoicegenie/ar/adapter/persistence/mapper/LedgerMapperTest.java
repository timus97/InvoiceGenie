package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.LedgerEntryEntity;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LedgerMapperTest {

    private final LedgerMapper mapper = new LedgerMapper();
    private final TenantId tenantId = TenantId.of(UUID.randomUUID());

    @Test
    void toEntity_MapsDebitEntry() {
        UUID txId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        LedgerEntry entry = new LedgerEntry(
                Account.AR, Money.of("100.00", "USD"), EntryType.DEBIT,
                "Invoice INV-1 issued", txId, "INVOICE", invoiceId);

        LedgerEntryEntity entity = mapper.toEntity(tenantId, entry);

        assertEquals(entry.getId(), entity.getId());
        assertEquals(tenantId.getValue(), entity.getTenantId());
        assertEquals("AR", entity.getAccount());
        assertEquals(0, new BigDecimal("100.00").compareTo(entity.getDebit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(entity.getCredit()));
        assertEquals(txId, entity.getTransactionId());
        assertEquals(invoiceId, entity.getInvoiceId());
        assertEquals("INVOICE", entity.getReferenceType());
    }

    @Test
    void toEntity_MapsCreditEntry() {
        UUID txId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        LedgerEntry entry = new LedgerEntry(
                Account.AR, Money.of("50.00", "USD"), EntryType.CREDIT,
                "Payment applied", txId, "PAYMENT", paymentId);

        LedgerEntryEntity entity = mapper.toEntity(tenantId, entry);

        assertEquals(0, BigDecimal.ZERO.compareTo(entity.getDebit()));
        assertEquals(0, new BigDecimal("50.00").compareTo(entity.getCredit()));
        assertEquals(paymentId, entity.getPaymentId());
    }

    @Test
    void toDomain_RoundTrips() {
        UUID txId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();
        LedgerEntry original = new LedgerEntry(
                Account.REVENUE, Money.of("200.00", "EUR"), EntryType.CREDIT,
                "Revenue", txId, "INVOICE", refId);

        LedgerEntry restored = mapper.toDomain(mapper.toEntity(tenantId, original));

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getAccount(), restored.getAccount());
        assertEquals(original.getEntryType(), restored.getEntryType());
        assertEquals(original.getAmount(), restored.getAmount());
        assertEquals(original.getTransactionId(), restored.getTransactionId());
        assertEquals(original.getReferenceId(), restored.getReferenceId());
    }
}
