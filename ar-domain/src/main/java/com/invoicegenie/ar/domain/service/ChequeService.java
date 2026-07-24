package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeLifecycleEngine;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain service for cheque processing operations.
 *
 * <p>Plain Java (no CDI). Wired via producers / application layer.
 *
 * <p>Cheque lifecycle:
 * <ol>
 *   <li>RECEIVED → DEPOSITED (deposit to bank)</li>
 *   <li>DEPOSITED → CLEARED (bank confirmed)</li>
 *   <li>DEPOSITED → BOUNCED (bank returned)</li>
 * </ol>
 *
 * <p>On Bounce:
 * <ul>
 *   <li>Create reverse ledger entries: Debit AR, Credit Bank</li>
 *   <li>Reopen affected invoices (change status back)</li>
 *   <li>Remove payment allocations if any</li>
 * </ul>
 */
public class ChequeService {

    private static final ChequeLifecycleEngine LIFECYCLE = new ChequeLifecycleEngine();

    /**
     * Result of cheque deposit operation.
     */
    public record DepositResult(Cheque cheque, boolean success, String message) {}

    /**
     * Result of cheque clear operation (creates payment allocation).
     */
    public record ClearResult(Cheque cheque, UUID paymentId, List<LedgerEntry> ledgerEntries, 
                               boolean success, String message) {}

    /**
     * Result of cheque bounce operation (reverse entries + reopen invoices).
     */
    public record BounceResult(Cheque cheque, List<LedgerEntry> reverseEntries,
                                List<UUID> affectedInvoiceIds, boolean success, String message) {}

    /**
     * Deposit cheque to bank.
     */
    public DepositResult deposit(TenantId tenantId, Cheque cheque) {
        try {
            cheque.deposit();
            return new DepositResult(cheque, true, "Cheque deposited successfully");
        } catch (Exception e) {
            return new DepositResult(cheque, false, e.getMessage());
        }
    }

    /**
     * Clear cheque status only (DEPOSITED → CLEARED). Ledger/payment are owned by application layer
     * via RecordPayment so we do not double-post.
     */
    public ClearResult clearStatusOnly(TenantId tenantId, Cheque cheque) {
        try {
            cheque.clear();
            return new ClearResult(cheque, cheque.getPaymentId(), List.of(), true,
                    "Cheque cleared successfully");
        } catch (Exception e) {
            return new ClearResult(cheque, null, List.of(), false, e.getMessage());
        }
    }

    /**
     * Clear cheque (bank confirmed payment).
     * Legacy helper that also builds Dr Bank / Cr AR entries — prefer application-layer payment path.
     */
    public ClearResult clear(TenantId tenantId, Cheque cheque) {
        try {
            cheque.clear();

            List<LedgerEntry> entries = new ArrayList<>();
            UUID transactionId = UUID.randomUUID();

            entries.add(new LedgerEntry(
                    Account.BANK,
                    cheque.getAmount(),
                    EntryType.DEBIT,
                    "Cheque " + cheque.getChequeNumber() + " cleared",
                    transactionId,
                    "CHEQUE",
                    cheque.getId()
            ));

            entries.add(new LedgerEntry(
                    Account.AR,
                    cheque.getAmount(),
                    EntryType.CREDIT,
                    "Cheque " + cheque.getChequeNumber() + " payment",
                    transactionId,
                    "CHEQUE",
                    cheque.getId()
            ));

            return new ClearResult(cheque, cheque.getPaymentId(), entries, true,
                    "Cheque cleared successfully");
        } catch (Exception e) {
            return new ClearResult(cheque, null, List.of(), false, e.getMessage());
        }
    }

    /**
     * Bounce status only (DEPOSITED|CLEARED → BOUNCED). Cash reverse is application responsibility.
     */
    public BounceResult bounceStatusOnly(TenantId tenantId, Cheque cheque, String reason) {
        try {
            Cheque.ChequeBouncedResult bouncedResult = cheque.bounce(reason);
            return new BounceResult(cheque, List.of(), bouncedResult.invoiceIds(), true,
                    "Cheque bounced: " + reason);
        } catch (Exception e) {
            return new BounceResult(cheque, List.of(), List.of(), false, e.getMessage());
        }
    }

    /**
     * Bounce cheque (bank returned cheque).
     * Builds reverse ledger entries only when a payment was linked (cleared path).
     */
    public BounceResult bounce(TenantId tenantId, Cheque cheque, String reason) {
        try {
            boolean hadPayment = cheque.getPaymentId() != null
                    || cheque.getStatus() == ChequeStatus.CLEARED;
            Cheque.ChequeBouncedResult bouncedResult = cheque.bounce(reason);

            List<LedgerEntry> reverseEntries = new ArrayList<>();
            // Only reverse cash if the cheque had been cleared with a payment (or legacy clear ledger)
            if (hadPayment) {
                UUID transactionId = UUID.randomUUID();

                reverseEntries.add(new LedgerEntry(
                        Account.AR,
                        cheque.getAmount(),
                        EntryType.DEBIT,
                        "Cheque " + cheque.getChequeNumber() + " bounced - " + reason,
                        transactionId,
                        "CHEQUE_BOUNCE",
                        cheque.getId()
                ));

                reverseEntries.add(new LedgerEntry(
                        Account.BANK,
                        cheque.getAmount(),
                        EntryType.CREDIT,
                        "Cheque " + cheque.getChequeNumber() + " bounced - " + reason,
                        transactionId,
                        "CHEQUE_BOUNCE",
                        cheque.getId()
                ));
            }

            return new BounceResult(cheque, reverseEntries,
                    bouncedResult.invoiceIds(), true,
                    "Cheque bounced: " + reason);
        } catch (Exception e) {
            return new BounceResult(cheque, List.of(), List.of(), false, e.getMessage());
        }
    }

    /**
     * Get valid state transitions from current state.
     */
    public List<ChequeStatus> getValidTransitions(ChequeStatus currentStatus) {
        return new ArrayList<>(LIFECYCLE.getValidTransitions(currentStatus));
    }

    /**
     * Validate cheque data.
     */
    public boolean validateCheque(String chequeNumber, String bankName, 
                                   java.time.LocalDate chequeDate, Money amount) {
        if (chequeNumber == null || chequeNumber.isBlank()) return false;
        if (bankName == null || bankName.isBlank()) return false;
        if (chequeDate == null) return false;
        if (amount == null || amount.getAmount().signum() <= 0) return false;
        return true;
    }
}
