'use client';

import React, { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Palette, Check, RotateCcw, Eye, ImagePlus, Trash2, Upload } from 'lucide-react';
import {
  PROFILE_BANNERS, HIDEABLE_SECTIONS, BIO_MAX_LENGTH,
  BANNER_MAX_STILL_BYTES, BANNER_MAX_ANIMATED_BYTES, BANNER_MIN_WIDTH
} from '@/lib/profile-theme';

// "Customize profile" panel on /profile. Edits a local draft and reports every
// change up through onDraftChange so the hero above can preview it live;
// nothing is stored until Save (PUT /api/profile/settings, own account only).
// The custom banner image is the one exception: choosing a file previews it
// locally, and "Use this banner" uploads it straight away (POST
// /api/profile/banner), because a file can't ride along in the settings JSON.
export default function ProfileCustomize({ saved, mcUsername, onDraftChange, onSaved, onBannerChange }) {
  const [bio, setBio] = useState(saved.bio || '');
  const [banner, setBanner] = useState(saved.banner || 'gears');
  const [hidden, setHidden] = useState(saved.hidden || []);
  const [pos, setPos] = useState(saved.bannerPos ?? 50);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null); // { ok, text }

  // Custom banner: a file picked but not uploaded yet (previewed in the hero).
  const [pending, setPending] = useState(null); // { file, url }
  const [uploading, setUploading] = useState(false);
  const [bannerMsg, setBannerMsg] = useState(null);
  const fileRef = useRef(null);

  // Re-sync when the saved copy arrives/changes (initial load, after Save).
  useEffect(() => {
    setBio(saved.bio || '');
    setBanner(saved.banner || 'gears');
    setHidden(saved.hidden || []);
    setPos(saved.bannerPos ?? 50);
  }, [saved.bio, saved.banner, saved.hidden, saved.bannerPos]); // eslint-disable-line react-hooks/exhaustive-deps

  const dirty = bio !== (saved.bio || '') || banner !== (saved.banner || 'gears') || pos !== (saved.bannerPos ?? 50)
    || [...hidden].sort().join() !== [...(saved.hidden || [])].sort().join();

  const hasImage = !!(pending || saved.bannerImage);

  useEffect(() => {
    onDraftChange(dirty || pending ? { bio, banner, hidden, bannerPos: pos, bannerImage: pending ? pending.url : saved.bannerImage } : null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bio, banner, hidden, pos, dirty, pending, saved.bannerImage]);

  // Don't leave a stale preview (or an object URL) behind when leaving the tab.
  useEffect(() => () => onDraftChange(null), []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => () => { if (pending) URL.revokeObjectURL(pending.url); }, [pending]);

  const toggle = (key) => setHidden(h => (h.includes(key) ? h.filter(k => k !== key) : [...h, key]));

  function pickFile(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setBannerMsg(null);
    if (!/^image\/(png|jpeg|gif|webp)$/.test(file.type)) {
      setBannerMsg({ ok: false, text: 'Please choose a PNG, JPEG, GIF or WebP image.' });
      return;
    }
    // Quick client-side check; the server re-checks from the file's real bytes
    // (and knows whether an animated file is allowed to be the larger size).
    if (file.size > BANNER_MAX_ANIMATED_BYTES) {
      setBannerMsg({ ok: false, text: `That file is too large (max ${BANNER_MAX_ANIMATED_BYTES / 1024 / 1024} MB).` });
      return;
    }
    setPending({ file, url: URL.createObjectURL(file) });
  }

  async function uploadPending() {
    if (!pending) return;
    setUploading(true);
    setBannerMsg(null);
    try {
      const form = new FormData();
      form.append('file', pending.file);
      const res = await fetch('/api/profile/banner', { method: 'POST', body: form });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not upload the banner');
      setPending(null);
      onBannerChange(data.bannerImage);
      setBannerMsg({ ok: true, text: data.animated ? 'Animated banner uploaded.' : 'Banner uploaded.' });
    } catch (err) {
      setBannerMsg({ ok: false, text: err.message || 'Could not upload the banner' });
    } finally {
      setUploading(false);
    }
  }

  async function removeBanner() {
    setBannerMsg(null);
    try {
      const res = await fetch('/api/profile/banner', { method: 'DELETE' });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not remove the banner');
      onBannerChange(null);
      setBannerMsg({ ok: true, text: 'Custom banner removed.' });
    } catch (err) {
      setBannerMsg({ ok: false, text: err.message || 'Could not remove the banner' });
    }
  }

  async function save() {
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/profile/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bio, banner, hidden, bannerPos: pos })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not save');
      onSaved({ bio: data.bio, banner: data.banner, hidden: data.hidden, bannerPos: data.bannerPos });
      setMessage({ ok: true, text: 'Profile saved.' });
    } catch (err) {
      setMessage({ ok: false, text: err.message || 'Could not save' });
    } finally {
      setSaving(false);
    }
  }

  function discard() {
    setBio(saved.bio || '');
    setBanner(saved.banner || 'gears');
    setHidden(saved.hidden || []);
    setPos(saved.bannerPos ?? 50);
    setPending(null);
    setMessage(null);
  }

  return (
    <div className="lg:col-span-2 bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-7 text-left">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-sp-bronze/20 pb-3">
        <div>
          <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
            <Palette className="w-5 h-5 text-sp-copper" /> Customize your profile
          </h3>
          <p className="text-[11px] text-text-warm/50 mt-0.5">Changes show up in the preview above straight away. Nothing is public until you press Save.</p>
        </div>
        {mcUsername && (
          <Link href={`/members/${encodeURIComponent(mcUsername)}`} className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-sp-gold hover:text-white">
            <Eye className="w-3.5 h-3.5" /> View public profile
          </Link>
        )}
      </div>

      {/* Banner: preset themes + your own image */}
      <div className="space-y-3">
        <h4 className="text-[10px] uppercase font-bold tracking-wider text-sp-gold/80">Banner theme</h4>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {PROFILE_BANNERS.map(b => (
            <button
              key={b.key}
              type="button"
              onClick={() => setBanner(b.key)}
              aria-pressed={banner === b.key}
              className={`group relative rounded-xl overflow-hidden border-2 text-left cursor-pointer transition-all ${banner === b.key ? 'border-sp-gold shadow-[0_0_0_3px_rgba(212,175,55,.25)]' : 'border-sp-bronze/30 hover:border-sp-bronze'}`}
            >
              <span className="block h-14" style={{ background: b.css }} />
              <span className="flex items-center justify-between gap-1 px-2.5 py-1.5 text-[10px] font-bold text-white bg-neutral-950/70">
                {b.label}
                {banner === b.key && <Check className="w-3.5 h-3.5 text-sp-gold" />}
              </span>
            </button>
          ))}
        </div>

        <div className="bg-neutral-950/40 border border-sp-bronze/25 rounded-xl p-4 space-y-3">
          <h4 className="text-[10px] uppercase font-bold tracking-wider text-sp-gold/80 flex items-center gap-1.5"><ImagePlus className="w-3.5 h-3.5" /> Or upload your own banner</h4>
          <p className="text-[11px] text-text-warm/55 leading-relaxed">
            PNG, JPEG, GIF or WebP — <strong>animated GIF/WebP and AI-generated art are welcome</strong>. At least {BANNER_MIN_WIDTH}px wide, up to {BANNER_MAX_STILL_BYTES / 1024 / 1024} MB
            ({BANNER_MAX_ANIMATED_BYTES / 1024 / 1024} MB if animated). It must follow the server rules (no NSFW, hate or harassment) — staff can remove banners that don&apos;t.
            Wide images work best; the middle of the picture is what shows on phones.
          </p>

          <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/gif,image/webp" onChange={pickFile} className="hidden" />
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" onClick={() => fileRef.current?.click()} disabled={uploading} className="px-4 py-2 rounded-lg border border-sp-bronze/50 text-xs font-bold uppercase tracking-wider text-sp-gold hover:bg-sp-gold hover:text-black disabled:opacity-40 transition-all cursor-pointer flex items-center gap-1.5">
              <ImagePlus className="w-3.5 h-3.5" /> {saved.bannerImage || pending ? 'Choose another image' : 'Choose an image'}
            </button>
            {pending && (
              <>
                <button type="button" onClick={uploadPending} disabled={uploading} className="px-4 py-2 rounded-lg bg-sp-gold hover:bg-white text-black text-xs font-bold uppercase tracking-wider disabled:opacity-50 cursor-pointer flex items-center gap-1.5">
                  <Upload className="w-3.5 h-3.5" /> {uploading ? 'Uploading…' : 'Use this banner'}
                </button>
                <button type="button" onClick={() => setPending(null)} disabled={uploading} className="px-3 py-2 rounded-lg border border-sp-bronze/40 text-xs text-text-warm/70 hover:text-white cursor-pointer">Cancel</button>
              </>
            )}
            {saved.bannerImage && !pending && (
              <button type="button" onClick={removeBanner} className="px-3 py-2 rounded-lg border border-crimson/40 text-xs font-bold uppercase tracking-wider text-rose-300 hover:bg-crimson hover:text-white cursor-pointer flex items-center gap-1.5 transition-all">
                <Trash2 className="w-3.5 h-3.5" /> Remove my banner
              </button>
            )}
            {bannerMsg && <span className={`text-xs ${bannerMsg.ok ? 'text-emerald-400' : 'text-rose-400'}`}>{bannerMsg.text}</span>}
          </div>

          {hasImage && (
            <label className="flex items-center gap-3 text-[10px] uppercase font-bold tracking-wider text-text-warm/55">
              Vertical position
              <input type="range" min="0" max="100" value={pos} onChange={e => setPos(Number(e.target.value))} className="flex-1 max-w-xs accent-amber-500" />
              <span className="font-mono text-text-warm/40 w-8">{pos}%</span>
            </label>
          )}
          {pending && <p className="text-[10px] text-amber-300/80">Preview only — press <strong>Use this banner</strong> to upload it. The position is saved with <strong>Save profile</strong>.</p>}
        </div>
      </div>

      {/* Bio */}
      <div className="space-y-2">
        <div className="flex items-end justify-between">
          <h4 className="text-[10px] uppercase font-bold tracking-wider text-sp-gold/80">About you</h4>
          <span className={`text-[10px] font-mono ${bio.length > BIO_MAX_LENGTH - 20 ? 'text-amber-400' : 'text-text-warm/40'}`}>{bio.length}/{BIO_MAX_LENGTH}</span>
        </div>
        <textarea
          value={bio}
          maxLength={BIO_MAX_LENGTH}
          rows={4}
          onChange={e => setBio(e.target.value)}
          placeholder="A line or two about who you are on the server — favourite build, what you're brewing, who you're looking for…"
          className="w-full bg-neutral-900 border border-sp-bronze text-white text-sm rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-y"
        />
        <p className="text-[10px] text-text-warm/40">Visible to everyone who opens your profile. Keep it friendly — staff can clear a bio that breaks the rules.</p>
      </div>

      {/* Visibility */}
      <div className="space-y-2.5">
        <h4 className="text-[10px] uppercase font-bold tracking-wider text-sp-gold/80">What visitors can see</h4>
        <p className="text-[10px] text-text-warm/40">Switch a section off to hide it from other players. You always see everything on your own profile, and your logbook is only ever shared with friends.</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {HIDEABLE_SECTIONS.map(s => {
            const visible = !hidden.includes(s.key);
            return (
              <button
                key={s.key}
                type="button"
                role="switch"
                aria-checked={visible}
                onClick={() => toggle(s.key)}
                className="flex items-center justify-between gap-3 bg-neutral-950/40 border border-sp-bronze/25 hover:border-sp-bronze/60 rounded-lg px-3.5 py-2.5 cursor-pointer transition-all"
              >
                <span className="text-xs text-white">{s.label}</span>
                <span className={`relative w-10 h-5 rounded-full transition-colors ${visible ? 'bg-emerald-600' : 'bg-neutral-700'}`}>
                  <span className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all ${visible ? 'left-[22px]' : 'left-0.5'}`} />
                </span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 pt-1">
        <button
          type="button"
          onClick={save}
          disabled={!dirty || saving}
          className="px-6 py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-black bg-sp-gold hover:bg-white disabled:opacity-40 disabled:cursor-default transition-all cursor-pointer font-serif"
        >
          {saving ? 'Saving…' : 'Save profile'}
        </button>
        <button
          type="button"
          onClick={discard}
          disabled={(!dirty && !pending) || saving}
          className="px-4 py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-text-warm/70 border border-sp-bronze/40 hover:text-white hover:border-sp-gold disabled:opacity-30 disabled:cursor-default transition-all cursor-pointer flex items-center gap-1.5"
        >
          <RotateCcw className="w-3.5 h-3.5" /> Discard changes
        </button>
        {message && <span className={`text-xs ${message.ok ? 'text-emerald-400' : 'text-rose-400'}`}>{message.text}</span>}
      </div>
    </div>
  );
}
