package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.exception.DomainValidationException;
import com.invoicegenie.ar.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChequeLifecycleEngine")
class ChequeLifecycleEngineTest {

    private ChequeLifecycleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ChequeLifecycleEngine();
    }

    @Test
    @DisplayName("allows RECEIVED -> DEPOSITED")
    void allowsReceivedToDeposited() {
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(ChequeStatus.RECEIVED, ChequeStatus.DEPOSITED));
    }

    @Test
    @DisplayName("allows DEPOSITED -> CLEARED and BOUNCED")
    void allowsDepositedToTerminal() {
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(ChequeStatus.DEPOSITED, ChequeStatus.CLEARED));
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(ChequeStatus.DEPOSITED, ChequeStatus.BOUNCED));
    }

    @Test
    @DisplayName("rejects invalid transitions")
    void rejectsInvalidTransitions() {
        assertThrows(InvalidStateTransitionException.class,
                () -> engine.assertTransitionAllowed(ChequeStatus.RECEIVED, ChequeStatus.CLEARED));
        assertThrows(InvalidStateTransitionException.class,
                () -> engine.assertTransitionAllowed(ChequeStatus.RECEIVED, ChequeStatus.BOUNCED));
        assertThrows(InvalidStateTransitionException.class,
                () -> engine.assertTransitionAllowed(ChequeStatus.CLEARED, ChequeStatus.DEPOSITED));
        assertThrows(InvalidStateTransitionException.class,
                () -> engine.assertTransitionAllowed(ChequeStatus.BOUNCED, ChequeStatus.CLEARED));
        assertThrows(InvalidStateTransitionException.class,
                () -> engine.assertTransitionAllowed(ChequeStatus.DEPOSITED, ChequeStatus.RECEIVED));
    }

    @Test
    @DisplayName("rejects same-status transition")
    void rejectsSameStatus() {
        assertThrows(InvalidStateTransitionException.class,
                () -> engine.assertTransitionAllowed(ChequeStatus.RECEIVED, ChequeStatus.RECEIVED));
    }

    @Test
    @DisplayName("rejects null status")
    void rejectsNullStatus() {
        assertThrows(DomainValidationException.class,
                () -> engine.assertTransitionAllowed(null, ChequeStatus.DEPOSITED));
        assertThrows(DomainValidationException.class,
                () -> engine.assertTransitionAllowed(ChequeStatus.RECEIVED, null));
    }

    @Test
    @DisplayName("returns valid transitions per status")
    void returnsValidTransitions() {
        assertEquals(1, engine.getValidTransitions(ChequeStatus.RECEIVED).size());
        assertTrue(engine.getValidTransitions(ChequeStatus.RECEIVED).contains(ChequeStatus.DEPOSITED));
        assertEquals(2, engine.getValidTransitions(ChequeStatus.DEPOSITED).size());
        assertTrue(engine.getValidTransitions(ChequeStatus.CLEARED).isEmpty());
        assertTrue(engine.getValidTransitions(ChequeStatus.BOUNCED).isEmpty());
    }

    @Test
    @DisplayName("transition helpers return expected targets")
    void transitionHelpers() {
        assertEquals(ChequeStatus.DEPOSITED, engine.deposit().target());
        assertEquals(ChequeStatus.CLEARED, engine.clear().target());
        assertEquals(ChequeStatus.BOUNCED, engine.bounce().target());
    }
}
