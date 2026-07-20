package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ApiKeyRegistry;
import com.invoicegenie.ar.adapter.api.security.SecurityConstants;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Optional authentication gate for production.
 *
 * <p>When {@code invoicegenie.security.enabled=true}:
 * <ul>
 *   <li>{@code api-key} mode - require {@code X-API-Key} (or Bearer token)
 *       mapped via {@code invoicegenie.security.api-keys=key:tenantUuid,...}</li>
 *   <li>{@code jwt} mode - require HS256 JWT (Bearer) with {@code tenant_id} claim,
 *       signed with {@code invoicegenie.security.jwt.secret}</li>
 * </ul>
 *
 * <p>Public paths (health) always bypass. OpenAPI/Swagger bypass only when
 * {@code invoicegenie.security.allow-openapi=true}.
 */
@Provider
@jakarta.annotation.Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AuthFilter.class);

    private static final Set<String> ALWAYS_PUBLIC_PREFIXES = Set.of(
            "/q/health",
            "/q/health/live",
            "/q/health/ready"
    );

    @ConfigProperty(name = "invoicegenie.security.enabled", defaultValue = "false")
    boolean securityEnabled;

    @ConfigProperty(name = "invoicegenie.security.mode", defaultValue = "api-key")
    String mode;

    /** Use "none" instead of empty string — SmallRye rejects empty defaults as null. */
    @ConfigProperty(name = "invoicegenie.security.api-keys", defaultValue = "none")
    String apiKeysConfig;

    @ConfigProperty(name = "invoicegenie.security.jwt.secret", defaultValue = "none")
    String jwtSecret;

    @ConfigProperty(name = "invoicegenie.security.allow-openapi", defaultValue = "true")
    boolean allowOpenApi;

    private ApiKeyRegistry apiKeyRegistry = new ApiKeyRegistry("");

    @PostConstruct
    void init() {
        apiKeyRegistry = new ApiKeyRegistry(normalizeConfig(apiKeysConfig));
        if (securityEnabled) {
            LOG.infof("API security enabled (mode=%s, apiKeys=%d)", mode, apiKeyRegistry.size());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!securityEnabled) {
            return;
        }
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (isPublic(path)) {
            return;
        }

        String modeNorm = mode == null ? "api-key" : mode.trim().toLowerCase(Locale.ROOT);
        Optional<AuthResult> auth = switch (modeNorm) {
            case "jwt" -> authenticateJwt(requestContext);
            case "api-key" -> authenticateApiKey(requestContext);
            default -> {
                LOG.warnf("Unknown security mode '%s' - rejecting request", mode);
                yield Optional.empty();
            }
        };

        if (auth.isEmpty()) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("UNAUTHORIZED", "Valid API credentials are required"))
                    .build());
            return;
        }

        AuthResult result = auth.get();
        requestContext.setProperty(SecurityConstants.AUTH_TENANT_PROPERTY, result.tenantId());
        requestContext.setProperty(SecurityConstants.AUTH_SUBJECT_PROPERTY, result.subject());
        requestContext.setProperty(SecurityConstants.AUTH_METHOD_PROPERTY, result.method());
    }

    private Optional<AuthResult> authenticateApiKey(ContainerRequestContext ctx) {
        String key = ctx.getHeaderString(SecurityConstants.HEADER_API_KEY);
        if (key == null || key.isBlank()) {
            String auth = ctx.getHeaderString(SecurityConstants.HEADER_AUTHORIZATION);
            if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
                key = auth.substring(7).trim();
            }
        }
        return apiKeyRegistry.resolveTenant(key)
                .map(tenant -> new AuthResult(tenant, "api-key", "api-key"));
    }

    private Optional<AuthResult> authenticateJwt(ContainerRequestContext ctx) {
        String auth = ctx.getHeaderString(SecurityConstants.HEADER_AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            return Optional.empty();
        }
        return ApiKeyRegistry.validateHs256Jwt(auth, normalizeConfig(jwtSecret))
                .map(c -> new AuthResult(c.tenantId(), c.subject(), "jwt"));
    }

    private boolean isPublic(String path) {
        for (String prefix : ALWAYS_PUBLIC_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        if (allowOpenApi && (path.startsWith("/q/swagger")
                || path.startsWith("/q/openapi")
                || path.startsWith("/q/dev"))) {
            return true;
        }
        return false;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String normalizeConfig(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.isEmpty() || "none".equalsIgnoreCase(v) || "-".equals(v)) {
            return "";
        }
        return v;
    }

    private record AuthResult(String tenantId, String subject, String method) {}
}