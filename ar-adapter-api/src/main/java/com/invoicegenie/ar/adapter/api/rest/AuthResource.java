package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ApiKeyRegistry;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.LoginUserRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Lightweight login for the web console (STORY-003 Phase 2).
 * Issues HS256 JWT for configured users, or validates API key and returns session claims.
 * Public path — excluded from AuthFilter / TenantFilter.
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Web login / session")
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @ConfigProperty(name = "invoicegenie.security.api-keys", defaultValue = "none")
    String apiKeysConfig;

    @ConfigProperty(name = "invoicegenie.security.jwt.secret", defaultValue = "none")
    String jwtSecret;

    @ConfigProperty(name = "invoicegenie.security.users", defaultValue = "none")
    String usersConfig;

    @ConfigProperty(name = "invoicegenie.security.jwt.ttl-seconds", defaultValue = "28800")
    long jwtTtlSeconds;

    private ApiKeyRegistry apiKeyRegistry = new ApiKeyRegistry("");
    private LoginUserRegistry loginUserRegistry = new LoginUserRegistry("");

    @PostConstruct
    void init() {
        apiKeyRegistry = new ApiKeyRegistry(normalizeConfig(apiKeysConfig));
        loginUserRegistry = new LoginUserRegistry(normalizeConfig(usersConfig));
        LOG.infof("Auth login ready (users=%d, apiKeys=%d, jwtConfigured=%s)",
                loginUserRegistry.size(), apiKeyRegistry.size(), !normalizeConfig(jwtSecret).isEmpty());
    }

    @POST
    @Path("/login")
    @Operation(summary = "Login with username/password or API key; returns session + optional JWT")
    public Response login(LoginRequestDto dto) {
        if (dto == null) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "Body required")).build();
        }

        // Prefer username/password when provided
        if (dto.username() != null && !dto.username().isBlank()) {
            if (dto.password() == null) {
                return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "password required")).build();
            }
            Optional<LoginUserRegistry.LoginUser> user =
                    loginUserRegistry.authenticate(dto.username(), dto.password());
            if (user.isEmpty()) {
                return Response.status(401)
                        .entity(new ErrorResponse("UNAUTHORIZED", "Invalid username or password"))
                        .build();
            }
            LoginUserRegistry.LoginUser u = user.get();
            String secret = normalizeConfig(jwtSecret);
            if (secret.isEmpty()) {
                return Response.status(503).entity(new ErrorResponse("JWT_NOT_CONFIGURED",
                        "Username login requires invoicegenie.security.jwt.secret")).build();
            }
            String token = ApiKeyRegistry.signHs256Jwt(secret, u.tenantId(), u.username(), u.roles(), jwtTtlSeconds);
            return Response.ok(new LoginResponseDto(
                    token,
                    "Bearer",
                    u.tenantId(),
                    u.username(),
                    "jwt",
                    new ArrayList<>(u.roles()),
                    jwtTtlSeconds
            )).build();
        }

        // API key login — tenant derived from key, not free-form client input
        if (dto.apiKey() != null && !dto.apiKey().isBlank()) {
            Optional<String> tenant = apiKeyRegistry.resolveTenant(dto.apiKey().trim());
            if (tenant.isEmpty()) {
                return Response.status(401)
                        .entity(new ErrorResponse("UNAUTHORIZED", "Invalid API key"))
                        .build();
            }
            Set<String> roles = new LinkedHashSet<>(ArRoles.M2M_ROLES);
            String secret = normalizeConfig(jwtSecret);
            String token = null;
            Long expiresIn = null;
            String tokenType = null;
            String method = "api-key";
            if (!secret.isEmpty()) {
                token = ApiKeyRegistry.signHs256Jwt(secret, tenant.get(), "api-key", roles, jwtTtlSeconds);
                tokenType = "Bearer";
                expiresIn = jwtTtlSeconds;
                method = "jwt";
            }
            return Response.ok(new LoginResponseDto(
                    token,
                    tokenType,
                    tenant.get(),
                    "api-key",
                    method,
                    new ArrayList<>(roles),
                    expiresIn
            )).build();
        }

        return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR",
                "Provide username+password or apiKey")).build();
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

    public record LoginRequestDto(String username, String password, String apiKey) {}

    public record LoginResponseDto(
            String accessToken,
            String tokenType,
            String tenantId,
            String subject,
            String method,
            List<String> roles,
            Long expiresInSeconds
    ) {}
}
