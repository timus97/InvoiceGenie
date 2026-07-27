"use client";

import { QueryClient, QueryClientProvider, QueryCache, MutationCache } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Toaster } from "sonner";
import { TenantProvider } from "@/components/tenant-provider";
import { AuthProvider } from "@/components/auth-provider";
import { AppErrorBoundary } from "@/components/error-boundary";
import { logClientError } from "@/lib/log-client-error";
import { ApiError } from "@/lib/errors";

function isAbort(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === "AbortError") ||
    (error instanceof Error && error.name === "AbortError")
  );
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        queryCache: new QueryCache({
          onError: (error, query) => {
            if (isAbort(error)) return;
            // 401 already redirects; still log for ops
            logClientError("react-query.query", error, {
              queryKey: query.queryKey,
              status: error instanceof ApiError ? error.status : undefined,
            });
          },
        }),
        mutationCache: new MutationCache({
          onError: (error, _vars, _ctx, mutation) => {
            if (isAbort(error)) return;
            logClientError("react-query.mutation", error, {
              mutationKey: mutation.options.mutationKey,
              status: error instanceof ApiError ? error.status : undefined,
            });
          },
        }),
        defaultOptions: {
          queries: {
            staleTime: 15_000,
            retry: 1,
            refetchOnWindowFocus: false,
            // Prevent unhandled rejection noise for cancelled requests
            throwOnError: false,
          },
          mutations: {
            throwOnError: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <TenantProvider>
        <AuthProvider>
          <AppErrorBoundary>{children}</AppErrorBoundary>
          <Toaster richColors position="top-right" closeButton />
        </AuthProvider>
      </TenantProvider>
    </QueryClientProvider>
  );
}