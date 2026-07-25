"use client";

import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useAuth } from "@/components/auth-provider";
import { useTenant } from "@/components/tenant-provider";
import {
  ALL_ROLES,
  createUser,
  listUsers,
  updateUser,
  type AppUser,
} from "@/lib/api/users";

export default function UsersAdminPage() {
  const { hasRole, session } = useAuth();
  const { tenantId } = useTenant();
  const qc = useQueryClient();
  const isAdmin = hasRole("TENANT_ADMIN");

  const users = useQuery({
    queryKey: ["users", tenantId],
    queryFn: ({ signal }) => listUsers(tenantId, signal),
    enabled: isAdmin && !!tenantId,
  });

  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [roles, setRoles] = useState<string[]>(["AR_CLERK"]);

  const createMut = useMutation({
    mutationFn: () =>
      createUser(tenantId, {
        email: email.trim().toLowerCase(),
        password,
        displayName: displayName.trim(),
        roles,
      }),
    onSuccess: () => {
      toast.success("User created");
      setEmail("");
      setDisplayName("");
      setPassword("");
      setRoles(["AR_CLERK"]);
      void qc.invalidateQueries({ queryKey: ["users", tenantId] });
    },
    onError: (e: Error) => toast.error(e.message || "Create failed"),
  });

  const toggleRole = (role: string) => {
    setRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  };

  const onCreate = (e: FormEvent) => {
    e.preventDefault();
    if (!email.includes("@")) {
      toast.error("Valid email required");
      return;
    }
    if (password.length < 8) {
      toast.error("Password must be at least 8 characters");
      return;
    }
    if (!displayName.trim()) {
      toast.error("Display name required");
      return;
    }
    if (!roles.length) {
      toast.error("Select at least one role");
      return;
    }
    createMut.mutate();
  };

  const roleLabel = useMemo(
    () => (u: AppUser) => u.roles?.join(", ") || "—",
    [],
  );

  if (!isAdmin) {
    return (
      <div>
        <PageHeader
          title="Users"
          description="Only TENANT_ADMIN can manage users."
        />
        <Card>
          <p className="text-sm text-zinc-500">
            Signed in as {session?.email || session?.subject}. You do not have
            permission to manage users.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Users"
        description="Create and manage application users. Roles control API and UI access (RBAC)."
      />

      <div className="grid max-w-5xl gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-sm font-semibold">Create user</h2>
          <form onSubmit={onCreate} className="space-y-3">
            <div>
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                className="mt-1"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="displayName">Display name</Label>
              <Input
                id="displayName"
                className="mt-1"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="password">Temporary password</Label>
              <Input
                id="password"
                type="password"
                className="mt-1"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={8}
                required
              />
              <p className="mt-1 text-xs text-zinc-500">Minimum 8 characters</p>
            </div>
            <div>
              <p className="text-sm font-medium text-zinc-700 dark:text-zinc-200">
                Roles
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                {ALL_ROLES.map((role) => (
                  <label
                    key={role}
                    className="inline-flex items-center gap-1.5 rounded-md border border-zinc-200 px-2 py-1 text-xs dark:border-zinc-700"
                  >
                    <input
                      type="checkbox"
                      checked={roles.includes(role)}
                      onChange={() => toggleRole(role)}
                    />
                    {role}
                  </label>
                ))}
              </div>
            </div>
            <Button type="submit" disabled={createMut.isPending}>
              {createMut.isPending ? "Creating…" : "Create user"}
            </Button>
          </form>
        </Card>

        <Card>
          <h2 className="mb-4 text-sm font-semibold">Tenant users</h2>
          {users.isLoading ? (
            <p className="text-sm text-zinc-500">Loading…</p>
          ) : users.isError ? (
            <p className="text-sm text-rose-600">
              {(users.error as Error).message}
            </p>
          ) : (
            <ul className="divide-y divide-zinc-100 dark:divide-zinc-800">
              {(users.data ?? []).map((u) => (
                <UserRow
                  key={u.id}
                  user={u}
                  tenantId={tenantId}
                  roleLabel={roleLabel(u)}
                  onChanged={() =>
                    void qc.invalidateQueries({ queryKey: ["users", tenantId] })
                  }
                />
              ))}
              {!users.data?.length ? (
                <li className="py-4 text-sm text-zinc-500">No users yet</li>
              ) : null}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}

function UserRow({
  user,
  tenantId,
  roleLabel,
  onChanged,
}: {
  user: AppUser;
  tenantId: string;
  roleLabel: string;
  onChanged: () => void;
}) {
  const [busy, setBusy] = useState(false);

  const toggleStatus = async () => {
    setBusy(true);
    try {
      const next = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
      await updateUser(tenantId, user.id, { status: next });
      toast.success(next === "ACTIVE" ? "User enabled" : "User disabled");
      onChanged();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Update failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <li className="flex flex-col gap-1 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-zinc-900 dark:text-zinc-50">
          {user.displayName}{" "}
          <span className="font-normal text-zinc-500">({user.email})</span>
        </p>
        <p className="text-xs text-zinc-500">
          {user.status} · {roleLabel}
        </p>
      </div>
      <Button
        type="button"
        size="sm"
        variant="secondary"
        disabled={busy}
        onClick={() => void toggleStatus()}
      >
        {user.status === "ACTIVE" ? "Disable" : "Enable"}
      </Button>
    </li>
  );
}