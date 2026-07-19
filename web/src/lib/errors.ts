export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

export async function parseApiError(res: Response): Promise<ApiError> {
  const text = await res.text();
  if (!text) {
    return new ApiError(res.status, "HTTP_ERROR", res.statusText || `HTTP ${res.status}`);
  }
  try {
    const json = JSON.parse(text) as { code?: string; message?: string };
    return new ApiError(
      res.status,
      json.code ?? "ERROR",
      json.message ?? text,
    );
  } catch {
    // TenantFilter returns plain text "Invalid or missing tenant"
    const code =
      res.status === 400 && /tenant/i.test(text) ? "TENANT_ERROR" : "HTTP_ERROR";
    return new ApiError(res.status, code, text);
  }
}