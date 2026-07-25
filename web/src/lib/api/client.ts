import { ApiError, parseApiError } from "@/lib/errors";

export type ApiRequestOptions = {
  method?: string;
  body?: unknown;
  tenantId: string;
  idempotencyKey?: string;
  signal?: AbortSignal;
  query?: Record<string, string | number | boolean | undefined | null>;
};

function buildUrl(path: string, query?: ApiRequestOptions["query"]): string {
  const base = path.startsWith("/") ? path : `/${path}`;
  if (!query) return base;
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === "") continue;
    params.set(k, String(v));
  }
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
}

/**
 * Browser-side API client.
 * Calls same-origin /api/v1/* which the BFF proxies to Quarkus with httpOnly cookies.
 * Never attaches Authorization / X-API-Key from the browser — credentials stay server-side.
 */
export async function apiFetch<T>(
  path: string,
  options: ApiRequestOptions,
): Promise<T> {
  const { method = "GET", body, tenantId, idempotencyKey, signal, query } =
    options;

  if (!tenantId?.trim()) {
    throw new ApiError(400, "TENANT_ERROR", "Tenant ID is required");
  }

  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Tenant-Id": tenantId.trim(),
  };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (idempotencyKey) {
    headers["Idempotency-Key"] = idempotencyKey;
  }

  let res: Response;
  try {
    res = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
      cache: "no-store",
      credentials: "include",
    });
  } catch (e) {
    // React Query aborts superseded requests; rethrow as-is so RQ treats them
    // as cancellations (not user-visible errors).
    if (
      (e instanceof DOMException && e.name === "AbortError") ||
      (e instanceof Error && e.name === "AbortError") ||
      signal?.aborted
    ) {
      throw e;
    }
    throw e;
  }

  if (res.status === 401) {
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
      const next = encodeURIComponent(
        window.location.pathname + window.location.search,
      );
      window.location.assign(`/login?next=${next}`);
    }
    throw new ApiError(401, "UNAUTHORIZED", "Sign in required");
  }

  if (!res.ok) {
    throw await parseApiError(res);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export async function checkBackendHealth(): Promise<{
  ok: boolean;
  status: number;
  detail?: string;
}> {
  try {
    const res = await fetch("/q/health", { cache: "no-store" });
    const detail = await res.text();
    return { ok: res.ok, status: res.status, detail: detail.slice(0, 200) };
  } catch (e) {
    return {
      ok: false,
      status: 0,
      detail: e instanceof Error ? e.message : "Network error",
    };
  }
}
