package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound port: dunning scan that emits DunningNotice events (STORY-015).
 */
public interface DunningUseCase {

    /**
     * Scans open invoices for a tenant and emits dunning notices per policy.
     *
     * @return summary of notices emitted
     */
    DunningRunResult runForTenant(TenantId tenantId, LocalDate asOf);

    record DunningRunResult(int invoicesScanned, int noticesEmitted, List<String> invoiceIds) {}
}