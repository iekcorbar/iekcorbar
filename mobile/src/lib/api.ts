// Base URL for the iekcorbar Next.js API (src/app/api/** in the website repo).
// Override at build time via `EXPO_PUBLIC_API_BASE_URL` (e.g. for a staging server
// or `http://<your-lan-ip>:3000/api` while developing against `npm run dev` locally —
// `localhost` from the phone/emulator would point at the device itself, not your PC).
export const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL || 'https://iekcorbar.com/api';

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export async function apiFetch<T = any>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    ...init
  });

  const contentType = res.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await res.json() : await res.text();

  if (!res.ok) {
    const message = typeof body === 'object' && body?.error ? body.error : `Request failed (${res.status})`;
    throw new ApiError(res.status, message);
  }

  return body as T;
}
