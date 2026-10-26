'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import ShareButton from '@/components/ShareButton';
import { Smartphone, MessageSquare, CalendarCheck, Lightbulb, Bell, Download, Apple, X, Sparkles } from 'lucide-react';

// Homepage call-to-action for the app (Android APK / iPhone home screen).
// Hidden for people already using it (native app user agent, or the site
// running standalone from a home screen), and dismissable for 14 days.
const DISMISS_KEY = 'iekcorbar_app_promo_dismissed';
const DISMISS_DAYS = 14;

const BENEFITS = [
  { icon: MessageSquare, title: 'Instant chat notifications', text: "Know right away when someone messages you — even with the app closed." },
  { icon: CalendarCheck, title: 'Never miss an event', text: 'Reminders the day before and 1 hour before, and sign up with one tap.' },
  { icon: Bell, title: 'All news in your pocket', text: 'New events, projects, polls, wiki pages, shop items and more.' },
  { icon: Lightbulb, title: 'Daily tip at 10:00', text: 'A handy Iekcorbar tip every morning to level up faster.' }
];

// variant="compact": slim one-line bar for the top of the homepage on
// phones (the full block sits below the tall hero, out of view on mobile).
export default function AppPromo({ variant = 'full' }) {
  const [mode, setMode] = useState(null); // null = hidden, 'android' | 'iphone' | 'desktop'

  useEffect(() => {
    try {
      const ua = navigator.userAgent || '';
      const standalone = window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true;
      if (ua.includes('IekcorbarApp/') || standalone) return; // already using it
      const dismissed = Number(localStorage.getItem(DISMISS_KEY) || 0);
      if (dismissed && Date.now() - dismissed < DISMISS_DAYS * 86400000) return;
      const isIphone = /iPhone|iPad|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
      setMode(isIphone ? 'iphone' : /Android/.test(ua) ? 'android' : 'desktop');
    } catch {
      setMode('desktop');
    }
  }, []);

  if (!mode) return null;

  if (variant === 'compact') {
    if (mode === 'desktop') return null; // phones only
    return (
      <Link
        href={mode === 'iphone' ? '/app#iphone' : '/app'}
        className="lg:hidden flex items-center gap-3 rounded-xl border border-sp-gold/60 bg-gradient-to-r from-sp-gold/15 to-neutral-950 px-4 py-3"
      >
        <span className="w-10 h-10 rounded-lg bg-sp-gold/20 border border-sp-gold/40 flex items-center justify-center shrink-0">
          <Smartphone className="w-5 h-5 text-sp-gold" />
        </span>
        <span className="flex-1 min-w-0">
          <span className="block text-sm font-bold text-white leading-tight">Get the Iekcorbar app</span>
          <span className="block text-[11px] text-text-warm/65 leading-snug">Chat &amp; event notifications, daily tip — free</span>
        </span>
        <span className="shrink-0 px-3 py-1.5 rounded-lg bg-sp-gold text-black text-[11px] font-bold uppercase tracking-wider">
          {mode === 'iphone' ? 'Add' : 'Get it'}
        </span>
      </Link>
    );
  }

  const dismiss = () => {
    try { localStorage.setItem(DISMISS_KEY, String(Date.now())); } catch { /* ignore */ }
    setMode(null);
  };

  const androidBtn = (
    <Link href="/app" className={`flex items-center justify-center gap-2 px-5 py-3 rounded-lg font-bold uppercase tracking-wider text-sm whitespace-nowrap transition-all ${
      mode === 'iphone' ? 'border border-sp-bronze/60 text-text-warm/85 hover:border-sp-gold hover:text-sp-gold' : 'bg-sp-gold hover:bg-white text-black shadow-md shadow-sp-gold/20'
    }`}>
      <Download className="w-4 h-4" /> Android app
    </Link>
  );
  const iphoneBtn = (
    <Link href="/app#iphone" className={`flex items-center justify-center gap-2 px-5 py-3 rounded-lg font-bold uppercase tracking-wider text-sm whitespace-nowrap transition-all ${
      mode === 'iphone' ? 'bg-sp-gold hover:bg-white text-black shadow-md shadow-sp-gold/20' : 'border border-sp-bronze/60 text-text-warm/85 hover:border-sp-gold hover:text-sp-gold'
    }`}>
      <Apple className="w-4 h-4" /> iPhone
    </Link>
  );

  return (
    <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold/70 bg-gradient-to-br from-neutral-950 via-sp-dark to-neutral-900 shadow-xl shadow-sp-gold/10">
      <button
        type="button"
        onClick={dismiss}
        aria-label="Hide this for now"
        title="Hide this for now"
        className="absolute top-3 right-3 p-1.5 rounded-md text-text-warm/40 hover:text-white hover:bg-white/5 z-10"
      >
        <X className="w-4 h-4" />
      </button>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 p-6 sm:p-8">
        <div className="lg:col-span-2 flex flex-col justify-center gap-4 text-center lg:text-left">
          <span className="inline-flex items-center gap-1.5 self-center lg:self-start px-2.5 py-1 rounded-full bg-sakura-accent/15 border border-sakura-accent/40 text-[10px] font-bold uppercase tracking-widest text-sakura-pink">
            <Sparkles className="w-3 h-3" /> New · Free
          </span>
          <h2 className="font-serif text-2xl sm:text-3xl font-bold text-sp-gold leading-tight flex items-center justify-center lg:justify-start gap-2">
            <Smartphone className="w-7 h-7 text-sp-copper shrink-0" /> Iekcorbar in your pocket
          </h2>
          <p className="text-sm text-text-warm/80 leading-relaxed">
            Get the Iekcorbar app and stay connected with the server and your friends — wherever you are.
            It takes less than a minute to install.
          </p>
          <div className="flex flex-col sm:flex-row lg:flex-col xl:flex-row gap-2">
            {mode === 'iphone' ? <>{iphoneBtn}{androidBtn}</> : <>{androidBtn}{iphoneBtn}</>}
          </div>
          <div className="flex flex-col sm:flex-row items-center gap-3 justify-center lg:justify-start">
            <p className="text-[11px] text-text-warm/45">No ads · no tracking · you choose which notifications you get</p>
            <ShareButton
              url="/app"
              title="Iekcorbar in your pocket"
              text="Get the Iekcorbar app: chat notifications, event reminders and a daily tip — Android & iPhone."
              label="Share with a friend"
              className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-sp-copper hover:text-sp-gold shrink-0"
            />
          </div>
        </div>

        <div className="lg:col-span-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
          {BENEFITS.map(({ icon: Icon, title, text }) => (
            <div key={title} className="bg-neutral-900/50 border border-sp-bronze/30 rounded-xl p-4 space-y-1.5">
              <p className="flex items-center gap-2 font-semibold text-white text-sm">
                <span className="w-8 h-8 rounded-lg bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center shrink-0">
                  <Icon className="w-4 h-4 text-sp-gold" />
                </span>
                {title}
              </p>
              <p className="text-xs text-text-warm/70 leading-relaxed">{text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
