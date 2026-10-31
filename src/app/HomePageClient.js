'use client';

import React, { useState, useEffect } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useUser } from './providers';
import AppPromo from '@/components/AppPromo';
import {
  Copy,
  Check,
  Users,
  Compass,
  Gamepad2,
  Flame,
  Globe,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  HeartHandshake,
  MessageSquare,
  Shield,
  Activity,
  ChevronDown,
  Download,
  UserPlus,
  Lightbulb,
  Calendar,
  Clock,
  ArrowRight,
  Newspaper,
  Rocket
} from 'lucide-react';

export default function HomePage() {
  const { user, setIsLoginOpen, setIsRegisterMode } = useUser();
  const [copied, setCopied] = useState(false);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [carouselIndex, setCarouselIndex] = useState(0);
  const [guideOpen, setGuideOpen] = useState(false);
  const [guideSteps, setGuideSteps] = useState([]);
  const [javaVersion, setJavaVersion] = useState('25');
  const [todaysTip, setTodaysTip] = useState(null);
  // Latest News & Upcoming teaser — a handful of the most recent /news
  // posts, so a visitor sees at a glance that something's happening without
  // having to click through. Hidden entirely if nothing's been posted yet.
  const [latestNews, setLatestNews] = useState([]);

  // Upcoming Event promo — same "nearest future event" logic as /events'
  // own countdown, just surfaced here too since the homepage never
  // mentioned events at all before this.
  const [events, setEvents] = useState([]);
  const [eventCountdown, setEventCountdown] = useState(null);

  // Fetch server status
  useEffect(() => {
    async function fetchStatus() {
      try {
        const res = await fetch('/api/status', { cache: 'no-store' });
        const data = await res.json();
        setStatus(data);
      } catch (err) {
        console.error("Failed to load status API", err);
      } finally {
        setLoading(false);
      }
    }
    async function fetchGuide() {
      try {
        const res = await fetch('/api/guide');
        const data = await res.json();
        if (data.guide) setGuideSteps(data.guide);
      } catch (err) {
        console.error("Failed to load guide API", err);
      }
    }
    async function fetchSettings() {
      try {
        const res = await fetch('/api/settings');
        const data = await res.json();
        if (data.settings?.java_version) setJavaVersion(data.settings.java_version);
      } catch (err) {
        console.error("Failed to load settings API", err);
      }
    }
    async function fetchTip() {
      try {
        const res = await fetch('/api/tips');
        const data = await res.json();
        if (data.todaysTip) setTodaysTip(data.todaysTip);
      } catch (err) {
        console.error("Failed to load tips API", err);
      }
    }
    async function fetchEvents() {
      try {
        const res = await fetch('/api/events');
        const data = await res.json();
        if (Array.isArray(data.events)) setEvents(data.events);
      } catch (err) {
        console.error("Failed to load events API", err);
      }
    }
    async function fetchNews() {
      try {
        const res = await fetch('/api/news');
        const data = await res.json();
        if (Array.isArray(data.posts)) {
          const sorted = [...data.posts].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
          setLatestNews(sorted.slice(0, 3));
        }
      } catch (err) {
        console.error("Failed to load news API", err);
      }
    }
    fetchStatus();
    fetchGuide();
    fetchSettings();
    fetchTip();
    fetchEvents();
    fetchNews();

    // Auto-refresh Live Broadcast every 30 seconds (works in production too)
    const statusInterval = setInterval(fetchStatus, 30000);
    return () => clearInterval(statusInterval);
  }, []);

  // /api/status' `address` is the staff-editable server_address setting
  // verbatim (see db.getSettings()'s comment) — shown as-is so a bare
  // hostname backed by an SRV record isn't given a bogus default port.
  // Falls back to the real current address while /api/status hasn't
  // resolved yet.
  const serverAddress = status?.address || 'play.iekcorbar.com';

  const copyIp = () => {
    navigator.clipboard.writeText(serverAddress);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const DEFAULT_SLIDES = [
    {
      src: "/images/castle.png",
      title: "The Copper Shogunate Capital",
      desc: "Our primary hub where ancient Japanese architecture blends seamlessly with steam power, cherry blossoms, and mechanical airships."
    },
    {
      src: "/images/boss.png",
      title: "Weekly Boss Raid: Shogun Automaton",
      desc: "Assemble your squad at the Adventure Guild to battle high-level mechanical elites and loot legendary custom trinkets."
    }
  ];

  const [carouselImages, setCarouselImages] = useState(DEFAULT_SLIDES);

  useEffect(() => {
    async function fetchCarousel() {
      try {
        const res = await fetch('/api/carousel');
        const data = await res.json();
        if (data.items) {
          setCarouselImages(data.items);
        }
      } catch (err) {
        console.error("Failed to load carousel API", err);
      }
    }
    fetchCarousel();
  }, []);

  const handlePrevCarousel = () => {
    setCarouselIndex((prev) => (prev === 0 ? carouselImages.length - 1 : prev - 1));
  };

  const handleNextCarousel = () => {
    setCarouselIndex((prev) => (prev === carouselImages.length - 1 ? 0 : prev + 1));
  };

  // Carousel Auto-play
  useEffect(() => {
    if (carouselImages.length <= 1) return;
    const timer = setInterval(() => {
      handleNextCarousel();
    }, 6000);
    return () => clearInterval(timer);
  }, [carouselImages]);

  // Reset index if it gets out of bounds (e.g. when database items are deleted)
  useEffect(() => {
    if (carouselIndex >= carouselImages.length) {
      setCarouselIndex(0);
    }
  }, [carouselImages, carouselIndex]);

  // Nearest upcoming event — same date/time parsing (assumes the server's
  // UTC+2 timezone unless the event says otherwise) and live tick as the
  // /events page's own countdown, just surfaced here too.
  useEffect(() => {
    if (events.length === 0) { setEventCountdown(null); return; }

    const timer = setInterval(() => {
      const now = new Date().getTime();
      const futureEvents = events
        .map(e => ({ ...e, ts: new Date(`${e.date}T${e.time}:00+02:00`).getTime() }))
        .filter(e => e.ts > now)
        .sort((a, b) => a.ts - b.ts);

      if (futureEvents.length > 0) {
        const next = futureEvents[0];
        const diff = next.ts - now;
        setEventCountdown({
          event: next,
          days: Math.floor(diff / (1000 * 60 * 60 * 24)),
          hours: Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)),
          minutes: Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60)),
          seconds: Math.floor((diff % (1000 * 60)) / 1000)
        });
      } else {
        setEventCountdown(null);
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [events]);

  return (
    <div className="space-y-12 pb-12">
      
      {/* Compact app bar — phones only, right above the (tall) hero */}
      <AppPromo variant="compact" />

      {/* HERO SECTION */}
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-8 sm:p-12 text-center shadow-xl shadow-sp-gold/5 bg-black">
        {/* Background Banner Image */}
        <div className="absolute inset-0 pointer-events-none select-none z-0">
          <img src="/images/banner.png" alt="" className="w-full h-full object-cover opacity-70" />
          <div className="absolute inset-0 bg-gradient-to-b from-black/60 via-black/20 to-black/90" />
        </div>

        {/* Decorative Steam / Smoke Effect */}
        <div className="absolute inset-0 pointer-events-none opacity-20 z-0">
          <div className="absolute bottom-4 left-1/4 w-12 h-12 bg-white rounded-full filter blur-xl animate-steam-rise" style={{ animationDelay: '0s' }} />
          <div className="absolute bottom-8 left-1/2 w-16 h-16 bg-white rounded-full filter blur-xl animate-steam-rise" style={{ animationDelay: '1.5s' }} />
          <div className="absolute bottom-2 right-1/3 w-10 h-10 bg-white rounded-full filter blur-xl animate-steam-rise" style={{ animationDelay: '3s' }} />
        </div>

        <div className="relative z-10 max-w-3xl mx-auto space-y-6">
          <div className="flex items-center justify-center gap-3 flex-wrap">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-emerald-500/40 bg-emerald-900/20 text-emerald-300 text-xs font-semibold uppercase tracking-wider">
              ☕ Java {javaVersion}
            </div>
          </div>

          <div className="flex justify-center mb-2">
            <div className="w-32 h-32 md:w-36 md:h-36 rounded-full border-4 border-sp-gold overflow-hidden shadow-2xl shadow-sp-gold/45 bg-neutral-950 flex items-center justify-center">
              <img src="/images/logo.png" alt="Logo" className="w-full h-full object-cover" />
            </div>
          </div>
          
          <h1 className="font-serif text-4xl sm:text-6xl font-black text-sp-gold tracking-wider sm:tracking-widest uppercase steampunk-glow">
            iekcorbar
          </h1>
          <p className="text-sm font-medium tracking-widest text-sakura-pink/90 uppercase">
            桜と蒸気 &bull; Sakura and Steam RPG
          </p>

          <p className="text-text-warm/80 max-w-xl mx-auto text-sm sm:text-base leading-relaxed">
            Embark on a unique Minecraft adventure. Harness steam technology, declare your race, master custom jobs, claim your land, and craft artisanal beverages in our cherry-blossom themed RPG world.
          </p>

          {/* Click to Copy IP Box */}
          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
            <button
              onClick={copyIp}
              className="flex items-center gap-3 px-6 py-4 bg-neutral-900/90 border-2 border-sp-gold rounded-xl hover:border-white transition-all group cursor-pointer relative overflow-hidden"
            >
              <div className="text-left">
                <span className="block text-[10px] uppercase font-bold text-sp-gold/60 tracking-wider">Server Access Address</span>
                <span className="font-mono text-lg text-white font-bold tracking-wider">{serverAddress}</span>
              </div>
              <div className="p-2 rounded-lg bg-sp-gold/10 group-hover:bg-sp-gold group-hover:text-black text-sp-gold transition-colors ml-4">
                {copied ? <Check className="w-5 h-5 text-green-400" /> : <Copy className="w-5 h-5" />}
              </div>
            </button>

            {/* Quick Action Buttons */}
            <a
              href="https://discord.gg/G9P2khU7F4"
              target="_blank"
              rel="noreferrer"
              className="w-full sm:w-auto px-6 py-4 rounded-xl border-2 border-sakura-pink/40 bg-sakura-dark/20 text-white font-serif hover:bg-sakura-dark hover:border-sakura-pink transition-all flex items-center justify-center gap-2 cursor-pointer shadow-md"
            >
              <MessageSquare className="w-5 h-5" /> Join Discord
            </a>
          </div>
          
          {copied && (
            <p className="text-green-400 text-xs font-bold animate-pulse">
              IP Address copied to clipboard! Open Minecraft and add server.
            </p>
          )}

          {/* Register CTA — the site account (profile, stats, events, voting,
              friends) is separate from just joining the Minecraft server
              above, so this gets its own clear call-to-action rather than
              being buried as a link inside the Login modal. */}
          {!user && (
            <div className="pt-2 flex flex-col sm:flex-row items-center justify-center gap-3">
              <div className="hidden sm:block h-px w-10 bg-sp-bronze/40" />
              <p className="text-xs text-text-warm/70">
                New here? Create a free account to track your stats, join events, and vote for rewards.
              </p>
              <button
                onClick={() => { setIsLoginOpen(true); setIsRegisterMode(true); }}
                className="px-5 py-2.5 rounded-lg bg-sp-gold hover:bg-white border border-transparent text-black font-bold uppercase text-xs tracking-wider transition-all cursor-pointer shadow-md shadow-sp-gold/20 flex items-center gap-2 shrink-0"
              >
                <UserPlus className="w-4 h-4" /> Create Your Account
              </button>
            </div>
          )}
        </div>
      </section>

      {/* PROJECT NOTICE — added 2026-10-26 alongside the donation section, as
          the plain-spoken legal framing the user asked for on the homepage
          itself: private hobby project (no company/KvK), the Shop only ever
          trades in-game currency that can't be bought with real money, and
          donations are purely voluntary. Kept compact so it doesn't compete
          with the hero CTA. */}
      <section className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl px-5 py-4 text-center space-y-1">
        <p className="text-xs text-text-warm/70 leading-relaxed">
          <HeartHandshake className="w-4 h-4 text-sp-copper inline-block mr-1.5" />
          <strong className="text-white">iekcorbar is a private community project, not a company.</strong>{' '}
          The Shop works entirely with in-game currencies you earn by playing — nothing here is ever sold for real money.
          Running the server does cost us money, so voluntary donations are welcome, never expected.
        </p>
        <p className="text-[10px] text-text-warm/40">
          <Link href="/donate" className="hover:text-white underline">Support the project</Link> ·{' '}
          <Link href="/terms" className="hover:text-white underline">Terms</Link> ·{' '}
          <Link href="/privacy" className="hover:text-white underline">Privacy</Link>
        </p>
      </section>

      {/* APP CTA — Android app / iPhone home screen (hidden for people already using it) */}
      <AppPromo />

      {/* UPCOMING EVENT PROMO — hidden entirely if nothing's scheduled in
          the future, same pattern as the Tip of the Day / Carousel sections
          below. Links out to /events for registration rather than
          duplicating the torch-toggle registration UI here. */}
      {eventCountdown && (
        <section className="relative rounded-2xl overflow-hidden border-2 border-sakura-accent shadow-xl shadow-sakura-accent/10">
          {eventCountdown.event.image && (
            <>
              <img src={eventCountdown.event.image} alt="" className="absolute inset-0 w-full h-full object-cover" />
              <div className="absolute inset-0 bg-linear-to-r from-black via-black/70 to-black/40" />
            </>
          )}
          <div className={`relative p-6 sm:p-8 flex flex-col md:flex-row md:items-center md:justify-between gap-6 ${eventCountdown.event.image ? '' : 'bg-sp-dark/80'}`}>
            <div className="space-y-2 text-left">
              <span className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-sakura-accent">
                <Calendar className="w-3.5 h-3.5" /> Upcoming Event
              </span>
              <h2 className="font-serif text-2xl sm:text-3xl font-bold text-white tracking-wide">{eventCountdown.event.title}</h2>
              <p className="text-xs sm:text-sm text-text-warm/80 max-w-xl leading-relaxed">{eventCountdown.event.description}</p>
              <p className="text-[11px] text-text-warm/60 flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5" /> {eventCountdown.event.date} at {eventCountdown.event.time} ({eventCountdown.event.timezone})
              </p>
            </div>

            <div className="flex flex-col items-center gap-3 shrink-0">
              <div className="flex items-center gap-2 sm:gap-3">
                {[['D', eventCountdown.days], ['H', eventCountdown.hours], ['M', eventCountdown.minutes], ['S', eventCountdown.seconds]].map(([label, value]) => (
                  <div key={label} className="w-14 sm:w-16 bg-black/50 border border-sp-gold/40 rounded-lg py-2 text-center">
                    <span className="block font-serif text-xl sm:text-2xl font-black text-sp-gold">{String(value).padStart(2, '0')}</span>
                    <span className="block text-[8px] uppercase font-bold text-text-warm/50">{label}</span>
                  </div>
                ))}
              </div>
              <Link
                href="/events"
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-sp-gold hover:bg-white text-black text-xs font-bold uppercase tracking-wider rounded-lg transition-all"
              >
                View & Register <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        </section>
      )}

      {/* STARTER GUIDE ACCORDION */}
      <section className="bg-neutral-900/40 border border-sp-bronze/60 rounded-xl overflow-hidden backdrop-blur-xs">
        <button
          onClick={() => setGuideOpen(!guideOpen)}
          className="w-full flex items-center justify-between p-5 text-left transition-all hover:bg-sp-charcoal/30 cursor-pointer group"
        >
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-sp-gold/10 flex items-center justify-center border border-sp-gold/30 text-sp-gold group-hover:scale-105 transition-transform">
              <Compass className="w-4.5 h-4.5" />
            </div>
            <div>
              <h3 className="font-serif text-sm sm:text-base text-white font-bold tracking-wide uppercase group-hover:text-sp-gold transition-colors">
                Starter guide
              </h3>
              <p className="text-[10px] sm:text-xs text-text-warm/60 mt-0.5">
                Welcome to Iekcorbar, a challenging server with a lot to discover! For essential information click here &rarr;
              </p>
            </div>
          </div>
          <ChevronDown className={`w-5 h-5 text-sp-gold transition-transform duration-300 ${guideOpen ? 'rotate-180' : ''}`} />
        </button>

        {guideOpen && (
          <div className="p-5 border-t border-sp-bronze/25 bg-neutral-950/20 space-y-4 animate-fadeIn">
            {guideSteps.length > 0 ? (
              <ul className="space-y-3.5 text-xs sm:text-sm text-text-warm/90">
                {guideSteps.map((step, i) => {
                  const isImageAttachment = step.attachmentUrl && /\.(png|jpe?g|gif|webp|svg)(\?|$)/i.test(step.attachmentUrl);
                  return (
                    <li key={step.id} className="flex items-start gap-3">
                      <span className={`w-1.5 h-1.5 rounded-full mt-2 shrink-0 ${
                        i % 3 === 0 ? 'bg-sp-gold' : i % 3 === 1 ? 'bg-sakura-pink' : 'bg-sp-copper'
                      }`} />
                      <div className="flex-1 min-w-0">
                        <span dangerouslySetInnerHTML={{ __html: step.content
                          .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
                          .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" class="text-sp-gold hover:text-white font-bold underline transition-colors">$1</a>')
                        }} />
                        {step.attachmentUrl && (
                          isImageAttachment ? (
                            <a href={step.attachmentUrl} target="_blank" rel="noopener noreferrer" className="block mt-2">
                              <img src={step.attachmentUrl} alt={step.attachmentName || step.title} className="max-h-40 rounded-lg border border-sp-bronze/30" />
                            </a>
                          ) : (
                            <a
                              href={step.attachmentUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              download
                              className="inline-flex items-center gap-1.5 mt-2 text-[10px] sm:text-xs font-bold uppercase tracking-wider text-sp-gold hover:text-white bg-sp-gold/10 hover:bg-sp-gold/20 border border-sp-gold/30 rounded-lg px-2.5 py-1.5 transition-colors"
                            >
                              <Download className="w-3.5 h-3.5" /> {step.attachmentName || 'Download attachment'}
                            </a>
                          )
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="text-xs text-text-warm/40 italic">No guide steps configured yet.</p>
            )}
          </div>
        )}
      </section>

      {/* DYNAMIC LIVE STATUS & STEAM PRESSURE CHART */}
      <section className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Server & Discord Live Status */}
        <div className="lg:col-span-1 bg-sp-dark/70 border border-sp-bronze p-6 rounded-xl flex flex-col justify-between space-y-6 steampunk-glass">
          <div className="border-b border-sp-bronze pb-3 flex items-center justify-between">
            <h3 className="font-serif text-lg text-sp-gold flex items-center gap-2 tracking-wide uppercase">
              <Activity className="w-5 h-5 text-sp-copper" /> Live Broadcast
            </h3>
            <span className="flex h-2.5 w-2.5 relative">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-green-500"></span>
            </span>
          </div>

          {loading ? (
            <div className="text-center py-12 text-text-warm/40 text-sm">
              Connecting to transmitter...
            </div>
          ) : (
            <div className="space-y-5">
              {/* Server Stats */}
              <div className="flex items-center justify-between p-3 rounded-lg bg-neutral-900/50 border border-sp-bronze/30">
                <div className="flex items-center gap-3">
                  <Gamepad2 className="w-5 h-5 text-sp-gold" />
                  <div>
                    <span className="block text-[10px] uppercase font-bold text-text-warm/50">Minecraft Server</span>
                    <span className="text-sm font-semibold text-white">Online</span>
                  </div>
                </div>
                <div className="text-right">
                  <span className="block text-xs font-bold text-sp-gold">{status?.players?.online || 0} / {status?.players?.max || 100} Players</span>
                  <span className="text-[10px] text-text-warm/40">{status?.version}</span>
                </div>
              </div>

              {/* Discord Stats — the avatar row only appears once the guild
                  has "Enable Server Widget" turned on (Discord Server
                  Settings -> Widget); read-only member presence, no chat
                  content, see api/status. */}
              <div className="p-3 rounded-lg bg-neutral-900/50 border border-sp-bronze/30 space-y-2.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <MessageSquare className="w-5 h-5 text-sakura-accent" />
                    <div>
                      <span className="block text-[10px] uppercase font-bold text-text-warm/50">Discord Signal</span>
                      <span className="text-sm font-semibold text-white">Active</span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="block text-xs font-bold text-sakura-pink">{status?.discord?.onlineCount || 0} Online</span>
                    <span className="text-[10px] text-text-warm/40">{status?.discord?.membersCount || 0} members</span>
                  </div>
                </div>

                {status?.discord?.widget?.members?.length > 0 && (
                  <div className="flex items-center gap-1.5 pt-1 border-t border-sp-bronze/15">
                    <span className="text-[9px] uppercase font-bold text-text-warm/40 shrink-0">Online now:</span>
                    <div className="flex -space-x-2">
                      {status.discord.widget.members.slice(0, 8).map((m, i) => (
                        <img
                          key={i}
                          src={m.avatarUrl}
                          alt={m.username}
                          title={m.username}
                          className="w-6 h-6 rounded-full border-2 border-sp-dark bg-neutral-800"
                        />
                      ))}
                    </div>
                    {status.discord.widget.members.length > 8 && (
                      <span className="text-[10px] text-text-warm/40 ml-1">+{status.discord.widget.members.length - 8} more</span>
                    )}
                  </div>
                )}
              </div>

              {/* Extra Economy Widget */}
              <div className="p-3 bg-neutral-900/40 border border-sp-bronze/20 rounded-lg text-center">
                <span className="block text-[10px] uppercase font-bold text-text-warm/40">Claims protected</span>
                <span className="text-lg font-bold text-white font-serif">{status?.stats?.totalClaims || 0}</span>
              </div>

              {/* Online Player List */}
              <div className="space-y-1.5">
                <span className="block text-[10px] uppercase font-bold text-text-warm/50 tracking-wider">Active Crew Members</span>
                <div className="flex flex-wrap gap-1.5 max-h-24 overflow-y-auto pr-1">
                  {Array.isArray(status?.players?.list) && status.players.list.length > 0 ? (
                    status.players.list.map((p, i) => (
                      <Link
                        key={i}
                        href={`/members/${p}`}
                        title={`View ${p}'s profile`}
                        className="text-xs bg-sp-charcoal/80 border border-sp-bronze/35 text-text-warm/95 px-2 py-0.5 rounded-sm hover:border-sp-gold hover:text-sp-gold transition-colors cursor-pointer"
                      >
                        {p}
                      </Link>
                    ))
                  ) : (
                    <span className="text-xs text-text-warm/40 italic">No pilots currently active on server.</span>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Steam Pressure Trend Chart (SVG) */}
        <div className="lg:col-span-2 bg-sp-dark/70 border border-sp-bronze p-6 rounded-xl flex flex-col justify-between space-y-6 steampunk-glass">
          <div className="border-b border-sp-bronze pb-3 flex items-center justify-between">
            <h3 className="font-serif text-lg text-sp-gold flex items-center gap-2 tracking-wide uppercase">
              <Globe className="w-5 h-5 text-sp-copper" /> Steam Pressure (Weekly Players)
            </h3>
            <div className="flex items-center gap-2 bg-green-950/40 border border-green-500/20 px-2 py-0.5 rounded-sm">
              <span className="text-[9px] font-bold text-green-400 tracking-wider uppercase font-mono">Synced</span>
              <span className="flex h-2 w-2 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
              </span>
            </div>
          </div>

          {loading ? (
            <div className="text-center py-20 text-text-warm/40 text-sm">
              Calibrating steam pressure gauges...
            </div>
          ) : (
            <div className="flex flex-col md:flex-row items-center justify-around gap-6 py-4">
              
              {/* Master Circular Gauge */}
              <div className="relative w-52 h-52 flex items-center justify-center shrink-0">
                <svg className="w-full h-full overflow-visible" viewBox="0 0 200 200">
                  <defs>
                    <radialGradient id="dialBezel" cx="50%" cy="50%" r="50%">
                      <stop offset="70%" stopColor="#121215" />
                      <stop offset="88%" stopColor="#2c1a0e" />
                      <stop offset="96%" stopColor="#8c5225" />
                      <stop offset="100%" stopColor="#d4af37" />
                    </radialGradient>
                    <linearGradient id="needleGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#dc2626" />
                      <stop offset="60%" stopColor="#b91c1c" />
                      <stop offset="100%" stopColor="#d4af37" />
                    </linearGradient>
                  </defs>

                  {/* Outer Bezel (Copper Ring with Gold Rim) */}
                  <circle cx="100" cy="100" r="92" fill="url(#dialBezel)" stroke="#8c5225" strokeWidth="3.5" className="shadow-2xl" />
                  
                  {/* Bezel Rivets (12 Gold Points around the dial rim) */}
                  {Array.from({ length: 12 }).map((_, i) => {
                    const angle = i * 30;
                    const cos = Math.cos((angle - 90) * Math.PI / 180);
                    const sin = Math.sin((angle - 90) * Math.PI / 180);
                    return (
                      <circle key={i} cx={100 + 87 * cos} cy={100 + 87 * sin} r="2.2" fill="#d4af37" stroke="#3b200c" strokeWidth="0.5" />
                    );
                  })}

                  <circle cx="100" cy="100" r="82" fill="none" stroke="#d4af37" strokeWidth="1" strokeDasharray="3 5" opacity="0.3" />

                  {/* Faint Cherry Blossom decorative background path in center */}
                  <path d="M 100 65 C 97 75, 90 77, 100 85 C 110 77, 103 75, 100 65 Z" fill="rgba(244,114,182,0.06)" />
                  <path d="M 100 135 C 97 125, 90 123, 100 115 C 110 123, 103 125, 100 135 Z" fill="rgba(244,114,182,0.06)" />
                  <path d="M 65 100 C 75 97, 77 90, 85 100 C 77 110, 75 103, 65 100 Z" fill="rgba(244,114,182,0.06)" />
                  <path d="M 135 100 C 125 97, 123 90, 115 100 C 123 110, 125 103, 135 100 Z" fill="rgba(244,114,182,0.06)" />

                  {/* Danger Zone (Crimson Red Arc from 80% to 100%) */}
                  <path d="M 143.4 43.4 A 80 80 0 0 1 156.6 156.6" fill="none" stroke="#be185d" strokeWidth="5.5" strokeLinecap="round" opacity="0.75" />

                  {/* Rotating Brass Gear in Background */}
                  <g className="animate-spin-gear-ccw" style={{ transformOrigin: '100px 100px', opacity: 0.12 }}>
                    <circle cx="100" cy="100" r="24" fill="none" stroke="#d4af37" strokeWidth="3" strokeDasharray="6 4" />
                    <circle cx="100" cy="100" r="18" fill="none" stroke="#d4af37" strokeWidth="1.5" />
                  </g>

                  {/* Gauge Tick Marks */}
                  {Array.from({ length: 21 }).map((_, i) => {
                    const angle = -135 + i * 13.5;
                    const cos = Math.cos((angle - 90) * Math.PI / 180);
                    const sin = Math.sin((angle - 90) * Math.PI / 180);
                    const isMajor = i % 5 === 0;
                    const r1 = isMajor ? 70 : 75;
                    const r2 = 80;
                    return (
                      <line
                        key={i}
                        x1={100 + r1 * cos}
                        y1={100 + r1 * sin}
                        x2={100 + r2 * cos}
                        y2={100 + r2 * sin}
                        stroke={i >= 16 ? "#be185d" : "#d4af37"}
                        strokeWidth={isMajor ? "2" : "1"}
                        opacity={isMajor ? "0.85" : "0.4"}
                      />
                    );
                  })}

                  {/* Kanji Labels at key marks */}
                  {/* 0 (〇), 25 (二五), 50 (五〇), 75 (七五), 100 (百) */}
                  {[
                    { a: -135, t: "〇" },
                    { a: -67.5, t: "二五" },
                    { a: 0, t: "五〇" },
                    { a: 67.5, t: "七五" },
                    { a: 135, t: "百" }
                  ].map((lbl, idx) => {
                    const cos = Math.cos((lbl.a - 90) * Math.PI / 180);
                    const sin = Math.sin((lbl.a - 90) * Math.PI / 180);
                    const r = 58;
                    return (
                      <text
                        key={idx}
                        x={100 + r * cos}
                        y={100 + r * sin + 3.5}
                        fill="#d4af37"
                        fontSize="9.5"
                        fontFamily="serif"
                        fontWeight="bold"
                        textAnchor="middle"
                        opacity="0.8"
                      >
                        {lbl.t}
                      </text>
                    );
                  })}

                  {/* Central labels */}
                  <text x="100" y="130" fill="#f4ebd0" fontSize="8" fontFamily="serif" textAnchor="middle" opacity="0.45" letterSpacing="2">
                    蒸気圧
                  </text>
                  <text x="100" y="152" fill="#ffb7c5" fontSize="16" fontFamily="monospace" fontWeight="bold" textAnchor="middle" className="steampunk-glow">
                    {status?.players?.online || 0} PSI
                  </text>

                  {/* Mechanical Needle */}
                  <g style={{
                    transform: `rotate(${-135 + (Math.min(status?.players?.max || 100, Math.max(0, status?.players?.online || 0)) / (status?.players?.max || 100)) * 270}deg)`,
                    transformOrigin: '100px 100px',
                    transition: 'transform 1.8s cubic-bezier(0.25, 1, 0.5, 1)'
                  }}>
                    {/* The pointer spear */}
                    <path d="M 97 100 L 100 15 L 103 100 Z" fill="url(#needleGrad)" />
                    {/* Needle circular counterweight tail */}
                    <circle cx="100" cy="116" r="6" fill="#8c5225" stroke="#d4af37" strokeWidth="1" />
                    <line x1="100" y1="100" x2="100" y2="122" stroke="#d4af37" strokeWidth="1.5" />
                    
                    {/* Central rivet caps */}
                    <circle cx="100" cy="100" r="11" fill="#8c5225" stroke="#d4af37" strokeWidth="1.8" />
                    <circle cx="100" cy="100" r="5" fill="#d4af37" />
                    <circle cx="100" cy="100" r="2" fill="#121215" />
                  </g>
                </svg>

                {/* Circular Glass Reflection Cover overlay */}
                <div className="absolute inset-0 rounded-full bg-gradient-to-tr from-transparent via-white/5 to-white/10 pointer-events-none" />
              </div>

              {/* Log History stats - 7 small bar cylinders representing daily peaks */}
              <div className="flex-1 w-full space-y-4">
                <span className="block text-[10px] uppercase font-bold text-text-warm/40 tracking-wider text-center md:text-left">Weekly Pressure Logs (Daily Peaks)</span>
                
                <div className="grid grid-cols-7 gap-2">
                  {status?.weeklyTrend?.map((pt, idx) => {
                    const percentage = Math.min(100, Math.max(5, ((pt.players || 0) / (status?.players?.max || 100)) * 100));
                    return (
                      <div key={idx} className="flex flex-col items-center gap-2 group cursor-help relative">
                        {/* Daily Tooltip */}
                        <div className="opacity-0 group-hover:opacity-100 transition-opacity bg-neutral-950 border border-sp-gold rounded px-1.5 py-0.5 text-[9px] text-center absolute -top-8 z-20 whitespace-nowrap shadow-md pointer-events-none">
                          <span className="block font-bold text-white">{pt.players} Players</span>
                          <span className="text-sp-gold">Peak: {pt.peakTime}</span>
                        </div>

                        {/* Steam Tube cylinder container */}
                        <div className="w-4 h-24 bg-neutral-950 border border-sp-bronze/45 rounded-sm relative overflow-hidden flex flex-col justify-end">
                          <div 
                            className="w-full bg-gradient-to-t from-sp-bronze via-sp-gold to-sakura-accent rounded-xs transition-all duration-1000"
                            style={{ height: `${percentage}%` }}
                          />
                        </div>

                        <span className="text-[9px] uppercase font-bold text-text-warm/40 font-mono">{pt.day}</span>
                      </div>
                    );
                  })}
                </div>
              </div>

            </div>
          )}
        </div>
      </section>

      {/* TIP OF THE DAY — one active tip, picked deterministically per
          calendar day server-side (see api/tips), not re-randomized on
          every visit. Hidden entirely if no active tips exist. */}
      {todaysTip && (
        <section className="bg-sp-dark/60 border border-sp-gold/30 rounded-xl p-4 flex items-start gap-3">
          <div className="w-8 h-8 rounded-lg bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center shrink-0">
            <Lightbulb className="w-4 h-4 text-sp-gold" />
          </div>
          <div>
            <span className="block text-[9px] uppercase font-bold text-sp-gold/70 tracking-wider">Tip of the Day</span>
            <p className="text-sm text-text-warm/85">{todaysTip.content}</p>
          </div>
        </section>
      )}

      {/* LATEST NEWS & UPCOMING — teaser for the /news page (Minecraft news
          + planned server/website features). Hidden entirely if nothing's
          been posted yet, same pattern as the Tip of the Day / Event promo
          sections above. */}
      {latestNews.length > 0 && (
        <section className="bg-sp-dark/80 border border-sp-bronze/60 rounded-xl p-5 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <h2 className="font-serif text-sm font-bold text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <Newspaper className="w-4 h-4 text-sp-copper" /> News &amp; Upcoming
            </h2>
            <Link href="/news" className="text-[10px] font-bold uppercase text-sp-copper hover:text-sp-gold flex items-center gap-1 shrink-0">
              View All <ArrowRight className="w-3 h-3" />
            </Link>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {latestNews.map(post => {
              const isUpcoming = post.type === 'upcoming';
              return (
                <Link
                  key={post.id}
                  href="/news"
                  className="block bg-neutral-950/40 border border-sp-bronze/25 hover:border-sp-gold/60 rounded-lg p-3 space-y-1.5 transition-all"
                >
                  <span className={`inline-flex items-center gap-1 text-[8px] px-1.5 py-0.5 border font-bold uppercase tracking-wider rounded ${
                    isUpcoming ? 'bg-sp-copper/10 border-sp-copper/40 text-sp-copper' : 'bg-sp-gold/10 border-sp-gold/40 text-sp-gold'
                  }`}>
                    {isUpcoming ? <><Rocket className="w-2.5 h-2.5" /> Upcoming</> : 'News'}
                  </span>
                  <h3 className="text-xs font-bold text-white leading-snug line-clamp-2">{post.title}</h3>
                  <span className="block text-[9px] text-text-warm/40 font-mono">{post.topic}</span>
                </Link>
              );
            })}
          </div>
        </section>
      )}

      {/* SCREENSHOT & ARTWORK CAROUSEL */}
      {carouselImages.length > 0 && (
        <section className="bg-sp-dark/80 border-2 border-sp-bronze rounded-xl overflow-hidden relative shadow-lg">
          
          {/* Slides */}
          <div className="relative h-[250px] sm:h-[400px] w-full">
            <img
              src={carouselImages[carouselIndex].src}
              alt={carouselImages[carouselIndex].title}
              className="w-full h-full object-cover"
            />
            {/* Overlay gradient */}
            <div className="absolute inset-0 bg-linear-to-t from-black via-black/40 to-transparent" />
            
            {/* Info Card */}
            <div className="absolute bottom-6 left-6 right-6 sm:left-10 sm:right-10 text-left max-w-2xl space-y-2">
              <h3 className="font-serif text-xl sm:text-2xl font-bold text-sp-gold tracking-wide uppercase">
                {carouselImages[carouselIndex].title}
              </h3>
              <p className="text-xs sm:text-sm text-text-warm/90 leading-relaxed">
                {carouselImages[carouselIndex].desc}
              </p>
            </div>
          </div>

          {/* Manual Slides Controls */}
          <button 
            onClick={handlePrevCarousel}
            className="absolute left-4 top-1/2 -translate-y-1/2 p-2 bg-sp-dark/80 hover:bg-sp-gold hover:text-black border border-sp-bronze hover:border-sp-gold text-sp-gold rounded-full transition-all cursor-pointer z-10"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>
          <button 
            onClick={handleNextCarousel}
            className="absolute right-4 top-1/2 -translate-y-1/2 p-2 bg-sp-dark/80 hover:bg-sp-gold hover:text-black border border-sp-bronze hover:border-sp-gold text-sp-gold rounded-full transition-all cursor-pointer z-10"
          >
            <ChevronRight className="w-5 h-5" />
          </button>

          {/* Indicators */}
          <div className="absolute bottom-4 right-6 flex items-center gap-1.5">
            {carouselImages.map((_, i) => (
              <button
                key={i}
                onClick={() => setCarouselIndex(i)}
                className={`w-2.5 h-2.5 rounded-full transition-all cursor-pointer ${
                  i === carouselIndex ? 'bg-sp-gold scale-125' : 'bg-sp-bronze hover:bg-sp-copper'
                }`}
              />
            ))}
          </div>
        </section>
      )}

      {/* CORE FEATURES & lore */}
      <section className="grid grid-cols-1 md:grid-cols-3 gap-6">
        
        <div className="bg-neutral-900/50 border border-sp-bronze/55 p-6 rounded-xl hover:border-sp-gold/60 transition-all flex flex-col justify-between space-y-4">
          <div className="space-y-2">
            <div className="w-10 h-10 bg-sp-gold/10 rounded-lg flex items-center justify-center border border-sp-gold/30 text-sp-gold">
              <Compass className="w-5 h-5" />
            </div>
            <h4 className="font-serif text-lg text-white font-bold">RPG Progression & Skills</h4>
            <p className="text-xs text-text-warm/75 leading-relaxed">
              Ascend in rank within the Adventure Guild, complete rich storyline quests, unlock powerful combat trinkets, and master elite dungeons alongside teammates.
            </p>
          </div>
          <a href="/wiki#races-of-iekcorbar" className="text-xs text-sp-gold hover:text-white flex items-center gap-1 hover:underline">
            Read Wiki guidelines &rarr;
          </a>
        </div>

        <div className="bg-neutral-900/50 border border-sp-bronze/55 p-6 rounded-xl hover:border-sp-gold/60 transition-all flex flex-col justify-between space-y-4">
          <div className="space-y-2">
            <div className="w-10 h-10 bg-sakura-pink/10 rounded-lg flex items-center justify-center border border-sakura-pink/30 text-sakura-pink">
              <Shield className="w-5 h-5" />
            </div>
            <h4 className="font-serif text-lg text-white font-bold">Protected Lands claim</h4>
            <p className="text-xs text-text-warm/75 leading-relaxed">
              Safeguard your steampunk factories and traditional shogunate pagodas. Claim land chunks dynamically and manage advanced collaboration policies in-game.
            </p>
          </div>
          <a href="/wiki#claiming-territory" className="text-xs text-sp-gold hover:text-white flex items-center gap-1 hover:underline">
            Read Lands guidelines &rarr;
          </a>
        </div>

        <div className="bg-neutral-900/50 border border-sp-bronze/55 p-6 rounded-xl hover:border-sp-gold/60 transition-all flex flex-col justify-between space-y-4">
          <div className="space-y-2">
            <div className="w-10 h-10 bg-sp-copper/10 rounded-lg flex items-center justify-center border border-sp-copper/30 text-sp-copper">
              <Flame className="w-5 h-5" />
            </div>
            <h4 className="font-serif text-lg text-white font-bold">The Steam Brewery</h4>
            <p className="text-xs text-text-warm/75 leading-relaxed">
              Set up cauldrons, ferment secret recipes (like Sakura Blossom Sake or Steam Stout), age liquids in barrels for years, and trade special beverages on the Villager Market.
            </p>
          </div>
          <a href="/sakagura" className="text-xs text-sp-gold hover:text-white flex items-center gap-1 hover:underline">
            Sakagura Guide &rarr;
          </a>
        </div>

      </section>

    </div>
  );
}
