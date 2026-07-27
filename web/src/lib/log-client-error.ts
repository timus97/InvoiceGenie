/**
 * Client-side error logging. Technical details never go to the UI.
 */
export function logClientError(
  context: string,
  error: unknown,
  extra?: Record<string, unknown>,
): void {
  const detail =
    error instanceof Error
      ? { name: error.name, message: error.message, stack: error.stack }
      : { value: String(error) };
  // Always log full technical detail for operators / browser console / log pipelines
  console.error(`[InvoiceGenie] ${context}`, detail, extra ?? {});
}

/** Safe, non-technical copy for optional toast feedback on user-initiated actions. */
export const USER_ACTION_FAILED =
  "Something went wrong. Please try again. If the problem continues, contact support.";

export const USER_LOAD_FAILED =
  "We could not load this data right now. Please refresh or try again later.";
