package com.invoicegenie.ar.adapter.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.UUID;

/**
 * Request/Response logging filter for debugging.
 * Logs all incoming requests and outgoing responses with timing.
 */
@Provider
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class);
    
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_START_TIME = "requestStartTime";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        requestContext.setProperty(REQUEST_ID_HEADER, requestId);
        requestContext.setProperty(REQUEST_START_TIME, System.currentTimeMillis());
        
        String tenantId = requestContext.getHeaderString("X-Tenant-Id");
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();
        
        LOG.debugf("[%s] %s %s [tenant: %s]", requestId, method, path, tenantId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Long startTime = (Long) requestContext.getProperty(REQUEST_START_TIME);
        String requestId = (String) requestContext.getProperty(REQUEST_ID_HEADER);
        
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String method = requestContext.getMethod();
            String path = requestContext.getUriInfo().getPath();
            int status = responseContext.getStatus();
            
            if (status >= 400) {
                LOG.warnf("[%s] %s %s -> %d (%dms)", requestId, method, path, status, duration);
            } else {
                LOG.debugf("[%s] %s %s -> %d (%dms)", requestId, method, path, status, duration);
            }
        }
    }
}
