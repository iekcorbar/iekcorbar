'use client';

import { useEffect, useState } from 'react';
import Login from '@/components/Login';
import Hub from '@/components/Hub';

export default function Home() {
  const [state, setState] = useState(null); // { user, needsSetup }

  useEffect(() => {
    fetch('/api/auth/me').then((r) => r.json()).then(setState).catch(() => setState({ user: null, needsSetup: false }));
  }, []);

  if (!state) return <div className="min-h-screen grid place-items-center text-gold/60 text-sm">StaffHub laden…</div>;
  if (!state.user) return <Login needsSetup={state.needsSetup} onDone={(u) => setState({ user: u, needsSetup: false })} />;
  return <Hub me={state.user} onLogout={() => setState({ user: null, needsSetup: false })} />;
}
