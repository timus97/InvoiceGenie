package com.invoicegenie.ar.domain.model.payment;

/**
 * Cheque processing states.
 * 
 * <p>State transitions:
 * <pre>
 * RECEIVED → DEPOSITED → CLEARED
 *                    ↘
 *                  BOUNCED
 * </pre>
 * 
 * <p>Rules:
 * <ul>
 *   <li>RECEIVED: Cheque received from customer, not yet deposited</li>
 *   <li>DEPOSITED: Cheque deposited to bank, awaiting clearance</li>
 *   <li>CLEARED: Cheque cleared by bank, payment confirmed</li>
 *   <li>BOUNCED: Cheque returned by bank (insufficient funds, etc.)</li>
 * </ul>
 */
public enum ChequeStatus {
    RECEIVED("Cheque received from customer"),
    DEPOSITED("Cheque deposited to bank"),
    CLEARED("Cheque cleared by bank - payment confirmed"),
    BOUNCED("Cheque bounced/returned by bank");

    private final String description;

    ChequeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this is a terminal state.
     */
    public boolean isTerminal() {
        return this == CLEARED || this == BOUNCED;
    }

    /**
     * Check if cheque can be deposited from current state.
     */
    public boolean canDeposit() {
        return this == RECEIVED;
    }

    /**
     * Check if cheque can be cleared from current state.
     */
    public boolean canClear() {
        return this == DEPOSITED;
    }

    /**
     * Check if cheque can be bounced from current state.
     */
    public boolean canBounce() {
        return this == DEPOSITED;
    }
}
