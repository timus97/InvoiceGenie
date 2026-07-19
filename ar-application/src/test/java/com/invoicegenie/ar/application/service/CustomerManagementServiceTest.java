package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.CustomerUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.ar.domain.service.CustomerService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CustomerManagementService")
@ExtendWith(MockitoExtension.class)
class CustomerManagementServiceTest {

    @Mock private CustomerRepository customerRepository;
    private CustomerService customerService;
    private CustomerManagementService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService();
        service = new CustomerManagementService(customerService, customerRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("should create customer when code is unique")
        void shouldCreateWhenCodeUnique() {
            when(customerRepository.existsByTenantAndCode(eq(tenantId), eq("C001"))).thenReturn(false);

            var result = service.create(tenantId, "C001", "Acme Corp", "USD");

            assertTrue(result.success());
            assertNotNull(result.customer());
            assertEquals("C001", result.customer().getCustomerCode());
            verify(customerRepository).save(eq(tenantId), any(Customer.class));
        }

        @Test
        @DisplayName("should fail when code already exists")
        void shouldFailWhenCodeExists() {
            when(customerRepository.existsByTenantAndCode(eq(tenantId), eq("C001"))).thenReturn(true);

            var result = service.create(tenantId, "C001", "Acme Corp", "USD");

            assertFalse(result.success());
            assertTrue(result.message().contains("already exists"));
            verify(customerRepository, never()).save(any(), any());
        }
    }

    @Nested
    @DisplayName("get / list")
    class GetAndList {
        @Test
        @DisplayName("should return customer when found")
        void shouldGetWhenFound() {
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            Optional<Customer> result = service.get(tenantId, customerId);

            assertTrue(result.isPresent());
            assertEquals("C001", result.get().getCustomerCode());
        }

        @Test
        @DisplayName("should list by status")
        void shouldListByStatus() {
            when(customerRepository.findByTenantAndStatus(tenantId, CustomerStatus.ACTIVE))
                    .thenReturn(List.of(new Customer(customerId, "C001", "Acme", "USD")));

            var result = service.list(tenantId, "ACTIVE", null, false);

            assertTrue(result.success());
            assertEquals(1, result.customers().size());
        }

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            var result = service.list(tenantId, "NOT_A_STATUS", null, false);

            assertFalse(result.success());
            assertTrue(result.errorMessage().contains("Unknown status"));
        }

        @Test
        @DisplayName("should search when query provided")
        void shouldSearch() {
            when(customerRepository.searchByTenant(tenantId, "acm"))
                    .thenReturn(List.of(new Customer(customerId, "C001", "Acme", "USD")));

            var result = service.list(tenantId, null, "acm", false);

            assertTrue(result.success());
            assertEquals(1, result.customers().size());
        }
    }

    @Nested
    @DisplayName("block / unblock / delete")
    class StatusChanges {
        @Test
        @DisplayName("should block active customer")
        void shouldBlock() {
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            var result = service.block(tenantId, customerId);

            assertTrue(result.success());
            assertEquals(CustomerStatus.BLOCKED, result.customer().getStatus());
            verify(customerRepository).save(eq(tenantId), any(Customer.class));
        }

        @Test
        @DisplayName("should fail block when not found")
        void shouldFailBlockWhenNotFound() {
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.empty());

            var result = service.block(tenantId, customerId);

            assertFalse(result.success());
            assertTrue(result.message().contains("not found"));
        }
    }

    @Nested
    @DisplayName("stats / credit-check")
    class StatsAndCredit {
        @Test
        @DisplayName("should return stats")
        void shouldReturnStats() {
            when(customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.ACTIVE)).thenReturn(5L);
            when(customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.BLOCKED)).thenReturn(1L);
            when(customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.DELETED)).thenReturn(2L);

            CustomerUseCase.CustomerStats stats = service.stats(tenantId);

            assertEquals(5L, stats.active());
            assertEquals(1L, stats.blocked());
            assertEquals(2L, stats.deleted());
        }

        @Test
        @DisplayName("should check credit for active customer")
        void shouldCheckCredit() {
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            customer.setCreditLimit(new BigDecimal("10000"));
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            var result = service.checkCredit(tenantId, customerId, new BigDecimal("1000"), new BigDecimal("500"));

            assertTrue(result.canInvoice());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("should update display name")
        void shouldUpdate() {
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            var result = service.update(tenantId, customerId,
                    new CustomerUseCase.UpdateCustomerCommand("Acme Display", null, null, null, null, null));

            assertTrue(result.isPresent());
            assertEquals("Acme Display", result.get().getDisplayName());
            verify(customerRepository).save(eq(tenantId), any(Customer.class));
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.empty());

            var result = service.update(tenantId, customerId,
                    new CustomerUseCase.UpdateCustomerCommand("X", null, null, null, null, null));

            assertTrue(result.isEmpty());
        }
    }
}
