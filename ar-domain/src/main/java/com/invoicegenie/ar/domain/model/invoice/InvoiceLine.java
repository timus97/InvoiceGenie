package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Child entity within Invoice aggregate. Line item with pricing, discount, tax.
 *
 * <p>Business rules:
 * <ul>
 *   <li>lineTotal = (quantity * unitPrice) - discountAmount + taxAmount</li>
 *   <li>All amounts in same currency as parent Invoice</li>
 *   <li>Quantity can be fractional (e.g., 0.5 hours)</li>
 * </ul>
 */
public final class InvoiceLine {

    private final int sequence;
    private final String description;
    private final BigDecimal quantity;
    private final Money unitPrice;
    private final Money discountAmount;
    private final BigDecimal taxRate; // e.g., 0.0825 for 8.25%
    private final Money taxAmount;
    private final Money lineTotal;

    /** Simple constructor: description + single amount (no qty/tax). */
    public InvoiceLine(int sequence, String description, Money amount) {
        this(sequence, description, BigDecimal.ONE, amount, Money.of(BigDecimal.ZERO, amount.getCurrencyCode()),
                null, Money.of(BigDecimal.ZERO, amount.getCurrencyCode()),
                amount);
    }

    /**
     * Builds a line with qty/unitPrice/discount/taxRate, computing tax and lineTotal (STORY-011).
     * If quantity/unitPrice are null, falls back to a single {@code amount} (or zero).
     */
    public static InvoiceLine of(int sequence, String description, String currencyCode,
                                 BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice,
                                 BigDecimal discountAmount, BigDecimal taxRate) {
        String ccy = currencyCode != null ? currencyCode : "USD";
        String desc = description != null ? description : "";
        if (quantity != null && unitPrice != null) {
            BigDecimal qty = quantity;
            Money unit = Money.of(unitPrice, ccy);
            Money discount = Money.of(
                    discountAmount != null ? discountAmount : BigDecimal.ZERO, ccy);
            BigDecimal base = qty.multiply(unit.getAmount())
                    .subtract(discount.getAmount())
                    .setScale(2, RoundingMode.HALF_UP);
            if (base.signum() < 0) {
                throw new IllegalArgumentException("line base amount cannot be negative");
            }
            Money tax = Money.of(BigDecimal.ZERO, ccy);
            if (taxRate != null && taxRate.signum() > 0) {
                tax = Money.of(base.multiply(taxRate).setScale(2, RoundingMode.HALF_UP), ccy);
            }
            Money total = Money.of(base.add(tax.getAmount()).setScale(2, RoundingMode.HALF_UP), ccy);
            return new InvoiceLine(sequence, desc, qty, unit, discount, taxRate, tax, total);
        }
        BigDecimal amt = amount != null ? amount : BigDecimal.ZERO;
        return new InvoiceLine(sequence, desc, Money.of(amt, ccy));
    }

    /** Full constructor. */
    public InvoiceLine(int sequence, String description, BigDecimal quantity, Money unitPrice,
                       Money discountAmount, BigDecimal taxRate, Money taxAmount, Money lineTotal) {
        this.sequence = sequence;
        this.description = Objects.requireNonNull(description);
        this.quantity = Objects.requireNonNull(quantity);
        if (quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be > 0");
        this.unitPrice = Objects.requireNonNull(unitPrice);
        this.discountAmount = discountAmount != null ? discountAmount : Money.of(BigDecimal.ZERO, unitPrice.getCurrencyCode());
        this.taxRate = taxRate;
        this.taxAmount = taxAmount != null ? taxAmount : Money.of(BigDecimal.ZERO, unitPrice.getCurrencyCode());
        this.lineTotal = Objects.requireNonNull(lineTotal);
    }

    public int getSequence() { return sequence; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
    public Money getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public Money getTaxAmount() { return taxAmount; }
    public Money getLineTotal() { return lineTotal; }

    /** Convenience: returns base amount before tax (qty * unitPrice - discount). */
    public Money getAmount() {
        return Money.of(quantity.multiply(unitPrice.getAmount()).subtract(discountAmount.getAmount())
                        .setScale(2, RoundingMode.HALF_UP), unitPrice.getCurrencyCode());
    }
}
