package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.messaging.OutboxWorker;
import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.ar.domain.model.outbox.OutboxStatus;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST adapter: Outbox monitoring and testing endpoints.
 * Provides visibility into the transactional outbox for debugging and testing.
 */
@Path("/api/v1/outbox")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Outbox", description = "Transactional outbox monitoring")
public class OutboxResource {

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    OutboxWorker outboxWorker;

    /**
     * Get outbox statistics and status.
     */
    @GET
    @Path("/stats")
    @Operation(summary = "Get outbox statistics", description = "Returns counts by status and worker metrics")
    public Response getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("pending", outboxRepository.countByStatus(OutboxStatus.PENDING));
            stats.put("processing", outboxRepository.countByStatus(OutboxStatus.PROCESSING));
            stats.put("published", outboxRepository.countByStatus(OutboxStatus.PUBLISHED));
            stats.put("failed", outboxRepository.countByStatus(OutboxStatus.FAILED));
            stats.put("workerPublishedCount", outboxWorker.getPublishedCount());
            stats.put("workerFailedCount", outboxWorker.getFailedCount());
            stats.put("databaseReady", outboxWorker.isDatabaseReady());
        } catch (Exception e) {
            stats.put("error", e.getMessage());
            stats.put("databaseReady", false);
        }
        
        return Response.ok(stats).build();
    }

    /**
     * List pending outbox entries.
     */
    @GET
    @Path("/pending")
    @Operation(summary = "List pending entries", description = "Returns pending outbox entries")
    public Response getPending(@QueryParam("limit") @DefaultValue("10") int limit) {
        try {
            List<OutboxEntry> entries = outboxRepository.findPending(limit);
            List<Map<String, Object>> result = entries.stream()
                    .map(this::toMap)
                    .toList();
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Trigger the outbox worker manually (for testing).
     */
    @POST
    @Path("/process")
    @Operation(summary = "Process pending entries", description = "Manually trigger outbox processing")
    public Response processPending() {
        try {
            outboxWorker.processPendingEvents();
            return Response.ok(Map.of(
                    "message", "Processing triggered",
                    "publishedCount", outboxWorker.getPublishedCount(),
                    "failedCount", outboxWorker.getFailedCount()
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    private Map<String, Object> toMap(OutboxEntry entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entry.getId());
        map.put("tenantId", entry.getTenantId().getValue());
        map.put("aggregateType", entry.getAggregateType());
        map.put("aggregateId", entry.getAggregateId());
        map.put("eventType", entry.getEventType());
        map.put("status", entry.getStatus().name());
        map.put("createdAt", entry.getCreatedAt().toString());
        map.put("publishedAt", entry.getPublishedAt() != null ? entry.getPublishedAt().toString() : null);
        map.put("retryCount", entry.getRetryCount());
        map.put("lastError", entry.getLastError());
        return map;
    }
}
