/**
 * In-process opaque session store for the Next.js BFF.
 * Holds access + refresh tokens server-side only.
 */

import { randomBytes } from "node:crypto";
import type { PublicSession } from "@/lib/server/auth-core";

export type ServerSession = {
  accessToken?: string;
  refreshToken?: string;
  apiKey?: string;
  session: PublicSession;
  accessExpiresAt?: number | null;
  expiresAt: number;
};

type StoreShape = Map<string, ServerSession>;

const GLOBAL_KEY = "__invoicegenie_session_store__";

function getStore(): StoreShape {
  const g = globalThis as unknown as Record<string, StoreShape | undefined>;
  if (!g[GLOBAL_KEY]) {
    g[GLOBAL_KEY] = new Map<string, ServerSession>();
  }
  return g[GLOBAL_KEY]!;
}

const CLEAN_EVERY_MS = 60_000;
let lastClean = 0;

function maybeClean(now: number) {
  if (now - lastClean < CLEAN_EVERY_MS) return;
  lastClean = now;
  const store = getStore();
  for (const [id, s] of store) {
    if (s.expiresAt <= now) store.delete(id);
  }
}

export function createSessionId(): string {
  return randomBytes(32).toString("base64url");
}

export function putSession(
  id: string,
  data: {
    accessToken?: string | null;
    refreshToken?: string | null;
    apiKey?: string | null;
    session: PublicSession;
    maxAgeSeconds: number;
    accessExpiresInSeconds?: number | null;
  },
): void {
  const now = Date.now();
  maybeClean(now);
  getStore().set(id, {
    accessToken: data.accessToken || undefined,
    refreshToken: data.refreshToken || undefined,
    apiKey: data.apiKey || undefined,
    session: data.session,
    accessExpiresAt:
      data.accessExpiresInSeconds != null
        ? now + data.accessExpiresInSeconds * 1000
        : null,
    expiresAt: now + data.maxAgeSeconds * 1000,
  });
}

export function getSession(id: string | undefined | null): ServerSession | null {
  if (!id) return null;
  const now = Date.now();
  maybeClean(now);
  const store = getStore();
  const s = store.get(id);
  if (!s) return null;
  if (s.expiresAt <= now) {
    store.delete(id);
    return null;
  }
  return s;
}

export function updateSessionTokens(
  id: string,
  data: {
    accessToken: string;
    refreshToken?: string | null;
    accessExpiresInSeconds?: number | null;
    session?: PublicSession;
  },
): void {
  const s = getSession(id);
  if (!s) return;
  s.accessToken = data.accessToken;
  if (data.refreshToken) s.refreshToken = data.refreshToken;
  if (data.session) s.session = data.session;
  if (data.accessExpiresInSeconds != null) {
    s.accessExpiresAt = Date.now() + data.accessExpiresInSeconds * 1000;
  }
  getStore().set(id, s);
}

export function deleteSession(id: string | undefined | null): void {
  if (id) getStore().delete(id);
}

export function _resetSessionsForTests(): void {
  getStore().clear();
}