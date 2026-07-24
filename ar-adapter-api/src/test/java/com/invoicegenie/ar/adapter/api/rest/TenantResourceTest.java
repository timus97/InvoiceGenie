package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.TenantUseCase;
import com.invoicegenie.ar.domain.model.tenant.Tenant;
import com.invoicegenie.ar.domain.model.tenant.TenantStatus;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantResourceTest {

    @Mock TenantUseCase tenantUseCase;
    TenantResource resource;

    @BeforeEach
    void setUp() {
        resource = new TenantResource(tenantUseCase);
    }

    @Test
    void createSuccess() {
        Tenant t = new Tenant(UUID.randomUUID(), "DEMO2", "Demo 2", "USD",
                TenantStatus.ACTIVE, "{}", Instant.now(), Instant.now());
        when(tenantUseCase.create(any())).thenReturn(t);
        Response res = resource.create(new TenantResource.CreateTenantDto("DEMO2", "Demo 2", "USD", null));
        assertEquals(201, res.getStatus());
    }

    @Test
    void listOk() {
        when(tenantUseCase.list(null)).thenReturn(List.of());
        assertEquals(200, resource.list(null).getStatus());
    }

    @Test
    void getNotFound() {
        UUID id = UUID.randomUUID();
        when(tenantUseCase.get(id)).thenReturn(Optional.empty());
        assertEquals(404, resource.get(id.toString()).getStatus());
    }
}