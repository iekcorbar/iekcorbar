'use client';

import React, { useState, useEffect } from 'react';
import { ShieldCheck, AlertTriangle, RefreshCw, Clock, CheckCircle2, XCircle, Users } from 'lucide-react';
import { useUser } from '../providers';

const STATUS_STYLE = {
  pending: { icon: Clock, label: 'Pending Review', className: 'bg-sp-gold/10 border-sp-gold/40 text-sp-gold' },
  approved: { icon: CheckCircle2, label: 'Approved', className: 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400' },
  declined: { icon: XCircle, label: 'Declined', className: 'bg-crimson/10 border-crimson/40 text-rose-400' },
};

const EMPTY_FORM = {
  age: '', availability: '', timezone: '', discordUsername: '', experience: '', referenceName: '', reason: '', contribution: ''
};

export default function StaffApplicationPage() {
  const { user, setIsLoginOpen } = useUser();
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [justSubmitted, setJustSubmitted] = useState(false);

  useEffect(() => {
    if (!user) return;
    fetchMine();
  }, [user]);

  async function fetchMine() {
    try {
      const res = await fetch('/api/staff-applications?mine=1');
      const data = await res.json();
      if (res.ok && data.applications) setApplications(data.applications);
    } catch (err) {
      console.error('Failed to load your staff applications:', err);
    } finally {
      setLoading(false);
    }
  }

  function set(field) {
    return e => setForm(prev => ({ ...prev, [field]: e.target.value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const res = await fetch('/api/staff-applications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'submit', ...form, age: Number(form.age) })
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || 'Could not submit your application.');
        return;
      }
      setJustSubmitted(true);
      setForm(EMPTY_FORM);
      fetchMine();
    } catch (err) {
      console.error('Failed to submit staff application:', err);
      setError('Could not submit your application — please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  // Members-only, same boundary as /known-bugs — the button that links here
  // only ever appears on the (already members-only) Profile page, but the
  // route itself must not be reachable while signed out either.
  if (!user) {
    return (
      <div className="max-w-md mx-auto my-12 bg-sp-dark/95 border-2 border-crimson p-8 rounded-xl text-center steampunk-border shadow-2xl space-y-6">
        <div className="w-16 h-16 bg-crimson/15 rounded-full flex items-center justify-center border border-crimson text-rose-400 mx-auto">
          <AlertTriangle className="w-8 h-8" />
        </div>
        <h2 className="font-serif text-2xl font-bold text-rose-300 uppercase tracking-wider">Access Restrained</h2>
        <p className="text-sm text-text-warm/85 leading-relaxed">
          The Staff Application is open to registered members only — sign in with your iekcorbar account first.
        </p>
        <button
          onClick={() => setIsLoginOpen(true)}
          className="w-full py-2.5 rounded-lg text-xs font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white transition-all cursor-pointer font-serif border border-transparent shadow-md"
        >
          Sign In / Register
        </button>
      </div>
    );
  }

  const pending = applications.find(a => a.status === 'pending');
  const mostRecent = applications[0];

  return (
    <div className="max-w-3xl mx-auto space-y-8">
      {/* HEADER */}
      <div className="border-b border-sp-bronze pb-5">
        <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
          <ShieldCheck className="w-8 h-8 text-sp-copper" /> Staff Application
        </h1>
        <p className="text-xs text-text-warm/60 mt-1 leading-relaxed">
          Apply for the Moderator role on iekcorbar.
        </p>
      </div>

      {/* Explainer — what the role is, and explicitly that it's unpaid */}
      <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-5 space-y-4">
        <div className="flex items-start gap-3">
          <Users className="w-6 h-6 text-sp-gold shrink-0 mt-0.5" />
          <div className="text-xs text-text-warm/80 leading-relaxed space-y-2">
            <p>
              A <span className="text-sp-gold font-bold">Moderator</span> helps keep iekcorbar fun and fair: answering
              player questions, enforcing the <a href="/rules" className="text-sp-gold underline hover:text-white">server rules</a>,
              handling reports and bug reports, and generally being a trustworthy, active presence in the community —
              both in-game and on this website&apos;s Staff Panel.
            </p>
            <p className="text-sp-gold/90 font-semibold">
              This is a volunteer position — it is not a paid job, and it never comes with any real-money compensation.
              Staff do it because they enjoy the server and want to help take care of it.
            </p>
            <p>
              Every application is reviewed by the admin team. We&apos;ll reach out to you (in-game, by email, or via a
              bell notification here) if we want to talk further — you don&apos;t need to do anything else after submitting.
            </p>
          </div>
        </div>
      </div>

      {/* Existing application status, if any */}
      {loading ? (
        <p className="text-xs text-text-warm/40 text-center py-4">Loading your applications...</p>
      ) : mostRecent && (
        <div className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-5 space-y-2">
          <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide">Your Most Recent Application</h3>
          <div className="flex items-center gap-2 flex-wrap">
            {(() => {
              const s = STATUS_STYLE[mostRecent.status] || STATUS_STYLE.pending;
              const Icon = s.icon;
              return (
                <span className={`inline-flex items-center gap-1.5 text-[10px] px-2.5 py-1 rounded uppercase font-mono font-bold border ${s.className}`}>
                  <Icon className="w-3 h-3" /> {s.label}
                </span>
              );
            })()}
            <span className="text-[10px] text-text-warm/40">
              Submitted {new Date(mostRecent.createdAt).toLocaleDateString()}
            </span>
          </div>
          {mostRecent.status === 'declined' && (
            <p className="text-xs text-text-warm/60">You&apos;re welcome to submit a new application below.</p>
          )}
        </div>
      )}

      {/* Form — hidden while a pending application already exists, or right
          after a fresh submission */}
      {pending ? (
        <div className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-6 text-center space-y-2">
          <Clock className="w-8 h-8 text-sp-gold mx-auto" />
          <p className="text-sm font-bold text-white">Your application is being reviewed</p>
          <p className="text-xs text-text-warm/50">Staff will contact you once a decision has been made — no need to apply again in the meantime.</p>
        </div>
      ) : justSubmitted ? (
        <div className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-6 text-center space-y-2">
          <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto" />
          <p className="text-sm font-bold text-emerald-400">Application submitted</p>
          <p className="text-xs text-text-warm/50">Thanks for applying — staff will review it and reach out.</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-6 space-y-4">
          <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2">
            Application Form
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="text-left">
              <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Age</label>
              <input
                type="number"
                required
                min={10}
                max={99}
                value={form.age}
                onChange={set('age')}
                placeholder="e.g. 17"
                className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
              />
            </div>
            <div className="text-left">
              <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Timezone (optional)</label>
              <input
                type="text"
                value={form.timezone}
                onChange={set('timezone')}
                placeholder="e.g. CET / GMT+1"
                className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
              />
            </div>
          </div>

          <div className="text-left">
            <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Estimated Time Online / Availability</label>
            <input
              type="text"
              required
              value={form.availability}
              onChange={set('availability')}
              placeholder="e.g. ~15 hours/week, mostly evenings and weekends"
              className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
            />
          </div>

          <div className="text-left">
            <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Reference</label>
            <input
              type="text"
              required
              value={form.referenceName}
              onChange={set('referenceName')}
              placeholder="An existing staff member or player who can vouch for you"
              className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
            />
            <p className="text-[10px] text-text-warm/40 mt-1">No one in mind yet? Get to know the community a bit first, then apply.</p>
          </div>

          <div className="text-left">
            <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Discord Username (optional)</label>
            <input
              type="text"
              value={form.discordUsername}
              onChange={set('discordUsername')}
              placeholder="e.g. yourname"
              className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
            />
          </div>

          <div className="text-left">
            <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Prior Staff / Moderation Experience (optional)</label>
            <textarea
              rows={3}
              value={form.experience}
              onChange={set('experience')}
              placeholder="Any other server or community where you've moderated before, if any"
              className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
            />
          </div>

          <div className="text-left">
            <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Why do you want to become staff?</label>
            <textarea
              required
              rows={4}
              value={form.reason}
              onChange={set('reason')}
              placeholder="What makes you want to join the team?"
              className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
            />
          </div>

          <div className="text-left">
            <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">What can you add to iekcorbar?</label>
            <textarea
              required
              rows={4}
              value={form.contribution}
              onChange={set('contribution')}
              placeholder="Skills, experience, or qualities you'd bring to the team"
              className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
            />
          </div>

          {error && <p className="text-xs text-rose-400">{error}</p>}

          <button
            type="submit"
            disabled={submitting || !form.age || !form.availability.trim() || !form.referenceName.trim() || !form.reason.trim() || !form.contribution.trim()}
            className="w-full py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-black bg-sp-gold hover:bg-white disabled:opacity-50 transition-all cursor-pointer font-serif flex items-center justify-center gap-2 shadow-md"
          >
            {submitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <>Submit Application</>}
          </button>
        </form>
      )}
    </div>
  );
}
