package com.invoicegenie.ar.domain.model.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Customer Aggregate")
class CustomerTest {

    private CustomerId customerId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerId = CustomerId.generate();
        customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create customer with required fields")
        void shouldCreateWithRequiredFields() {
            assertNotNull(customer.getId());
            assertEquals("CUST001", customer.getCustomerCode());
            assertEquals("Acme Corp", customer.getLegalName());
            assertEquals("USD", customer.getCurrency());
            assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
        }

        @Test
        @DisplayName("should create customer with all fields for reconstitution")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            Customer fullCustomer = new Customer(
                    customerId, "CUST002", "Full Corp", "Display Name",
                    "email@test.com", "555-1234", "123 Main St", "EUR",
                    new BigDecimal("10000.00"), "NET30", "TAX123",
                    CustomerStatus.ACTIVE, now, now, 1L
            );
            
            assertEquals("CUST002", fullCustomer.getCustomerCode());
            assertEquals("Full Corp", fullCustomer.getLegalName());
            assertEquals("Display Name", fullCustomer.getDisplayName());
            assertEquals("email@test.com", fullCustomer.getEmail());
            assertEquals("555-1234", fullCustomer.getPhone());
            assertEquals("123 Main St", fullCustomer.getBillingAddress());
            assertEquals("EUR", fullCustomer.getCurrency());
            assertEquals(0, new BigDecimal("10000.00").compareTo(fullCustomer.getCreditLimit()));
            assertEquals("NET30", fullCustomer.getPaymentTerms());
            assertEquals("TAX123", fullCustomer.getTaxId());
        }

        @Test
        @DisplayName("should throw when id is null")
        void shouldThrowWhenIdNull() {
            assertThrows(NullPointerException.class, () ->
                    new Customer(null, "CUST001", "Acme Corp", "USD"));
        }

        @Test
        @DisplayName("should throw when customerCode is null")
        void shouldThrowWhenCodeNull() {
            assertThrows(NullPointerException.class, () ->
                    new Customer(customerId, null, "Acme Corp", "USD"));
        }

        @Test
        @DisplayName("should throw when legalName is null")
        void shouldThrowWhenLegalNameNull() {
            assertThrows(NullPointerException.class, () ->
                    new Customer(customerId, "CUST001", null, "USD"));
        }

        @Test
        @DisplayName("should throw when currency is null")
        void shouldThrowWhenCurrencyNull() {
            assertThrows(NullPointerException.class, () ->
                    new Customer(customerId, "CUST001", "Acme Corp", null));
        }

        @Test
        @DisplayName("should throw when currency is not ISO 4217")
        void shouldThrowWhenCurrencyInvalid() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Customer(customerId, "CUST001", "Acme Corp", "US"));
        }
    }

    @Nested
    @DisplayName("Display Name")
    class DisplayNameTests {

        @Test
        @DisplayName("should return legal name when display name is null")
        void shouldReturnLegalNameWhenDisplayNameNull() {
            assertEquals("Acme Corp", customer.getDisplayName());
        }

        @Test
        @DisplayName("should update display name")
        void shouldUpdateDisplayName() {
            customer.updateDisplayName("ACME Inc");
            assertEquals("ACME Inc", customer.getDisplayName());
        }

        @Test
        @DisplayName("should reset display name to legal name when set to null")
        void shouldResetDisplayName() {
            customer.updateDisplayName("ACME Inc");
            customer.updateDisplayName(null);
            assertEquals("Acme Corp", customer.getDisplayName());
        }
    }

    @Nested
    @DisplayName("Contact Info")
    class ContactInfo {

        @Test
        @DisplayName("should update contact info")
        void shouldUpdateContactInfo() {
            customer.updateContact("new@email.com", "555-9999", "456 Oak Ave");
            
            assertEquals("new@email.com", customer.getEmail());
            assertEquals("555-9999", customer.getPhone());
            assertEquals("456 Oak Ave", customer.getBillingAddress());
        }
    }

    @Nested
    @DisplayName("Currency")
    class Currency {

        @Test
        @DisplayName("should change currency")
        void shouldChangeCurrency() {
            customer.changeCurrency("EUR");
            assertEquals("EUR", customer.getCurrency());
        }

        @Test
        @DisplayName("should throw when currency is invalid")
        void shouldThrowWhenCurrencyInvalid() {
            assertThrows(IllegalArgumentException.class, () -> customer.changeCurrency("EURO"));
        }
    }

    @Nested
    @DisplayName("Credit Limit")
    class CreditLimit {

        @Test
        @DisplayName("should set credit limit")
        void shouldSetCreditLimit() {
            customer.setCreditLimit(new BigDecimal("5000.00"));
            assertEquals(0, new BigDecimal("5000.00").compareTo(customer.getCreditLimit()));
        }

        @Test
        @DisplayName("should allow null credit limit (unlimited)")
        void shouldAllowNullCreditLimit() {
            customer.setCreditLimit(new BigDecimal("5000.00"));
            customer.setCreditLimit(null);
            assertNull(customer.getCreditLimit());
        }

        @Test
        @DisplayName("should throw when credit limit is negative")
        void shouldThrowWhenCreditLimitNegative() {
            assertThrows(IllegalArgumentException.class, () ->
                    customer.setCreditLimit(new BigDecimal("-100.00")));
        }
    }

    @Nested
    @DisplayName("Payment Terms")
    class PaymentTerms {

        @Test
        @DisplayName("should set payment terms")
        void shouldSetPaymentTerms() {
            customer.setPaymentTerms("NET45");
            assertEquals("NET45", customer.getPaymentTerms());
        }
    }

    @Nested
    @DisplayName("Status Changes")
    class StatusChanges {

        @Test
        @DisplayName("should block customer")
        void shouldBlockCustomer() {
            customer.block();
            assertEquals(CustomerStatus.BLOCKED, customer.getStatus());
        }

        @Test
        @DisplayName("should throw when blocking deleted customer")
        void shouldThrowWhenBlockingDeleted() {
            customer.delete();
            assertThrows(IllegalStateException.class, () -> customer.block());
        }

        @Test
        @DisplayName("should unblock customer")
        void shouldUnblockCustomer() {
            customer.block();
            customer.unblock();
            assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
        }

        @Test
        @DisplayName("should delete customer")
        void shouldDeleteCustomer() {
            customer.delete();
            assertEquals(CustomerStatus.DELETED, customer.getStatus());
        }
    }

    @Nested
    @DisplayName("Invoicing Checks")
    class InvoicingChecks {

        @Test
        @DisplayName("active customer can be invoiced")
        void activeCustomerCanBeInvoiced() {
            assertTrue(customer.canBeInvoiced());
        }

        @Test
        @DisplayName("blocked customer cannot be invoiced")
        void blockedCustomerCannotBeInvoiced() {
            customer.block();
            assertFalse(customer.canBeInvoiced());
        }

        @Test
        @DisplayName("deleted customer cannot be invoiced")
        void deletedCustomerCannotBeInvoiced() {
            customer.delete();
            assertFalse(customer.canBeInvoiced());
        }

        @Test
        @DisplayName("should check invoicing with credit limit")
        void shouldCheckInvoicingWithCreditLimit() {
            customer.setCreditLimit(new BigDecimal("1000.00"));
            
            assertTrue(customer.canBeInvoicedForAmount(new BigDecimal("500.00"), new BigDecimal("400.00")));
            assertFalse(customer.canBeInvoicedForAmount(new BigDecimal("500.00"), new BigDecimal("600.00")));
        }

        @Test
        @DisplayName("unlimited credit allows any amount")
        void unlimitedCreditAllowsAnyAmount() {
            assertTrue(customer.canBeInvoicedForAmount(new BigDecimal("1000000.00"), new BigDecimal("500000.00")));
        }

        @Test
        @DisplayName("should detect over credit limit")
        void shouldDetectOverCreditLimit() {
            customer.setCreditLimit(new BigDecimal("1000.00"));
            
            assertFalse(customer.isOverCreditLimit(new BigDecimal("500.00")));
            assertTrue(customer.isOverCreditLimit(new BigDecimal("1500.00")));
        }

        @Test
        @DisplayName("unlimited credit never over limit")
        void unlimitedCreditNeverOverLimit() {
            assertFalse(customer.isOverCreditLimit(new BigDecimal("1000000.00")));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should validate successfully")
        void shouldValidateSuccessfully() {
            assertDoesNotThrow(() -> customer.validate());
        }

        @Test
        @DisplayName("should throw when customer code is blank")
        void shouldThrowWhenCodeBlank() {
            Customer invalidCustomer = new Customer(
                    customerId, "", "Acme Corp", "USD",
                    null, null, null, "USD", null, null, null,
                    CustomerStatus.ACTIVE, Instant.now(), Instant.now(), 1L
            );
            assertThrows(IllegalArgumentException.class, invalidCustomer::validate);
        }

        @Test
        @DisplayName("should throw when legal name is blank")
        void shouldThrowWhenLegalNameBlank() {
            Customer invalidCustomer = new Customer(
                    customerId, "CUST001", "", "USD",
                    null, null, null, "USD", null, null, null,
                    CustomerStatus.ACTIVE, Instant.now(), Instant.now(), 1L
            );
            assertThrows(IllegalArgumentException.class, invalidCustomer::validate);
        }
    }

    @Nested
    @DisplayName("Available Credit")
    class AvailableCredit {

        @Test
        @DisplayName("should return null for unlimited credit")
        void shouldReturnNullForUnlimited() {
            assertNull(customer.getAvailableCredit(new BigDecimal("500.00")));
        }

        @Test
        @DisplayName("should calculate available credit")
        void shouldCalculateAvailableCredit() {
            customer.setCreditLimit(new BigDecimal("1000.00"));
            
            assertEquals(0, new BigDecimal("500.00").compareTo(customer.getAvailableCredit(new BigDecimal("500.00"))));
            assertEquals(0, new BigDecimal("0.00").compareTo(customer.getAvailableCredit(new BigDecimal("1000.00"))));
            assertEquals(0, new BigDecimal("0.00").compareTo(customer.getAvailableCredit(new BigDecimal("1500.00"))));
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal by id")
        void shouldBeEqualById() {
            Customer other = new Customer(customerId, "CUST002", "Other Corp", "EUR");
            assertEquals(customer, other);
        }

        @Test
        @DisplayName("should not be equal when different ids")
        void shouldNotBeEqualWhenDifferentIds() {
            Customer other = new Customer(CustomerId.generate(), "CUST001", "Acme Corp", "USD");
            assertNotEquals(customer, other);
        }
    }
}
