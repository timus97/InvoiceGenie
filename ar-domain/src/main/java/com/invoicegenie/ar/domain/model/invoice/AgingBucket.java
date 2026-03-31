package com.invoicegenie.ar.domain.model.invoice;

/**
 * Aging buckets for invoice aging analysis.
 * 
 * <p>Buckets:
 * <ul>
 *   <li>BUCKET_0_30: 0-30 days (current)</li>
 *   <li>BUCKET_31_60: 31-60 days (1-2 months)</li>
 *   <li>BUCKET_61_90: 61-90 days (2-3 months)</li>
 *   <li>BUCKET_90_PLUS: 90+ days (overdue)</li>
 * </ul>
 */
public enum AgingBucket {
    BUCKET_0_30("0-30 Days", 0, 30),
    BUCKET_31_60("31-60 Days", 31, 60),
    BUCKET_61_90("61-90 Days", 61, 90),
    BUCKET_90_PLUS("90+ Days", 91, Integer.MAX_VALUE);

    private final String label;
    private final int minDays;
    private final int maxDays;

    AgingBucket(String label, int minDays, int maxDays) {
        this.label = label;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    public String getLabel() {
        return label;
    }

    public int getMinDays() {
        return minDays;
    }

    public int getMaxDays() {
        return maxDays;
    }

    /**
     * Determine which bucket a given number of days overdue falls into.
     */
    public static AgingBucket fromDaysOverdue(int daysOverdue) {
        if (daysOverdue <= 30) {
            return BUCKET_0_30;
        } else if (daysOverdue <= 60) {
            return BUCKET_31_60;
        } else if (daysOverdue <= 90) {
            return BUCKET_61_90;
        } else {
            return BUCKET_90_PLUS;
        }
    }

    /**
     * Check if this is the early payment discount eligible bucket (0-30 days).
     */
    public boolean isEarlyPaymentEligible() {
        return this == BUCKET_0_30;
    }
}
