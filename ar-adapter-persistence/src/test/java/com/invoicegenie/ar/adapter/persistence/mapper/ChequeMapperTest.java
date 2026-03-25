package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.ChequeEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChequeMapperTest {

    private final ChequeMapper mapper = new ChequeMapper();

    @Test
    void testToEntity() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        Cheque cheque = new Cheque(
            id,
            "CHQ-001",
            CustomerId.of(customerId),
            Money.of("500.00", "USD"),
            "First National Bank",
            "Downtown Branch",
            today,
            today,
            today.plusDays(1),
            today.plusDays(3),
            null,
            null,
            ChequeStatus.CLEARED,
            paymentId,
            new ArrayList<>(),
            "Customer payment",
            now,
            now
        );

        ChequeEntity entity = mapper.toEntity(TenantId.of(tenantId), cheque);

        assertEquals(id, entity.getId());
        assertEquals(tenantId, entity.getTenantId());
        assertEquals("CHQ-001", entity.getChequeNumber());
        assertEquals(customerId, entity.getCustomerId());
        assertEquals(new BigDecimal("500.00"), entity.getAmount());
        assertEquals("USD", entity.getCurrency());
        assertEquals("First National Bank", entity.getBankName());
        assertEquals("Downtown Branch", entity.getBankBranch());
        assertEquals(today, entity.getChequeDate());
        assertEquals(today, entity.getReceivedDate());
        assertEquals(today.plusDays(1), entity.getDepositedDate());
        assertEquals(today.plusDays(3), entity.getClearedDate());
        assertNull(entity.getBouncedDate());
        assertNull(entity.getBounceReason());
        assertEquals(ChequeStatus.CLEARED, entity.getStatus());
        assertEquals(paymentId, entity.getPaymentId());
        assertEquals("Customer payment", entity.getNotes());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    void testToEntity_WithBouncedCheque() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        Cheque cheque = new Cheque(
            id,
            "CHQ-002",
            CustomerId.of(customerId),
            Money.of("250.00", "USD"),
            "City Bank",
            null,
            today,
            today,
            today.plusDays(1),
            null,
            today.plusDays(2),
            "Insufficient funds",
            ChequeStatus.BOUNCED,
            null,
            new ArrayList<>(),
            "Bounced cheque",
            now,
            now
        );

        ChequeEntity entity = mapper.toEntity(TenantId.of(tenantId), cheque);

        assertEquals(ChequeStatus.BOUNCED, entity.getStatus());
        assertEquals(today.plusDays(2), entity.getBouncedDate());
        assertEquals("Insufficient funds", entity.getBounceReason());
    }

    @Test
    void testToDomain() {
        ChequeEntity entity = new ChequeEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setChequeNumber("CHQ-003");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("1000.00"));
        entity.setCurrency("USD");
        entity.setBankName("Test Bank");
        entity.setBankBranch("Main Branch");
        entity.setChequeDate(today);
        entity.setReceivedDate(today);
        entity.setDepositedDate(today.plusDays(1));
        entity.setClearedDate(today.plusDays(2));
        entity.setBouncedDate(null);
        entity.setBounceReason(null);
        entity.setStatus(ChequeStatus.CLEARED);
        entity.setPaymentId(paymentId);
        entity.setNotes("Test notes");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        Cheque cheque = mapper.toDomain(entity);

        assertEquals(id, cheque.getId());
        assertEquals("CHQ-003", cheque.getChequeNumber());
        assertEquals(customerId, cheque.getCustomerId().getValue());
        assertEquals(new BigDecimal("1000.00"), cheque.getAmount().getAmount());
        assertEquals("USD", cheque.getAmount().getCurrencyCode());
        assertEquals("Test Bank", cheque.getBankName());
        assertEquals("Main Branch", cheque.getBankBranch());
        assertEquals(today, cheque.getChequeDate());
        assertEquals(today, cheque.getReceivedDate());
        assertEquals(today.plusDays(1), cheque.getDepositedDate());
        assertEquals(today.plusDays(2), cheque.getClearedDate());
        assertEquals(ChequeStatus.CLEARED, cheque.getStatus());
        assertEquals(paymentId, cheque.getPaymentId());
        assertEquals("Test notes", cheque.getNotes());
        assertEquals(now, cheque.getCreatedAt());
        assertEquals(now, cheque.getUpdatedAt());
    }

    @Test
    void testToDomain_WithNullOptionalFields() {
        ChequeEntity entity = new ChequeEntity();
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        entity.setId(id);
        entity.setTenantId(UUID.randomUUID());
        entity.setChequeNumber("CHQ-004");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("100.00"));
        entity.setCurrency("USD");
        entity.setBankName("Bank");
        entity.setBankBranch(null);
        entity.setChequeDate(today);
        entity.setReceivedDate(today);
        entity.setDepositedDate(null);
        entity.setClearedDate(null);
        entity.setBouncedDate(null);
        entity.setBounceReason(null);
        entity.setStatus(ChequeStatus.RECEIVED);
        entity.setPaymentId(null);
        entity.setNotes(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        Cheque cheque = mapper.toDomain(entity);

        assertNull(cheque.getBankBranch());
        assertNull(cheque.getDepositedDate());
        assertNull(cheque.getClearedDate());
        assertNull(cheque.getBouncedDate());
        assertNull(cheque.getBounceReason());
        assertNull(cheque.getPaymentId());
        assertNull(cheque.getNotes());
        assertEquals(ChequeStatus.RECEIVED, cheque.getStatus());
    }

    @Test
    void testRoundTrip() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        Cheque original = new Cheque(
            id,
            "CHQ-RT",
            CustomerId.of(customerId),
            Money.of("750.00", "USD"),
            "Round Trip Bank",
            "Branch 1",
            today,
            today,
            today.plusDays(1),
            today.plusDays(2),
            null,
            null,
            ChequeStatus.CLEARED,
            paymentId,
            new ArrayList<>(),
            "Round trip test",
            now,
            now
        );

        ChequeEntity entity = mapper.toEntity(TenantId.of(tenantId), original);
        Cheque result = mapper.toDomain(entity);

        assertEquals(original.getId(), result.getId());
        assertEquals(original.getChequeNumber(), result.getChequeNumber());
        assertEquals(original.getCustomerId().getValue(), result.getCustomerId().getValue());
        assertEquals(original.getAmount().getAmount(), result.getAmount().getAmount());
        assertEquals(original.getAmount().getCurrencyCode(), result.getAmount().getCurrencyCode());
        assertEquals(original.getBankName(), result.getBankName());
        assertEquals(original.getBankBranch(), result.getBankBranch());
        assertEquals(original.getChequeDate(), result.getChequeDate());
        assertEquals(original.getReceivedDate(), result.getReceivedDate());
        assertEquals(original.getDepositedDate(), result.getDepositedDate());
        assertEquals(original.getClearedDate(), result.getClearedDate());
        assertEquals(original.getStatus(), result.getStatus());
        assertEquals(original.getPaymentId(), result.getPaymentId());
        assertEquals(original.getNotes(), result.getNotes());
    }
}
