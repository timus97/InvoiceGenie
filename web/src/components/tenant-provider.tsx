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

  useEffect(() => {
    const id = readTenantFromStorage();
    setTenantIdState(id);
    writeTenantToStorage(id);
    setReady(true);
  }, []);

  const setTenantId = useCallback(
    (id: string) => {
      const trimmed = id.trim();
      if (!isValidTenantId(trimmed)) return false;
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