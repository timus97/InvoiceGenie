package com.invoicegenie.ar.adapter.persistence.entity;

import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChequeEntityTest {

    @Test
    void testGettersAndSetters() {
        ChequeEntity entity = new ChequeEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setChequeNumber("CHQ-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("500.00"));
        entity.setCurrency("USD");
        entity.setBankName("First National Bank");
        entity.setBankBranch("Downtown Branch");
        entity.setChequeDate(today);
        entity.setReceivedDate(today);
        entity.setDepositedDate(today.plusDays(1));
        entity.setClearedDate(today.plusDays(3));
        entity.setBouncedDate(null);
        entity.setBounceReason(null);
        entity.setStatus(ChequeStatus.CLEARED);
        entity.setPaymentId(paymentId);
        entity.setNotes("Customer payment");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

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
    void testBouncedCheque() {
        ChequeEntity entity = new ChequeEntity();
        LocalDate today = LocalDate.now();

        entity.setStatus(ChequeStatus.BOUNCED);
        entity.setBouncedDate(today);
        entity.setBounceReason("Insufficient funds");

        assertEquals(ChequeStatus.BOUNCED, entity.getStatus());
        assertEquals(today, entity.getBouncedDate());
        assertEquals("Insufficient funds", entity.getBounceReason());
    }

    @Test
    void testNullValues() {
        ChequeEntity entity = new ChequeEntity();
        
        assertNull(entity.getId());
        assertNull(entity.getTenantId());
        assertNull(entity.getChequeNumber());
        assertNull(entity.getCustomerId());
        assertNull(entity.getAmount());
        assertNull(entity.getCurrency());
        assertNull(entity.getBankName());
        assertNull(entity.getBankBranch());
        assertNull(entity.getChequeDate());
        assertNull(entity.getReceivedDate());
        assertNull(entity.getDepositedDate());
        assertNull(entity.getClearedDate());
        assertNull(entity.getBouncedDate());
        assertNull(entity.getBounceReason());
        assertNull(entity.getStatus());
        assertNull(entity.getPaymentId());
        assertNull(entity.getNotes());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }

    @Test
    void testAllStatuses() {
        ChequeEntity entity = new ChequeEntity();
        
        entity.setStatus(ChequeStatus.RECEIVED);
        assertEquals(ChequeStatus.RECEIVED, entity.getStatus());
        
        entity.setStatus(ChequeStatus.DEPOSITED);
        assertEquals(ChequeStatus.DEPOSITED, entity.getStatus());
        
        entity.setStatus(ChequeStatus.CLEARED);
        assertEquals(ChequeStatus.CLEARED, entity.getStatus());
        
        entity.setStatus(ChequeStatus.BOUNCED);
        assertEquals(ChequeStatus.BOUNCED, entity.getStatus());
    }

    @Test
    void testOptionalFields() {
        ChequeEntity entity = new ChequeEntity();
        
        // Bank branch is optional
        entity.setBankBranch(null);
        assertNull(entity.getBankBranch());
        
        // Deposited, cleared, bounced dates are optional
        entity.setDepositedDate(null);
        assertNull(entity.getDepositedDate());
        
        entity.setClearedDate(null);
        assertNull(entity.getClearedDate());
        
        entity.setBouncedDate(null);
        assertNull(entity.getBouncedDate());
        
        entity.setBounceReason(null);
        assertNull(entity.getBounceReason());
        
        // Payment ID is optional
        entity.setPaymentId(null);
        assertNull(entity.getPaymentId());
        
        // Notes are optional
        entity.setNotes(null);
        assertNull(entity.getNotes());
    }
}
