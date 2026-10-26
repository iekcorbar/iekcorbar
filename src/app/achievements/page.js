'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useUser } from '../providers';
import { Trophy, Lock, Loader2, Plus, Trash2, Edit, X, Sparkles, Cog, Wrench, Gift, Check } from 'lucide-react';
import { ACHIEVEMENT_SLUGS, ACHIEVEMENT_SLUG_INFO, isKnownAchievementSlug, slugify } from '@/lib/achievements';

const CUSTOM_SLUG_OPTION = '__custom__';
const emptyForm = { id: null, slug: '', title: '', description: '', icon: 'minecraft:paper', active: true, currencyReward: '0' };

// Appends "-2", "-3", ... until the slug doesn't collide with an existing
// achievement — staff creating a custom achievement never type a slug, so
// this is the only thing standing between two similarly-titled custom
// achievements and a silent duplicate-slug write (db.saveAchievement falls
// back to db.json on a MySQL constraint error rather than surfacing it).
function uniqueCustomSlug(title, existingSlugs) {
  const base = slugify(title);
  if (!existingSlugs.has(base)) return base;
  let n = 2;
  while (existingSlugs.has(`${base}-${n}`)) n++;
  return `${base}-${n}`;
}

export default function AchievementsPage() {
  const { user } = useUser();
  const isStaff = user && (user.role === 'admin' || user.role === 'moderator');

  const [achievements, setAchievements] = useState([]);
  const [earned, setEarned] = useState([]);
  const [loading, setLoading] = useState(true);
  // Website "Gears" currency earned from achievements — see api/currency.
  const [currency, setCurrency] = useState(null);

  const [form, setForm] = useState(emptyForm);
  // Whether the slug picker is on "Custom" — true means form.slug is a
  // freely-chosen identifier with no code path that ever earns it
  // automatically, so it can only ever be handed out via the Grant control.
  const [useCustomSlug, setUseCustomSlug] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  // Manual "Grant to player" panel — only one open at a time, keyed by
  // achievement id. This is the actual answer to "what if we want a whole
  // new custom achievement": there's no trigger to wire up for it, so staff
  // hand it out here instead.
  const [grantForId, setGrantForId] = useState(null);
  const [grantUsername, setGrantUsername] = useState('');
  const [grantSubmitting, setGrantSubmitting] = useState(false);
  const [grantMessage, setGrantMessage] = useState(null); // { type: 'success'|'error', text }

  const fetchAchievements = useCallback(async () => {
    try {
      const res = await fetch('/api/achievements');
      const data = await res.json();
      setAchievements(data.achievements || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchEarned = useCallback(async () => {
    if (!user?.mcUsername) { setEarned([]); return; }
    try {
      const res = await fetch('/api/achievements/earned');
      const data = await res.json();
      setEarned(data.earned || []);
    } catch (err) {
      console.error(err);
    }
  }, [user]);

  const fetchCurrency = useCallback(async () => {
    if (!user) { setCurrency(null); return; }
    try {
      const res = await fetch('/api/currency');
      const data = await res.json();
      if (res.ok && typeof data.currency === 'number') setCurrency(data.currency);
    } catch (err) {
      console.error(err);
    }
  }, [user]);

  useEffect(() => { fetchAchievements(); }, [fetchAchievements]);
  useEffect(() => { fetchEarned(); }, [fetchEarned]);
  useEffect(() => { fetchCurrency(); }, [fetchCurrency]);

  const earnedIds = new Set(earned.map(e => e.id));
  // Which known trigger slugs already have an achievement row configured —
  // the picker below disables (and marks) those, since creating a second
  // achievement on the same trigger just means neither one earns reliably.
  // The achievement currently being edited doesn't disable its own slug.
  const slugToAchievementId = new Map(achievements.map(a => [a.slug, a.id]));

  function openNewForm() {
    setForm(emptyForm);
    setUseCustomSlug(false);
    setFormError('');
    setShowForm(true);
  }

  function openEditForm(a) {
    setForm({ id: a.id, slug: a.slug, title: a.title, description: a.description || '', icon: a.icon || 'minecraft:paper', active: !!a.active, currencyReward: String(a.currencyReward || 0) });
    setUseCustomSlug(!isKnownAchievementSlug(a.slug));
    setFormError('');
    setShowForm(true);
  }

  async function handleSave(e) {
    e.preventDefault();
    // A brand-new custom achievement never had a slug typed in — it's
    // derived from the title right here, guaranteed unique against every
    // slug already in use. Editing an existing one (new or triggered) just
    // keeps whatever's already in form.slug.
    let finalSlug = form.slug;
    if (useCustomSlug && !form.id) {
      finalSlug = uniqueCustomSlug(form.title, new Set(achievements.map(a => a.slug)));
    }
    if (!finalSlug) {
      setFormError('Pick a trigger, or switch to Custom, before saving.');
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      const res = await fetch('/api/achievements', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, slug: finalSlug })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setAchievements(data.achievements);
        setShowForm(false);
      } else {
        setFormError(data.error || 'Could not save achievement.');
      }
    } catch (err) {
      setFormError('Network error.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id) {
    if (!confirm('Delete this achievement? Players who earned it lose the record.')) return;
    try {
      const res = await fetch(`/api/achievements?id=${encodeURIComponent(id)}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) setAchievements(data.achievements);
    } catch (err) {
      console.error(err);
    }
  }

  function openGrant(id) {
    setGrantForId(prev => (prev === id ? null : id));
    setGrantUsername('');
    setGrantMessage(null);
  }

  async function submitGrant(achievementId) {
    const username = grantUsername.trim();
    if (!username) return;
    setGrantSubmitting(true);
    setGrantMessage(null);
    try {
      const res = await fetch('/api/achievements/earned', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ achievementId, username })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setGrantMessage({ type: 'success', text: data.alreadyEarned ? `${username} already has this.` : `Granted to ${username}.` });
        setGrantUsername('');
        if (user?.mcUsername && username.toLowerCase() === user.mcUsername.toLowerCase()) fetchEarned();
      } else {
        setGrantMessage({ type: 'error', text: data.error || 'Could not grant achievement.' });
      }
    } catch (err) {
      setGrantMessage({ type: 'error', text: 'Network error.' });
    } finally {
      setGrantSubmitting(false);
    }
  }

  return (
    <div className="space-y-8">
      {/* Playful "still being built" banner — steampunk hazard-tape framing
          instead of a generic yellow/black web meme, since this feature is
          freshly deployed and its in-game delivery hasn't been live-verified
          yet (see src/lib/achievements.js). Not a functional gate — the page
          underneath works fine, this is purely an expectations-setter. */}
      <div className="relative rounded-xl overflow-hidden border-2 border-sp-gold/60 shadow-lg shadow-black/30">
        <div className="steampunk-hazard-stripes h-2.5" />
        <div className="bg-sp-charcoal/95 px-5 py-4 flex items-center gap-4">
          <div className="relative shrink-0 w-12 h-12 rounded-full bg-black/40 border-2 border-sp-bronze/50 flex items-center justify-center">
            <Cog className="w-7 h-7 text-sp-gold animate-spin-gear-cw" />
          </div>
          <div className="min-w-0">
            <p className="font-serif text-sm font-bold text-sp-gold uppercase tracking-wider flex items-center gap-1.5">
              <Wrench className="w-3.5 h-3.5 shrink-0" /> Under Construction &mdash; Guild Workshop
            </p>
            <p className="text-[11px] text-text-warm/60 mt-1">
              Achievements are fresh off the forge and still being tuned on the live server &mdash; titles, icons and unlock triggers may shift a little while we tighten the bolts. Spot something broken? Let staff know.
            </p>
          </div>
        </div>
        <div className="steampunk-hazard-stripes h-2.5" />
      </div>

      <div className="border-b border-sp-bronze pb-5 flex flex-col md:flex-row md:items-end md:justify-between gap-4">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <Trophy className="w-8 h-8 text-sp-copper" /> Achievements
          </h1>
          <p className="text-xs text-text-warm/60 mt-1 max-w-2xl">
            Earned by doing things on the website — sending your first chat message, writing a logbook entry,
            making a friend, buying something from the shop, and more. Unlocking one
            shows a real in-game notification the next time you join the server, and some also pay out Gears —
            a website-only currency spendable on <a href="/shop" className="text-sp-gold hover:text-white underline">website-only shop items</a>.
          </p>
        </div>
        {user && (
          <div className="bg-neutral-900/40 border border-sp-bronze/35 px-5 py-3 rounded-xl flex items-center gap-3 shrink-0">
            <Cog className="w-6 h-6 text-sp-copper" />
            <div>
              <span className="block text-[9px] uppercase font-bold text-text-warm/40">Your Gears</span>
              <span className="font-serif text-xl font-black text-white">{currency ?? '—'}</span>
            </div>
          </div>
        )}
      </div>

      {!user && (
        <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-4 text-xs text-text-warm/60">
          Log in to track your own progress — the list below is still browsable while logged out.
        </div>
      )}

      {isStaff && (
        <button
          onClick={openNewForm}
          className="flex items-center gap-1.5 text-xs text-sp-gold hover:text-white cursor-pointer"
        >
          <Plus className="w-3.5 h-3.5" /> Add New Achievement
        </button>
      )}

      {loading ? (
        <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-sp-copper" /></div>
      ) : achievements.length === 0 ? (
        <p className="text-sm text-text-warm/50 italic">No achievements configured yet.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {achievements.map(a => {
            const unlocked = earnedIds.has(a.id);
            const earnedRow = earned.find(e => e.id === a.id);
            return (
              <div
                key={a.id}
                className={`relative bg-neutral-900/40 border rounded-xl p-4 space-y-2 ${
                  unlocked ? 'border-sp-gold/50' : 'border-sp-bronze/25'
                } ${!a.active ? 'opacity-50' : ''}`}
              >
                <div className="flex items-start gap-3">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 border ${
                    unlocked ? 'bg-sp-gold/15 border-sp-gold/40' : 'bg-black/30 border-sp-bronze/30'
                  }`}>
                    {unlocked ? <Sparkles className="w-5 h-5 text-sp-gold" /> : <Lock className="w-4 h-4 text-text-warm/30" />}
                  </div>
                  <div className="min-w-0">
                    <h3 className={`font-serif font-bold text-sm flex items-center gap-1.5 flex-wrap ${unlocked ? 'text-sp-gold' : 'text-text-warm/70'}`}>
                      {a.title}
                      {a.currencyReward > 0 && (
                        <span className="inline-flex items-center gap-0.5 text-[9px] px-1.5 py-0.5 bg-sp-copper/15 border border-sp-copper/40 text-sp-copper rounded font-bold uppercase tracking-wide">
                          <Cog className="w-2.5 h-2.5" /> +{a.currencyReward} Gears
                        </span>
                      )}
                    </h3>
                    {a.description && <p className="text-[11px] text-text-warm/50 mt-0.5">{a.description}</p>}
                    {unlocked && earnedRow?.earnedAt && (
                      <p className="text-[9px] text-sp-gold/60 mt-1 uppercase tracking-wide">
                        Earned {new Date(earnedRow.earnedAt).toLocaleDateString()}
                      </p>
                    )}
                    {!a.active && <p className="text-[9px] text-rose-300/70 mt-1 uppercase tracking-wide">Inactive (staff only)</p>}
                    {isStaff && !isKnownAchievementSlug(a.slug) && (
                      <p className="text-[9px] text-sp-copper/80 mt-1 uppercase tracking-wide">Custom — no automatic trigger, grant it below</p>
                    )}
                  </div>
                </div>
                {isStaff && (
                  <div className="pt-1 border-t border-sp-bronze/15 mt-2 space-y-2">
                    <div className="flex items-center gap-2">
                      <button onClick={() => openEditForm(a)} className="flex items-center gap-1 text-[10px] text-text-warm/50 hover:text-sp-gold cursor-pointer">
                        <Edit className="w-3 h-3" /> Edit
                      </button>
                      <button onClick={() => openGrant(a.id)} className="flex items-center gap-1 text-[10px] text-text-warm/50 hover:text-sp-gold cursor-pointer">
                        <Gift className="w-3 h-3" /> Grant
                      </button>
                      <button onClick={() => handleDelete(a.id)} className="flex items-center gap-1 text-[10px] text-text-warm/50 hover:text-rose-400 cursor-pointer">
                        <Trash2 className="w-3 h-3" /> Delete
                      </button>
                    </div>
                    {grantForId === a.id && (
                      <div className="flex items-center gap-1.5 bg-neutral-950/60 border border-sp-bronze/25 rounded-lg p-2">
                        <input
                          type="text"
                          value={grantUsername}
                          onChange={e => setGrantUsername(e.target.value)}
                          onKeyDown={e => { if (e.key === 'Enter') submitGrant(a.id); }}
                          placeholder="Minecraft username"
                          autoFocus
                          className="flex-1 min-w-0 bg-neutral-900 border border-sp-bronze/30 text-white text-[11px] rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold"
                        />
                        <button
                          onClick={() => submitGrant(a.id)}
                          disabled={grantSubmitting || !grantUsername.trim()}
                          className="shrink-0 px-2 py-1 bg-sp-gold hover:bg-white disabled:opacity-40 text-black text-[10px] font-bold uppercase rounded cursor-pointer"
                        >
                          {grantSubmitting ? '…' : 'Give'}
                        </button>
                      </div>
                    )}
                    {grantForId === a.id && grantMessage && (
                      <p className={`text-[10px] flex items-center gap-1 ${grantMessage.type === 'success' ? 'text-emerald-400' : 'text-rose-400'}`}>
                        {grantMessage.type === 'success' && <Check className="w-3 h-3" />} {grantMessage.text}
                      </p>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {showForm && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
          <form onSubmit={handleSave} className="bg-neutral-950 border border-sp-bronze/40 rounded-xl p-6 w-full max-w-md space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="font-serif text-lg font-bold text-sp-gold">{form.id ? 'Edit' : 'New'} Achievement</h2>
              <button type="button" onClick={() => setShowForm(false)} className="text-text-warm/40 hover:text-white cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div>
              <label className="block text-[10px] uppercase font-bold text-text-warm/40 mb-1">Trigger</label>
              <select
                value={useCustomSlug ? CUSTOM_SLUG_OPTION : form.slug}
                onChange={e => {
                  const val = e.target.value;
                  if (val === CUSTOM_SLUG_OPTION) {
                    setUseCustomSlug(true);
                    // Editing an already-custom achievement keeps its real
                    // slug (shown as an editable field below); a brand new
                    // one just clears it — handleSave derives it from the
                    // title at submit time.
                    if (!form.id) setForm(f => ({ ...f, slug: '' }));
                  } else {
                    setUseCustomSlug(false);
                    setForm(f => ({ ...f, slug: val }));
                  }
                }}
                required
                className="w-full bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold"
              >
                <option value="" disabled>-- Choose what earns this --</option>
                {Object.values(ACHIEVEMENT_SLUGS).map(slug => {
                  const existingId = slugToAchievementId.get(slug);
                  const taken = existingId && existingId !== form.id;
                  return (
                    <option key={slug} value={slug} disabled={!!taken}>
                      {ACHIEVEMENT_SLUG_INFO[slug]}{taken ? ' (already configured)' : ''}
                    </option>
                  );
                })}
                <option value={CUSTOM_SLUG_OPTION}>Custom — no automatic trigger, I'll grant it by hand</option>
              </select>
              {useCustomSlug ? (
                form.id ? (
                  <>
                    <input
                      type="text" value={form.slug} onChange={e => setForm(f => ({ ...f, slug: e.target.value }))}
                      required
                      className="w-full mt-1.5 bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                    />
                    <p className="text-[9px] text-text-warm/35 mt-1">Identifier for this custom achievement. No code path awards it — use the Grant button on its card.</p>
                  </>
                ) : (
                  <p className="text-[9px] text-text-warm/35 mt-1">A unique identifier will be generated from the title below. Nothing awards this automatically — use the Grant button on its card once it's saved.</p>
                )
              ) : (
                <p className="text-[9px] text-text-warm/35 mt-1">Picked from src/lib/achievements.js — this fires the moment a player does that, no code changes needed.</p>
              )}
            </div>

            <div>
              <label className="block text-[10px] uppercase font-bold text-text-warm/40 mb-1">Title</label>
              <input
                type="text" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                required
                className="w-full bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold"
              />
            </div>

            <div>
              <label className="block text-[10px] uppercase font-bold text-text-warm/40 mb-1">Description</label>
              <textarea
                value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                rows={2}
                className="w-full bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold resize-none"
              />
            </div>

            <div>
              <label className="block text-[10px] uppercase font-bold text-text-warm/40 mb-1">In-game toast icon (Minecraft item id)</label>
              <input
                type="text" value={form.icon} onChange={e => setForm(f => ({ ...f, icon: e.target.value }))}
                placeholder="minecraft:paper"
                className="w-full bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold"
              />
            </div>

            <div>
              <label className="block text-[10px] uppercase font-bold text-text-warm/40 mb-1">Gears reward (0 = none)</label>
              <input
                type="number" min="0" step="1" value={form.currencyReward}
                onChange={e => setForm(f => ({ ...f, currencyReward: e.target.value }))}
                className="w-full bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold"
              />
              <p className="text-[9px] text-text-warm/35 mt-1">Paid out once, the first time a player earns this — spendable on website-only items in the <a href="/shop" className="underline hover:text-sp-gold">Shop</a>.</p>
            </div>

            <label className="flex items-center gap-2 text-xs text-amber-300/80 cursor-pointer">
              <input type="checkbox" checked={form.active} onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} /> Active (earnable, visible to everyone)
            </label>

            {formError && <p className="text-xs text-rose-400">{formError}</p>}
            <button type="submit" disabled={saving} className="w-full px-4 py-2 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-xs font-bold uppercase rounded cursor-pointer transition-all">
              {saving ? 'Saving…' : 'Save Achievement'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
