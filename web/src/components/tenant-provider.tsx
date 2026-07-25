"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  DEFAULT_TENANT_ID,
  isValidTenantId,
  readTenantFromStorage,
  writeTenantToStorage,
} from "@/lib/tenant";

type TenantContextValue = {
  tenantId: string;
  setTenantId: (id: string) => boolean;
  ready: boolean;
};

const TenantCtx = createContext<TenantContextValue | null>(null);

export function TenantProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [tenantId, setTenantIdState] = useState(DEFAULT_TENANT_ID);
  const [ready, setReady] = useState(false);
  const tenantIdRef = useRef(tenantId);
  tenantIdRef.current = tenantId;

  useEffect(() => {
    const id = readTenantFromStorage();
    tenantIdRef.current = id;
    setTenantIdState(id);
    writeTenantToStorage(id);
    setReady(true);
  }, []);

  const setTenantId = useCallback(
    (id: string) => {
      const trimmed = id.trim();
      if (!isValidTenantId(trimmed)) return false;
      // Only clear React Query when the tenant actually changes.
      // Auth hydration often re-applies the same tenant and used to cancel
      // in-flight requests (aging, etc.) → browser Network "canceled".
      if (tenantIdRef.current === trimmed) {
        writeTenantToStorage(trimmed);
        return true;
      }
      tenantIdRef.current = trimmed;
      setTenantIdState(trimmed);
      writeTenantToStorage(trimmed);
      queryClient.clear();
      return true;
    },
    [queryClient],
  );

  const value = useMemo(
    () => ({ tenantId, setTenantId, ready }),
    [tenantId, setTenantId, ready],
  );

  return <TenantCtx.Provider value={value}>{children}</TenantCtx.Provider>;
}

export function useTenant(): TenantContextValue {
  const ctx = useContext(TenantCtx);
  if (!ctx) {
    throw new Error("useTenant must be used within TenantProvider");
  }
  return ctx;
}