package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.CreditNoteEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreditNoteMapperTest {

    private final CreditNoteMapper mapper = new CreditNoteMapper();

    @Test
    void testToEntity_EarlyPaymentDiscount() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID referenceInvoiceId = UUID.randomUUID();
        UUID appliedToPaymentId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNote creditNote = new CreditNote(
            id,
            "CN-EPD-001",
            CustomerId.of(customerId),
            Money.of("20.00", "USD"),
            CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT,
            referenceInvoiceId,
            "2% early payment discount",
            CreditNote.CreditNoteStatus.APPLIED,
            today,
            today.plusDays(1),
            today.plusYears(1),
            appliedToPaymentId,
            "Applied to payment",
            now,
            now
        );

        CreditNoteEntity entity = mapper.toEntity(TenantId.of(tenantId), creditNote);

        assertEquals(id, entity.getId());
        assertEquals(tenantId, entity.getTenantId());
        assertEquals("CN-EPD-001", entity.getCreditNoteNumber());
        assertEquals(customerId, entity.getCustomerId());
        assertEquals(new BigDecimal("20.00"), entity.getAmount());
        assertEquals("USD", entity.getCurrency());
        assertEquals(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT, entity.getType());
        assertEquals(referenceInvoiceId, entity.getReferenceInvoiceId());
        assertEquals("2% early payment discount", entity.getDescription());
        assertEquals(CreditNoteEntity.CreditNoteStatus.APPLIED, entity.getStatus());
        assertEquals(today, entity.getIssueDate());
        assertEquals(today.plusDays(1), entity.getAppliedDate());
        assertEquals(today.plusYears(1), entity.getExpiryDate());
        assertEquals(appliedToPaymentId, entity.getAppliedToPaymentId());
        assertEquals("Applied to payment", entity.getNotes());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    void testToEntity_Adjustment() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNote creditNote = new CreditNote(
            id,
            "CN-ADJ-001",
            CustomerId.of(customerId),
            Money.of("50.00", "USD"),
            CreditNote.CreditNoteType.ADJUSTMENT,
            null,
            "Price adjustment",
            CreditNote.CreditNoteStatus.ISSUED,
            today,
            null,
            null,
            null,
            null,
            now,
            now
        );

        CreditNoteEntity entity = mapper.toEntity(TenantId.of(tenantId), creditNote);

        assertEquals(CreditNoteEntity.CreditNoteType.ADJUSTMENT, entity.getType());
        assertEquals(CreditNoteEntity.CreditNoteStatus.ISSUED, entity.getStatus());
        assertNull(entity.getReferenceInvoiceId());
        assertNull(entity.getAppliedDate());
        assertNull(entity.getExpiryDate());
        assertNull(entity.getAppliedToPaymentId());
    }

    @Test
    void testToEntity_Refund() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID referenceInvoiceId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNote creditNote = new CreditNote(
            id,
            "CN-REF-001",
            CustomerId.of(customerId),
            Money.of("100.00", "USD"),
            CreditNote.CreditNoteType.REFUND,
            referenceInvoiceId,
            "Refund for returned goods",
            CreditNote.CreditNoteStatus.ISSUED,
            today,
            null,
            today.plusMonths(6),
            null,
            "Customer returned items",
            now,
            now
        );

        CreditNoteEntity entity = mapper.toEntity(TenantId.of(tenantId), creditNote);

        assertEquals(CreditNoteEntity.CreditNoteType.REFUND, entity.getType());
        assertEquals(referenceInvoiceId, entity.getReferenceInvoiceId());
        assertEquals("Refund for returned goods", entity.getDescription());
        assertEquals(today.plusMonths(6), entity.getExpiryDate());
        assertEquals("Customer returned items", entity.getNotes());
    }

    @Test
    void testToDomain() {
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
        entity.setCreditNoteNumber("CN-DOM-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("75.00"));
        entity.setCurrency("USD");
        entity.setType(CreditNoteEntity.CreditNoteType.ADJUSTMENT);
        entity.setReferenceInvoiceId(referenceInvoiceId);
        entity.setDescription("Test description");
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.ISSUED);
        entity.setIssueDate(today);
        entity.setAppliedDate(null);
        entity.setExpiryDate(today.plusYears(1));
        entity.setAppliedToPaymentId(appliedToPaymentId);
        entity.setNotes("Test notes");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        CreditNote creditNote = mapper.toDomain(entity);

        assertEquals(id, creditNote.getId());
        assertEquals("CN-DOM-001", creditNote.getCreditNoteNumber());
        assertEquals(customerId, creditNote.getCustomerId().getValue());
        assertEquals(new BigDecimal("75.00"), creditNote.getAmount().getAmount());
        assertEquals("USD", creditNote.getAmount().getCurrencyCode());
        assertEquals(CreditNote.CreditNoteType.ADJUSTMENT, creditNote.getType());
        assertEquals(referenceInvoiceId, creditNote.getReferenceInvoiceId());
        assertEquals("Test description", creditNote.getDescription());
        assertEquals(CreditNote.CreditNoteStatus.ISSUED, creditNote.getStatus());
        assertEquals(today, creditNote.getIssueDate());
        assertNull(creditNote.getAppliedDate());
        assertEquals(today.plusYears(1), creditNote.getExpiryDate());
        assertEquals(appliedToPaymentId, creditNote.getAppliedToPaymentId());
        assertEquals("Test notes", creditNote.getNotes());
        assertEquals(now, creditNote.getCreatedAt());
        assertEquals(now, creditNote.getUpdatedAt());
    }

    @Test
    void testToDomain_AllStatuses() {
        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setCreditNoteNumber("CN-STATUS");
        entity.setCustomerId(UUID.randomUUID());
        entity.setAmount(new BigDecimal("10.00"));
        entity.setCurrency("USD");
        entity.setType(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT);
        entity.setIssueDate(LocalDate.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        entity.setStatus(CreditNoteEntity.CreditNoteStatus.ISSUED);
        assertEquals(CreditNote.CreditNoteStatus.ISSUED, mapper.toDomain(entity).getStatus());

        entity.setStatus(CreditNoteEntity.CreditNoteStatus.APPLIED);
        assertEquals(CreditNote.CreditNoteStatus.APPLIED, mapper.toDomain(entity).getStatus());

        entity.setStatus(CreditNoteEntity.CreditNoteStatus.EXPIRED);
        assertEquals(CreditNote.CreditNoteStatus.EXPIRED, mapper.toDomain(entity).getStatus());

        entity.setStatus(CreditNoteEntity.CreditNoteStatus.VOIDED);
        assertEquals(CreditNote.CreditNoteStatus.VOIDED, mapper.toDomain(entity).getStatus());
    }

    @Test
    void testToDomain_AllTypes() {
        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setCreditNoteNumber("CN-TYPE");
        entity.setCustomerId(UUID.randomUUID());
        entity.setAmount(new BigDecimal("10.00"));
        entity.setCurrency("USD");
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.ISSUED);
        entity.setIssueDate(LocalDate.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        entity.setType(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT);
        assertEquals(CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, mapper.toDomain(entity).getType());

        entity.setType(CreditNoteEntity.CreditNoteType.ADJUSTMENT);
        assertEquals(CreditNote.CreditNoteType.ADJUSTMENT, mapper.toDomain(entity).getType());

        entity.setType(CreditNoteEntity.CreditNoteType.REFUND);
        assertEquals(CreditNote.CreditNoteType.REFUND, mapper.toDomain(entity).getType());
    }

    @Test
    void testRoundTrip() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID referenceInvoiceId = UUID.randomUUID();
        UUID appliedToPaymentId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNote original = new CreditNote(
            id,
            "CN-RT-001",
            CustomerId.of(customerId),
            Money.of("150.00", "USD"),
            CreditNote.CreditNoteType.REFUND,
            referenceInvoiceId,
            "Round trip test",
            CreditNote.CreditNoteStatus.APPLIED,
            today,
            today.plusDays(5),
            today.plusMonths(6),
            appliedToPaymentId,
            "Applied",
            now,
            now
        );

        CreditNoteEntity entity = mapper.toEntity(TenantId.of(tenantId), original);
        CreditNote result = mapper.toDomain(entity);

        assertEquals(original.getId(), result.getId());
        assertEquals(original.getCreditNoteNumber(), result.getCreditNoteNumber());
        assertEquals(original.getCustomerId().getValue(), result.getCustomerId().getValue());
        assertEquals(original.getAmount().getAmount(), result.getAmount().getAmount());
        assertEquals(original.getAmount().getCurrencyCode(), result.getAmount().getCurrencyCode());
        assertEquals(original.getType(), result.getType());
        assertEquals(original.getReferenceInvoiceId(), result.getReferenceInvoiceId());
        assertEquals(original.getDescription(), result.getDescription());
        assertEquals(original.getStatus(), result.getStatus());
        assertEquals(original.getIssueDate(), result.getIssueDate());
        assertEquals(original.getAppliedDate(), result.getAppliedDate());
        assertEquals(original.getExpiryDate(), result.getExpiryDate());
        assertEquals(original.getAppliedToPaymentId(), result.getAppliedToPaymentId());
        assertEquals(original.getNotes(), result.getNotes());
    }
}
