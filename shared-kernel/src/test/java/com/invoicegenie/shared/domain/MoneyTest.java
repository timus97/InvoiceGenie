package com.invoicegenie.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Money Value Object")
class MoneyTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create Money with valid amount and currency")
        void shouldCreateWithValidAmountAndCurrency() {
            BigDecimal amount = new BigDecimal("100.50");
            Money money = new Money(amount, "USD");
            
            assertEquals(0, new BigDecimal("100.50").compareTo(money.getAmount()));
            assertEquals("USD", money.getCurrencyCode());
        }

        @Test
        @DisplayName("should create Money using factory method with BigDecimal")
        void shouldCreateWithFactoryMethodBigDecimal() {
            Money money = Money.of(new BigDecimal("200.00"), "EUR");
            
            assertEquals(0, new BigDecimal("200.00").compareTo(money.getAmount()));
            assertEquals("EUR", money.getCurrencyCode());
        }

        @Test
        @DisplayName("should create Money using factory method with String")
        void shouldCreateWithFactoryMethodString() {
            Money money = Money.of("150.75", "GBP");
            
            assertEquals(0, new BigDecimal("150.75").compareTo(money.getAmount()));
            assertEquals("GBP", money.getCurrencyCode());
        }

        @Test
        @DisplayName("should scale amount to 2 decimal places")
        void shouldScaleToTwoDecimalPlaces() {
            Money money = new Money(new BigDecimal("100.12345"), "USD");
            
            assertEquals(0, new BigDecimal("100.12").compareTo(money.getAmount()));
            assertEquals(2, money.getAmount().scale());
        }

        @Test
        @DisplayName("should throw when amount is null")
        void shouldThrowWhenAmountNull() {
            assertThrows(NullPointerException.class, () -> new Money(null, "USD"));
        }

        @Test
        @DisplayName("should throw when currencyCode is null")
        void shouldThrowWhenCurrencyCodeNull() {
            assertThrows(NullPointerException.class, () -> new Money(BigDecimal.TEN, null));
        }

        @Test
        @DisplayName("should throw when currencyCode is not 3 characters")
        void shouldThrowWhenCurrencyCodeInvalid() {
            assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.TEN, "US"));
            assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.TEN, "DOLLAR"));
        }
    }

    @Nested
    @DisplayName("Arithmetic Operations")
    class ArithmeticOperations {

        @Test
        @DisplayName("should add two Money values with same currency")
        void shouldAddSameCurrency() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("50.00", "USD");
            
            Money result = m1.add(m2);
            
            assertEquals(0, new BigDecimal("150.00").compareTo(result.getAmount()));
            assertEquals("USD", result.getCurrencyCode());
        }

        @Test
        @DisplayName("should subtract two Money values with same currency")
        void shouldSubtractSameCurrency() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("30.00", "USD");
            
            Money result = m1.subtract(m2);
            
            assertEquals(0, new BigDecimal("70.00").compareTo(result.getAmount()));
            assertEquals("USD", result.getCurrencyCode());
        }

        @Test
        @DisplayName("should throw when adding different currencies")
        void shouldThrowWhenAddingDifferentCurrencies() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("50.00", "EUR");
            
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> m1.add(m2));
            assertTrue(ex.getMessage().contains("different currencies"));
        }

        @Test
        @DisplayName("should throw when subtracting different currencies")
        void shouldThrowWhenSubtractingDifferentCurrencies() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("50.00", "EUR");
            
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> m1.subtract(m2));
            assertTrue(ex.getMessage().contains("different currencies"));
        }

        @Test
        @DisplayName("should handle negative amounts")
        void shouldHandleNegativeAmounts() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("150.00", "USD");
            
            Money result = m1.subtract(m2);
            
            assertEquals(0, new BigDecimal("-50.00").compareTo(result.getAmount()));
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when same amount and currency")
        void shouldBeEqualWhenSameAmountAndCurrency() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("100.00", "USD");
            
            assertEquals(m1, m2);
            assertEquals(m1.hashCode(), m2.hashCode());
        }

        @Test
        @DisplayName("should be equal when amounts are equivalent but different precision")
        void shouldBeEqualWhenEquivalentPrecision() {
            Money m1 = new Money(new BigDecimal("100.00"), "USD");
            Money m2 = new Money(new BigDecimal("100.0"), "USD");
            
            assertEquals(m1, m2);
        }

        @Test
        @DisplayName("should not be equal when different amounts")
        void shouldNotBeEqualWhenDifferentAmounts() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("200.00", "USD");
            
            assertNotEquals(m1, m2);
        }

        @Test
        @DisplayName("should not be equal when different currencies")
        void shouldNotBeEqualWhenDifferentCurrencies() {
            Money m1 = Money.of("100.00", "USD");
            Money m2 = Money.of("100.00", "EUR");
            
            assertNotEquals(m1, m2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            Money money = Money.of("100.00", "USD");
            assertNotEquals(null, money);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            Money money = Money.of("100.00", "USD");
            assertEquals(money, money);
        }
    }
}
