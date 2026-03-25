package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgingReport")
class AgingReportTest {

    private AgingReport report;

    @BeforeEach
    void setUp() {
        report = new AgingReport(LocalDate.now(), "USD");
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create report with date and currency")
        void shouldCreateWithDateAndCurrency() {
            assertEquals(LocalDate.now(), report.getAsOfDate());
            assertEquals("USD", report.getCurrencyCode());
        }

        @Test
        @DisplayName("should initialize all buckets")
        void shouldInitializeAllBuckets() {
            Map<AgingBucket, AgingReport.AgingBucketSummary> buckets = report.getBucketSummaries();
            
            assertEquals(4, buckets.size());
            assertTrue(buckets.containsKey(AgingBucket.BUCKET_0_30));
            assertTrue(buckets.containsKey(AgingBucket.BUCKET_31_60));
            assertTrue(buckets.containsKey(AgingBucket.BUCKET_61_90));
            assertTrue(buckets.containsKey(AgingBucket.BUCKET_90_PLUS));
        }

        @Test
        @DisplayName("should have empty invoice details initially")
        void shouldHaveEmptyInvoiceDetailsInitially() {
            assertTrue(report.getInvoiceDetails().isEmpty());
        }
    }

    @Nested
    @DisplayName("Add Invoice")
    class AddInvoice {

        @Test
        @DisplayName("should add invoice to correct bucket")
        void shouldAddInvoiceToCorrectBucket() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(10), 10);
            
            List<AgingReport.AgingInvoiceDetail> details = report.getInvoiceDetails();
            
