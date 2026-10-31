import { NextResponse } from 'next/server';

export function middleware(request) {
  const host = (request.headers.get('host') || '').toLowerCase();
  if (host.startsWith('shop.')) {
    const url = request.nextUrl.clone();
    if (url.pathname === '/') {
      url.pathname = '/shop';
    }
    return NextResponse.rewrite(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
};
