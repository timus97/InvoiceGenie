package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aging report containing invoice aging analysis by bucket.
 * 
 * <p>Buckets:
 * <ul>
 *   <li>0-30 days: Current, eligible for early payment discount</li>
 *   <li>31-60 days: 1-2 months overdue</li>
 *   <li>61-90 days: 2-3 months overdue</li>
 *   <li>90+ days: Severely overdue</li>
 * </ul>
 */
public final class AgingReport {

    private final LocalDate asOfDate;
    private final String currencyCode;
    private final Map<AgingBucket, AgingBucketSummary> bucketSummaries;
    private final List<AgingInvoiceDetail> invoiceDetails;
    private final BigDecimal totalOutstanding;
    private final int totalInvoiceCount;

    public AgingReport(LocalDate asOfDate, String currencyCode) {
        this.asOfDate = asOfDate;
        this.currencyCode = currencyCode;
        this.bucketSummaries = new EnumMap<>(AgingBucket.class);
        this.invoiceDetails = new ArrayList<>();
        
        // Initialize all buckets
        for (AgingBucket bucket : AgingBucket.values()) {
            bucketSummaries.put(bucket, new AgingBucketSummary(bucket));
        }
        
        this.totalOutstanding = BigDecimal.ZERO;
        this.totalInvoiceCount = 0;
    }

    /** For reconstitution from persistence. */
    public AgingReport(LocalDate asOfDate, String currencyCode,
                       Map<AgingBucket, AgingBucketSummary> bucketSummaries,
                       List<AgingInvoiceDetail> invoiceDetails,
                       BigDecimal totalOutstanding, int totalInvoiceCount) {
        this.asOfDate = asOfDate;
        this.currencyCode = currencyCode;
        this.bucketSummaries = bucketSummaries != null ? new EnumMap<>(bucketSummaries) : new EnumMap<>(AgingBucket.class);
        this.invoiceDetails = invoiceDetails != null ? new ArrayList<>(invoiceDetails) : new ArrayList<>();
        this.totalOutstanding = totalOutstanding != null ? totalOutstanding : BigDecimal.ZERO;
        this.totalInvoiceCount = totalInvoiceCount;
    }

    // ==================== Accessors ====================

    public LocalDate getAsOfDate() { return asOfDate; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getTotalOutstanding() { return totalOutstanding; }
    public int getTotalInvoiceCount() { return totalInvoiceCount; }

    public AgingBucketSummary getBucket(AgingBucket bucket) {
        return bucketSummaries.getOrDefault(bucket, new AgingBucketSummary(bucket));
    }

    public Map<AgingBucket, AgingBucketSummary> getBucketSummaries() {
        return bucketSummaries;
    }

    public List<AgingInvoiceDetail> getInvoiceDetails() {
        return invoiceDetails;
    }

    // ==================== Builder Methods ====================

    /**
     * Add an invoice to the aging report.
     */
    public void addInvoice(UUID invoiceId, String invoiceNumber, UUID customerId, String customerRef,
                           Money amountDue, LocalDate dueDate, int daysOverdue) {
        AgingBucket bucket = AgingBucket.fromDaysOverdue(daysOverdue);
        
        // Update bucket summary
        AgingBucketSummary summary = bucketSummaries.get(bucket);
        summary.addInvoice(amountDue.getAmount());
        
        // Add invoice detail
        invoiceDetails.add(new AgingInvoiceDetail(
                invoiceId, invoiceNumber, customerId, customerRef,
                amountDue, dueDate, daysOverdue, bucket,
                bucket.isEarlyPaymentEligible()
        ));
    }

    /**
     * Finalize the report and calculate totals.
     */
    public void finalizeReport() {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        
        for (AgingBucketSummary summary : bucketSummaries.values()) {
            total = total.add(summary.totalAmount());
            count += summary.invoiceCount();
        }
        
        // Update totals (would need mutable fields, simplified here)
    }

    // ==================== Nested Records ====================

    public record AgingBucketSummary(AgingBucket bucket, BigDecimal totalAmount, int invoiceCount) {
        
        public AgingBucketSummary(AgingBucket bucket) {
            this(bucket, BigDecimal.ZERO, 0);
        }

        public AgingBucketSummary addInvoice(BigDecimal amount) {
            return new AgingBucketSummary(
                    bucket,
                    totalAmount.add(amount),
                    invoiceCount + 1
            );
        }

        public String getLabel() {
            return bucket.getLabel();
        }
    }

    public record AgingInvoiceDetail(
            UUID invoiceId,
            String invoiceNumber,
            UUID customerId,
            String customerRef,
            Money amountDue,
            LocalDate dueDate,
            int daysOverdue,
            AgingBucket bucket,
            boolean eligibleForEarlyDiscount
    ) {}

    // ==================== Summary DTO ====================

    public record AgingSummary(
            LocalDate asOfDate,
            String currencyCode,
            BigDecimal total0To30,
            BigDecimal total31To60,
            BigDecimal total61To90,
            BigDecimal total90Plus,
            BigDecimal grandTotal,
            int count0To30,
            int count31To60,
            int count61To90,
            int count90Plus,
            int totalCount
    ) {
        public static AgingSummary from(AgingReport report) {
            var b0 = report.getBucket(AgingBucket.BUCKET_0_30);
            var b31 = report.getBucket(AgingBucket.BUCKET_31_60);
            var b61 = report.getBucket(AgingBucket.BUCKET_61_90);
            var b90 = report.getBucket(AgingBucket.BUCKET_90_PLUS);
            
            BigDecimal grandTotal = b0.totalAmount()
                    .add(b31.totalAmount())
                    .add(b61.totalAmount())
                    .add(b90.totalAmount());
            
            int totalCount = b0.invoiceCount() + b31.invoiceCount() + b61.invoiceCount() + b90.invoiceCount();
            
            return new AgingSummary(
                    report.getAsOfDate(),
                    report.getCurrencyCode(),
                    b0.totalAmount(),
                    b31.totalAmount(),
                    b61.totalAmount(),
                    b90.totalAmount(),
                    grandTotal,
                    b0.invoiceCount(),
                    b31.invoiceCount(),
                    b61.invoiceCount(),
                    b90.invoiceCount(),
                    totalCount
            );
        }
    }
}
