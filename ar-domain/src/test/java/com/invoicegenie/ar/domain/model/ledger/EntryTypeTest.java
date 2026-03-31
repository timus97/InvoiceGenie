package com.invoicegenie.ar.domain.model.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EntryType Enum")
class EntryTypeTest {

    @Test
    @DisplayName("should have DEBIT and CREDIT")
    void shouldHaveDebitAndCredit() {
        EntryType[] types = EntryType.values();
        
        assertEquals(2, types.length);
        assertNotNull(EntryType.DEBIT);
        assertNotNull(EntryType.CREDIT);
    }

    @Test
    @DisplayName("should have correct names")
    void shouldHaveCorrectNames() {
        assertEquals("DEBIT", EntryType.DEBIT.name());
        assertEquals("CREDIT", EntryType.CREDIT.name());
    }

    @Test
    @DisplayName("should be able to get by name")
    void shouldBeAbleToGetByName() {
        assertEquals(EntryType.DEBIT, EntryType.valueOf("DEBIT"));
        assertEquals(EntryType.CREDIT, EntryType.valueOf("CREDIT"));
    }
}
