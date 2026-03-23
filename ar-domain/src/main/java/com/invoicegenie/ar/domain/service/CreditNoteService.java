package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain service for credit note operations.
 * 
 * <p>Credit notes are used for:
 * <ul>
 *   <li>Early payment discounts (2% when paid within 30 days)</li>
 *   <li>Adjustments</li>
 *   <li>Refunds</li>
 * </ul>
 * 
 * <p>Credit notes can be applied to short payments or future invoices.
 */
public final class CreditNoteService {

    /**
     * Result of credit note generation.
     */
    public record CreditNoteResult(CreditNote creditNote, boolean success, String message) {}

    /**
     * Result of credit note application.
     */
    public record ApplyResult(CreditNote creditNote, Money appliedAmount, Money remainingBalance,
                               boolean success, String message) {}

    /**
     * Generate a credit note for early payment discount.
     */
    public CreditNoteResult generateEarlyPaymentDiscount(TenantId tenantId, UUID customerId,
                                                          Money discountAmount, UUID referenceInvoiceId) {
        try {
            UUID creditNoteId = UUID.randomUUID();
            String creditNoteNumber = "CN-EPD-" + System.currentTimeMillis();
            
            CreditNote creditNote = new CreditNote(
                    creditNoteId,
                    creditNoteNumber,
                    new com.invoicegenie.ar.domain.model.customer.CustomerId(customerId),
                    discountAmount,
                    CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT,
                    referenceInvoiceId,
                    "2% early payment discount"
            );
            
            return new CreditNoteResult(creditNote, true, "Credit note generated successfully");
        } catch (Exception e) {
            return new CreditNoteResult(null, false, e.getMessage());
        }
    }

    /**
     * Apply available credit notes to cover a short payment.
     * 
     * @param tenantId Tenant ID
     * @param customerId Customer ID
     * @param shortPaymentAmount Amount short (invoice - payment)
     * @return List of credit notes to apply
     */
    public List<CreditNote> findCreditNotesToApply(TenantId tenantId, UUID customerId, Money shortPaymentAmount) {
        // In full implementation, would query repository for available credit notes
        // and return those that can cover the short payment
        return new ArrayList<>();
    }

    /**
     * Calculate how much credit note is needed to cover a short payment.
     */
    public Money calculateCreditNeeded(Money invoiceAmount, Money paymentAmount) {
        Money shortAmount = Money.of(
                invoiceAmount.getAmount().subtract(paymentAmount.getAmount()).toString(),
                invoiceAmount.getCurrencyCode()
        );
        return shortAmount;
    }

    /**
     * Validate that credit note application is valid.
     */
    public boolean validateApplication(CreditNote creditNote, Money amountToApply, Money shortPayment) {
        if (creditNote == null || !creditNote.canApply()) {
            return false;
        }
        if (amountToApply == null || amountToApply.getAmount().signum() <= 0) {
            return false;
        }
        // Cannot apply more than the credit note amount
        if (amountToApply.getAmount().compareTo(creditNote.getAmount().getAmount()) > 0) {
            return false;
        }
        // Cannot apply more than the short payment
        if (amountToApply.getAmount().compareTo(shortPayment.getAmount()) > 0) {
            return false;
        }
        return true;
    }
}
