package com.invoicegenie.ar.workflow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Base class for workflow integration tests.
 * 
 * <p>Provides:
 * <ul>
 *   <li>Common REST client setup (RestAssured)</li>
 *   <li>Tenant ID management</li>
 *   <li>Helper methods for common API calls</li>
 *   <li>Assertion helpers</li>
 * </ul>
 * 
 * <p>Usage: Extend this class and write @Test methods that exercise full workflows.
 */
@QuarkusTest
public abstract class WorkflowTestBase {

    protected UUID tenantId;
    protected UUID customerId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        // RestAssured base URI is set by QuarkusTest to random port
    }

    // ==================== REST Helpers ====================

    protected Response post(String path, String body) {
        return given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", tenantId.toString())
                .body(body)
                .when()
                .post(path);
    }

    protected Response get(String path) {
        return given()
                .header("X-Tenant-Id", tenantId.toString())
                .when()
                .get(path);
    }

    protected Response patch(String path, String body) {
        return given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", tenantId.toString())
                .body(body)
                .when()
                .patch(path);
    }

    protected Response delete(String path) {
        return given()
                .header("X-Tenant-Id", tenantId.toString())
                .when()
                .delete(path);
    }

    // ==================== Workflow Helpers ====================

    /**
     * Generate a unique suffix based on tenantId to avoid collisions across tests.
     * Uses first 8 chars of tenant UUID for brevity.
     */
    private String uniqueSuffix() {
        return tenantId.toString().substring(0, 8);
    }

    protected UUID createCustomer(String code, String legalName) {
        // Append unique suffix to avoid 409 conflicts across tests sharing H2 DB
        String uniqueCode = code + "-" + uniqueSuffix();
        String body = String.format("""
            {"customerCode": "%s", "legalName": "%s", "currency": "USD"}
            """, uniqueCode, legalName);
        
        Response resp = post("/api/v1/customers", body);
        resp.then().statusCode(201);
        return UUID.fromString(resp.jsonPath().getString("id"));
    }

    protected UUID createInvoice(String invoiceNumber, String customerRef, String dueDate, double amount) {
        String uniqueNumber = invoiceNumber + "-" + uniqueSuffix();
        String body = String.format("""
            {
              "invoiceNumber": "%s",
              "customerRef": "%s",
              "currencyCode": "USD",
              "dueDate": "%s",
              "lines": [{"description": "Service", "amount": %s}]
            }
            """, uniqueNumber, customerRef, dueDate, amount);
        
        Response resp = post("/api/v1/invoices", body);
        resp.then().statusCode(201);
        return UUID.fromString(resp.jsonPath().getString("id"));
    }

    protected UUID createPayment(String paymentNumber, UUID custId, double amount, String method) {
        String uniqueNumber = paymentNumber + "-" + uniqueSuffix();
        String body = String.format("""
            {
              "paymentNumber": "%s",
              "customerId": "%s",
              "amount": %s,
              "currencyCode": "USD",
              "paymentDate": "2026-03-28",
              "method": "%s",
              "reference": "TEST-REF"
            }
            """, uniqueNumber, custId, amount, method);
        
        Response resp = post("/api/v1/payments", body);
        resp.then().statusCode(201);
        return UUID.fromString(resp.jsonPath().getString("id"));
    }

    protected void allocatePaymentFifo(UUID paymentId) {
        String body = String.format("{\"allocatedBy\": \"%s\"}", UUID.randomUUID());
        post("/api/v1/payments/" + paymentId + "/allocate/fifo", body)
                .then().statusCode(200);
    }

    protected void allocatePaymentManual(UUID paymentId, UUID invoiceId, double amount) {
        String body = String.format(
            "{\"allocatedBy\": \"%s\", \"allocations\": [{\"invoiceId\": \"%s\", \"amount\": %s}]}",
            UUID.randomUUID(), invoiceId, amount);
        post("/api/v1/payments/" + paymentId + "/allocate/manual", body)
                .then().statusCode(200);
    }

    protected String getInvoiceStatus(UUID invoiceId) {
        return get("/api/v1/invoices/" + invoiceId)
                .then().statusCode(200)
                .extract().path("status");
    }

    protected double getInvoiceBalance(UUID invoiceId) {
        return get("/api/v1/invoices/" + invoiceId)
                .then().statusCode(200)
                .extract().path("amountDue");
    }

    // ==================== Assertion Helpers ====================

    protected void assertInvoiceStatus(UUID invoiceId, String expectedStatus) {
        get("/api/v1/invoices/" + invoiceId)
                .then().statusCode(200)
                .body("status", equalTo(expectedStatus));
    }

    protected void assertPaymentUnallocated(UUID paymentId, double expectedUnallocated) {
        get("/api/v1/payments/" + paymentId + "/allocations")
                .then().statusCode(200)
                .body("remainingUnallocated", equalTo((float) expectedUnallocated));
    }
}
