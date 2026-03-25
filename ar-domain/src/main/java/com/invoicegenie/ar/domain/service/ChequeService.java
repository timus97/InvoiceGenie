package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain service for cheque processing operations.
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
@ApplicationScoped
public class ChequeService {

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
     * Clear cheque (bank confirmed payment).
     * Creates ledger entries: Debit Bank, Credit AR
     */
    public ClearResult clear(TenantId tenantId, Cheque cheque) {
        try {
            cheque.clear();
            
            // Create ledger entries for cleared cheque
            // Note: Actual payment allocation would be done by PaymentAllocationService
            List<LedgerEntry> entries = new ArrayList<>();
            UUID transactionId = UUID.randomUUID();
            
            // Debit Bank
            entries.add(new LedgerEntry(
                    Account.BANK,
                    cheque.getAmount(),
                    EntryType.DEBIT,
                    "Cheque " + cheque.getChequeNumber() + " cleared",
                    transactionId,
                    "CHEQUE",
                    cheque.getId()
            ));
            
            // Credit AR (payment received)
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
     * Bounce cheque (bank returned cheque).
     * Creates reverse ledger entries and returns affected invoices for reopening.
     */
    public BounceResult bounce(TenantId tenantId, Cheque cheque, String reason) {
        try {
            Cheque.ChequeBouncedResult bouncedResult = cheque.bounce(reason);
            
            // Create reverse ledger entries
            // If cheque was deposited, we need to reverse: Debit AR, Credit Bank
            List<LedgerEntry> reverseEntries = new ArrayList<>();
            UUID transactionId = UUID.randomUUID();
            
            // Debit AR (customer still owes)
            reverseEntries.add(new LedgerEntry(
                    Account.AR,
                    cheque.getAmount(),
                    EntryType.DEBIT,
                    "Cheque " + cheque.getChequeNumber() + " bounced - " + reason,
                    transactionId,
                    "CHEQUE_BOUNCE",
                    cheque.getId()
            ));
            
            // Credit Bank (reverse the deposit)
            reverseEntries.add(new LedgerEntry(
                    Account.BANK,
                    cheque.getAmount(),
                    EntryType.CREDIT,
                    "Cheque " + cheque.getChequeNumber() + " bounced - " + reason,
                    transactionId,
                    "CHEQUE_BOUNCE",
                    cheque.getId()
            ));
            
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
        List<ChequeStatus> transitions = new ArrayList<>();
        
        switch (currentStatus) {
            case RECEIVED:
                transitions.add(ChequeStatus.DEPOSITED);
                break;
            case DEPOSITED:
                transitions.add(ChequeStatus.CLEARED);
                transitions.add(ChequeStatus.BOUNCED);
                break;
            case CLEARED:
            case BOUNCED:
                // Terminal states - no transitions
                break;
        }
        
        return transitions;
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
