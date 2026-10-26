'use client';

import React, { useState, useEffect } from 'react';
import { useUser } from '../providers';
import {
  Calendar,
  Clock,
  MapPin,
  Users,
  Check,
  AlertCircle,
  X,
  CalendarCheck,
  Globe,
  Cog,
  Share2
} from 'lucide-react';
import { renderMarkdown } from '@/lib/markdown';

// 'derby'/'boss' are leftover sentinel values from the old "Poster Theme"
// dropdown (now replaced by a real staff-uploaded image, see
// api/events/route.js) — still mapped here so events created before that
// change keep showing their poster. Anything else is treated as a real
// uploaded image URL; no image at all falls back to a generic default.
function getEventPosterSrc(image) {
  if (image === 'boss') return '/images/boss.png';
  if (image === 'derby' || !image) return '/images/castle.png';
  return image;
}

// Matches MAX_EVENTS_PER_PLAYER in api/events/register/route.js — the
// server is the real source of truth, this just avoids a pointless round
// trip (and the confirm modal) when the player's already at the cap.
const MAX_EVENTS_PER_PLAYER = 3;

export default function EventsClient() {
  const { user, setIsLoginOpen } = useUser();
  const [events, setEvents] = useState([]);
  const [registrations, setRegistrations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [polls, setPolls] = useState([]);
  const [countdown, setCountdown] = useState({ days: 0, hours: 0, minutes: 0, seconds: 0, active: false });
  const [showWarningModal, setShowWarningModal] = useState(false);
  const [pendingEventId, setPendingEventId] = useState(null);
  const [registerError, setRegisterError] = useState('');
  const [pollError, setPollError] = useState('');

  // User Local Timezone
  const [userTimezone, setUserTimezone] = useState('UTC');

  // Share-link support: which event's link was just copied (brief
  // "Copied!" state), and which event a ?event=<id> deep link should
  // scroll to + highlight once it's actually rendered.
  const [copiedId, setCopiedId] = useState(null);
  const [highlightedEventId, setHighlightedEventId] = useState(null);
  // Set by a "Sign up" push-notification button (?event=<id>&signup=1):
  // opens the normal sign-up confirmation for that event once data + user are ready.
  const [signupIntent, setSignupIntent] = useState(null);

  useEffect(() => {
    fetchEvents();
    fetchPolls();
    if (typeof window !== 'undefined') {
      setUserTimezone(Intl.DateTimeFormat().resolvedOptions().timeZone);
    }
  }, []);

  async function fetchEvents() {
    try {
      const res = await fetch('/api/events');
      const data = await res.json();
      const fetchedEvents = data.events || [];
      setEvents(fetchedEvents);
      setRegistrations(data.registrations || []);

      const params = new URLSearchParams(window.location.search);
      const targetId = params.get('event');
      if (targetId && fetchedEvents.some(ev => String(ev.id) === targetId)) {
        setHighlightedEventId(targetId);
        if (params.get('signup') === '1') setSignupIntent(targetId);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!highlightedEventId) return;
    const el = document.getElementById(`event-${highlightedEventId}`);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const timer = setTimeout(() => setHighlightedEventId(null), 3000);
    return () => clearTimeout(timer);
  }, [highlightedEventId, events]);

  const copyShareLink = async (event, e) => {
    e?.stopPropagation();
    const url = `${window.location.origin}/events?event=${event.id}`;
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = url;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); } catch { /* nothing more we can do */ }
      document.body.removeChild(ta);
    }
    setCopiedId(event.id);
    setTimeout(() => setCopiedId(prev => (prev === event.id ? null : prev)), 1500);
  };

  async function fetchPolls() {
    try {
      const res = await fetch('/api/polls');
      const data = await res.json();
      setPolls(data.polls || []);
    } catch (err) {
      console.error(err);
    }
  }

  const handleVote = async (pollId, optionIndex) => {
    if (!user) {
      setIsLoginOpen(true);
      return;
    }
    setPollError('');
    try {
      const res = await fetch('/api/polls', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        // Voting is keyed by email + the request's own IP (checked
        // server-side) — no Minecraft IGN link required to vote.
        body: JSON.stringify({
          action: 'vote',
          pollId,
          optionIndex,
          email: user.email
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setPolls(data.polls);
      } else if (res.status === 409) {
        // Already voted — not really an error, just re-sync so the UI
        // flips to the results view instead of leaving the button live.
        if (data.polls) setPolls(data.polls);
        setPollError(data.error || "You've already voted in this poll.");
      } else {
        setPollError(data.error || 'Failed to submit vote.');
      }
    } catch (err) {
      setPollError('Network error. Please try again.');
    }
  };

  const eventsList = Array.isArray(events) ? events : [];
  const registrationsList = Array.isArray(registrations) ? registrations : [];
  const pollsList = Array.isArray(polls) ? polls : [];

  // Calculate Countdown to nearest future event
  useEffect(() => {
    if (eventsList.length === 0) return;

    const timer = setInterval(() => {
      // Find nearest upcoming event
      const now = new Date().getTime();
      const futureEvents = eventsList
        .map(e => {
          // Parse date & time string in UTC+2 (Central European Summer Time) or given timezone
          // Simple parser assuming YYYY-MM-DD and HH:MM
          const [yr, mo, dy] = (e.date || '').split('-');
          const [hr, mn] = (e.time || '').split(':');
          // Create date in local timezone first, then offset to represent server timezone (UTC+2)
          // For simplicity we create standard ISO string:
          const eventDate = new Date(`${e.date}T${e.time}:00+02:00`); // Assuming UTC+2 server time
          return { ...e, ts: eventDate.getTime() };
        })
        .filter(e => e.ts > now)
        .sort((a, b) => a.ts - b.ts);

      if (futureEvents.length > 0) {
        const nextEvent = futureEvents[0];
        const diff = nextEvent.ts - now;

        setCountdown({
          title: nextEvent.title,
          days: Math.floor(diff / (1000 * 60 * 60 * 24)),
          hours: Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)),
          minutes: Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60)),
          seconds: Math.floor((diff % (1000 * 60)) / 1000),
          active: true
        });
      } else {
        setCountdown({ days: 0, hours: 0, minutes: 0, seconds: 0, active: false });
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [events]);

  const handleRegister = async (eventId, action) => {
    if (!user || !user.mcUsername) return;
    const username = user.mcUsername;
    setRegisterError('');

    // Snapshot so a rejected request (e.g. cap hit) can be reverted instead
    // of leaving the torch showing a state the server didn't actually accept.
    const previousRegistrations = registrationsList;

    // Optimistic UI update so torch lights up / goes out instantly
    if (action === 'register') {
      setRegistrations(prev => [...(Array.isArray(prev) ? prev : []), { id: Date.now(), eventId: String(eventId), username }]);
    } else {
      setRegistrations(prev => (Array.isArray(prev) ? prev : []).filter(r => !(String(r.eventId) === String(eventId) && String(r.username).toLowerCase() === username.toLowerCase())));
    }

    try {
      const res = await fetch('/api/events/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventId,
          username,
          action
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setRegistrations(Array.isArray(data.registrations) ? data.registrations : []);
      } else {
        setRegistrations(previousRegistrations);
        setRegisterError(data.error || 'Failed to update registration');
      }
    } catch (err) {
      setRegistrations(previousRegistrations);
      setRegisterError('Network error. Please try again.');
    }
  };

  const myRegistrationCount = user
    ? registrationsList.filter(r => String(r.username).toLowerCase() === String(user.mcUsername || '').toLowerCase()).length
    : 0;

  const onTorchToggle = (eventId, registered) => {
    if (!user) {
      setIsLoginOpen(true);
      return;
    }
    if (registered) {
      handleRegister(eventId, 'unregister');
    } else if (myRegistrationCount >= MAX_EVENTS_PER_PLAYER) {
      setRegisterError(`You're already signed up for ${MAX_EVENTS_PER_PLAYER} events — that's the max at once. Leave one first to join another.`);
    } else {
      setPendingEventId(eventId);
      setShowWarningModal(true);
    }
  };

  useEffect(() => {
    if (!signupIntent || loading) return;
    if (!user) { setIsLoginOpen(true); return; } // re-runs after logging in
    const eventId = signupIntent;
    setSignupIntent(null);
    window.history.replaceState(null, '', `/events?event=${encodeURIComponent(eventId)}`);
    if (!isUserRegistered(eventId)) onTorchToggle(eventId, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signupIntent, loading, user]);

  const confirmRegistration = () => {
    if (pendingEventId) {
      handleRegister(pendingEventId, 'register');
      setPendingEventId(null);
    }
    setShowWarningModal(false);
  };

  const isUserRegistered = (eventId) => {
    if (!user || !user.mcUsername) return false;
    return registrationsList.some(
      r => String(r.eventId) === String(eventId) && String(r.username).toLowerCase() === String(user.mcUsername).toLowerCase()
    );
  };

  const getRegistrantCount = (eventId) => {
    return registrationsList.filter(r => String(r.eventId) === String(eventId)).length;
  };



  // Convert Server Time (assuming input is in UTC+2) to Local Browser Time
  const formatLocalTime = (dateStr, timeStr) => {
    try {
      const serverDate = new Date(`${dateStr}T${timeStr}:00+02:00`);
      return serverDate.toLocaleString(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short'
      });
    } catch (err) {
      return `${dateStr} ${timeStr}`;
    }
  };

  const isStaff = user && (user.role === 'admin' || user.role === 'moderator');

  return (
    <div className="space-y-8">
      
      {/* HEADER & ADD EVENT TRIGGER */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-sp-bronze pb-4">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <Calendar className="w-8 h-8 text-sp-copper" /> Community Chronicles & Events
          </h1>
          <p className="text-xs text-text-warm/60 mt-1">Sign up for upcoming weekly derby races and automaton raids.</p>
        </div>

        {user && (
          <div className={`shrink-0 px-3 py-1.5 rounded-lg border text-xs font-bold uppercase tracking-wider ${
            myRegistrationCount >= MAX_EVENTS_PER_PLAYER
              ? 'border-crimson/40 bg-crimson/10 text-rose-300'
              : 'border-sp-bronze/40 bg-neutral-900/40 text-text-warm/70'
          }`}>
            {myRegistrationCount} / {MAX_EVENTS_PER_PLAYER} events joined
          </div>
        )}
      </div>

      {/* COUNTDOWN GAUGE */}
      {countdown.active && (
        <section className="bg-linear-to-r from-neutral-950 via-sp-dark to-neutral-950 border-2 border-sp-gold p-6 rounded-xl relative overflow-hidden steampunk-border shadow-xl text-center">
          <div className="absolute top-2 left-2 opacity-5">
            <Clock className="w-32 h-32 text-white" />
          </div>
          <div className="relative z-10 max-w-2xl mx-auto space-y-4">
            <span className="block text-[10px] uppercase font-bold text-sakura-pink tracking-widest">TRANSMISSION: Next Live Event Approaches</span>
            <h2 className="font-serif text-xl sm:text-2xl font-bold text-white uppercase tracking-wide truncate">
              {countdown.title}
            </h2>
            
            {/* Clock Grid */}
            <div className="grid grid-cols-4 gap-2 sm:gap-4 max-w-md mx-auto pt-2">
              <div className="bg-neutral-900 border border-sp-bronze p-2 rounded-lg">
                <span className="block font-mono text-xl sm:text-3xl font-black text-sp-gold">{String(countdown.days).padStart(2, '0')}</span>
                <span className="text-[9px] uppercase font-bold text-text-warm/40 font-sans">Days</span>
              </div>
              <div className="bg-neutral-900 border border-sp-bronze p-2 rounded-lg">
                <span className="block font-mono text-xl sm:text-3xl font-black text-sp-gold">{String(countdown.hours).padStart(2, '0')}</span>
                <span className="text-[9px] uppercase font-bold text-text-warm/40 font-sans">Hours</span>
              </div>
              <div className="bg-neutral-900 border border-sp-bronze p-2 rounded-lg">
                <span className="block font-mono text-xl sm:text-3xl font-black text-sp-gold">{String(countdown.minutes).padStart(2, '0')}</span>
                <span className="text-[9px] uppercase font-bold text-text-warm/40 font-sans">Minutes</span>
              </div>
              <div className="bg-neutral-900 border border-sp-bronze p-2 rounded-lg">
                <span className="block font-mono text-xl sm:text-3xl font-black text-sp-gold animate-pulse">{String(countdown.seconds).padStart(2, '0')}</span>
                <span className="text-[9px] uppercase font-bold text-text-warm/40 font-sans">Seconds</span>
              </div>
            </div>

            <p className="text-[10px] text-text-warm/40 flex items-center justify-center gap-1">
              <Globe className="w-3.5 h-3.5" /> Synchronized with browser timezone: <strong className="text-white">{userTimezone}</strong>
            </p>
          </div>
        </section>
      )}

      {/* MAIN CONTAINER */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        


        {/* LIST OF EVENTS */}
        <div className="lg:col-span-2 space-y-6">
          {registerError && (
            <div className="bg-crimson/10 border border-crimson/40 text-rose-300 text-xs px-4 py-3 rounded-lg flex items-start justify-between gap-3">
              <span>{registerError}</span>
              <button onClick={() => setRegisterError('')} className="text-rose-300/60 hover:text-white cursor-pointer shrink-0">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}
          {loading ? (
            <div className="text-center py-12 text-text-warm/40 text-sm">Loading logs...</div>
          ) : eventsList.length === 0 ? (
            <div className="text-center py-12 text-text-warm/40 text-sm bg-neutral-900/10 border border-sp-bronze/20 rounded-xl">
              No upcoming transmissions. Check back later!
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-6">
              {eventsList.map((event) => {
                const registered = isUserRegistered(event.id);
                const count = getRegistrantCount(event.id);

                return (
                  <div
                    key={event.id}
                    id={`event-${event.id}`}
                    className={`bg-neutral-900/30 border rounded-xl overflow-hidden flex flex-col sm:flex-row justify-between transition-all backdrop-blur-xs relative group ${
                      highlightedEventId === String(event.id) ? 'border-sp-gold ring-2 ring-sp-gold/50' : 'border-sp-bronze/55 hover:border-sp-gold/50'
                    }`}
                  >
                    {/* Event Poster Header */}
                    <div className="h-40 sm:h-auto sm:w-48 bg-neutral-950 relative overflow-hidden shrink-0">
                      <img
                        src={getEventPosterSrc(event.image)}
                        alt={event.title}
                        className="w-full h-full object-cover opacity-60 group-hover:scale-105 transition-transform duration-500"
                      />
                      <div className="absolute inset-0 bg-linear-to-t from-neutral-900 to-transparent" />
                    </div>

                    {/* Event Details */}
                    <div className="p-5 space-y-4 flex-1 flex flex-col justify-between">
                      <div className="space-y-2">
                        <div className="flex items-center justify-between gap-2">
                          <h3 className="font-serif text-lg font-bold text-white group-hover:text-sp-gold transition-colors">
                            {event.title}
                          </h3>
                          <button
                            onClick={(e) => copyShareLink(event, e)}
                            title="Copy share link"
                            className={`p-1 rounded-sm transition-colors cursor-pointer shrink-0 ${copiedId === event.id ? 'text-emerald-400' : 'text-text-warm/30 hover:text-sp-gold'}`}
                          >
                            {copiedId === event.id ? <Check className="w-3.5 h-3.5" /> : <Share2 className="w-3.5 h-3.5" />}
                          </button>
                        </div>
                        <div className="text-xs text-text-warm/80 leading-relaxed text-left space-y-2">
                          {renderMarkdown(event.description)}
                        </div>
                      </div>

                      <div className="space-y-2 pt-2 border-t border-sp-bronze/10 text-[11px] text-text-warm/60">
                        {/* Server Time */}
                        <div className="flex items-center gap-2">
                          <Clock className="w-4 h-4 text-sp-copper" />
                          <span>Server Time: <strong>{event.date} at {event.time} ({event.timezone})</strong></span>
                        </div>
                        {/* Local Time */}
                        <div className="flex items-center gap-2">
                          <Globe className="w-4 h-4 text-sakura-accent" />
                          <span suppressHydrationWarning>Your Local Time: <strong className="text-white">{formatLocalTime(event.date, event.time)}</strong></span>
                        </div>
                        {/* Attendees Count */}
                        <div className="flex items-center gap-2">
                          <Users className="w-4 h-4 text-sp-gold" />
                          <span>Roster Registered: <strong className="text-white">{count} players signed up</strong></span>
                        </div>
                      </div>
                    </div>

                    {/* 3D Torch RSVP Element */}
                    <div className="p-5 sm:border-l border-sp-bronze/10 flex flex-col items-center justify-center min-w-[160px] bg-neutral-950/20 shrink-0">
                      <label className="torch-container">
                        <span className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-2 select-none tracking-widest text-center">
                          {registered ? "Lighted / Registered" : myRegistrationCount >= MAX_EVENTS_PER_PLAYER ? "Max Reached" : "Click to Light"}
                        </span>
                        <input
                          type="checkbox"
                          checked={registered}
                          onChange={() => onTorchToggle(event.id, registered)}
                        />
                        <div className="torch">
                          <div className="torch-light-effect"></div>
                          <div className="torch-glow-effect"></div>
                          <div className="torch-particles">
                            <span></span><span></span><span></span><span></span>
                            <span></span><span></span><span></span><span></span>
                          </div>
                          <div className="torch-smoke">
                            <span></span><span></span><span></span><span></span>
                          </div>
                          <div className="torch-head">
                            <div className="torch-face top"><div></div><div></div><div></div><div></div></div>
                            <div className="torch-face left"><div></div><div></div><div></div><div></div></div>
                            <div className="torch-face right"><div></div><div></div><div></div><div></div></div>
                          </div>
                          <div className="torch-stick">
                            <div className="torch-side torch-side-left">
                              {Array.from({ length: 16 }).map((_, idx) => <div key={idx}></div>)}
                            </div>
                            <div className="torch-side torch-side-right">
                              {Array.from({ length: 16 }).map((_, idx) => <div key={idx}></div>)}
                            </div>
                          </div>
                        </div>
                      </label>
                    </div>

                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* SIDEBAR: COMMUNITY DECISIONS & POLLS */}
        <div className="lg:col-span-1 space-y-6">
          <div className="bg-sp-dark border-2 border-sp-bronze/70 p-5 rounded-xl space-y-4 steampunk-border shadow-xl">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wider border-b border-sp-bronze/25 pb-2 flex items-center gap-2">
              <Users className="w-5 h-5 text-sp-copper" /> Community Decisions
            </h3>
            <p className="text-xs text-text-warm/75 leading-relaxed">
              Every vote fuels the steam engine. Help steer our server by casting your ballot.
            </p>

            {pollError && (
              <div className="text-[11px] text-rose-300 bg-crimson/10 border border-crimson/30 rounded-md px-3 py-2">
                {pollError}
              </div>
            )}

            {pollsList.length === 0 ? (
              <div className="text-center py-6 text-text-warm/40 text-xs italic">
                No active decisions in transmission.
              </div>
            ) : (
              <div className="space-y-6">
                {pollsList.map((poll) => {
                  const totalVotes = poll.options.reduce((sum, opt) => sum + (opt.votes || 0), 0);
                  const hasVoted = user && poll.voters && poll.voters.includes(user.email);

                  return (
                    <div key={poll.id} className="bg-neutral-950/40 p-4 border border-sp-bronze/30 rounded-lg space-y-3">
                      <h4 className="text-xs font-bold text-white leading-relaxed">
                        {poll.question}
                      </h4>

                      <div className="space-y-2">
                        {poll.options.map((option, optIdx) => {
                          const votes = option.votes || 0;
                          const pct = totalVotes > 0 ? Math.round((votes / totalVotes) * 100) : 0;

                          if (hasVoted) {
                            return (
                              <div key={optIdx} className="space-y-1">
                                <div className="flex justify-between text-[10px] text-text-warm/80">
                                  <span className="font-semibold">{option.text}</span>
                                  <span>{votes} votes ({pct}%)</span>
                                </div>
                                <div className="w-full bg-neutral-900 h-2 rounded-full overflow-hidden border border-sp-bronze/20">
                                  <div 
                                    className="bg-sp-gold h-full rounded-full transition-all duration-500" 
                                    style={{ width: `${pct}%` }}
                                  />
                                </div>
                              </div>
                            );
                          }

                          return (
                            <button
                              key={optIdx}
                              onClick={() => handleVote(poll.id, optIdx)}
                              className="w-full text-left px-3 py-2 text-xs bg-neutral-900 hover:bg-sp-bronze/20 border border-sp-bronze/40 hover:border-sp-gold/60 rounded-md text-text-warm/90 hover:text-white transition-all cursor-pointer flex justify-between items-center group/opt"
                            >
                              <span>{option.text}</span>
                              <span className="opacity-0 group-hover/opt:opacity-100 text-[10px] text-sp-gold font-bold transition-opacity uppercase font-mono">&bull; Vote</span>
                            </button>
                          );
                        })}
                      </div>

                      <div className="flex justify-between items-center text-[9px] text-text-warm/40 pt-1 border-t border-sp-bronze/10">
                        <span>Total: {totalVotes} votes</span>
                        {hasVoted && <span className="text-sp-gold font-semibold uppercase">Ballot Registered</span>}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

      </div>

      {/* RSVP Warning Modal */}
      {showWarningModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xs p-4">
          <div className="bg-sp-dark border-2 border-crimson p-6 rounded-xl max-w-md w-full steampunk-border shadow-2xl space-y-6 relative">
            <div className="absolute top-2 right-2 flex gap-1">
              <Cog className="w-4 h-4 text-crimson opacity-20 animate-spin-gear-cw" />
            </div>

            <div className="flex items-center gap-3 border-b border-crimson/25 pb-3">
              <div className="w-10 h-10 bg-crimson/15 rounded-full flex items-center justify-center border border-crimson text-rose-400">
                <AlertCircle className="w-6 h-6" />
              </div>
              <h3 className="font-serif text-lg font-bold text-rose-300 uppercase tracking-wider">Confirm Registration</h3>
            </div>

            <div className="space-y-3 text-xs text-text-warm/90 leading-relaxed text-left">
              <p>
                You are about to sign up for this event. <strong>Please note:</strong> if you indicate that you will attend, the organization expects your presence!
              </p>
              <p>
                Should anything come up, please make sure to cancel your registration in a timely manner by turning off your torch status.
              </p>
            </div>

            <div className="flex justify-end gap-3 pt-3 border-t border-sp-bronze/10">
              <button
                type="button"
                onClick={() => {
                  setShowWarningModal(false);
                  setPendingEventId(null);
                }}
                className="px-4 py-2 border-2 border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white hover:border-sp-copper cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={confirmRegistration}
                className="px-5 py-2 border-2 border-crimson bg-crimson hover:bg-rose-600 text-white text-xs font-bold uppercase rounded-lg transition-all cursor-pointer shadow-md"
              >
                I understand, Confirm
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
