package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.AgingUseCase;
import com.invoicegenie.ar.domain.model.invoice.AgingBucket;
import com.invoicegenie.ar.domain.model.invoice.AgingReport;
import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST adapter: Aging reports and early payment discount.
 */
@Path("/api/v1/aging")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Aging", description = "Aging reports and early payment discount")
public class AgingResource {

    private final AgingUseCase agingUseCase;

    public AgingResource(AgingUseCase agingUseCase) {
        this.agingUseCase = agingUseCase;
    }

    @GET
    @Operation(summary = "Generate aging report")
    public Response getAgingReport(@QueryParam("asOfDate") String asOfDateStr) {
        var tenantId = TenantContext.getCurrentTenant();
        LocalDate asOfDate = asOfDateStr != null ? LocalDate.parse(asOfDateStr) : LocalDate.now();

        AgingService.AgingReportResult result = agingUseCase.getReport(tenantId, asOfDate);
        if (!result.success() || result.summary() == null) {
            return Response.status(500)
                    .entity(new ErrorResponse("AGING_ERROR",
                            result.message() != null ? result.message() : "Failed to generate aging report"))
                    .build();
        }

        AgingReport.AgingSummary summary = result.summary();
        List<InvoiceDetailDto> invoices = result.report() != null
                ? result.report().getInvoiceDetails().stream()
                    .map(d -> new InvoiceDetailDto(
                            d.invoiceId().toString(),
                            d.invoiceNumber(),
                            d.amountDue().getAmount(),
                            d.dueDate().toString(),
                            d.daysOverdue(),
                            d.bucket().name()))
                    .collect(Collectors.toList())
                : List.of();

        return Response.ok(new AgingReportDto(
                summary.asOfDate().toString(),
                summary.currencyCode(),
                summary.grandTotal(),
                summary.total0To30(),
                summary.total31To60(),
                summary.total61To90(),
                summary.total90Plus(),
                summary.totalCount(),
                summary.count0To30(),
                summary.count31To60(),
                summary.count61To90(),
                summary.count90Plus(),
                invoices
        )).build();
    }

    @GET
    @Path("/buckets")
    @Operation(summary = "Get aging bucket labels")
    public Response getBuckets() {
        List<BucketDto> buckets = new ArrayList<>();
        for (AgingBucket bucket : AgingBucket.values()) {
            buckets.add(new BucketDto(bucket.name(), bucket.getLabel(), bucket.isEarlyPaymentEligible()));
        }
        return Response.ok(buckets).build();
    }

    @POST
    @Path("/discount/calculate")
    @Operation(summary = "Calculate early payment discount")
    public Response calculateDiscount(DiscountRequestDto dto) {
        try {
            Money amountDue = Money.of(dto.amount().toString(), dto.currencyCode() != null ? dto.currencyCode() : "USD");
            LocalDate dueDate = dto.dueDate() != null ? dto.dueDate() : LocalDate.now().plusDays(30);
            LocalDate today = dto.today() != null ? dto.today() : LocalDate.now();

            var result = agingUseCase.calculateEarlyPaymentDiscount(
                    UUID.randomUUID(), amountDue, dueDate, today);

            return Response.ok(new DiscountResponseDto(
                    result.invoiceId().toString(),
                    result.originalAmount().getAmount(),
                    result.discountAmount().getAmount(),
                    result.discountedAmount().getAmount(),
                    result.eligible(),
                    result.reason(),
                    AgingService.EARLY_PAYMENT_DISCOUNT_RATE.toString()
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(new ErrorResponse("ERROR", e.getMessage())).build();
        }
    }

    public record AgingReportDto(String asOfDate, String currencyCode, BigDecimal grandTotal,
            BigDecimal total0To30, BigDecimal total31To60, BigDecimal total61To90, BigDecimal total90Plus,
            int totalCount, int count0To30, int count31To60, int count61To90, int count90Plus,
            List<InvoiceDetailDto> invoices) {}

    public record InvoiceDetailDto(String invoiceId, String invoiceNumber, BigDecimal amountDue,
            String dueDate, int daysOverdue, String bucket) {}

    public record BucketDto(String code, String label, boolean earlyPaymentEligible) {}

    public record DiscountRequestDto(BigDecimal amount, String currencyCode, LocalDate dueDate, LocalDate today) {}

    public record DiscountResponseDto(String invoiceId, BigDecimal originalAmount, BigDecimal discountAmount,
            BigDecimal discountedAmount, boolean eligible, String reason, String discountRate) {}
}
