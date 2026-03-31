package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain service for double-entry ledger operations.
 * 
 * <p>Core principles of double-entry accounting:
 * <ul>
 *   <li>Every transaction has equal debits and credits</li>
 *   <li>Debits increase assets and expenses</li>
 *   <li>Credits increase liabilities, equity, and revenue</li>
 *   <li>The accounting equation: Assets = Liabilities + Equity</li>
 * </ul>
 * 
 * <p>Transaction types:
 * <ul>
 *   <li>Invoice Issued: Debit AR, Credit Revenue</li>
 *   <li>Payment Received: Debit Bank, Credit AR</li>
 *   <li>Payment Allocation: No new entries (already captured in AR)</li>
 *   <li>Write-off: Debit Bad Debt Expense, Credit AR</li>
 * </ul>
 */
public final class LedgerService {

    /**
     * Result of a ledger transaction.
     */
    public record TransactionResult(
            UUID transactionId,
            List<LedgerEntry> entries,
            boolean balanced
    ) {
        public Money getTotalDebits() {
            return entries.stream()
                    .filter(e -> e.getEntryType() == EntryType.DEBIT)
                    .map(LedgerEntry::getAmount)
                    .reduce(Money.of("0.00", entries.get(0).getAmount().getCurrencyCode()), Money::add);
        }

        public Money getTotalCredits() {
            return entries.stream()
                    .filter(e -> e.getEntryType() == EntryType.CREDIT)
                    .map(LedgerEntry::getAmount)
                    .reduce(Money.of("0.00", entries.get(0).getAmount().getCurrencyCode()), Money::add);
        }
    }

    /**
     * Creates ledger entries when an invoice is issued.
     * 
     * <p>Journal entry:
     * <pre>
     * Dr AR              $amount
     *     Cr Revenue         $amount
     * </pre>
     * 
     * @param tenantId the tenant
     * @param invoiceId the invoice ID
     * @param invoiceNumber the invoice number for reference
     * @param amount the invoice total amount
     * @param currencyCode the currency
     * @return transaction result with balanced entries
     */
    public TransactionResult recordInvoiceIssued(TenantId tenantId, UUID invoiceId,
                                                  String invoiceNumber, Money amount) {
        UUID transactionId = UUID.randomUUID();
        List<LedgerEntry> entries = new ArrayList<>();

        // Debit AR (increase asset)
        entries.add(new LedgerEntry(
                Account.AR,
                amount,
                EntryType.DEBIT,
                "Invoice " + invoiceNumber + " issued",
                transactionId,
                "INVOICE",
                invoiceId
        ));

        // Credit Revenue (increase revenue)
        entries.add(new LedgerEntry(
                Account.REVENUE,
                amount,
                EntryType.CREDIT,
                "Invoice " + invoiceNumber + " revenue",
                transactionId,
                "INVOICE",
                invoiceId
        ));

        return new TransactionResult(transactionId, entries, validateBalanced(entries));
    }

    /**
     * Creates ledger entries when a payment is received.
     * 
     * <p>Journal entry:
     * <pre>
     * Dr Bank            $amount
     *     Cr AR                $amount
     * </pre>
     * 
     * @param tenantId the tenant
     * @param paymentId the payment ID
     * @param paymentNumber the payment number for reference
     * @param amount the payment amount
     * @param currencyCode the currency
     * @return transaction result with balanced entries
     */
    public TransactionResult recordPaymentReceived(TenantId tenantId, UUID paymentId,
                                                    String paymentNumber, Money amount) {
        UUID transactionId = UUID.randomUUID();
        List<LedgerEntry> entries = new ArrayList<>();

        // Debit Bank (increase asset)
        entries.add(new LedgerEntry(
                Account.BANK,
                amount,
                EntryType.DEBIT,
                "Payment " + paymentNumber + " received",
                transactionId,
                "PAYMENT",
                paymentId
        ));

        // Credit AR (decrease asset - customer owes less)
        entries.add(new LedgerEntry(
                Account.AR,
                amount,
                EntryType.CREDIT,
                "Payment " + paymentNumber + " applied to AR",
                transactionId,
                "PAYMENT",
                paymentId
        ));

        return new TransactionResult(transactionId, entries, validateBalanced(entries));
    }

    /**
     * Creates ledger entries when an invoice is written off.
     * 
     * <p>Journal entry:
     * <pre>
     * Dr Bad Debt Expense  $amount
     *     Cr AR                  $amount
     * </pre>
     * 
     * @param tenantId the tenant
     * @param invoiceId the invoice ID
     * @param invoiceNumber the invoice number
     * @param amount the write-off amount
     * @param currencyCode the currency
     * @return transaction result with balanced entries
     */
    public TransactionResult recordWriteOff(TenantId tenantId, UUID invoiceId,
                                             String invoiceNumber, Money amount) {
        UUID transactionId = UUID.randomUUID();
        List<LedgerEntry> entries = new ArrayList<>();

        // Debit Bad Debt Expense (increase expense)
        entries.add(new LedgerEntry(
                Account.EXPENSE,
                amount,
                EntryType.DEBIT,
                "Write-off of invoice " + invoiceNumber,
                transactionId,
                "INVOICE",
                invoiceId
        ));

        // Credit AR (decrease asset)
        entries.add(new LedgerEntry(
                Account.AR,
                amount,
                EntryType.CREDIT,
                "Write-off of invoice " + invoiceNumber,
                transactionId,
                "INVOICE",
                invoiceId
        ));

        return new TransactionResult(transactionId, entries, validateBalanced(entries));
    }

    /**
     * Validates that a list of entries is balanced (debits = credits).
     */
    public boolean validateBalanced(List<LedgerEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }

        Money totalDebits = Money.of("0.00", entries.get(0).getAmount().getCurrencyCode());
        Money totalCredits = Money.of("0.00", entries.get(0).getAmount().getCurrencyCode());

        for (LedgerEntry entry : entries) {
            if (entry.getEntryType() == EntryType.DEBIT) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }

        return totalDebits.getAmount().compareTo(totalCredits.getAmount()) == 0;
    }

    /**
     * Validates that a transaction has equal debits and credits.
     * Throws exception if not balanced.
     */
    public void assertBalanced(List<LedgerEntry> entries) {
        if (!validateBalanced(entries)) {
            throw new IllegalStateException("Ledger transaction is not balanced: debits != credits");
        }
    }
}
