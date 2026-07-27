import { ApiError, parseApiError } from "@/lib/errors";
import { logClientError } from "@/lib/log-client-error";

export type ApiRequestOptions = {
  method?: string;
  body?: unknown;
  tenantId: string;
  idempotencyKey?: string;
  signal?: AbortSignal;
  query?: Record<string, string | number | boolean | undefined | null>;
};

/** Prevent multi-query 401 storms from assigning /login repeatedly. */
let loginRedirectScheduled = false;

/**
 * Single-flight hard redirect to login. Parallel React Query failures must not
 * each call location.assign — that looks like an infinite browser refresh.
 */
export function redirectToLogin(nextPath?: string): void {
  if (typeof window === "undefined") return;
  if (window.location.pathname.startsWith("/login")) return;
  if (loginRedirectScheduled) return;
  loginRedirectScheduled = true;
  const next = encodeURIComponent(
    nextPath ?? window.location.pathname + window.location.search,
  );
  window.location.assign(`/login?next=${next}`);
}

/** Test / post-login helper — resets the single-flight guard. */
export function _resetLoginRedirectGuardForTests(): void {
  loginRedirectScheduled = false;
}

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
    logClientError("apiFetch.network", e, { path, method });
    throw new ApiError(0, "NETWORK_ERROR", "Network request failed");
  }

  if (res.status === 401) {
    redirectToLogin();
    throw new ApiError(401, "UNAUTHORIZED", "Sign in required");
  }

  if (!res.ok) {
    const err = await parseApiError(res);
    logClientError("apiFetch.http", err, { path, method, status: res.status });
    throw err;
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch (e) {
    logClientError("apiFetch.json", e, { path, method, preview: text.slice(0, 200) });
    throw new ApiError(res.status, "PARSE_ERROR", "Invalid response from server");
  }
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

/**
 * Download a binary response (PDF, CSV, etc.) via the BFF proxy.
 * Triggers a browser file save with the given filename.
 */
export async function apiDownload(
  path: string,
  options: ApiRequestOptions & { filename: string },
): Promise<void> {
  const { method = "GET", body, tenantId, idempotencyKey, signal, query, filename } =
    options;

  if (!tenantId?.trim()) {
    throw new ApiError(400, "TENANT_ERROR", "Tenant ID is required");
  }

  const headers: Record<string, string> = {
    Accept: "*/*",
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
    redirectToLogin();
    throw new ApiError(401, "UNAUTHORIZED", "Sign in required");
  }

  if (!res.ok) {
    throw await parseApiError(res);
  }

  const blob = await res.blob();
  const objectUrl = URL.createObjectURL(blob);
  try {
    const a = document.createElement("a");
    a.href = objectUrl;
    a.download = filename;
    a.rel = "noopener";
    document.body.appendChild(a);
    a.click();
    a.remove();
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}
