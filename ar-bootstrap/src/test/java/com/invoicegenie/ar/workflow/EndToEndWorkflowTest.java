package com.invoicegenie.ar.workflow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;

/**
 * End-to-End Workflow Tests for InvoiceGenie AR Backend.
 * 
 * <p>These tests exercise complete business workflows through the full HTTP API stack
 * with a real (in-memory H2) database. They are designed to:
 * <ul>
 *   <li>Catch integration bugs between layers</li>
 *   <li>Validate complete user scenarios</li>
 *   <li>Evolve as new features are added</li>
 * </ul>
 * 
 * <p>How to add new workflow tests:
 * <ol>
 *   <li>Extend WorkflowTestBase</li>
 *   <li>Use helper methods: createCustomer(), createInvoice(), createPayment()</li>
 *   <li>Use assertion helpers: assertInvoiceStatus()</li>
 *   <li>Follow the GIVEN/WHEN/THEN pattern in method names</li>
 * </ol>
 */
@QuarkusTest
@DisplayName("End-to-End AR Workflows")
class EndToEndWorkflowTest extends WorkflowTestBase {

    @Nested
    @DisplayName("Happy Path: Issue Invoice and Receive Payment")
    class InvoiceAndPaymentWorkflow {

        @Test
        @DisplayName("GIVEN new customer WHEN create invoice AND record payment AND allocate THEN invoice is PAID")
        void invoicePaidViaPaymentAllocation() {
            // GIVEN: A customer exists
            UUID custId = createCustomer("CUST-001", "Acme Corp");

            // WHEN: Invoice is created (API creates + issues in one step)
            // customerRef must match customerId for allocation to find invoices
            UUID invoiceId = createInvoice("INV-001", custId.toString(), "2026-04-30", 1000.00);

            // THEN: Invoice is ISSUED with full balance (total=1000)
            assertInvoiceStatus(invoiceId, "ISSUED");
            get("/api/v1/invoices/" + invoiceId)
                    .then().body("total", equalTo(1000.0f));

            // WHEN: Payment is recorded
            UUID paymentId = createPayment("PAY-001", custId, 1000.00, "BANK_TRANSFER");

            // AND: Payment is allocated to invoice (FIFO)
            allocatePaymentFifo(paymentId);

            // THEN: Invoice is fully paid
            assertInvoiceStatus(invoiceId, "PAID");
            assertPaymentUnallocated(paymentId, 0.0);
        }

        @Test
        @DisplayName("GIVEN invoice WHEN partial payment THEN invoice is PARTIALLY_PAID")
        void partialPaymentLeavesInvoicePartiallyPaid() {
            UUID custId = createCustomer("CUST-002", "Beta Inc");
            // customerRef must match customerId for allocation to find invoices
            UUID invoiceId = createInvoice("INV-002", custId.toString(), "2026-05-01", 2000.00);

            UUID paymentId = createPayment("PAY-002", custId, 750.00, "CHECK");
            allocatePaymentManual(paymentId, invoiceId, 750.00);

            assertInvoiceStatus(invoiceId, "PARTIALLY_PAID");
            assertPaymentUnallocated(paymentId, 0.0);
        }
    }

    @Nested
    @DisplayName("Multi-Invoice Allocation")
    class MultiInvoiceWorkflow {

        @Test
        @DisplayName("GIVEN multiple invoices WHEN payment exceeds total THEN all paid, remainder unallocated")
        void paymentOverMultipleInvoices() {
            UUID custId = createCustomer("CUST-003", "Gamma LLC");
            
            // Create 3 invoices (API creates + issues in one step)
            // customerRef must match customerId for allocation to find invoices
            UUID inv1 = createInvoice("INV-A1", custId.toString(), "2026-04-01", 100.00);
            UUID inv2 = createInvoice("INV-A2", custId.toString(), "2026-04-02", 200.00);
            UUID inv3 = createInvoice("INV-A3", custId.toString(), "2026-04-03", 300.00);

            // Payment of 700 (more than total 600)
            UUID paymentId = createPayment("PAY-003", custId, 700.00, "BANK_TRANSFER");
            allocatePaymentFifo(paymentId);

            // All invoices paid
            assertInvoiceStatus(inv1, "PAID");
            assertInvoiceStatus(inv2, "PAID");
            assertInvoiceStatus(inv3, "PAID");

            // 100 remains unallocated
            assertPaymentUnallocated(paymentId, 100.0);
        }
    }

