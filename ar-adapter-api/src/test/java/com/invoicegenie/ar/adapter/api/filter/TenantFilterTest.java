package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TenantFilter")
@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock private ContainerRequestContext requestContext;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TenantFilter filterWithH2() throws Exception {
        TenantFilter filter = new TenantFilter();
        Field dbKind = TenantFilter.class.getDeclaredField("dbKind");
        dbKind.setAccessible(true);
        dbKind.set(filter, "h2");
        return filter;
    }

    @Test
    @DisplayName("sets TenantContext from X-Tenant-Id")
    void setsTenant() throws Exception {
        UUID id = UUID.randomUUID();
        when(requestContext.getHeaderString("X-Tenant-Id")).thenReturn(id.toString());

        filterWithH2().filter(requestContext);

        assertEquals(TenantId.of(id), TenantContext.getCurrentTenant());
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    @DisplayName("aborts when header missing")
    void abortsMissing() throws Exception {
        when(requestContext.getHeaderString("X-Tenant-Id")).thenReturn(null);

        filterWithH2().filter(requestContext);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(400, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("aborts when header blank")
    void abortsBlank() throws Exception {
        when(requestContext.getHeaderString("X-Tenant-Id")).thenReturn("  ");

        filterWithH2().filter(requestContext);

        verify(requestContext).abortWith(any());
    }

    @Test
    @DisplayName("aborts when header not a UUID")
    void abortsInvalidUuid() throws Exception {
        when(requestContext.getHeaderString("X-Tenant-Id")).thenReturn("not-a-uuid");

        filterWithH2().filter(requestContext);

        verify(requestContext).abortWith(any());
    }
}