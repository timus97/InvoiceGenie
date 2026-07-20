package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.ar.adapter.api.security.SecurityConstants;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthFilter")
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock private ContainerRequestContext requestContext;
    @Mock private UriInfo uriInfo;

    private AuthFilter filter(boolean enabled, String mode, String keys, String jwtSecret) throws Exception {
        AuthFilter f = new AuthFilter();
        set(f, "securityEnabled", enabled);
        set(f, "mode", mode);
        set(f, "apiKeysConfig", keys);
        set(f, "jwtSecret", jwtSecret);
        set(f, "allowOpenApi", false);
        f.init();
        return f;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = AuthFilter.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("no-op when security disabled")
    void disabled() throws Exception {
        filter(false, "api-key", "", "").filter(requestContext);
        verify(requestContext, never()).abortWith(any());
        verify(requestContext, never()).getUriInfo();
    }

    @Test
    @DisplayName("allows health when security enabled")
    void healthPublic() throws Exception {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("q/health");
        filter(true, "api-key", "k:00000000-0000-0000-0000-000000000001", "").filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    @DisplayName("rejects API without key")
    void rejectsMissingKey() throws Exception {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("api/v1/invoices");
        when(requestContext.getHeaderString(SecurityConstants.HEADER_API_KEY)).thenReturn(null);
        when(requestContext.getHeaderString(SecurityConstants.HEADER_AUTHORIZATION)).thenReturn(null);

        filter(true, "api-key", "k:00000000-0000-0000-0000-000000000001", "").filter(requestContext);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("accepts valid API key and sets tenant property")
    void acceptsKey() throws Exception {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/v1/invoices");
        when(requestContext.getHeaderString(SecurityConstants.HEADER_API_KEY)).thenReturn("k");

        filter(true, "api-key", "k:00000000-0000-0000-0000-000000000001", "").filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(requestContext).setProperty(SecurityConstants.AUTH_TENANT_PROPERTY,
                "00000000-0000-0000-0000-000000000001");
    }
}