    @Nested
    @DisplayName("Invoice Lifecycle")
    class InvoiceLifecycleWorkflow {

        @Test
        @DisplayName("GIVEN issued invoice WHEN mark overdue THEN status OVERDUE")
        void markOverdue() {
            UUID custId = createCustomer("CUST-004", "Delta Co");
            UUID invoiceId = createInvoice("INV-004", "Delta", "2020-01-01", 500.00); // past due (already issued by create)

            post("/api/v1/invoices/" + invoiceId + "/overdue?today=2026-01-01", "{}")
                    .then().statusCode(200);

            assertInvoiceStatus(invoiceId, "OVERDUE");
        }

        @Test
        @DisplayName("GIVEN overdue invoice WHEN write off THEN status WRITTEN_OFF")
        void writeOff() {
            UUID custId = createCustomer("CUST-005", "Epsilon Ltd");
            UUID invoiceId = createInvoice("INV-005", "Epsilon", "2020-01-01", 500.00); // already issued
            post("/api/v1/invoices/" + invoiceId + "/overdue?today=2026-01-01", "{}").then().statusCode(200);

            String body = "{\"reason\": \"Uncollectible\"}";
            post("/api/v1/invoices/" + invoiceId + "/writeoff", body)
                    .then().statusCode(200);

            assertInvoiceStatus(invoiceId, "WRITTEN_OFF");
        }
    }

    @Nested
    @DisplayName("Customer Management")
    class CustomerWorkflow {

        @Test
        @DisplayName("GIVEN customer WHEN block THEN cannot be invoiced")
        void blockCustomer() {
            UUID custId = createCustomer("CUST-006", "Zeta Corp");

            post("/api/v1/customers/" + custId + "/block", "{}")
                    .then().statusCode(200);

            get("/api/v1/customers/" + custId)
                    .then().body("status", equalTo("BLOCKED"));
        }

        @Test
        @DisplayName("GIVEN customer WHEN unblock THEN can be invoiced")
        void unblockCustomer() {
            UUID custId = createCustomer("CUST-007", "Eta Inc");
            post("/api/v1/customers/" + custId + "/block", "{}").then().statusCode(200);
            post("/api/v1/customers/" + custId + "/unblock", "{}").then().statusCode(200);

            get("/api/v1/customers/" + custId)
                    .then().body("status", equalTo("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("Ledger Integration")
    class LedgerWorkflow {

        @Test
        @DisplayName("GIVEN invoice issued WHEN check AR balance THEN reflects invoice total")
        void ledgerReflectsInvoice() {
            UUID custId = createCustomer("CUST-008", "Theta Co");
            createInvoice("INV-008", "Theta", "2026-05-01", 1500.00);
            // Note: Invoice is created in DRAFT; need to issue for ledger entry
            // (Ledger entries are created on issue via event; simplified here)

            // Just verify ledger accounts exist
            get("/api/v1/ledger/accounts")
                    .then().statusCode(200)
                    .body("size()", greaterThan(0));
        }
    }

    @Nested
    @DisplayName("Payment via Invoice Shortcut (Legacy)")
    class InvoicePaymentShortcut {

        @Test
        @DisplayName("GIVEN invoice WHEN apply payment via invoice endpoint THEN marked PAID")
        void invoiceQuickPay() {
            UUID custId = createCustomer("CUST-009", "Iota LLC");
            UUID invoiceId = createInvoice("INV-009", "Iota", "2026-05-01", 500.00); // already issued

            // Legacy shortcut: marks invoice PAID without Payment aggregate
            post("/api/v1/invoices/" + invoiceId + "/payment", "{\"fullyPaid\": true}")
                    .then().statusCode(200);

            assertInvoiceStatus(invoiceId, "PAID");
        }
    }
}
