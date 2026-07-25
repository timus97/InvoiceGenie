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
  fetchSession,
  isAuthRequired,
  loginRequest,
  logoutRequest,
  type AuthSession,
} from "@/lib/auth-session";
import { useTenant } from "@/components/tenant-provider";

type AuthContextValue = {
  session: AuthSession | null;
  ready: boolean;
  loginWithPassword: (email: string, password: string) => Promise<void>;
  loginWithApiKey: (apiKey: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (...roles: string[]) => boolean;
  refreshSession: () => Promise<void>;
};

const AuthCtx = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [ready, setReady] = useState(false);
  const { setTenantId } = useTenant();
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();

  const refreshSession = useCallback(async () => {
    const s = await fetchSession();
    setSession(s);
    if (s?.tenantId) {
      setTenantId(s.tenantId);
    }
  }, [setTenantId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const s = await fetchSession();
        if (cancelled) return;
        setSession(s);
        if (s?.tenantId) {
          setTenantId(s.tenantId);
        }
      } finally {
        if (!cancelled) setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setTenantId]);

  useEffect(() => {
    if (!ready) return;
    if (!isAuthRequired()) return;
    if (pathname === "/login") return;
    // Soft-navigate once session check fails. Prefer router over full reload;
    // apiFetch 401s use a single-flight hard redirect only as a fallback.
    if (!session) {
      router.replace(`/login?next=${encodeURIComponent(pathname || "/")}`);
    }
  }, [ready, session, pathname, router]);

  const applyLogin = useCallback(
    async (body: {
      email?: string;
      username?: string;
      password?: string;
      apiKey?: string;
    }) => {
      const res = await loginRequest(body);
      const next: AuthSession = {
        tenantId: res.tenantId,
        subject: res.subject,
        method: res.method,
        roles: res.roles ?? [],
        expiresAt: res.expiresAt ?? null,
        email: res.email ?? null,
        displayName: res.displayName ?? null,
        userId: res.userId ?? null,
      };
      setSession(next);
      setTenantId(res.tenantId);
      queryClient.clear();
    },
    [queryClient, setTenantId],
  );

  const loginWithPassword = useCallback(
    (email: string, password: string) => applyLogin({ email, password }),
    [applyLogin],
  );

  const loginWithApiKey = useCallback(
    (apiKey: string) => applyLogin({ apiKey }),
    [applyLogin],
  );

  const logout = useCallback(async () => {
    await logoutRequest();
    setSession(null);
    queryClient.clear();
    router.replace("/login");
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
      refreshSession,
    }),
    [
      session,
      ready,
      loginWithPassword,
      loginWithApiKey,
      logout,
      hasRole,
      refreshSession,
    ],
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