            assertEquals(1, details.size());
            assertEquals(AgingBucket.BUCKET_0_30, details.get(0).bucket());
        }

        @Test
        @DisplayName("should add invoice to 31-60 bucket")
        void shouldAddInvoiceTo31_60Bucket() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(45), 45);
            
            List<AgingReport.AgingInvoiceDetail> details = report.getInvoiceDetails();
            
            assertEquals(1, details.size());
            assertEquals(AgingBucket.BUCKET_31_60, details.get(0).bucket());
        }

        @Test
        @DisplayName("should add invoice to 61-90 bucket")
        void shouldAddInvoiceTo61_90Bucket() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(75), 75);
            
            List<AgingReport.AgingInvoiceDetail> details = report.getInvoiceDetails();
            
            assertEquals(1, details.size());
            assertEquals(AgingBucket.BUCKET_61_90, details.get(0).bucket());
        }

        @Test
        @DisplayName("should add invoice to 90+ bucket")
        void shouldAddInvoiceTo90PlusBucket() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(120), 120);
            
            List<AgingReport.AgingInvoiceDetail> details = report.getInvoiceDetails();
            
            assertEquals(1, details.size());
            assertEquals(AgingBucket.BUCKET_90_PLUS, details.get(0).bucket());
        }

        @Test
        @DisplayName("should mark 0-30 bucket as eligible for early discount")
        void shouldMark0_30BucketAsEligibleForEarlyDiscount() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(10), 10);
            
            assertTrue(report.getInvoiceDetails().get(0).eligibleForEarlyDiscount());
        }

        @Test
        @DisplayName("should not mark other buckets as eligible for early discount")
        void shouldNotMarkOtherBucketsAsEligibleForEarlyDiscount() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(45), 45);
            
            assertFalse(report.getInvoiceDetails().get(0).eligibleForEarlyDiscount());
        }
    }

    @Nested
    @DisplayName("Bucket Summary")
    class BucketSummary {

        @Test
        @DisplayName("should add invoice detail to report")
        void shouldAddInvoiceDetailToReport() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(10), 10);
            
            List<AgingReport.AgingInvoiceDetail> details = report.getInvoiceDetails();
            
            assertEquals(1, details.size());
        }

        @Test
        @DisplayName("should add multiple invoice details")
        void shouldAddMultipleInvoiceDetails() {
            report.addInvoice(
                    UUID.randomUUID(), "INV-001", UUID.randomUUID(), "CUST-001",
                    Money.of("1000.00", "USD"), LocalDate.now().minusDays(10), 10);
            report.addInvoice(
                    UUID.randomUUID(), "INV-002", UUID.randomUUID(), "CUST-002",
                    Money.of("500.00", "USD"), LocalDate.now().minusDays(20), 20);
            
            List<AgingReport.AgingInvoiceDetail> details = report.getInvoiceDetails();
            
            assertEquals(2, details.size());
        }
        
        @Test
        @DisplayName("should create bucket summary with correct bucket")
        void shouldCreateBucketSummaryWithCorrectBucket() {
            AgingReport.AgingBucketSummary summary = new AgingReport.AgingBucketSummary(AgingBucket.BUCKET_0_30);
            
            assertEquals(AgingBucket.BUCKET_0_30, summary.bucket());
            assertEquals(0, summary.totalAmount().compareTo(BigDecimal.ZERO));
            assertEquals(0, summary.invoiceCount());
        }
        
        @Test
        @DisplayName("should add invoice to bucket summary")
        void shouldAddInvoiceToBucketSummary() {
            AgingReport.AgingBucketSummary summary = new AgingReport.AgingBucketSummary(AgingBucket.BUCKET_0_30);
            
            AgingReport.AgingBucketSummary updated = summary.addInvoice(new BigDecimal("1000.00"));
            
            assertEquals(0, updated.totalAmount().compareTo(new BigDecimal("1000.00")));
            assertEquals(1, updated.invoiceCount());
        }
        
        @Test
        @DisplayName("should get label from bucket summary")
        void shouldGetLabelFromBucketSummary() {
            AgingReport.AgingBucketSummary summary = new AgingReport.AgingBucketSummary(AgingBucket.BUCKET_0_30);
            
            assertEquals("0-30 Days", summary.getLabel());
        }
    }

    @Nested
    @DisplayName("AgingSummary")
    class AgingSummaryTest {

        @Test
        @DisplayName("should create summary with all fields")
        void shouldCreateSummaryWithAllFields() {
            AgingReport.AgingSummary summary = new AgingReport.AgingSummary(
                    LocalDate.now(), "USD",
                    new BigDecimal("1000.00"), new BigDecimal("500.00"),
                    new BigDecimal("250.00"), new BigDecimal("100.00"),
                    new BigDecimal("1850.00"),
                    10, 5, 3, 2, 20);
            
            assertEquals(LocalDate.now(), summary.asOfDate());
            assertEquals("USD", summary.currencyCode());
            assertEquals(0, new BigDecimal("1000.00").compareTo(summary.total0To30()));
            assertEquals(0, new BigDecimal("500.00").compareTo(summary.total31To60()));
            assertEquals(20, summary.totalCount());
        }
        
        @Test
        @DisplayName("should create summary from report")
        void shouldCreateSummaryFromReport() {
            AgingReport.AgingSummary summary = AgingReport.AgingSummary.from(report);
            
            assertEquals(LocalDate.now(), summary.asOfDate());
            assertEquals("USD", summary.currencyCode());
            // Bucket summaries are initialized with zero values
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.grandTotal()));
            assertEquals(0, summary.totalCount());
        }
    }

    @Nested
    @DisplayName("AgingInvoiceDetail")
    class AgingInvoiceDetailTest {

        @Test
        @DisplayName("should create invoice detail with all fields")
        void shouldCreateInvoiceDetailWithAllFields() {
            UUID invoiceId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            Money amount = Money.of("1000.00", "USD");
            LocalDate dueDate = LocalDate.now().minusDays(10);
            
            AgingReport.AgingInvoiceDetail detail = new AgingReport.AgingInvoiceDetail(
                    invoiceId, "INV-001", customerId, "CUST-001",
                    amount, dueDate, 10, AgingBucket.BUCKET_0_30, true);
            
            assertEquals(invoiceId, detail.invoiceId());
            assertEquals("INV-001", detail.invoiceNumber());
            assertEquals(customerId, detail.customerId());
            assertEquals("CUST-001", detail.customerRef());
            assertEquals(amount, detail.amountDue());
            assertEquals(dueDate, detail.dueDate());
            assertEquals(10, detail.daysOverdue());
            assertEquals(AgingBucket.BUCKET_0_30, detail.bucket());
            assertTrue(detail.eligibleForEarlyDiscount());
        }
    }
}
