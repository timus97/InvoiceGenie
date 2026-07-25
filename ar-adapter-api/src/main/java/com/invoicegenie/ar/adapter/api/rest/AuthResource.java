package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.AuthService;
import com.invoicegenie.ar.adapter.api.security.SecurityConstants;
import com.invoicegenie.ar.adapter.persistence.entity.AppUserEntity;
import com.invoicegenie.ar.adapter.persistence.repository.AppUserRepositoryAdapter;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Production auth: email/password + JWT access (15m) + refresh tokens (7d, rotation).
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Login, refresh, logout, session")
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    AppUserRepositoryAdapter users;

    @POST
    @Path("/login")
    @Operation(summary = "Login with email/password (or API key for M2M)")
    public Response login(LoginRequestDto dto, @Context HttpHeaders headers) {
        if (dto == null) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "Body required")).build();
        }
        String ua = headers != null ? headers.getHeaderString("User-Agent") : null;
        String ip = clientIp(headers);

        String email = firstNonBlank(dto.email(), dto.username());
        if (email != null && email.contains("@") && dto.password() != null) {
            try {
                Optional<AuthService.AuthTokens> tokens =
                        authService.loginWithEmail(email, dto.password(), ua, ip);
                if (tokens.isEmpty()) {
                    return Response.status(401)
                            .entity(new ErrorResponse("UNAUTHORIZED", "Invalid email or password"))
                            .build();
                }
                return Response.ok(toResponse(tokens.get())).build();
            } catch (IllegalStateException e) {
                return Response.status(503)
                        .entity(new ErrorResponse("JWT_NOT_CONFIGURED", e.getMessage()))
                        .build();
            }
        }

        if (dto.apiKey() != null && !dto.apiKey().isBlank()) {
            Optional<AuthService.AuthTokens> tokens = authService.loginWithApiKey(dto.apiKey());
            if (tokens.isEmpty()) {
                return Response.status(401)
                        .entity(new ErrorResponse("UNAUTHORIZED", "Invalid API key"))
                        .build();
            }
            return Response.ok(toResponse(tokens.get())).build();
        }

        if (email != null && !email.contains("@")) {
            return Response.status(400)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "Use a full email address to sign in"))
                    .build();
        }

        return Response.status(400)
                .entity(new ErrorResponse("VALIDATION_ERROR", "Provide email+password or apiKey"))
                .build();
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new access JWT")
    public Response refresh(RefreshRequestDto dto, @Context HttpHeaders headers) {
        if (dto == null || dto.refreshToken() == null || dto.refreshToken().isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "refreshToken required"))
                    .build();
        }
        String ua = headers != null ? headers.getHeaderString("User-Agent") : null;
        String ip = clientIp(headers);
        try {
            Optional<AuthService.AuthTokens> tokens =
                    authService.refresh(dto.refreshToken(), ua, ip);
            if (tokens.isEmpty()) {
                return Response.status(401)
                        .entity(new ErrorResponse("UNAUTHORIZED", "Invalid or revoked refresh token"))
                        .build();
            }
            return Response.ok(toResponse(tokens.get())).build();
        } catch (IllegalStateException e) {
            return Response.status(503)
                    .entity(new ErrorResponse("JWT_NOT_CONFIGURED", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Revoke a refresh token")
    public Response logout(RefreshRequestDto dto) {
        if (dto != null && dto.refreshToken() != null) {
            authService.logout(dto.refreshToken());
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/me")
    @Operation(summary = "Current authenticated user profile")
    public Response me(@Context ContainerRequestContext requestContext) {
        Object subject = requestContext.getProperty(SecurityConstants.AUTH_SUBJECT_PROPERTY);
        Object tenant = requestContext.getProperty(SecurityConstants.AUTH_TENANT_PROPERTY);
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) requestContext.getProperty(SecurityConstants.AUTH_ROLES_PROPERTY);
        Object method = requestContext.getProperty(SecurityConstants.AUTH_METHOD_PROPERTY);

        if (subject == null || tenant == null) {
            return Response.status(401)
                    .entity(new ErrorResponse("UNAUTHORIZED", "Not authenticated"))
                    .build();
        }

        String email = subject.toString();
        Optional<AppUserEntity> user = users.findByEmail(email);
        if (user.isPresent()) {
            AppUserEntity u = user.get();
            return Response.ok(new MeResponseDto(
                    u.getId().toString(),
                    u.getEmail(),
                    u.getDisplayName(),
                    u.getTenantId().toString(),
                    u.getStatus(),
                    new ArrayList<>(u.getRoles()),
                    method != null ? method.toString() : "jwt"
            )).build();
        }

        return Response.ok(new MeResponseDto(
                null,
                email,
                email,
                tenant.toString(),
                "ACTIVE",
                roles != null ? new ArrayList<>(roles) : List.of(),
                method != null ? method.toString() : "jwt"
        )).build();
    }

    private static LoginResponseDto toResponse(AuthService.AuthTokens t) {
        return new LoginResponseDto(
                t.accessToken(),
                t.refreshToken(),
                "Bearer",
                t.tenantId() != null ? t.tenantId().toString() : null,
                t.email() != null ? t.email() : t.displayName(),
                t.method(),
                t.roles() != null ? new ArrayList<>(t.roles()) : List.of(),
                t.expiresInSeconds(),
                t.refreshExpiresInSeconds(),
                t.userId() != null ? t.userId().toString() : null,
                t.displayName(),
                t.email()
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static String clientIp(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String xff = headers.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return headers.getHeaderString("X-Real-IP");
    }

    public record LoginRequestDto(String email, String username, String password, String apiKey) {}

    public record RefreshRequestDto(String refreshToken) {}

    public record LoginResponseDto(
            String accessToken,
            String refreshToken,
            String tokenType,
            String tenantId,
            String subject,
            String method,
            List<String> roles,
            Long expiresInSeconds,
            Long refreshExpiresInSeconds,
            String userId,
            String displayName,
            String email
    ) {}

    public record MeResponseDto(
            String userId,
            String email,
            String displayName,
            String tenantId,
            String status,
            List<String> roles,
            String method
    ) {}
}