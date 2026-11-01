'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { Bug, CheckCircle2, Send } from 'lucide-react';
import { BUG_CATEGORIES } from '@/lib/bug-categories';
import { useUser } from '../providers';

const INPUT = 'w-full bg-neutral-900/60 border border-sp-bronze/40 rounded-lg px-3 py-2 text-sm text-white placeholder:text-text-warm/30 focus:outline-none focus:border-sp-gold';

export default function ReportBugPage() {
  const { user, setIsLoginOpen } = useUser();
  const [form, setForm] = useState({ subject: '', description: '', category: '', customCategory: '', ign: '', contact: '', website: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'submit_anonymous', ...form })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) setDone(true);
      else setError(data.error || 'Something went wrong. Please try again.');
    } catch {
      setError('Could not reach the server. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="max-w-md mx-auto my-12 bg-sp-dark/95 border-2 border-emerald-500/50 p-8 rounded-xl text-center steampunk-border space-y-4">
        <CheckCircle2 className="w-12 h-12 text-emerald-400 mx-auto" />
        <h2 className="font-serif text-2xl font-bold text-sp-gold uppercase tracking-wider">Thank you!</h2>
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Your report has reached the staff. Without an account we can&apos;t send you a status update, so if you left a contact detail we may use it to ask a question.
        </p>
        <Link href="/" className="inline-block text-xs font-bold uppercase tracking-wide text-sp-gold hover:text-white">Back to the home page</Link>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
          <Bug className="w-8 h-8 text-sp-copper" /> Report a bug
        </h1>
        <p className="text-xs text-text-warm/60 mt-1 leading-relaxed">
          No account needed. Tell us what went wrong and how to make it happen again.
          {!user && (
            <> Have an account? <button type="button" onClick={() => setIsLoginOpen(true)} className="text-sp-gold hover:text-white underline cursor-pointer">Sign in</button> to follow your report and get a notification when it&apos;s fixed.</>
          )}
          {user && <> You&apos;re signed in — <Link href="/profile#report-a-bug" className="text-sp-gold hover:text-white underline">report from your Profile</Link> to follow its status.</>}
        </p>
      </div>

      <form onSubmit={submit} className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-6 space-y-4">
        {/* Honeypot — hidden from people, bots fill it in */}
        <input type="text" name="website" value={form.website} onChange={set('website')} tabIndex={-1} autoComplete="off" aria-hidden="true" className="hidden" />

        <div>
          <label className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest mb-1">Category</label>
          <select required value={form.category} onChange={set('category')} className={INPUT}>
            <option value="">Pick a category…</option>
            {BUG_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
          </select>
        </div>

        {form.category === 'other' && (
          <div>
            <label className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest mb-1">What kind of bug?</label>
            <input required maxLength={150} value={form.customCategory} onChange={set('customCategory')} className={INPUT} placeholder="Short label" />
          </div>
        )}

        <div>
          <label className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest mb-1">Subject</label>
          <input required maxLength={200} value={form.subject} onChange={set('subject')} className={INPUT} placeholder="One line summary" />
        </div>

        <div>
          <label className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest mb-1">What happened?</label>
          <textarea required rows={6} maxLength={5000} value={form.description} onChange={set('description')} className={INPUT} placeholder="What you did, what you expected, what happened instead. Where on the server or site?" />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest mb-1">In-game name <span className="text-text-warm/40 normal-case">(optional)</span></label>
            <input maxLength={16} value={form.ign} onChange={set('ign')} className={INPUT} placeholder="Your Minecraft name" />
          </div>
          <div>
            <label className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest mb-1">Contact <span className="text-text-warm/40 normal-case">(optional)</span></label>
            <input maxLength={200} value={form.contact} onChange={set('contact')} className={INPUT} placeholder="Email or Discord, if we may ask questions" />
          </div>
        </div>

        {error && <p className="text-xs text-rose-400">{error}</p>}

        <button
          type="submit"
          disabled={busy}
          className="w-full py-2.5 rounded-lg text-xs font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white transition-all cursor-pointer font-serif disabled:opacity-50 flex items-center justify-center gap-2"
        >
          <Send className="w-4 h-4" /> {busy ? 'Sending…' : 'Send report'}
        </button>
        <p className="text-[10px] text-text-warm/40 leading-relaxed">
          We store what you type here. Contact details are optional and only used to follow up on this report.
        </p>
      </form>
    </div>
  );
}
