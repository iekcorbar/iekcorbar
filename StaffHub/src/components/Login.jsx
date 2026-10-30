'use client';

import { useState } from 'react';
import { Anchor } from 'lucide-react';
import { useLang, trServer, LangToggle } from '@/lib/i18n';

export default function Login({ needsSetup, onDone }) {
  const { t } = useLang();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [bootstrap, setBootstrap] = useState('');
  const [totp, setTotp] = useState('');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [requires2fa, setRequires2fa] = useState(false);
  const [useRecovery, setUseRecovery] = useState(false);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setErr(''); setBusy(true);
    const url = needsSetup ? '/api/auth/setup' : (requires2fa ? '/api/auth/2fa/verify' : '/api/auth/login');
    let body;
    if (needsSetup) {
      body = { username, password, bootstrap };
    } else if (requires2fa) {
      body = useRecovery ? { username, password, recoveryCode } : { username, password, totp };
    } else {
      body = { username, password };
    }
    try {
      const r = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
      const j = await r.json().catch(() => ({}));
      if (j.requires2fa) { setRequires2fa(true); setBusy(false); return; }
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
          <img src="/icon-192.png" width="40" height="40" alt="" className="size-10 rounded-xl" />
          <div>
            <h1 className="text-lg font-bold text-parch leading-tight">Staff<span className="text-gold">Hub</span></h1>
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
        {requires2fa && !useRecovery && (
          <label className="block text-xs space-y-1">
            <span className="text-parch/60">2FA Code</span>
            <input value={totp} onChange={(e) => setTotp(e.target.value)} type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
              className="w-full bg-black/40 border border-bronze/40 rounded-lg px-3 py-2 text-sm focus:border-gold outline-none text-center tracking-widest" />
          </label>
        )}
        {requires2fa && useRecovery && (
          <label className="block text-xs space-y-1">
            <span className="text-parch/60">Recovery Code</span>
            <input value={recoveryCode} onChange={(e) => setRecoveryCode(e.target.value)} type="text" autoComplete="off"
              className="w-full bg-black/40 border border-bronze/40 rounded-lg px-3 py-2 text-sm focus:border-gold outline-none text-center tracking-widest" />
          </label>
        )}
        {requires2fa && (
          <button type="button" onClick={() => setUseRecovery(!useRecovery)} className="text-xs text-gold/70 hover:text-gold">
            {useRecovery ? 'Use 2FA code instead' : 'Use recovery code instead'}
          </button>
        )}
        {err && <p className="text-xs text-red-400">{err}</p>}
        <button disabled={busy} className="w-full py-2 rounded-lg bg-gold/90 text-ink font-bold text-sm hover:bg-gold disabled:opacity-50">
          {busy ? '…' : needsSetup ? t('login.btn_setup') : t('login.btn')}
        </button>
      </form>
      </div>
    </div>
  );
}
