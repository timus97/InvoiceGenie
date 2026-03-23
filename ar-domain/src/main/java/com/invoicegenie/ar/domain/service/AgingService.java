package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.invoice.AgingBucket;
import com.invoicegenie.ar.domain.model.invoice.AgingReport;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Domain service for generating aging reports.
 * 
 * <p>Aging buckets:
 * <ul>
 *   <li>0-30 days: Current, eligible for 2% early payment discount</li>
 *   <li>31-60 days: 1-2 months overdue</li>
 *   <li>61-90 days: 2-3 months overdue</li>
 *   <li>90+ days: Severely overdue, consider collection action</li>
 * </ul>
 * 
 * <p>Early Payment Discount:
 * <ul>
 *   <li>2% discount for invoices paid within 30 days</li>
 *   <li>Generates credit note for the discount amount</li>
 *   <li>Credit note can be applied to short payments</li>
 * </ul>
 */
public final class AgingService {

    /**
     * Default early payment discount percentage.
     */
    public static final BigDecimal EARLY_PAYMENT_DISCOUNT_RATE = new BigDecimal("0.02");

    /**
     * Result of aging report generation.
     */
    public record AgingReportResult(AgingReport report, AgingReport.AgingSummary summary, 
                                    boolean success, String message) {}

    /**
     * Result of early payment discount calculation.
     */
    public record EarlyPaymentDiscountResult(
            UUID invoiceId,
            Money originalAmount,
            Money discountAmount,
            Money discountedAmount,
            boolean eligible,
            String reason
    ) {
        public static EarlyPaymentDiscountResult eligible(UUID invoiceId, Money originalAmount, BigDecimal rate) {
            BigDecimal discount = originalAmount.getAmount()
                    .multiply(rate)
                    .setScale(2, RoundingMode.HALF_UP);
            Money discountMoney = Money.of(discount.toString(), originalAmount.getCurrencyCode());
            Money discounted = Money.of(
                    originalAmount.getAmount().subtract(discount).setScale(2, RoundingMode.HALF_UP).toString(),
                    originalAmount.getCurrencyCode()
            );
            return new EarlyPaymentDiscountResult(invoiceId, originalAmount, discountMoney, discounted, true, null);
        }

        public static EarlyPaymentDiscountResult notEligible(UUID invoiceId, Money originalAmount, String reason) {
            return new EarlyPaymentDiscountResult(invoiceId, originalAmount, 
                    Money.of("0.00", originalAmount.getCurrencyCode()), originalAmount, false, reason);
        }
    }

    /**
     * Generate aging report for all open invoices as of a given date.
     */
    public AgingReportResult generateAgingReport(TenantId tenantId, LocalDate asOfDate,
                                                  List<InvoiceWithBalance> openInvoices) {
        try {
            AgingReport report = new AgingReport(asOfDate, "USD"); // Default currency
            
            for (InvoiceWithBalance invoice : openInvoices) {
                int daysOverdue = calculateDaysOverdue(invoice.dueDate(), asOfDate);
                
                // Only include open invoices (not paid, not written off)
                if (invoice.status() == InvoiceStatus.PAID || invoice.status() == InvoiceStatus.WRITTEN_OFF) {
                    continue;
                }
                
                report.addInvoice(
                        invoice.id(),
                        invoice.invoiceNumber(),
                        invoice.customerId(),
                        invoice.customerRef(),
                        invoice.amountDue(),
                        invoice.dueDate(),
                        daysOverdue
                );
            }
            
            AgingReport.AgingSummary summary = AgingReport.AgingSummary.from(report);
            return new AgingReportResult(report, summary, true, "Aging report generated successfully");
        } catch (Exception e) {
            return new AgingReportResult(null, null, false, e.getMessage());
        }
    }

    /**
     * Calculate early payment discount for an invoice.
     * 
     * <p>Eligibility: Invoice must be in 0-30 day bucket (not yet due or within 30 days of due date).
     */
    public EarlyPaymentDiscountResult calculateEarlyPaymentDiscount(UUID invoiceId, Money amountDue,
                                                                     LocalDate dueDate, LocalDate today) {
        if (amountDue == null || amountDue.getAmount().signum() <= 0) {
            return EarlyPaymentDiscountResult.notEligible(invoiceId, amountDue, "Invalid amount");
        }
        
        int daysUntilDue = (int) ChronoUnit.DAYS.between(today, dueDate);
        
        // Eligible if due date is in the future or within 30 days past
        if (daysUntilDue >= -30) {
            return EarlyPaymentDiscountResult.eligible(invoiceId, amountDue, EARLY_PAYMENT_DISCOUNT_RATE);
        } else {
            return EarlyPaymentDiscountResult.notEligible(invoiceId, amountDue, 
                    "Invoice is more than 30 days overdue, not eligible for early payment discount");
        }
    }

    /**
     * Calculate discount amount for a given payment amount.
     * 
     * <p>If payment is within 2% of the invoice amount (after discount), 
     * the credit note can cover the difference.
     */
    public Money calculateDiscountAmount(Money originalAmount) {
        BigDecimal discount = originalAmount.getAmount()
                .multiply(EARLY_PAYMENT_DISCOUNT_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        return Money.of(discount.toString(), originalAmount.getCurrencyCode());
    }

    /**
     * Check if a short payment can be covered by a credit note.
     * 
     * @param invoiceAmount Original invoice amount
     * @param paymentAmount Actual payment amount
     * @return true if the difference can be covered by the 2% discount
     */
    public boolean canCoverShortPayment(Money invoiceAmount, Money paymentAmount) {
        BigDecimal discountAmount = invoiceAmount.getAmount()
                .multiply(EARLY_PAYMENT_DISCOUNT_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal expectedPayment = invoiceAmount.getAmount().subtract(discountAmount);
        BigDecimal actualPayment = paymentAmount.getAmount();
        
        // Payment should be at least the discounted amount
        return actualPayment.compareTo(expectedPayment) >= 0 && 
               actualPayment.compareTo(invoiceAmount.getAmount()) < 0;
    }

    private int calculateDaysOverdue(LocalDate dueDate, LocalDate asOfDate) {
        if (dueDate == null) return 0;
        long days = ChronoUnit.DAYS.between(dueDate, asOfDate);
        return (int) Math.max(0, days);
    }

    /**
     * DTO for invoice with balance information.
     */
    public record InvoiceWithBalance(
            UUID id,
            String invoiceNumber,
            UUID customerId,
            String customerRef,
            Money amountDue,
            LocalDate dueDate,
            InvoiceStatus status
    ) {}
}
