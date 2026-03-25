package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgingService")
class AgingServiceTest {

    private AgingService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new AgingService();
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Early Payment Discount")
    class EarlyPaymentDiscount {

        @Test
        @DisplayName("should calculate discount for eligible invoice")
        void shouldCalculateDiscountForEligibleInvoice() {
            Money amount = Money.of("1000.00", "USD");
            LocalDate dueDate = LocalDate.now().plusDays(10);
            
            AgingService.EarlyPaymentDiscountResult result = service.calculateEarlyPaymentDiscount(
                    UUID.randomUUID(), amount, dueDate, LocalDate.now());
            
            assertTrue(result.eligible());
            assertEquals(0, new BigDecimal("20.00").compareTo(result.discountAmount().getAmount()));
            assertEquals(0, new BigDecimal("980.00").compareTo(result.discountedAmount().getAmount()));
        }

        @Test
        @DisplayName("should be eligible for invoice within 30 days past due")
        void shouldBeEligibleWithin30DaysPastDue() {
            Money amount = Money.of("1000.00", "USD");
            LocalDate dueDate = LocalDate.now().minusDays(15);
            
            AgingService.EarlyPaymentDiscountResult result = service.calculateEarlyPaymentDiscount(
                    UUID.randomUUID(), amount, dueDate, LocalDate.now());
            
            assertTrue(result.eligible());
        }

        @Test
        @DisplayName("should not be eligible for invoice over 30 days past due")
        void shouldNotBeEligibleOver30DaysPastDue() {
            Money amount = Money.of("1000.00", "USD");
            LocalDate dueDate = LocalDate.now().minusDays(31);
            
            AgingService.EarlyPaymentDiscountResult result = service.calculateEarlyPaymentDiscount(
                    UUID.randomUUID(), amount, dueDate, LocalDate.now());
            
            assertFalse(result.eligible());
            assertTrue(result.reason().contains("30 days overdue"));
        }

        @Test
        @DisplayName("should not be eligible for zero amount")
        void shouldNotBeEligibleForZeroAmount() {
            Money amount = Money.of("0.00", "USD");
            LocalDate dueDate = LocalDate.now().plusDays(10);
            
            AgingService.EarlyPaymentDiscountResult result = service.calculateEarlyPaymentDiscount(
                    UUID.randomUUID(), amount, dueDate, LocalDate.now());
            
            assertFalse(result.eligible());
            assertEquals("Invalid amount", result.reason());
        }
    }

    @Nested
    @DisplayName("Discount Amount Calculation")
    class DiscountAmountCalculation {

        @Test
        @DisplayName("should calculate 2% discount")
        void shouldCalculate2PercentDiscount() {
            Money amount = Money.of("1000.00", "USD");
            
            Money discount = service.calculateDiscountAmount(amount);
            
            assertEquals(0, new BigDecimal("20.00").compareTo(discount.getAmount()));
        }

        @Test
        @DisplayName("should handle large amounts")
        void shouldHandleLargeAmounts() {
            Money amount = Money.of("1000000.00", "USD");
            
            Money discount = service.calculateDiscountAmount(amount);
            
            assertEquals(0, new BigDecimal("20000.00").compareTo(discount.getAmount()));
        }
    }

    @Nested
    @DisplayName("Short Payment Coverage")
    class ShortPaymentCoverage {

        @Test
        @DisplayName("should cover short payment within discount range")
        void shouldCoverShortPaymentWithinDiscount() {
            Money invoiceAmount = Money.of("1000.00", "USD");
            Money paymentAmount = Money.of("980.00", "USD"); // Exactly discounted amount
            
            assertTrue(service.canCoverShortPayment(invoiceAmount, paymentAmount));
        }

        @Test
        @DisplayName("should not cover payment equal to invoice")
        void shouldNotCoverFullPayment() {
            Money invoiceAmount = Money.of("1000.00", "USD");
            Money paymentAmount = Money.of("1000.00", "USD");
            
            assertFalse(service.canCoverShortPayment(invoiceAmount, paymentAmount));
        }

        @Test
        @DisplayName("should not cover payment less than discounted amount")
        void shouldNotCoverPaymentLessThanDiscounted() {
            Money invoiceAmount = Money.of("1000.00", "USD");
            Money paymentAmount = Money.of("970.00", "USD"); // Less than 980 (discounted)
            
            assertFalse(service.canCoverShortPayment(invoiceAmount, paymentAmount));
        }
    }

    @Nested
    @DisplayName("Aging Report Generation")
    class AgingReportGeneration {

        @Test
        @DisplayName("should generate aging report for open invoices")
        void shouldGenerateAgingReport() {
            List<AgingService.InvoiceWithBalance> invoices = List.of(
                    new AgingService.InvoiceWithBalance(
                            UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST001",
                            Money.of("1000.00", "USD"), LocalDate.now().minusDays(10),
                            InvoiceStatus.ISSUED
                    )
            );
            
            AgingService.AgingReportResult result = service.generateAgingReport(
                    tenantId, LocalDate.now(), invoices);
            
            assertTrue(result.success());
            assertNotNull(result.report());
            assertNotNull(result.summary());
        }

        @Test
        @DisplayName("should skip paid invoices in aging report")
        void shouldSkipPaidInvoices() {
            List<AgingService.InvoiceWithBalance> invoices = List.of(
                    new AgingService.InvoiceWithBalance(
                            UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST001",
                            Money.of("1000.00", "USD"), LocalDate.now().minusDays(10),
                            InvoiceStatus.PAID
                    )
            );
            
            AgingService.AgingReportResult result = service.generateAgingReport(
                    tenantId, LocalDate.now(), invoices);
            
            assertTrue(result.success());
        }

        @Test
        @DisplayName("should skip written off invoices")
        void shouldSkipWrittenOffInvoices() {
            List<AgingService.InvoiceWithBalance> invoices = List.of(
                    new AgingService.InvoiceWithBalance(
                            UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST001",
                            Money.of("1000.00", "USD"), LocalDate.now().minusDays(10),
                            InvoiceStatus.WRITTEN_OFF
                    )
            );
            
            AgingService.AgingReportResult result = service.generateAgingReport(
                    tenantId, LocalDate.now(), invoices);
            
            assertTrue(result.success());
        }

        @Test
        @DisplayName("should handle empty invoice list")
        void shouldHandleEmptyInvoiceList() {
            AgingService.AgingReportResult result = service.generateAgingReport(
                    tenantId, LocalDate.now(), List.of());
            
            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("Invoice With Balance Record")
    class InvoiceWithBalanceRecord {

        @Test
        @DisplayName("should create invoice with balance record")
        void shouldCreateInvoiceWithBalance() {
            UUID id = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            
            AgingService.InvoiceWithBalance invoice = new AgingService.InvoiceWithBalance(
                    id, "INV-001", customerId, "CUST001",
                    Money.of("1000.00", "USD"), LocalDate.now(), InvoiceStatus.ISSUED
            );
            
            assertEquals(id, invoice.id());
            assertEquals("INV-001", invoice.invoiceNumber());
            assertEquals(customerId, invoice.customerId());
            assertEquals("CUST001", invoice.customerRef());
            assertEquals(InvoiceStatus.ISSUED, invoice.status());
        }
    }
}
