package com.invoicegenie.ar.adapter.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RequestLoggingFilter")
@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock private ContainerRequestContext requestContext;
    @Mock private ContainerResponseContext responseContext;
    @Mock private UriInfo uriInfo;

    @Test
    @DisplayName("logs request and response")
    void logs() throws Exception {
        when(requestContext.getHeaderString("X-Tenant-Id")).thenReturn("t");
        when(requestContext.getMethod()).thenReturn("GET");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("api/v1/invoices");
        when(requestContext.getProperty("requestStartTime")).thenReturn(System.currentTimeMillis() - 5);
        when(requestContext.getProperty("X-Request-Id")).thenReturn("abcd1234");
        when(responseContext.getStatus()).thenReturn(200);

        RequestLoggingFilter filter = new RequestLoggingFilter();
        filter.filter(requestContext);
        filter.filter(requestContext, responseContext);

        verify(requestContext, atLeastOnce()).setProperty(eq("X-Request-Id"), anyString());
        verify(requestContext, atLeastOnce()).setProperty(eq("requestStartTime"), anyLong());
    }
}