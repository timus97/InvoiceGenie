import { apiFetch } from "@/lib/api/client";

export type AppUser = {
  id: string;
  email: string;
  displayName: string;
  tenantId: string;
  status: string;
  roles: string[];
  createdAt?: string | null;
  lastLoginAt?: string | null;
};

export type CreateUserRequest = {
  email: string;
  password: string;
  displayName: string;
  roles: string[];
};

export type UpdateUserRequest = {
  displayName?: string;
  roles?: string[];
  status?: string;
  password?: string;
};

export function listUsers(tenantId: string, signal?: AbortSignal) {
  return apiFetch<AppUser[]>("/api/v1/users", { tenantId, signal });
}

export function createUser(tenantId: string, body: CreateUserRequest) {
  return apiFetch<AppUser>("/api/v1/users", {
    method: "POST",
    tenantId,
    body,
  });
}

export function updateUser(
  tenantId: string,
  id: string,
  body: UpdateUserRequest,
) {
  return apiFetch<AppUser>(`/api/v1/users/${id}`, {
    method: "PATCH",
    tenantId,
    body,
  });
}

export const ALL_ROLES = [
  "AR_CLERK",
  "AR_CONTROLLER",
  "AR_AUDITOR",
  "TENANT_ADMIN",
] as const;