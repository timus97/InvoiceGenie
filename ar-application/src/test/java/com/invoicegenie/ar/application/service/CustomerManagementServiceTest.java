package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.CustomerUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.shared.domain.Money;
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
import java.time.LocalDate;
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
    @Mock private InvoiceRepository invoiceRepository;
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
    @Nested
    @DisplayName("arSummary / credit with system AR")
    class ArSummary {
        @Test
        @DisplayName("empty when customer not found")
        void notFound() {
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.empty());
            assertTrue(service.arSummary(tenantId, customerId).isEmpty());
        }

        @Test
        @DisplayName("zero summary without invoice repository")
        void noInvoiceRepo() {
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            var summary = service.arSummary(tenantId, customerId);

            assertTrue(summary.isPresent());
            assertEquals(0, summary.get().openInvoiceCount());
            assertEquals(0, summary.get().totalBalanceBaseCurrency().signum());
        }

        @Test
        @DisplayName("aggregates open invoices by currency")
        void aggregates() {
            service = new CustomerManagementService(customerService, customerRepository, invoiceRepository);
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            Invoice usd = new Invoice(InvoiceId.generate(), "INV-1", customerId, "C001", "USD",
                    LocalDate.now(), LocalDate.now().plusDays(10), List.of());
            usd.addLine(new InvoiceLine(1, "A", Money.of("100.00", "USD")));
            usd.issue();
            usd.recordPaymentApplied(Money.of("40.00", "USD"));

            Invoice eur = new Invoice(InvoiceId.generate(), "INV-2", customerId, "C001", "EUR",
                    LocalDate.now(), LocalDate.now().plusDays(10), List.of());
            eur.addLine(new InvoiceLine(1, "B", Money.of("50.00", "EUR")));
            eur.issue();

            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId))
                    .thenReturn(List.of(usd, eur));

            var summary = service.arSummary(tenantId, customerId);

            assertTrue(summary.isPresent());
            assertEquals(2, summary.get().openInvoiceCount());
            assertEquals("MIXED", summary.get().baseCurrency());
            assertTrue(summary.get().byCurrency().containsKey("USD"));
            assertTrue(summary.get().byCurrency().containsKey("EUR"));
            assertEquals(0, new BigDecimal("60.00").compareTo(summary.get().byCurrency().get("USD").balance()));
        }

        @Test
        @DisplayName("empty open invoices uses USD base")
        void emptyOpen() {
            service = new CustomerManagementService(customerService, customerRepository, invoiceRepository);
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));
            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of());

            var summary = service.arSummary(tenantId, customerId);
            assertTrue(summary.isPresent());
            assertEquals("USD", summary.get().baseCurrency());
            assertEquals(0, summary.get().openInvoiceCount());
        }

        @Test
        @DisplayName("checkCredit uses system AR when outstanding is zero")
        void creditUsesSystemAr() {
            service = new CustomerManagementService(customerService, customerRepository, invoiceRepository);
            Customer customer = new Customer(customerId, "C001", "Acme", "USD");
            customer.setCreditLimit(new BigDecimal("1000"));
            when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));

            Invoice inv = new Invoice(InvoiceId.generate(), "INV-3", customerId, "C001", "USD",
                    LocalDate.now(), LocalDate.now().plusDays(10), List.of());
            inv.addLine(new InvoiceLine(1, "A", Money.of("900.00", "USD")));
            inv.issue();
            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of(inv));

            var result = service.checkCredit(tenantId, customerId, BigDecimal.ZERO, new BigDecimal("200"));
            // 900 outstanding + 200 invoice > 1000 limit
            assertFalse(result.canInvoice());
        }
    }
}
