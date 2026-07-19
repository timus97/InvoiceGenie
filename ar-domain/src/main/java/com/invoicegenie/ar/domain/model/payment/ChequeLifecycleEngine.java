package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.exception.DomainValidationException;
import com.invoicegenie.ar.domain.exception.InvalidStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Centralized lifecycle rules for cheques.
 *
 * <pre>
 * RECEIVED  → DEPOSITED
 * DEPOSITED → CLEARED | BOUNCED
 * CLEARED, BOUNCED are terminal
 * </pre>
 */
public final class ChequeLifecycleEngine {

    private final EnumMap<ChequeStatus, EnumSet<ChequeStatus>> allowedTransitions;

    public ChequeLifecycleEngine() {
        allowedTransitions = new EnumMap<>(ChequeStatus.class);
        allowedTransitions.put(ChequeStatus.RECEIVED, EnumSet.of(ChequeStatus.DEPOSITED));
        allowedTransitions.put(ChequeStatus.DEPOSITED, EnumSet.of(ChequeStatus.CLEARED, ChequeStatus.BOUNCED));
        allowedTransitions.put(ChequeStatus.CLEARED, EnumSet.noneOf(ChequeStatus.class));
        allowedTransitions.put(ChequeStatus.BOUNCED, EnumSet.noneOf(ChequeStatus.class));
    }

    public Transition deposit() {
        return new Transition(ChequeStatus.DEPOSITED, "deposit");
    }

    public Transition clear() {
        return new Transition(ChequeStatus.CLEARED, "clear");
    }

    public Transition bounce() {
        return new Transition(ChequeStatus.BOUNCED, "bounce");
    }

    public void assertTransitionAllowed(ChequeStatus from, ChequeStatus to) {
        if (from == null || to == null) {
            throw new DomainValidationException("Cheque status cannot be null");
        }
        if (from == to) {
            throw new InvalidStateTransitionException("Cheque already in status: " + from);
        }
        EnumSet<ChequeStatus> allowed = allowedTransitions.getOrDefault(from, EnumSet.noneOf(ChequeStatus.class));
        if (!allowed.contains(to)) {
            throw new InvalidStateTransitionException(
                    "Invalid cheque status transition: " + from + " -> " + to);
        }
    }

    /**
     * Returns allowed target statuses from the current status.
     */
    public Set<ChequeStatus> getValidTransitions(ChequeStatus from) {
        if (from == null) {
            return EnumSet.noneOf(ChequeStatus.class);
        }
        return EnumSet.copyOf(allowedTransitions.getOrDefault(from, EnumSet.noneOf(ChequeStatus.class)));
    }

    public record Transition(ChequeStatus target, String action) {}
}
