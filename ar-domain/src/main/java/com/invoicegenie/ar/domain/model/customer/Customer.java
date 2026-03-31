package com.invoicegenie.ar.domain.model.customer;

import com.invoicegenie.shared.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Customer aggregate root. Holds master data and simple business rules.
 *
 * <p>Business rules in this aggregate:
 * <ul>
 *   <li>Customer code is immutable after creation</li>
 *   <li>Blocked customers cannot be invoiced (checked at application layer)</li>
 *   <li>Credit limit is optional; if set, application layer may validate against outstanding</li>
 *   <li>Display name falls back to legal name if not set</li>
 * </ul>
 */
public final class Customer {

    private final CustomerId id;
    private final String customerCode; // immutable business key
    private String legalName;
    private String displayName;
    private String email;
    private String phone;
    private String billingAddress; // JSON string or address object serialized
    private String currency; // ISO 4217
    private BigDecimal creditLimit; // nullable
    private String paymentTerms;
    private String taxId;
    private CustomerStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Customer(CustomerId id, String customerCode, String legalName, String currency) {
        this(id, customerCode, legalName, null, null, null, null, currency, null, null, null,
                CustomerStatus.ACTIVE, Instant.now(), Instant.now(), 1L);
    }

    /** For reconstitution from persistence. */
    public Customer(CustomerId id, String customerCode, String legalName, String displayName,
                    String email, String phone, String billingAddress, String currency,
                    BigDecimal creditLimit, String paymentTerms, String taxId,
                    CustomerStatus status, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.customerCode = Objects.requireNonNull(customerCode);
        this.legalName = Objects.requireNonNull(legalName);
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.billingAddress = billingAddress;
        this.currency = Objects.requireNonNull(currency);
        if (currency.length() != 3) throw new IllegalArgumentException("currency must be ISO 4217");
        this.creditLimit = creditLimit;
        this.paymentTerms = paymentTerms;
        this.taxId = taxId;
        this.status = status == null ? CustomerStatus.ACTIVE : status;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    // ==================== Accessors ====================

    public CustomerId getId() { return id; }
    public String getCustomerCode() { return customerCode; }
    public String getLegalName() { return legalName; }
    public String getDisplayName() { return displayName != null ? displayName : legalName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getBillingAddress() { return billingAddress; }
    public String getCurrency() { return currency; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public String getPaymentTerms() { return paymentTerms; }
    public String getTaxId() { return taxId; }
    public CustomerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    // ==================== Business Logic ====================

    /**
     * Updates display name. Null resets to legal name.
     */
    public void updateDisplayName(String name) {
        assertActive();
        this.displayName = name;
        touch();
    }

    /**
     * Updates contact info.
     */
    public void updateContact(String email, String phone, String billingAddress) {
        assertActive();
        this.email = email;
        this.phone = phone;
        this.billingAddress = billingAddress;
        touch();
    }

    /**
     * Changes default currency. Does not affect existing invoices.
     */
    public void changeCurrency(String currency) {
        assertActive();
        if (currency == null || currency.length() != 3)
            throw new IllegalArgumentException("currency must be ISO 4217");
        this.currency = currency;
        touch();
    }

    /**
     * Sets or clears credit limit. Null means unlimited.
     */
    public void setCreditLimit(BigDecimal limit) {
        assertActive();
        if (limit != null && limit.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("creditLimit must be >= 0");
        this.creditLimit = limit;
        touch();
    }

    /**
     * Updates payment terms (e.g., NET30).
     */
    public void setPaymentTerms(String terms) {
        assertActive();
        this.paymentTerms = terms;
        touch();
    }

    /**
     * Blocks the customer. Blocked customers should not be invoiced.
     */
    public void block() {
        if (this.status == CustomerStatus.DELETED)
            throw new IllegalStateException("Cannot block deleted customer");
        this.status = CustomerStatus.BLOCKED;
        touch();
    }

    /**
     * Unblocks the customer.
     */
    public void unblock() {
        if (status == CustomerStatus.DELETED) {
            throw new IllegalStateException("Cannot unblock deleted customer");
        }
        this.status = CustomerStatus.ACTIVE;
        touch();
    }

    /**
     * Marks as deleted (soft delete). Irreversible.
     */
    public void delete() {
        this.status = CustomerStatus.DELETED;
        touch();
    }

    /**
     * Checks if customer is active and can be invoiced.
     */
    public boolean canBeInvoiced() {
        return status == CustomerStatus.ACTIVE;
    }

    /**
     * Checks if customer can be invoiced for a specific amount.
     * Validates both status and credit limit (if set).
     * 
     * @param outstandingBalance Current outstanding balance for this customer
     * @param invoiceAmount Amount of the new invoice
     * @return true if customer can receive this invoice
     */
    public boolean canBeInvoicedForAmount(java.math.BigDecimal outstandingBalance, java.math.BigDecimal invoiceAmount) {
        if (!canBeInvoiced()) {
            return false;
        }
        if (creditLimit == null) {
            return true; // No credit limit means unlimited
        }
        java.math.BigDecimal newBalance = outstandingBalance.add(invoiceAmount);
        return newBalance.compareTo(creditLimit) <= 0;
    }

    /**
     * Checks if customer has exceeded their credit limit.
     * 
     * @param outstandingBalance Current outstanding balance
     * @return true if over credit limit
     */
    public boolean isOverCreditLimit(java.math.BigDecimal outstandingBalance) {
        if (creditLimit == null) {
            return false;
        }
        return outstandingBalance.compareTo(creditLimit) > 0;
    }

    /**
     * Validates customer data for creation/update.
     * 
     * @throws IllegalArgumentException if invalid
     */
    public void validate() {
        if (customerCode == null || customerCode.isBlank()) {
            throw new IllegalArgumentException("Customer code is required");
        }
        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException("Legal name is required");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be ISO 4217 code (3 chars)");
        }
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit limit must be >= 0");
        }
    }

    /**
     * Returns available credit (credit limit - outstanding balance).
     * Returns null if no credit limit is set.
     */
    public java.math.BigDecimal getAvailableCredit(java.math.BigDecimal outstandingBalance) {
        if (creditLimit == null) {
            return null; // Unlimited
        }
        java.math.BigDecimal available = creditLimit.subtract(outstandingBalance);
        return available.max(BigDecimal.ZERO);
    }

    // ==================== Helpers ====================

    private void assertActive() {
        if (status != CustomerStatus.ACTIVE)
            throw new IllegalStateException("Customer is not active: " + status);
    }

    private void touch() {
        this.updatedAt = Instant.now();
        this.version++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return id.equals(customer.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
