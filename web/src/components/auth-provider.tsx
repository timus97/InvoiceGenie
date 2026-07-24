"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import {
  clearAuthSession,
  isAuthRequired,
  loginRequest,
  readAuthSession,
  writeAuthSession,
  type AuthSession,
} from "@/lib/auth-session";
import { useTenant } from "@/components/tenant-provider";

type AuthContextValue = {
  session: AuthSession | null;
  ready: boolean;
  loginWithPassword: (username: string, password: string) => Promise<void>;
  loginWithApiKey: (apiKey: string) => Promise<void>;
  logout: () => void;
  hasRole: (...roles: string[]) => boolean;
};

const AuthCtx = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [ready, setReady] = useState(false);
  const { setTenantId } = useTenant();
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const s = readAuthSession();
    setSession(s);
    if (s?.tenantId) {
      setTenantId(s.tenantId);
    }
    setReady(true);
  }, [setTenantId]);

  useEffect(() => {
    if (!ready) return;
    if (!isAuthRequired()) return;
    if (pathname === "/login") return;
    if (!session) {
      router.replace(`/login?next=${encodeURIComponent(pathname || "/")}`);
    }
  }, [ready, session, pathname, router]);

  const applyLogin = useCallback(
    async (body: { username?: string; password?: string; apiKey?: string }) => {
      const res = await loginRequest(body);
      const expiresAt =
        res.expiresInSeconds != null
          ? Date.now() + res.expiresInSeconds * 1000
          : null;
      const next: AuthSession = {
        accessToken: res.accessToken ?? null,
        apiKey: body.apiKey && !res.accessToken ? body.apiKey : null,
        tenantId: res.tenantId,
        subject: res.subject,
        method: res.method,
        roles: res.roles ?? [],
        expiresAt,
      };
      writeAuthSession(next);
      setSession(next);
      setTenantId(res.tenantId);
      queryClient.clear();
    },
    [queryClient, setTenantId],
  );

  const loginWithPassword = useCallback(
    (username: string, password: string) =>
      applyLogin({ username, password }),
    [applyLogin],
  );

  const loginWithApiKey = useCallback(
    (apiKey: string) => applyLogin({ apiKey }),
    [applyLogin],
  );

  const logout = useCallback(() => {
    clearAuthSession();
    setSession(null);
    queryClient.clear();
    if (isAuthRequired()) {
      router.replace("/login");
    }
  }, [queryClient, router]);

  const hasRole = useCallback(
    (...roles: string[]) => {
      if (!session?.roles?.length) return false;
      const set = new Set(session.roles.map((r) => r.toUpperCase()));
      return roles.some((r) => set.has(r.toUpperCase()));
    },
    [session],
  );

  const value = useMemo(
    () => ({
      session,
      ready,
      loginWithPassword,
      loginWithApiKey,
      logout,
      hasRole,
    }),
    [session, ready, loginWithPassword, loginWithApiKey, logout, hasRole],
  );

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthCtx);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}
