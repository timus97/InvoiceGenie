package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Clears TenantContext after response to avoid leaking into pooled threads.
 */
@Provider
public class TenantContextClearFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        TenantContext.clear();
    }
}
