package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantContextClearFilter")
@ExtendWith(MockitoExtension.class)
class TenantContextClearFilterTest {

    @Mock private ContainerRequestContext requestContext;
    @Mock private ContainerResponseContext responseContext;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("clears TenantContext after response")
    void clearsTenant() throws Exception {
        TenantContext.setCurrentTenant(TenantId.of(UUID.randomUUID()));
        TenantContextClearFilter filter = new TenantContextClearFilter();
        Field dbKind = TenantContextClearFilter.class.getDeclaredField("dbKind");
        dbKind.setAccessible(true);
        dbKind.set(filter, "h2");

        filter.filter(requestContext, responseContext);

        assertThrows(IllegalStateException.class, TenantContext::getCurrentTenant);
    }
}