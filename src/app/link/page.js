'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useUser } from '../providers';
import { Link2, Gamepad2, Send, KeyRound, CheckCircle2, Clock, PauseCircle, Copy, Check, RefreshCw } from 'lucide-react';

// "Link your Minecraft account" — step-by-step page for new registrations
// (see api/auth/register + api/account/link). Pending accounts have 3 days;
// after that they're on hold (off the whitelist + kicked) until they link.

function timeLeft(deadline, now) {
  const ms = new Date(deadline).getTime() - now;
  if (!Number.isFinite(ms) || ms <= 0) return 'less than a minute';
  const d = Math.floor(ms / 86400000);
  const h = Math.floor((ms % 86400000) / 3600000);
  const m = Math.floor((ms % 3600000) / 60000);
  if (d > 0) return `${d} day${d === 1 ? '' : 's'} ${h} hour${h === 1 ? '' : 's'}`;
  if (h > 0) return `${h} hour${h === 1 ? '' : 's'} ${m} min`;
  return `${m} minute${m === 1 ? '' : 's'}`;
}

function Step({ n, icon: Icon, title, done, children }) {
  return (
    <div className={`flex gap-4 p-4 rounded-xl border ${done ? 'border-emerald-500/40 bg-emerald-950/20' : 'border-sp-bronze/40 bg-neutral-900/30'}`}>
      <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 font-bold ${done ? 'bg-emerald-500 text-black' : 'bg-sp-gold text-black'}`}>
        {done ? <Check className="w-5 h-5" /> : n}
      </div>
      <div className="flex-1 space-y-2 min-w-0">
        <p className="font-semibold text-white flex items-center gap-2"><Icon className="w-4 h-4 text-sp-copper shrink-0" /> {title}</p>
        <div className="text-sm text-text-warm/80 leading-relaxed">{children}</div>
      </div>
    </div>
  );
}

export default function LinkAccountPage() {
  const { user, setIsLoginOpen } = useUser();
  const [info, setInfo] = useState(null);
  const [now, setNow] = useState(Date.now());
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [devCode, setDevCode] = useState('');
  const [code, setCode] = useState('');
  const [copied, setCopied] = useState(false);

  const load = async () => {
    try {
      const res = await fetch('/api/account/link');
      if (res.ok) setInfo(await res.json());
    } catch { /* ignore */ }
  };

  useEffect(() => { if (user) load(); }, [user]);
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(t);
  }, []);

  const post = async (body, busyKey) => {
    setBusy(busyKey);
    setError('');
    try {
      const res = await fetch('/api/account/link', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Something went wrong');
      return data;
    } catch (err) {
      setError(err.message);
      return null;
    } finally {
      setBusy('');
    }
  };

  const reactivate = async () => {
    if (await post({ action: 'reactivate' }, 'reactivate')) await load();
  };
  const sendCode = async () => {
    const data = await post({ action: 'send_code' }, 'send');
    if (data) { setCodeSent(true); setDevCode(data.devCode || ''); }
  };
  const verify = async (e) => {
    e.preventDefault();
    const data = await post({ action: 'verify', code }, 'verify');
    if (data) {
      await load();
      window.dispatchEvent(new Event('iekcorbar-link-changed'));
    }
  };
  const copyIp = async () => {
    try { await navigator.clipboard.writeText(info?.serverAddress || ''); setCopied(true); setTimeout(() => setCopied(false), 1500); } catch { /* ignore */ }
  };

  if (!user) {
    return (
      <div className="max-w-xl mx-auto text-center space-y-4 py-10">
        <Link2 className="w-10 h-10 text-sp-copper mx-auto" />
        <p className="text-text-warm/80">Log in to link your Minecraft account.</p>
        <button onClick={() => setIsLoginOpen(true)} className="px-5 py-2 rounded-lg bg-sp-gold text-black font-bold uppercase text-sm">Log In</button>
      </div>
    );
  }

  if (!info) return <p className="text-center text-xs text-text-warm/40 italic py-16 animate-pulse">Loading...</p>;

  const ign = info.mcUsername;

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl font-bold text-sp-gold uppercase tracking-wider flex items-center gap-3">
          <Link2 className="w-8 h-8 text-sp-copper shrink-0" /> Link your account
        </h1>
        <p className="text-sm text-text-warm/70 mt-2">
          Prove that <strong className="text-white">{ign}</strong> is really you, so your website account and your Minecraft account belong together. It takes about a minute.
        </p>
      </div>

      {info.status === 'linked' && (
        <div className="bg-emerald-950/30 border border-emerald-500/40 rounded-xl p-6 text-center space-y-3">
          <CheckCircle2 className="w-12 h-12 text-emerald-400 mx-auto" />
          <p className="font-serif text-xl text-white">All set — {ign} is linked!</p>
          <p className="text-sm text-text-warm/75">You're on the whitelist and your account is fully active.</p>
          <Link href="/profile" className="inline-block px-5 py-2 rounded-lg bg-sp-gold text-black font-bold uppercase text-sm">Go to my profile</Link>
        </div>
      )}

      {info.status === 'on_hold' && (
        <div className="bg-rose-950/30 border border-rose-500/40 rounded-xl p-6 space-y-3">
          <p className="font-serif text-xl text-rose-200 flex items-center gap-2"><PauseCircle className="w-6 h-6" /> Your account is on hold</p>
          <p className="text-sm text-text-warm/80 leading-relaxed">
            You didn't link <strong className="text-white">{ign}</strong> within 3 days, so you were removed from the server whitelist.
            Tap <strong>Reactivate</strong>: you'll be back on the whitelist for <strong>1 hour</strong> — join the server and link your account in that time.
          </p>
          <button
            type="button"
            onClick={reactivate}
            disabled={busy === 'reactivate'}
            className="px-5 py-2.5 rounded-lg bg-sp-gold text-black font-bold uppercase text-sm disabled:opacity-50 flex items-center gap-2"
          >
            <RefreshCw className={`w-4 h-4 ${busy === 'reactivate' ? 'animate-spin' : ''}`} /> Reactivate
          </button>
        </div>
      )}

      {info.status === 'pending' && (
        <>
          <div className="bg-orange-950/30 border border-orange-500/40 rounded-xl p-4 flex items-center gap-3 text-sm">
            <Clock className="w-6 h-6 text-orange-300 shrink-0" />
            <p className="text-orange-100/90">
              <strong>{timeLeft(info.deadline, now)}</strong> left to link.
              After that you'll be removed from the whitelist and kicked from the server until you link.
            </p>
          </div>

          <div className="space-y-3">
            <Step n={1} icon={Gamepad2} title="Join the Minecraft server">
              Start Minecraft (Java Edition) with the account <strong className="text-white">{ign}</strong> and join:
              {info.serverAddress && (
                <button type="button" onClick={copyIp} className="mt-2 flex items-center gap-2 font-mono text-white bg-neutral-950 border border-sp-bronze/50 rounded-lg px-3 py-2 hover:border-sp-gold">
                  {info.serverAddress} {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4 text-sp-copper" />}
                </button>
              )}
              <p className="text-xs text-text-warm/50 mt-1">You're already on the whitelist.</p>
            </Step>

            <Step n={2} icon={Send} title="Get your code in-game" done={codeSent}>
              While you're online, tap the button — a 6-digit code appears in your in-game chat.
              <div className="mt-2">
                <button
                  type="button"
                  onClick={sendCode}
                  disabled={busy === 'send'}
                  className="px-4 py-2 rounded-lg bg-sp-gold text-black font-bold uppercase text-xs disabled:opacity-50 flex items-center gap-2"
                >
                  <Send className="w-3.5 h-3.5" /> {busy === 'send' ? 'Sending...' : codeSent ? 'Send a new code' : 'Send code'}
                </button>
                {codeSent && !devCode && <p className="text-xs text-emerald-400 mt-2">✓ Sent! Check your chat in Minecraft (valid for 30 minutes).</p>}
                {devCode && <p className="text-xs text-sp-copper mt-2">Test mode (no server connection): your code is <strong className="text-white font-mono">{devCode}</strong></p>}
              </div>
            </Step>

            <Step n={3} icon={KeyRound} title="Enter the code">
              <form onSubmit={verify} className="flex gap-2 mt-1">
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={code}
                  onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
                  placeholder="123456"
                  className="w-36 bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2 font-mono text-center text-lg tracking-widest focus:outline-hidden focus:border-sp-gold"
                />
                <button
                  type="submit"
                  disabled={code.length !== 6 || busy === 'verify'}
                  className="px-4 py-2 rounded-lg bg-sp-gold text-black font-bold uppercase text-xs disabled:opacity-40"
                >
                  {busy === 'verify' ? 'Checking...' : 'Link account'}
                </button>
              </form>
            </Step>
          </div>
        </>
      )}

      {error && <div className="bg-crimson/20 border border-crimson text-rose-200 text-sm px-3 py-2 rounded-lg">{error}</div>}

      <p className="text-xs text-text-warm/45 text-center">
        Wrong Minecraft name or stuck? Ask a staff member on our Discord.
      </p>
    </div>
  );
}
