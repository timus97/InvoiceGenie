package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.RequireRoles;
import com.invoicegenie.ar.adapter.api.security.UserAdminService;
import com.invoicegenie.ar.adapter.persistence.entity.AppUserEntity;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "Admin user management (RBAC)")
@RequireRoles({ArRoles.TENANT_ADMIN})
public class UserAdminResource {

    @Inject
    UserAdminService userAdminService;

    @GET
    @Operation(summary = "List users for the current tenant")
    public Response list() {
        UUID tenantId = TenantContext.getCurrentTenant().getValue();
        List<UserDto> out = userAdminService.listUsers(tenantId).stream()
                .map(UserAdminResource::toDto)
                .toList();
        return Response.ok(out).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a user by id")
    public Response get(@PathParam("id") UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant().getValue();
        return userAdminService.get(tenantId, id)
                .map(u -> Response.ok(toDto(u)).build())
                .orElseGet(() -> Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "User not found")).build());
    }

    @POST
    @Operation(summary = "Create a new user")
    public Response create(CreateUserDto dto) {
        if (dto == null) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "Body required")).build();
        }
        UUID tenantId = TenantContext.getCurrentTenant().getValue();
        Set<String> roles = dto.roles() != null ? new LinkedHashSet<>(dto.roles()) : Set.of();
        UserAdminService.Result result = userAdminService.create(
                tenantId, dto.email(), dto.password(), dto.displayName(), roles);
        if (!result.success()) {
            int status = "CONFLICT".equals(result.code()) ? 409 : 400;
            return Response.status(status)
                    .entity(new ErrorResponse(result.code(), result.message()))
                    .build();
        }
        return Response.status(201).entity(toDto(result.user())).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Update user profile, roles, status, or password")
    public Response update(@PathParam("id") UUID id, UpdateUserDto dto) {
        if (dto == null) {
            return Response.status(400).entity(new ErrorResponse("VALIDATION_ERROR", "Body required")).build();
        }
        UUID tenantId = TenantContext.getCurrentTenant().getValue();
        Set<String> roles = dto.roles() != null ? new LinkedHashSet<>(dto.roles()) : null;
        UserAdminService.Result result = userAdminService.update(
                tenantId, id, dto.displayName(), roles, dto.status(), dto.password());
        if (!result.success()) {
            int status = "NOT_FOUND".equals(result.code()) ? 404 : 400;
            return Response.status(status)
                    .entity(new ErrorResponse(result.code(), result.message()))
                    .build();
        }
        return Response.ok(toDto(result.user())).build();
    }

    private static UserDto toDto(AppUserEntity u) {
        return new UserDto(
                u.getId().toString(),
                u.getEmail(),
                u.getDisplayName(),
                u.getTenantId().toString(),
                u.getStatus(),
                u.getRoles() != null ? new ArrayList<>(u.getRoles()) : List.of(),
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null,
                u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null
        );
    }

    public record CreateUserDto(String email, String password, String displayName, List<String> roles) {}

    public record UpdateUserDto(String displayName, List<String> roles, String status, String password) {}

    public record UserDto(
            String id,
            String email,
            String displayName,
            String tenantId,
            String status,
            List<String> roles,
            String createdAt,
            String lastLoginAt
    ) {}
}