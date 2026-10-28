'use client';

import { useState } from 'react';
import { Anchor } from 'lucide-react';
import { useLang, trServer, LangToggle } from '@/lib/i18n';

export default function Login({ needsSetup, onDone }) {
  const { t } = useLang();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [bootstrap, setBootstrap] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setErr(''); setBusy(true);
    const url = needsSetup ? '/api/auth/setup' : '/api/auth/login';
    const body = needsSetup ? { username, password, bootstrap } : { username, password };
    try {
      const r = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) { setErr(trServer(j.error || 'Something went wrong')); return; }
      const me = await fetch('/api/auth/me').then((x) => x.json());
      onDone(me.user);
    } catch { setErr(t('login.unreachable')); } finally { setBusy(false); }
  }

  return (
    <div className="min-h-screen grid place-items-center p-6">
      <div className="w-full max-w-sm">
        <div className="flex justify-end mb-1"><LangToggle /></div>
        <form onSubmit={submit} className="w-full bg-panel border border-bronze/40 rounded-2xl p-6 space-y-4 shadow-2xl">
        <div className="flex items-center gap-3">
          <span className="size-10 grid place-items-center rounded-full bg-gold/10 border border-gold/40"><Anchor className="size-5 text-gold" /></span>
          <div>
            <h1 className="text-lg font-bold text-gold leading-tight">StaffHub</h1>
            <p className="text-[11px] text-parch/50">{needsSetup ? t('login.setup_sub') : t('login.crew_sub')}</p>
          </div>
        </div>
        {needsSetup && (
          <label className="block text-xs space-y-1">
            <span className="text-parch/60">{t('login.bootstrap')}</span>
            <input value={bootstrap} onChange={(e) => setBootstrap(e.target.value)} type="password" autoComplete="off"
              className="w-full bg-black/40 border border-bronze/40 rounded-lg px-3 py-2 text-sm focus:border-gold outline-none" />
          </label>
        )}
        <label className="block text-xs space-y-1">
          <span className="text-parch/60">{t('login.user')}</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus autoComplete="username"
            className="w-full bg-black/40 border border-bronze/40 rounded-lg px-3 py-2 text-sm focus:border-gold outline-none" />
        </label>
        <label className="block text-xs space-y-1">
          <span className="text-parch/60">{t('login.pass')}</span>
          <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" autoComplete={needsSetup ? 'new-password' : 'current-password'}
            className="w-full bg-black/40 border border-bronze/40 rounded-lg px-3 py-2 text-sm focus:border-gold outline-none" />
        </label>
        {err && <p className="text-xs text-red-400">{err}</p>}
        <button disabled={busy} className="w-full py-2 rounded-lg bg-gold/90 text-ink font-bold text-sm hover:bg-gold disabled:opacity-50">
          {busy ? '…' : needsSetup ? t('login.btn_setup') : t('login.btn')}
        </button>
      </form>
      </div>
    </div>
  );
}
