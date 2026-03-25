package com.invoicegenie.ar.adapter.persistence.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceLineEntityTest {

    @Test
    void testGettersAndSetters() {
        InvoiceLineEntity entity = new InvoiceLineEntity();
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        entity.setTenantId(tenantId);
        entity.setInvoiceId(invoiceId);
        entity.setSequence(1);
        entity.setDescription("Consulting Services");
        entity.setQuantity(new BigDecimal("10.00"));
        entity.setUnitPrice(new BigDecimal("150.00"));
        entity.setDiscountAmount(new BigDecimal("50.00"));
        entity.setTaxRate(new BigDecimal("0.10"));
        entity.setTaxAmount(new BigDecimal("145.00"));
        entity.setLineTotal(new BigDecimal("1595.00"));

        assertEquals(tenantId, entity.getTenantId());
        assertEquals(invoiceId, entity.getInvoiceId());
        assertEquals(1, entity.getSequence());
        assertEquals("Consulting Services", entity.getDescription());
        assertEquals(new BigDecimal("10.00"), entity.getQuantity());
        assertEquals(new BigDecimal("150.00"), entity.getUnitPrice());
        assertEquals(new BigDecimal("50.00"), entity.getDiscountAmount());
        assertEquals(new BigDecimal("0.10"), entity.getTaxRate());
        assertEquals(new BigDecimal("145.00"), entity.getTaxAmount());
        assertEquals(new BigDecimal("1595.00"), entity.getLineTotal());
    }

    @Test
    void testInvoiceLineKeyDefaultConstructor() {
        InvoiceLineEntity.InvoiceLineKey key = new InvoiceLineEntity.InvoiceLineKey();
        
        assertNull(key.getTenantId());
        assertNull(key.getInvoiceId());
        assertEquals(0, key.getSequence());
    }

    @Test
    void testInvoiceLineKeyParameterizedConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        
        InvoiceLineEntity.InvoiceLineKey key = new InvoiceLineEntity.InvoiceLineKey(tenantId, invoiceId, 1);
        
        assertEquals(tenantId, key.getTenantId());
        assertEquals(invoiceId, key.getInvoiceId());
        assertEquals(1, key.getSequence());
    }

    @Test
    void testInvoiceLineKeySetters() {
        InvoiceLineEntity.InvoiceLineKey key = new InvoiceLineEntity.InvoiceLineKey();
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        
        key.setTenantId(tenantId);
        key.setInvoiceId(invoiceId);
        key.setSequence(5);
        
        assertEquals(tenantId, key.getTenantId());
        assertEquals(invoiceId, key.getInvoiceId());
        assertEquals(5, key.getSequence());
    }

    @Test
    void testInvoiceLineKeyEquals_SameValues() {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        
        InvoiceLineEntity.InvoiceLineKey key1 = new InvoiceLineEntity.InvoiceLineKey(tenantId, invoiceId, 1);
        InvoiceLineEntity.InvoiceLineKey key2 = new InvoiceLineEntity.InvoiceLineKey(tenantId, invoiceId, 1);
        
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void testInvoiceLineKeyEquals_DifferentTenant() {
        InvoiceLineEntity.InvoiceLineKey key1 = new InvoiceLineEntity.InvoiceLineKey(
            UUID.randomUUID(), UUID.randomUUID(), 1);
        InvoiceLineEntity.InvoiceLineKey key2 = new InvoiceLineEntity.InvoiceLineKey(
            UUID.randomUUID(), UUID.randomUUID(), 1);
        
        assertNotEquals(key1, key2);
    }

    @Test
    void testInvoiceLineKeyEquals_DifferentInvoice() {
        UUID tenantId = UUID.randomUUID();
        InvoiceLineEntity.InvoiceLineKey key1 = new InvoiceLineEntity.InvoiceLineKey(
            tenantId, UUID.randomUUID(), 1);
        InvoiceLineEntity.InvoiceLineKey key2 = new InvoiceLineEntity.InvoiceLineKey(
            tenantId, UUID.randomUUID(), 1);
        
        assertNotEquals(key1, key2);
    }

    @Test
    void testInvoiceLineKeyEquals_DifferentSequence() {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        InvoiceLineEntity.InvoiceLineKey key1 = new InvoiceLineEntity.InvoiceLineKey(tenantId, invoiceId, 1);
        InvoiceLineEntity.InvoiceLineKey key2 = new InvoiceLineEntity.InvoiceLineKey(tenantId, invoiceId, 2);
        
        assertNotEquals(key1, key2);
    }

    @Test
    void testInvoiceLineKeyEquals_SameInstance() {
        InvoiceLineEntity.InvoiceLineKey key = new InvoiceLineEntity.InvoiceLineKey(
            UUID.randomUUID(), UUID.randomUUID(), 1);
        
        assertEquals(key, key);
    }

    @Test
    void testInvoiceLineKeyEquals_Null() {
        InvoiceLineEntity.InvoiceLineKey key = new InvoiceLineEntity.InvoiceLineKey(
            UUID.randomUUID(), UUID.randomUUID(), 1);
        
        assertNotEquals(null, key);
    }

    @Test
    void testInvoiceLineKeyEquals_DifferentClass() {
        InvoiceLineEntity.InvoiceLineKey key = new InvoiceLineEntity.InvoiceLineKey(
            UUID.randomUUID(), UUID.randomUUID(), 1);
        
        assertNotEquals("not a key", key);
    }

    @Test
    void testInvoiceLineKeyEquals_NullFields() {
        InvoiceLineEntity.InvoiceLineKey key1 = new InvoiceLineEntity.InvoiceLineKey();
        InvoiceLineEntity.InvoiceLineKey key2 = new InvoiceLineEntity.InvoiceLineKey();
        
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void testInvoiceLineKeyEquals_PartialNullFields() {
        UUID invoiceId = UUID.randomUUID();
        InvoiceLineEntity.InvoiceLineKey key1 = new InvoiceLineEntity.InvoiceLineKey(null, invoiceId, 1);
        InvoiceLineEntity.InvoiceLineKey key2 = new InvoiceLineEntity.InvoiceLineKey(null, invoiceId, 1);
        
        assertEquals(key1, key2);
    }
}
