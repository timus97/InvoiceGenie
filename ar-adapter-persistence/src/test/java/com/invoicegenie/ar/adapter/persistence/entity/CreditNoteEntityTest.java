package com.invoicegenie.ar.adapter.persistence.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreditNoteEntityTest {

    @Test
    void testGettersAndSetters() {
        CreditNoteEntity entity = new CreditNoteEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID referenceInvoiceId = UUID.randomUUID();
        UUID appliedToPaymentId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setCreditNoteNumber("CN-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("100.00"));
        entity.setCurrency("USD");
        entity.setType(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT);
        entity.setReferenceInvoiceId(referenceInvoiceId);
        entity.setDescription("Early payment discount");
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.ISSUED);
        entity.setIssueDate(today);
        entity.setAppliedDate(today.plusDays(1));
        entity.setExpiryDate(today.plusDays(30));
        entity.setAppliedToPaymentId(appliedToPaymentId);
        entity.setNotes("Test notes");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertEquals(id, entity.getId());
        assertEquals(tenantId, entity.getTenantId());
        assertEquals("CN-001", entity.getCreditNoteNumber());
        assertEquals(customerId, entity.getCustomerId());
        assertEquals(new BigDecimal("100.00"), entity.getAmount());
        assertEquals("USD", entity.getCurrency());
        assertEquals(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT, entity.getType());
        assertEquals(referenceInvoiceId, entity.getReferenceInvoiceId());
        assertEquals("Early payment discount", entity.getDescription());
        assertEquals(CreditNoteEntity.CreditNoteStatus.ISSUED, entity.getStatus());
        assertEquals(today, entity.getIssueDate());
        assertEquals(today.plusDays(1), entity.getAppliedDate());
        assertEquals(today.plusDays(30), entity.getExpiryDate());
        assertEquals(appliedToPaymentId, entity.getAppliedToPaymentId());
        assertEquals("Test notes", entity.getNotes());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    void testCreditNoteTypeEnum() {
        assertEquals(3, CreditNoteEntity.CreditNoteType.values().length);
        assertEquals(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT, 
            CreditNoteEntity.CreditNoteType.valueOf("EARLY_PAYMENT_DISCOUNT"));
        assertEquals(CreditNoteEntity.CreditNoteType.ADJUSTMENT, 
            CreditNoteEntity.CreditNoteType.valueOf("ADJUSTMENT"));
        assertEquals(CreditNoteEntity.CreditNoteType.REFUND, 
            CreditNoteEntity.CreditNoteType.valueOf("REFUND"));
    }

    @Test
    void testCreditNoteStatusEnum() {
        assertEquals(4, CreditNoteEntity.CreditNoteStatus.values().length);
        assertEquals(CreditNoteEntity.CreditNoteStatus.ISSUED, 
            CreditNoteEntity.CreditNoteStatus.valueOf("ISSUED"));
        assertEquals(CreditNoteEntity.CreditNoteStatus.APPLIED, 
            CreditNoteEntity.CreditNoteStatus.valueOf("APPLIED"));
        assertEquals(CreditNoteEntity.CreditNoteStatus.EXPIRED, 
            CreditNoteEntity.CreditNoteStatus.valueOf("EXPIRED"));
        assertEquals(CreditNoteEntity.CreditNoteStatus.VOIDED, 
            CreditNoteEntity.CreditNoteStatus.valueOf("VOIDED"));
    }

    @Test
    void testNullValues() {
        CreditNoteEntity entity = new CreditNoteEntity();
        assertNull(entity.getId());
        assertNull(entity.getTenantId());
        assertNull(entity.getCreditNoteNumber());
        assertNull(entity.getCustomerId());
        assertNull(entity.getAmount());
        assertNull(entity.getCurrency());
        assertNull(entity.getType());
        assertNull(entity.getReferenceInvoiceId());
        assertNull(entity.getDescription());
        assertNull(entity.getStatus());
        assertNull(entity.getIssueDate());
        assertNull(entity.getAppliedDate());
        assertNull(entity.getExpiryDate());
        assertNull(entity.getAppliedToPaymentId());
        assertNull(entity.getNotes());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }

    @Test
    void testAllTypes() {
        CreditNoteEntity entity = new CreditNoteEntity();
        
        entity.setType(CreditNoteEntity.CreditNoteType.ADJUSTMENT);
        assertEquals(CreditNoteEntity.CreditNoteType.ADJUSTMENT, entity.getType());
        
        entity.setType(CreditNoteEntity.CreditNoteType.REFUND);
        assertEquals(CreditNoteEntity.CreditNoteType.REFUND, entity.getType());
    }

    @Test
    void testAllStatuses() {
        CreditNoteEntity entity = new CreditNoteEntity();
        
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.APPLIED);
        assertEquals(CreditNoteEntity.CreditNoteStatus.APPLIED, entity.getStatus());
        
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.EXPIRED);
        assertEquals(CreditNoteEntity.CreditNoteStatus.EXPIRED, entity.getStatus());
        
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.VOIDED);
        assertEquals(CreditNoteEntity.CreditNoteStatus.VOIDED, entity.getStatus());
    }
}
