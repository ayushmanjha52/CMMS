import type { SessionUser, TokenResponse } from './types';

/**
 * The access token lives only in this module's memory — never localStorage, where any
 * injected script could read it. A page reload loses it; the HttpOnly refresh cookie gets
 * a new one.
 */
let accessToken: string | null = null;
let onSessionChange: (user: SessionUser | null) => void = () => {};

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export function setSessionListener(listener: (user: SessionUser | null) => void) {
  onSessionChange = listener;
}

/**
 * Single-flight refresh. The server rotates refresh tokens and treats reuse of an old one
 * as theft (it revokes the whole family). Two parallel 401s each calling /refresh would
 * send the same cookie twice and sign the user out — so every caller shares one request.
 * React StrictMode's double-mount in development hits exactly this.
 */
let refreshing: Promise<boolean> | null = null;

export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
    .then(async (res) => {
      if (!res.ok) {
        accessToken = null;
        onSessionChange(null);
        return false;
      }
      const body = (await res.json()) as TokenResponse;
      accessToken = body.accessToken;
      onSessionChange(body.user);
      return true;
    })
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

export async function login(plantCode: string, email: string, password: string): Promise<SessionUser> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ plantCode, email, password }),
  });
  if (!res.ok) {
    throw new ApiError(res.status, await problemDetail(res));
  }
  const body = (await res.json()) as TokenResponse;
  accessToken = body.accessToken;
  onSessionChange(body.user);
  return body.user;
}

export async function logout() {
  await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }).catch(() => undefined);
  accessToken = null;
  onSessionChange(null);
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res = await send(path, init);
  if (res.status === 401 && (await refreshSession())) {
    res = await send(path, init);
  }
  if (!res.ok) {
    throw new ApiError(res.status, await problemDetail(res));
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
}

export function put<T>(path: string, body: unknown): Promise<T> {
  return api<T>(path, { method: 'PUT', body: JSON.stringify(body) });
}

function send(path: string, init: RequestInit) {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  return fetch(path, { ...init, headers, credentials: 'same-origin' });
}

async function problemDetail(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body.detail ?? body.title ?? `Request failed (${res.status})`;
  } catch {
    return `Request failed (${res.status})`;
  }
}
