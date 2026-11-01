'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { useUser } from '../providers';
import { whyFor } from '@/lib/rule-reasons';
import { Check, Shield, Link2, Gamepad2, Compass, Copy, ChevronDown } from 'lucide-react';

// New-player onboarding: one checklist from "account created" to "playing".
// Step state comes from api/account/rules (agreed to the rules?) and api/account/link
// (Minecraft account linked?). Registration sends new players here.

const DISCORD = 'https://discord.gg/G9P2khU7F4';

function Step({ n, icon: Icon, title, done, active, children }) {
  return (
    <div className={`flex gap-4 p-5 rounded-xl border ${done ? 'border-emerald-500/40 bg-emerald-950/20' : active ? 'border-sp-gold/60 bg-neutral-900/50' : 'border-sp-bronze/30 bg-neutral-900/20 opacity-70'}`}>
      <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 font-bold ${done ? 'bg-emerald-500 text-black' : 'bg-sp-gold text-black'}`}>
        {done ? <Check className="w-5 h-5" /> : n}
      </div>
      <div className="flex-1 min-w-0 space-y-3">
        <p className="font-semibold text-white flex items-center gap-2"><Icon className="w-4 h-4 text-sp-copper shrink-0" /> {title}</p>
        <div className="text-sm text-text-warm/80 leading-relaxed space-y-3">{children}</div>
      </div>
    </div>
  );
}

export default function WelcomePage() {
  const { user, setIsLoginOpen } = useUser();
  const [rules, setRules] = useState([]);
  const [acc, setAcc] = useState(null);      // { accepted }
  const [link, setLink] = useState(null);    // { status, deadline, mcUsername, serverAddress }
  const [agree, setAgree] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    try {
      const [a, l] = await Promise.all([
        fetch('/api/account/rules').then(r => (r.ok ? r.json() : null)),
        fetch('/api/account/link').then(r => (r.ok ? r.json() : null)),
      ]);
      setAcc(a); setLink(l);
    } catch { /* ignore */ }
  }, []);
  useEffect(() => { if (user) load(); }, [user, load]);
  useEffect(() => { fetch('/api/rules').then(r => r.json()).then(d => setRules(d.rules || [])).catch(() => {}); }, []);

  const accept = async () => {
    setBusy(true); setError('');
    try {
      const res = await fetch('/api/account/rules', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ accept: true }) });
      const d = await res.json();
      if (!res.ok) throw new Error(d.error || 'Something went wrong');
      await load();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };

  const copy = (text) => { navigator.clipboard?.writeText(text).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500); }).catch(() => {}); };

  if (!user) {
    return (
      <div className="max-w-xl mx-auto py-20 text-center space-y-4">
        <h1 className="font-serif text-3xl font-black text-sp-gold uppercase tracking-widest">Welcome to Iekcorbar</h1>
        <p className="text-text-warm/70">Create an account or log in to start your checklist.</p>
        <button onClick={() => setIsLoginOpen(true)} className="px-5 py-2.5 rounded-lg bg-sp-gold text-black font-semibold uppercase tracking-wider text-sm hover:bg-white transition-all cursor-pointer">Log in / Create account</button>
      </div>
    );
  }

  const rulesDone = !!acc?.accepted;
  const linked = (link?.status || 'linked') === 'linked';
  const doneCount = 1 + (rulesDone ? 1 : 0) + (linked ? 1 : 0);
  const server = link?.serverAddress || 'iekcorbar.com';

  return (
    <div className="max-w-2xl mx-auto py-10 space-y-6">
      <title>Welcome — Iekcorbar</title>
      <div className="text-center space-y-2">
        <h1 className="font-serif text-3xl sm:text-4xl font-black text-sp-gold uppercase tracking-widest steampunk-glow">Welcome, {user.mcUsername || 'traveller'}</h1>
        <p className="text-text-warm/70 text-sm">Three steps and you are in. {doneCount} of 4 done.</p>
        <div className="h-1.5 rounded-full bg-neutral-800 overflow-hidden max-w-xs mx-auto"><div className="h-full bg-sp-gold transition-all" style={{ width: `${(doneCount / 4) * 100}%` }} /></div>
      </div>

      <Step n={1} icon={Check} title="Account created" done active={false}>
        <p>You are registered{user.mcUsername ? <> as <strong className="text-white font-mono">{user.mcUsername}</strong></> : null} and already on the whitelist.</p>
      </Step>

      <Step n={2} icon={Shield} title="Read and agree to the rules" done={rulesDone} active={!rulesDone}>
        {rulesDone ? (
          <p>You agreed to the rules. <Link href="/rules" className="text-sp-gold underline">Read them again anytime.</Link></p>
        ) : (
          <>
            <p>Every rule comes with the reason behind it. Open the ones you want to read, then agree below.</p>
            <div className="space-y-1.5 max-h-80 overflow-auto pr-1">
              {rules.map(r => {
                const { body, why } = whyFor(r);
                return (
                  <details key={r.id} className="group rounded-lg border border-sp-bronze/30 bg-black/30">
                    <summary className="flex items-center gap-2 px-3 py-2 cursor-pointer text-white text-xs font-semibold list-none">
                      <ChevronDown className="w-3.5 h-3.5 text-sp-gold/70 group-open:rotate-180 transition-transform" /> {r.title}
                    </summary>
                    <div className="px-3 pb-3 space-y-2 text-xs">
                      <pre className="whitespace-pre-wrap font-sans text-text-warm/80">{body.replace(/\*\*/g, '').replace(/^\*(.+)\*$/gm, '$1')}</pre>
                      {why && <p className="text-sp-copper"><strong className="text-sp-gold">Why:</strong> {why}</p>}
                    </div>
                  </details>
                );
              })}
            </div>
            <label className="flex items-start gap-2 text-xs cursor-pointer">
              <input type="checkbox" checked={agree} onChange={e => setAgree(e.target.checked)} className="mt-0.5 accent-[#d4a017]" />
              <span>I have read the <Link href="/rules" target="_blank" className="text-sp-gold underline">server rules</Link> and agree to follow them.</span>
            </label>
            <button onClick={accept} disabled={!agree || busy} className="px-4 py-2 rounded-lg bg-sp-gold text-black text-xs font-bold uppercase tracking-wider disabled:opacity-40 cursor-pointer">{busy ? 'Saving…' : 'I agree'}</button>
            {error && <p className="text-xs text-red-400">{error}</p>}
          </>
        )}
      </Step>

      <Step n={3} icon={Link2} title="Link your Minecraft account" done={linked} active={rulesDone && !linked}>
        {linked ? <p>Your Minecraft account is linked.</p> : (
          <>
            <p>Join the server once and enter a short code. It takes about a minute, and you have 3 days — after that you are taken off the whitelist until you link.</p>
            <Link href="/link" className="inline-block px-4 py-2 rounded-lg bg-sp-gold text-black text-xs font-bold uppercase tracking-wider hover:bg-white transition-all">Link now</Link>
          </>
        )}
      </Step>

      <Step n={4} icon={Gamepad2} title="Join and look around" done={false} active={rulesDone && linked}>
        <p>Server address (Java Edition):</p>
        <button onClick={() => copy(server)} className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-black/50 border border-sp-bronze/40 font-mono text-white text-sm hover:border-sp-gold cursor-pointer">
          {server} {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5 text-sp-copper" />}
        </button>
        <p className="flex items-center gap-2 text-text-warm/60 text-xs"><Compass className="w-3.5 h-3.5" /> Good places to start:</p>
        <div className="flex flex-wrap gap-2 text-xs">
          <a href={DISCORD} target="_blank" rel="noopener noreferrer" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Join our Discord</a>
          <Link href="/wiki" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Wiki</Link>
          <Link href="/start" className="px-3 py-1.5 rounded-full border border-sp-gold text-sp-gold">Your first hours</Link>
          <Link href="/wildlife" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Wildlife guide</Link>
          <Link href="/gouw" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Gouw guide</Link>
          <Link href="/sakagura" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Sakagura</Link>
          <Link href="/steamworks" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Steamworks</Link>
        </div>
      </Step>
    </div>
  );
}
