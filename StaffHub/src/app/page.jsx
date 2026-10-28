'use client';

import { useEffect, useState } from 'react';
import Login from '@/components/Login';
import Hub from '@/components/Hub';
import { useLang } from '@/lib/i18n';

export default function Home() {
  const { t } = useLang();
  const [state, setState] = useState(null); // { user, needsSetup }

  useEffect(() => {
    fetch('/api/auth/me').then((r) => r.json()).then(setState).catch(() => setState({ user: null, needsSetup: false }));
  }, []);

  if (!state) return <div className="min-h-screen grid place-items-center text-gold/60 text-sm">{t('app.loading')}</div>;
  if (!state.user) return <Login needsSetup={state.needsSetup} onDone={(u) => setState({ user: u, needsSetup: false })} />;
  return <Hub me={state.user} onLogout={() => setState({ user: null, needsSetup: false })} />;
}
