import { NextResponse } from 'next/server';
import { SESSION_COOKIE, destroySession, clearSessionCookie } from '@/lib/session';
import { unbindPushTokenFromRequest } from '@/lib/push';

export async function POST(request) {
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  await destroySession(token);
  await unbindPushTokenFromRequest(request);
  const response = NextResponse.json({ success: true });
  return clearSessionCookie(response);
}
