package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("CustomerService Domain Service")
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;

    private CustomerService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new CustomerService();
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Create Customer")
    class CreateCustomer {

        @Test
        @DisplayName("should create customer successfully")
        void shouldCreateSuccessfully() {
            when(repository.existsByTenantAndCode(tenantId, "CUST001")).thenReturn(false);

            CustomerService.CreateResult result = service.createCustomer(tenantId, repository, 
                    "CUST001", "Acme Corp", "USD");

            assertTrue(result.success());
            assertNotNull(result.customer());
            assertEquals("CUST001", result.customer().getCustomerCode());
            verify(repository).save(eq(tenantId), any(Customer.class));
        }

        @Test
        @DisplayName("should fail when customer code already exists")
        void shouldFailWhenCodeExists() {
            when(repository.existsByTenantAndCode(tenantId, "CUST001")).thenReturn(true);

            CustomerService.CreateResult result = service.createCustomer(tenantId, repository,
                    "CUST001", "Acme Corp", "USD");

            assertFalse(result.success());
            assertNull(result.customer());
            assertTrue(result.message().contains("already exists"));
        }

        @Test
        @DisplayName("should fail with invalid currency")
        void shouldFailWithInvalidCurrency() {
            when(repository.existsByTenantAndCode(tenantId, "CUST001")).thenReturn(false);

            CustomerService.CreateResult result = service.createCustomer(tenantId, repository,
                    "CUST001", "Acme Corp", "US");

            assertFalse(result.success());
            assertTrue(result.message().contains("ISO 4217"));
        }
    }

    @Nested
    @DisplayName("Block Customer")
    class BlockCustomer {

        @Test
        @DisplayName("should block customer successfully")
        void shouldBlockSuccessfully() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.StatusResult result = service.blockCustomer(tenantId, repository, customerId);

            assertTrue(result.success());
            assertEquals(CustomerStatus.BLOCKED, result.customer().getStatus());
            verify(repository).save(eq(tenantId), any(Customer.class));
        }

        @Test
        @DisplayName("should fail when customer not found")
        void shouldFailWhenNotFound() {
            CustomerId customerId = CustomerId.generate();
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.empty());

            CustomerService.StatusResult result = service.blockCustomer(tenantId, repository, customerId);

            assertFalse(result.success());
            assertTrue(result.message().contains("not found"));
        }

        @Test
        @DisplayName("should fail when customer already blocked")
        void shouldFailWhenAlreadyBlocked() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            customer.block();
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.StatusResult result = service.blockCustomer(tenantId, repository, customerId);

            assertFalse(result.success());
            assertTrue(result.message().contains("already blocked"));
        }
    }

    @Nested
    @DisplayName("Unblock Customer")
    class UnblockCustomer {

        @Test
        @DisplayName("should unblock customer successfully")
        void shouldUnblockSuccessfully() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            customer.block();
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.StatusResult result = service.unblockCustomer(tenantId, repository, customerId);

            assertTrue(result.success());
            verify(repository).save(eq(tenantId), any(Customer.class));
        }

        @Test
        @DisplayName("should fail when customer not blocked")
        void shouldFailWhenNotBlocked() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.StatusResult result = service.unblockCustomer(tenantId, repository, customerId);

            assertFalse(result.success());
            assertTrue(result.message().contains("not blocked"));
        }
    }

    @Nested
    @DisplayName("Delete Customer")
    class DeleteCustomer {

        @Test
        @DisplayName("should delete customer successfully")
        void shouldDeleteSuccessfully() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.StatusResult result = service.deleteCustomer(tenantId, repository, customerId);

            assertTrue(result.success());
            assertEquals(CustomerStatus.DELETED, result.customer().getStatus());
        }

        @Test
        @DisplayName("should fail when already deleted")
        void shouldFailWhenAlreadyDeleted() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            customer.delete();
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.StatusResult result = service.deleteCustomer(tenantId, repository, customerId);

            assertFalse(result.success());
            assertTrue(result.message().contains("already deleted"));
        }
    }

    @Nested
    @DisplayName("Credit Limit Check")
    class CreditLimitCheck {

        @Test
        @DisplayName("should pass credit check for active customer")
        void shouldPassForActiveCustomer() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.CreditCheckResult result = service.checkCreditLimit(
                    tenantId, repository, customerId, 
                    new BigDecimal("500.00"), new BigDecimal("200.00"));

            assertTrue(result.canInvoice());
        }

        @Test
        @DisplayName("should fail credit check for blocked customer")
        void shouldFailForBlockedCustomer() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            customer.block();
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.CreditCheckResult result = service.checkCreditLimit(
                    tenantId, repository, customerId,
                    new BigDecimal("500.00"), new BigDecimal("200.00"));

            assertFalse(result.canInvoice());
            assertTrue(result.message().contains("not active"));
        }

        @Test
        @DisplayName("should fail when credit limit exceeded")
        void shouldFailWhenCreditLimitExceeded() {
            CustomerId customerId = CustomerId.generate();
            Customer customer = new Customer(customerId, "CUST001", "Acme Corp", "USD");
            customer.setCreditLimit(new BigDecimal("1000.00"));
            when(repository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            CustomerService.CreditCheckResult result = service.checkCreditLimit(
                    tenantId, repository, customerId,
                    new BigDecimal("900.00"), new BigDecimal("200.00"));

            assertFalse(result.canInvoice());
            assertTrue(result.message().contains("exceeded"));
        }
    }

    @Nested
    @DisplayName("List and Search")
    class ListAndSearch {

        @Test
        @DisplayName("should list active customers")
        void shouldListActiveCustomers() {
            Customer c1 = new Customer(CustomerId.generate(), "CUST001", "Acme", "USD");
            Customer c2 = new Customer(CustomerId.generate(), "CUST002", "Beta", "USD");
            when(repository.findByTenantAndStatus(tenantId, CustomerStatus.ACTIVE))
                    .thenReturn(List.of(c1, c2));

            List<Customer> result = service.listActiveCustomers(tenantId, repository);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should search customers")
        void shouldSearchCustomers() {
            Customer c1 = new Customer(CustomerId.generate(), "CUST001", "Acme Corp", "USD");
            when(repository.searchByTenant(tenantId, "Acme")).thenReturn(List.of(c1));

            List<Customer> result = service.searchCustomers(tenantId, repository, "Acme");

            assertEquals(1, result.size());
            assertEquals("Acme Corp", result.get(0).getLegalName());
        }

        @Test
        @DisplayName("should return all customers when query is blank")
        void shouldReturnAllWhenQueryBlank() {
            Customer c1 = new Customer(CustomerId.generate(), "CUST001", "Acme", "USD");
            when(repository.findAllByTenant(tenantId, false)).thenReturn(List.of(c1));

            List<Customer> result = service.searchCustomers(tenantId, repository, "");

            assertEquals(1, result.size());
        }
    }
}
