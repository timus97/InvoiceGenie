package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.ar.adapter.api.security.ArRoles;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RoleAuthorizationFilter")
@ExtendWith(MockitoExtension.class)
class RoleAuthorizationFilterTest {

    @Mock private ContainerRequestContext requestContext;
    @Mock private UriInfo uriInfo;

    private RoleAuthorizationFilter filter(boolean enabled) throws Exception {
        RoleAuthorizationFilter f = new RoleAuthorizationFilter();
        Field field = RoleAuthorizationFilter.class.getDeclaredField("securityEnabled");
        field.setAccessible(true);
        field.set(f, enabled);
        return f;
    }

    @Test
    @DisplayName("no-op when security disabled")
    void disabled() throws Exception {
        filter(false).filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    @DisplayName("clerk can create payments")
    void clerkCreate() throws Exception {
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/v1/payments");
        when(requestContext.getProperty(SecurityConstants.AUTH_ROLES_PROPERTY))
                .thenReturn(Set.of(ArRoles.AR_CLERK));

        filter(true).filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    @DisplayName("clerk cannot reverse payments")
    void clerkCannotReverse() throws Exception {
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/v1/payments/abc/reverse");
        when(requestContext.getProperty(SecurityConstants.AUTH_ROLES_PROPERTY))
                .thenReturn(Set.of(ArRoles.AR_CLERK));

        filter(true).filter(requestContext);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("controller can reverse")
    void controllerReverse() throws Exception {
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/v1/payments/abc/reverse");
        when(requestContext.getProperty(SecurityConstants.AUTH_ROLES_PROPERTY))
                .thenReturn(Set.of(ArRoles.AR_CONTROLLER));

        filter(true).filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }
}