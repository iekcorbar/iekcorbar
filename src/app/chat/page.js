'use client';

import React, { useState, useEffect, useRef, useCallback, useMemo, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams, useRouter } from 'next/navigation';
import { useUser } from '../providers';
import PlayerAvatar from '@/components/PlayerAvatar';
import { linkifyText } from '@/lib/linkify';
import { roleBadgeClass } from '@/lib/staff-tabs';
import {
  Send, Search, ArrowLeft, MessageSquare, Check, CheckCheck, ImagePlus, X,
  Shield, Heart, Users, Trash2, Ban, Flag, UserRound, ChevronDown, Loader2, BellOff
} from 'lucide-react';

// Private messages (api/chat). The sidebar always lists everyone you can
// message — conversations first, then staff, friends and all members — so
// nobody has to know a name before they can start a chat.

const MAX_MESSAGE_LENGTH = 2000;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];
const THREAD_POLL_MS = 4000;
const OVERVIEW_POLL_MS = 12000;
const HIDDEN_POLL_MS = 30000;
const REPORT_CATEGORIES = ['Harassment', 'Spam', 'Scam', 'Inappropriate content', 'Other'];

const lower = (s) => String(s || '').toLowerCase();
const sameName = (a, b) => lower(a) === lower(b);
const ts = (m) => new Date(m.timestamp).getTime();

function sortMessages(list) {
  return [...list].sort((a, b) => ts(a) - ts(b) || String(a.id).localeCompare(String(b.id)));
}

// Merge a freshly polled "newest page" into what's on screen: keep older
// pages loaded with "Load older", replace everything inside the new page's
// time window (so read ticks update and deleted messages disappear).
function mergeLatest(current, incoming, hasMore) {
  if (!hasMore) return sortMessages(incoming); // the page is the whole conversation
  if (incoming.length === 0) return current;
  const cutoff = ts(incoming[0]);
  return sortMessages([...current.filter(m => ts(m) < cutoff), ...incoming]);
}

function dayLabel(date) {
  const d = new Date(date);
  const today = new Date();
  const yesterday = new Date(); yesterday.setDate(today.getDate() - 1);
  if (d.toDateString() === today.toDateString()) return 'Today';
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long', ...(d.getFullYear() !== today.getFullYear() ? { year: 'numeric' } : {}) });
}

function shortTime(date) {
  const d = new Date(date);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const days = (now - d) / 86400000;
  if (days < 7) return d.toLocaleDateString([], { weekday: 'short' });
  return d.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

function RoleBadge({ person }) {
  if (!person || !person.role || person.role === 'player') return null;
  const label = person.role === 'admin' ? 'Admin' : person.role === 'moderator' ? 'Mod' : (person.roleLabel || person.role);
  return (
    <span className={`text-[9px] px-1.5 py-0.5 rounded font-bold font-mono uppercase shrink-0 ${roleBadgeClass(person.role, person.roleColor)}`}>
      {label}
    </span>
  );
}

function SectionTitle({ icon: Icon, children, count }) {
  return (
    <p className="text-[10px] uppercase font-bold text-sp-gold/70 px-2 pt-3 pb-1 font-mono flex items-center gap-1.5">
      <Icon className="w-3 h-3" /> {children}
      {typeof count === 'number' && <span className="text-text-warm/30 font-normal">({count})</span>}
    </p>
  );
}

function PersonRow({ name, person, active, onSelect, subtitle, time, unread: rawUnread, muted = false }) {
  const unread = muted ? 0 : rawUnread; // a muted conversation never shows a badge
  return (
    <button
      onClick={() => onSelect(name)}
      className={`w-full p-2 flex items-center gap-3 rounded-lg transition-all text-left cursor-pointer border ${
        active ? 'border-sp-gold/40 bg-sp-gold/10' : 'border-transparent hover:bg-sp-gold/5'
      }`}
    >
      <div className="w-9 h-9 rounded-lg overflow-hidden border border-sp-bronze shrink-0 bg-neutral-950">
        <PlayerAvatar mcUsername={name} customSkinUrl={person?.customSkinUrl} size={36} className="w-full h-full object-cover" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5 min-w-0">
          <span className={`text-xs font-mono truncate ${unread ? 'font-bold text-white' : 'font-semibold text-white/90'}`}>{name}</span>
          <RoleBadge person={person} />
          {time && <span className="ml-auto text-[9px] text-text-warm/40 font-mono shrink-0">{shortTime(time)}</span>}
        </div>
        {subtitle && (
          <p className={`text-[11px] truncate mt-0.5 leading-tight ${unread ? 'text-text-warm' : 'text-text-warm/55'}`}>{subtitle}</p>
        )}
      </div>
      {muted && <BellOff className="w-3.5 h-3.5 text-text-warm/35 shrink-0" title="Muted" />}
      {unread > 0 && (
        <span className="flex h-5 min-w-5 px-1 items-center justify-center rounded-full bg-sp-gold text-[10px] font-bold text-black shrink-0">
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </button>
  );
}

function ReportDialog({ target, onClose }) {
  const [category, setCategory] = useState(REPORT_CATEGORIES[0]);
  const [details, setDetails] = useState('');
  const [state, setState] = useState({ sending: false, done: false, error: '' });

  const submit = async (e) => {
    e.preventDefault();
    if (!details.trim()) return;
    setState({ sending: true, done: false, error: '' });
    try {
      const res = await fetch('/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUsername: target, category: `Chat: ${category}`, details: details.trim() })
      });
      const data = await res.json();
      setState(res.ok && data.success ? { sending: false, done: true, error: '' } : { sending: false, done: false, error: data.error || 'Could not send the report.' });
    } catch {
      setState({ sending: false, done: false, error: 'Could not send the report.' });
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4" onClick={onClose}>
      <div className="w-full max-w-md bg-sp-dark border-2 border-sp-bronze rounded-2xl p-5 space-y-4" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h3 className="font-serif text-lg font-bold text-sp-gold flex items-center gap-2"><Flag className="w-4 h-4" /> Report {target}</h3>
          <button onClick={onClose} className="text-text-warm/50 hover:text-white cursor-pointer" aria-label="Close"><X className="w-4 h-4" /></button>
        </div>
        {state.done ? (
          <div className="space-y-3">
            <p className="text-sm text-text-warm">Thanks — the staff team has received your report. Reports are anonymous.</p>
            <button onClick={onClose} className="px-4 py-2 rounded-lg bg-sp-gold text-black text-xs font-bold cursor-pointer">Close</button>
          </div>
        ) : (
          <form onSubmit={submit} className="space-y-3">
            <p className="text-xs text-text-warm/60">Staff will look at what happened. Your name is not shown to {target}.</p>
            <select value={category} onChange={e => setCategory(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2">
              {REPORT_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <textarea
              value={details}
              onChange={e => setDetails(e.target.value)}
              rows={4}
              placeholder="What happened? Include dates or quote messages if you can."
              className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 resize-none focus:outline-hidden focus:border-sp-gold"
            />
            {state.error && <p className="text-[11px] text-rose-400">{state.error}</p>}
            <div className="flex justify-end gap-2">
              <button type="button" onClick={onClose} className="px-4 py-2 rounded-lg border border-sp-bronze/40 text-xs text-text-warm cursor-pointer">Cancel</button>
              <button type="submit" disabled={state.sending || !details.trim()} className="px-4 py-2 rounded-lg bg-crimson/80 hover:bg-crimson text-white text-xs font-bold disabled:opacity-40 cursor-pointer">
                {state.sending ? 'Sending…' : 'Send report'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

function ChatDeck() {
  const { user, setIsLoginOpen } = useUser();
  const searchParams = useSearchParams();
  const router = useRouter();
  const urlContact = searchParams.get('user');

  const [conversations, setConversations] = useState([]);
  const [people, setPeople] = useState([]);
  const [overviewLoaded, setOverviewLoaded] = useState(false);
  const [overviewError, setOverviewError] = useState('');
  const [search, setSearch] = useState('');
  const [showAllMembers, setShowAllMembers] = useState(false);

  const [activeContact, setActiveContact] = useState(urlContact || null);
  const [messages, setMessages] = useState([]);
  const [hasMore, setHasMore] = useState(false);
  const [loadingThread, setLoadingThread] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [block, setBlock] = useState({ byMe: false, byThem: false });
  const [muted, setMuted] = useState(false);

  const [draft, setDraft] = useState('');
  const [image, setImage] = useState(null);
  const [imagePreview, setImagePreview] = useState(null);
  const [sending, setSending] = useState(false);
  const [composerError, setComposerError] = useState('');
  const [reportTarget, setReportTarget] = useState(null);

  const threadRef = useRef(null);
  const fileInputRef = useRef(null);
  const textareaRef = useRef(null);
  const stickToBottomRef = useRef(true);
  const activeRef = useRef(activeContact);
  activeRef.current = activeContact;

  useEffect(() => {
    if (user) return;
    // `user` is null on the very first render of a fresh page load: the provider
    // restores the saved login in its own mount effect, which runs AFTER this
    // one. Redirecting then bounced every direct load of /chat (notification
    // links, refresh, the Android app) to the homepage with a login popup, even
    // for logged-in players. Only treat "no user" as logged out when there is
    // no saved login either.
    if (typeof window !== 'undefined' && localStorage.getItem('iekcorbar_user')) return;
    setIsLoginOpen(true);
    router.push('/');
  }, [user]);

  // Follow ?user= (bell notifications, profile "Message" buttons, back/forward).
  useEffect(() => {
    if (urlContact && !sameName(urlContact, activeRef.current)) setActiveContact(urlContact);
    if (!urlContact && activeRef.current && window.matchMedia('(max-width: 767px)').matches) setActiveContact(null);
  }, [urlContact]);

  const peopleByName = useMemo(() => new Map(people.map(p => [lower(p.mcUsername), p])), [people]);
  const personFor = (name) => peopleByName.get(lower(name)) || null;

  const fetchOverview = useCallback(async () => {
    try {
      const res = await fetch('/api/chat?overview=1');
      const data = await res.json();
      if (!res.ok) { setOverviewError(data.error || 'Could not load chat.'); return; }
      setOverviewError('');
      // The conversation you are looking at right now never shows an unread
      // badge: its messages get marked read by the thread poll, which can lag
      // this sidebar poll by a few seconds — without this the badge would
      // flash back to the server's (stale) count on every sidebar refresh.
      const looking = document.visibilityState === 'visible' ? activeRef.current : null;
      setConversations((data.conversations || []).map(c => (looking && sameName(c.contact, looking) ? { ...c, unreadCount: 0 } : c)));
      setPeople(data.people || []);
      // Desktop: reopen the latest conversation instead of an empty pane.
      if (!activeRef.current && data.conversations?.[0] && window.matchMedia('(min-width: 768px)').matches) {
        setActiveContact(data.conversations[0].contact);
      }
    } catch {
      setOverviewError('Could not load chat.');
    } finally {
      setOverviewLoaded(true);
    }
  }, []);

  const fetchThread = useCallback(async (contact, { initial = false } = {}) => {
    try {
      const visible = document.visibilityState === 'visible';
      const res = await fetch(`/api/chat?contact=${encodeURIComponent(contact)}${visible ? `&markRead=1${initial ? '&initial=1' : ''}` : ''}`);
      const data = await res.json();
      if (!sameName(contact, activeRef.current)) return; // switched conversation meanwhile
      if (!res.ok) { if (initial) setComposerError(data.error || 'Could not load messages.'); return; }
      const incoming = data.messages || [];
      setMessages(prev => (initial ? incoming : mergeLatest(prev, incoming, data.hasMore)));
      if (initial) setHasMore(!!data.hasMore);
      setBlock(data.block || { byMe: false, byThem: false });
      if (typeof data.muted === 'boolean') setMuted(data.muted);
      // Clear this conversation's unread badge locally right away.
      if (visible) setConversations(prev => prev.map(c => (sameName(c.contact, contact) ? { ...c, unreadCount: 0 } : c)));
    } catch { /* next poll retries */ }
  }, []);

  // Sidebar: load once, then poll (slower in a background tab).
  useEffect(() => {
    if (!user) return;
    fetchOverview();
    let timer;
    const tick = () => {
      timer = setTimeout(async () => { await fetchOverview(); tick(); }, document.visibilityState === 'visible' ? OVERVIEW_POLL_MS : HIDDEN_POLL_MS);
    };
    tick();
    return () => clearTimeout(timer);
  }, [user, fetchOverview]);

  // Open conversation: load, then poll.
  useEffect(() => {
    if (!user || !activeContact) return;
    setMessages([]);
    setHasMore(false);
    setBlock({ byMe: false, byThem: false });
    setMuted(false);
    setComposerError('');
    stickToBottomRef.current = true;
    setLoadingThread(true);
    fetchThread(activeContact, { initial: true }).finally(() => setLoadingThread(false));

    let timer;
    const tick = () => {
      timer = setTimeout(async () => { await fetchThread(activeContact); tick(); }, document.visibilityState === 'visible' ? THREAD_POLL_MS : HIDDEN_POLL_MS);
    };
    tick();
    const onVisible = () => { if (document.visibilityState === 'visible') fetchThread(activeContact); };
    document.addEventListener('visibilitychange', onVisible);
    return () => { clearTimeout(timer); document.removeEventListener('visibilitychange', onVisible); };
  }, [user, activeContact, fetchThread]);

  // Keep the view pinned to the newest message unless the reader scrolled up.
  useEffect(() => {
    const el = threadRef.current;
    if (el && stickToBottomRef.current) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const onThreadScroll = () => {
    const el = threadRef.current;
    if (el) stickToBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
  };

  const loadOlder = async () => {
    if (!messages.length || loadingOlder) return;
    const contact = activeContact;
    const el = threadRef.current;
    const prevHeight = el ? el.scrollHeight : 0;
    setLoadingOlder(true);
    try {
      const res = await fetch(`/api/chat?contact=${encodeURIComponent(contact)}&before=${encodeURIComponent(messages[0].timestamp)}`);
      const data = await res.json();
      if (res.ok && sameName(contact, activeRef.current)) {
        stickToBottomRef.current = false;
        setMessages(prev => sortMessages([...(data.messages || []).filter(m => !prev.some(p => p.id === m.id)), ...prev]));
        setHasMore(!!data.hasMore);
        requestAnimationFrame(() => { if (el) el.scrollTop = el.scrollHeight - prevHeight; });
      }
    } finally {
      setLoadingOlder(false);
    }
  };

  const selectContact = (name) => {
    setSearch('');
    if (!sameName(name, activeContact)) setActiveContact(name);
    router.push(`/chat?user=${encodeURIComponent(name)}`, { scroll: false });
    setTimeout(() => textareaRef.current?.focus(), 50);
  };

  const closeThread = () => {
    setActiveContact(null);
    router.push('/chat', { scroll: false });
  };

  const pickImage = (file) => {
    setComposerError('');
    if (!file) return;
    if (!IMAGE_TYPES.includes(file.type)) { setComposerError('Only PNG, JPG, GIF or WebP images are allowed.'); return; }
    if (file.size > MAX_IMAGE_BYTES) { setComposerError('Image must be smaller than 5MB.'); return; }
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setImage(file);
    setImagePreview(URL.createObjectURL(file));
  };

  const clearImage = () => {
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setImage(null);
    setImagePreview(null);
  };

  const onPaste = (e) => {
    const file = [...(e.clipboardData?.files || [])].find(f => f.type.startsWith('image/'));
    if (file) { e.preventDefault(); pickImage(file); }
  };

  // Grow the textarea with its content, up to ~6 lines.
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 140)}px`;
  }, [draft]);

  const send = async (e) => {
    e?.preventDefault();
    const text = draft.trim();
    if ((!text && !image) || sending || !activeContact) return;
    if (text.length > MAX_MESSAGE_LENGTH) { setComposerError(`Messages can be at most ${MAX_MESSAGE_LENGTH} characters.`); return; }
    setSending(true);
    setComposerError('');
    try {
      let res;
      if (image) {
        const form = new FormData();
        form.append('action', 'send');
        form.append('receiver', activeContact);
        form.append('message', text);
        form.append('image', image);
        res = await fetch('/api/chat', { method: 'POST', body: form });
      } else {
        res = await fetch('/api/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'send', receiver: activeContact, message: text })
        });
      }
      const data = await res.json();
      if (res.ok && data.message) {
        stickToBottomRef.current = true;
        setMessages(prev => sortMessages([...prev.filter(m => m.id !== data.message.id), data.message]));
        setDraft('');
        clearImage();
        fetchOverview();
      } else {
        setComposerError(data.error || 'Could not send the message.');
      }
    } catch {
      setComposerError('Could not send the message — check your connection.');
    } finally {
      setSending(false);
      textareaRef.current?.focus();
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      send();
    }
  };

  const deleteMessage = async (msg) => {
    if (!confirm('Delete this message for everyone?')) return;
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'delete', id: msg.id })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok) {
      setMessages(prev => prev.filter(m => m.id !== msg.id));
      fetchOverview();
    } else {
      setComposerError(data.error || 'Could not delete the message.');
    }
  };

  // Mute = keep receiving messages, but no unread badge and no bell/push for
  // this conversation (unlike block, nothing is withheld from either side).
  const toggleMute = async () => {
    const contact = activeContact;
    const next = !muted;
    setMuted(next); // optimistic
    setConversations(prev => prev.map(c => (sameName(c.contact, contact) ? { ...c, muted: next } : c)));
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: next ? 'mute' : 'unmute', username: contact })
      });
      if (!res.ok) throw new Error('failed');
    } catch {
      setMuted(!next);
      setConversations(prev => prev.map(c => (sameName(c.contact, contact) ? { ...c, muted: !next } : c)));
      setComposerError('Could not change that right now — please try again.');
    }
  };

  const toggleBlock = async () => {
    const blocking = !block.byMe;
    if (blocking && !confirm(`Block ${activeContact}? They won't be able to send you messages, and you can't message them until you unblock.`)) return;
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: blocking ? 'block' : 'unblock', username: activeContact })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok) {
      setBlock(b => ({ ...b, byMe: blocking }));
      fetchOverview();
    } else {
      setComposerError(data.error || 'Could not change the block.');
    }
  };

  // ---- Sidebar lists ----
  const q = lower(search.trim());
  const conversationNames = new Set(conversations.map(c => lower(c.contact)));
  const byName = (a, b) => a.mcUsername.localeCompare(b.mcUsername, undefined, { sensitivity: 'base' });
  const staff = people.filter(p => p.roleIsStaff && !conversationNames.has(lower(p.mcUsername)))
    .sort((a, b) => (a.roleRank ?? 0) - (b.roleRank ?? 0) || byName(a, b));
  const friends = people.filter(p => p.isFriend && !p.roleIsStaff && !conversationNames.has(lower(p.mcUsername))).sort(byName);
  const others = people.filter(p => !p.isFriend && !p.roleIsStaff && !conversationNames.has(lower(p.mcUsername))).sort(byName);
  const MEMBER_PREVIEW = 30;
  const searchResults = q
    ? people.filter(p => lower(p.mcUsername).includes(q) || lower(p.roleLabel).includes(q))
        .sort((a, b) => (lower(a.mcUsername).startsWith(q) ? 0 : 1) - (lower(b.mcUsername).startsWith(q) ? 0 : 1) || byName(a, b))
    : [];

  const activePerson = activeContact ? personFor(activeContact) : null;
  const activeIsStaff = !!activePerson?.roleIsStaff;
  const totalUnread = conversations.reduce((n, c) => n + (c.muted ? 0 : (c.unreadCount || 0)), 0);

  if (!user) {
    return (
      <div className="flex-1 flex items-center justify-center p-8">
        <Loader2 className="w-6 h-6 text-sp-gold animate-spin" />
      </div>
    );
  }

  if (!user.mcUsername) {
    return (
      <main className="flex-1 max-w-xl mx-auto px-4 py-16 text-center space-y-3">
        <MessageSquare className="w-10 h-10 text-sp-copper mx-auto" />
        <h1 className="font-serif text-2xl font-bold text-sp-gold">Chat</h1>
        <p className="text-sm text-text-warm/70">Link your Minecraft account first — chat uses your in-game name.</p>
      </main>
    );
  }

  // ---- Thread rendering ----
  const rows = [];
  let lastDay = null;
  messages.forEach((m, i) => {
    const day = new Date(m.timestamp).toDateString();
    if (day !== lastDay) {
      rows.push(
        <div key={`day-${day}`} className="flex items-center gap-3 py-2">
          <div className="flex-1 h-px bg-sp-bronze/25" />
          <span className="text-[10px] font-mono uppercase tracking-wider text-text-warm/45">{dayLabel(m.timestamp)}</span>
          <div className="flex-1 h-px bg-sp-bronze/25" />
        </div>
      );
      lastDay = day;
    }
    const isSelf = sameName(m.sender, user.mcUsername);
    const prev = messages[i - 1];
    const next = messages[i + 1];
    const groupedWithPrev = prev && sameName(prev.sender, m.sender) && new Date(prev.timestamp).toDateString() === day && ts(m) - ts(prev) < 5 * 60000;
    const groupedWithNext = next && sameName(next.sender, m.sender) && new Date(next.timestamp).toDateString() === day && ts(next) - ts(m) < 5 * 60000;
    rows.push(
      <div key={m.id} className={`group flex items-end gap-2 ${isSelf ? 'justify-end' : 'justify-start'} ${groupedWithPrev ? 'mt-0.5' : 'mt-3'}`}>
        {!isSelf && (
          <div className="w-7 h-7 shrink-0">
            {!groupedWithNext && (
              <div className="w-7 h-7 rounded-md overflow-hidden border border-sp-bronze/60 bg-neutral-950">
                <PlayerAvatar mcUsername={m.sender} customSkinUrl={personFor(m.sender)?.customSkinUrl} size={28} className="w-full h-full object-cover" />
              </div>
            )}
          </div>
        )}
        {isSelf && (
          <button
            onClick={() => deleteMessage(m)}
            className="opacity-0 group-hover:opacity-100 focus:opacity-100 p-1 text-text-warm/40 hover:text-rose-400 transition-opacity cursor-pointer self-center"
            title="Delete message"
            aria-label="Delete message"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}
        <div
          className={`max-w-[78%] sm:max-w-[65%] rounded-2xl px-3.5 py-2 border ${
            isSelf
              ? `bg-sp-gold/15 border-sp-gold/30 text-white ${groupedWithNext ? '' : 'rounded-br-sm'}`
              : `bg-sp-charcoal/90 border-sp-bronze/30 text-text-warm ${groupedWithNext ? '' : 'rounded-bl-sm'}`
          }`}
        >
          {m.imageUrl && (
            <a href={m.imageUrl} target="_blank" rel="noreferrer" className="block mb-1">
              <img src={m.imageUrl} alt="Image" loading="lazy" className="max-w-full max-h-72 rounded-lg border border-sp-bronze/30 object-contain" />
            </a>
          )}
          {m.message && (
            <p className="text-[13px] leading-relaxed whitespace-pre-wrap break-words">{linkifyText(m.message, { className: 'underline font-semibold break-all' })}</p>
          )}
          <div className="flex items-center justify-end gap-1 text-[9px] font-mono text-text-warm/40 mt-0.5 select-none">
            <span>{new Date(m.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
            {isSelf && (m.read
              ? <CheckCheck className="w-3 h-3 text-sp-gold" aria-label="Read" />
              : <Check className="w-3 h-3" aria-label="Sent" />)}
          </div>
        </div>
      </div>
    );
  });

  const composerDisabled = block.byMe || block.byThem;

  return (
    <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 flex flex-col font-sans">
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <h1 className="font-serif text-2xl sm:text-3xl font-bold tracking-widest text-sp-gold steampunk-glow uppercase flex items-center gap-3">
            <MessageSquare className="w-7 h-7 text-sp-copper" /> Messages
          </h1>
          <p className="text-xs text-text-warm/50 mt-1">Private messages with other members and staff.</p>
        </div>
        {totalUnread > 0 && (
          <span className="text-[11px] font-mono text-sp-gold bg-sp-gold/10 border border-sp-gold/30 rounded-lg px-2.5 py-1 shrink-0">
            {totalUnread} unread
          </span>
        )}
      </div>

      <div className="flex-1 bg-sp-dark border-2 border-sp-bronze rounded-2xl overflow-hidden shadow-2xl flex h-[calc(100dvh-210px)] min-h-[480px] max-h-[820px] steampunk-glass">

        {/* Sidebar */}
        <aside className={`w-full md:w-80 border-r-2 border-sp-bronze/50 flex-col min-h-0 ${activeContact ? 'hidden md:flex' : 'flex'}`}>
          <div className="p-3 border-b border-sp-bronze/35 bg-neutral-950/40">
            <div className="relative">
              <Search className="absolute left-3 top-2.5 w-4 h-4 text-text-warm/40" />
              <input
                type="text"
                placeholder="Search members…"
                value={search}
                onChange={e => setSearch(e.target.value)}
                className="w-full bg-neutral-900 border border-sp-bronze/40 hover:border-sp-bronze text-white placeholder-text-warm/30 text-xs rounded-xl pl-9 pr-8 py-2.5 focus:outline-hidden focus:border-sp-gold transition-all"
              />
              {search && (
                <button onClick={() => setSearch('')} className="absolute right-2.5 top-2.5 text-text-warm/40 hover:text-white cursor-pointer" aria-label="Clear search">
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-2 min-h-0">
            {!overviewLoaded ? (
              <div className="flex justify-center py-10"><Loader2 className="w-5 h-5 text-sp-gold animate-spin" /></div>
            ) : overviewError ? (
              <div className="text-center py-10 px-4 space-y-2">
                <p className="text-xs text-rose-400">{overviewError}</p>
                <button onClick={fetchOverview} className="text-xs text-sp-gold underline cursor-pointer">Try again</button>
              </div>
            ) : q ? (
              <>
                <SectionTitle icon={Search} count={searchResults.length}>Results</SectionTitle>
                {searchResults.length === 0
                  ? <p className="text-xs text-text-warm/40 italic p-3 text-center">No member called “{search.trim()}”.</p>
                  : searchResults.map(p => (
                    <PersonRow key={p.mcUsername} name={p.mcUsername} person={p} active={sameName(p.mcUsername, activeContact)} onSelect={selectContact} />
                  ))}
              </>
            ) : (
              <>
                {conversations.length > 0 && (
                  <>
                    <SectionTitle icon={MessageSquare}>Conversations</SectionTitle>
                    {conversations.map(c => (
                      <PersonRow
                        key={c.contact}
                        name={c.contact}
                        person={personFor(c.contact)}
                        active={sameName(c.contact, activeContact)}
                        onSelect={selectContact}
                        subtitle={c.lastMessage}
                        time={c.timestamp}
                        unread={c.unreadCount}
                        muted={c.muted}
                      />
                    ))}
                  </>
                )}

                {staff.length > 0 && (
                  <>
                    <SectionTitle icon={Shield}>Staff</SectionTitle>
                    {staff.map(p => (
                      <PersonRow key={p.mcUsername} name={p.mcUsername} person={p} active={sameName(p.mcUsername, activeContact)} onSelect={selectContact} subtitle="Ask staff for help" />
                    ))}
                  </>
                )}

                {friends.length > 0 && (
                  <>
                    <SectionTitle icon={Heart}>Friends</SectionTitle>
                    {friends.map(p => (
                      <PersonRow key={p.mcUsername} name={p.mcUsername} person={p} active={sameName(p.mcUsername, activeContact)} onSelect={selectContact} />
                    ))}
                  </>
                )}

                {others.length > 0 && (
                  <>
                    <SectionTitle icon={Users} count={others.length}>Members</SectionTitle>
                    {(showAllMembers ? others : others.slice(0, MEMBER_PREVIEW)).map(p => (
                      <PersonRow key={p.mcUsername} name={p.mcUsername} person={p} active={sameName(p.mcUsername, activeContact)} onSelect={selectContact} />
                    ))}
                    {others.length > MEMBER_PREVIEW && !showAllMembers && (
                      <button onClick={() => setShowAllMembers(true)} className="w-full text-[11px] text-sp-gold hover:text-white py-2 flex items-center justify-center gap-1 cursor-pointer">
                        <ChevronDown className="w-3.5 h-3.5" /> Show all {others.length} members
                      </button>
                    )}
                  </>
                )}

                {people.length === 0 && conversations.length === 0 && (
                  <p className="text-xs text-text-warm/40 italic p-6 text-center">No other members yet.</p>
                )}
              </>
            )}
          </div>
        </aside>

        {/* Conversation */}
        <section className={`flex-1 flex-col min-w-0 min-h-0 bg-neutral-950/20 ${activeContact ? 'flex' : 'hidden md:flex'}`}>
          {activeContact ? (
            <>
              <header className="px-3 sm:px-4 py-3 border-b border-sp-bronze/35 bg-neutral-950/60 flex items-center gap-3">
                <button onClick={closeThread} className="md:hidden p-1.5 -ml-1 text-text-warm hover:text-sp-gold rounded cursor-pointer" aria-label="Back to conversations">
                  <ArrowLeft className="w-5 h-5" />
                </button>
                <Link href={`/members/${encodeURIComponent(activeContact)}`} className="flex items-center gap-3 min-w-0 group">
                  <div className="w-10 h-10 rounded-lg overflow-hidden border-2 border-sp-bronze shrink-0 bg-neutral-950">
                    <PlayerAvatar mcUsername={activeContact} customSkinUrl={activePerson?.customSkinUrl} size={40} className="w-full h-full object-cover" />
                  </div>
                  <div className="min-w-0">
                    <h2 className="text-sm font-bold text-white font-mono flex items-center gap-2 group-hover:text-sp-gold truncate">
                      {activePerson?.mcUsername || activeContact} <RoleBadge person={activePerson} />
                    </h2>
                    <p className="text-[10px] text-text-warm/45">
                      {activeIsStaff ? 'Staff member' : activePerson?.isFriend ? 'Friend' : activePerson ? 'Member' : 'Not a member anymore'} · view profile
                    </p>
                  </div>
                </Link>
                <div className="ml-auto flex items-center gap-1">
                  <Link href={`/members/${encodeURIComponent(activeContact)}`} className="hidden sm:flex p-2 rounded-lg text-text-warm/60 hover:text-sp-gold hover:bg-sp-gold/5" title="View profile">
                    <UserRound className="w-4 h-4" />
                  </Link>
                  <button onClick={toggleMute} className={`p-2 rounded-lg cursor-pointer hover:bg-sp-gold/5 ${muted ? 'text-sp-gold' : 'text-text-warm/60 hover:text-sp-gold'}`} title={muted ? 'Unmute this conversation' : 'Mute this conversation (no badge or notifications)'}>
                    <BellOff className="w-4 h-4" />
                  </button>
                  {activePerson && !activeIsStaff && (
                    <button onClick={toggleBlock} className={`p-2 rounded-lg cursor-pointer hover:bg-sp-gold/5 ${block.byMe ? 'text-rose-400' : 'text-text-warm/60 hover:text-rose-300'}`} title={block.byMe ? 'Unblock' : 'Block'}>
                      <Ban className="w-4 h-4" />
                    </button>
                  )}
                  {activePerson && (
                    <button onClick={() => setReportTarget(activeContact)} className="p-2 rounded-lg text-text-warm/60 hover:text-rose-300 hover:bg-sp-gold/5 cursor-pointer" title="Report">
                      <Flag className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </header>

              <div ref={threadRef} onScroll={onThreadScroll} className="flex-1 overflow-y-auto px-3 sm:px-5 py-3 bg-neutral-950/40 min-h-0">
                {hasMore && (
                  <div className="flex justify-center pb-2">
                    <button onClick={loadOlder} disabled={loadingOlder} className="text-[11px] text-sp-gold hover:text-white border border-sp-bronze/40 rounded-full px-3 py-1 cursor-pointer disabled:opacity-50">
                      {loadingOlder ? 'Loading…' : 'Load older messages'}
                    </button>
                  </div>
                )}
                {loadingThread && messages.length === 0 ? (
                  <div className="h-full flex items-center justify-center"><Loader2 className="w-5 h-5 text-sp-gold animate-spin" /></div>
                ) : messages.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center text-center text-text-warm/40 space-y-2 py-12 px-6">
                    <MessageSquare className="w-10 h-10 opacity-30" />
                    <p className="text-sm">No messages yet.</p>
                    <p className="text-xs">Say hi to {activePerson?.mcUsername || activeContact}!</p>
                  </div>
                ) : rows}
              </div>

              <div className="border-t border-sp-bronze/35 bg-neutral-950/60">
                {composerDisabled ? (
                  <div className="p-4 text-center text-xs text-text-warm/60">
                    {block.byMe
                      ? <>You blocked {activeContact}. <button onClick={toggleBlock} className="text-sp-gold underline cursor-pointer">Unblock</button> to send messages again.</>
                      : <>{activeContact} isn&apos;t accepting messages from you.</>}
                  </div>
                ) : !activePerson && overviewLoaded ? (
                  <div className="p-4 text-center text-xs text-text-warm/60">This account no longer exists, so you can&apos;t reply.</div>
                ) : (
                  <>
                    {composerError && <p className="px-4 pt-2 text-[11px] text-rose-400">{composerError}</p>}
                    {imagePreview && (
                      <div className="px-4 pt-3 flex items-center gap-2">
                        <div className="relative w-16 h-16 rounded-lg overflow-hidden border border-sp-bronze/40 shrink-0">
                          <img src={imagePreview} alt="Selected image" className="w-full h-full object-cover" />
                          <button type="button" onClick={clearImage} className="absolute top-0.5 right-0.5 bg-black/70 hover:bg-crimson rounded-full p-0.5 text-white cursor-pointer" aria-label="Remove image">
                            <X className="w-3 h-3" />
                          </button>
                        </div>
                        <span className="text-[10px] text-text-warm/50 font-mono truncate">{image?.name}</span>
                      </div>
                    )}
                    <form onSubmit={send} className="p-3 flex items-end gap-2">
                      <input ref={fileInputRef} type="file" accept={IMAGE_TYPES.join(',')} onChange={e => { pickImage(e.target.files?.[0]); e.target.value = ''; }} className="hidden" />
                      <button
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        className="p-2.5 rounded-xl border border-sp-bronze/40 hover:border-sp-gold text-text-warm/70 hover:text-sp-gold transition-all cursor-pointer shrink-0"
                        title="Add image"
                        aria-label="Add image"
                      >
                        <ImagePlus className="w-4 h-4" />
                      </button>
                      <div className="flex-1 min-w-0">
                        <textarea
                          ref={textareaRef}
                          rows={1}
                          value={draft}
                          onChange={e => setDraft(e.target.value)}
                          onKeyDown={onKeyDown}
                          onPaste={onPaste}
                          maxLength={MAX_MESSAGE_LENGTH}
                          placeholder={`Message ${activePerson?.mcUsername || activeContact}…`}
                          className="w-full block bg-neutral-900 border border-sp-bronze/40 text-white placeholder-text-warm/35 text-[13px] rounded-xl px-3.5 py-2.5 resize-none focus:outline-hidden focus:border-sp-gold transition-colors leading-snug"
                        />
                        <div className="flex justify-between px-1 pt-1 text-[9px] text-text-warm/30 font-mono">
                          <span className="hidden sm:inline">Enter to send · Shift+Enter for a new line</span>
                          {draft.length > MAX_MESSAGE_LENGTH - 200 && <span className="ml-auto">{draft.length}/{MAX_MESSAGE_LENGTH}</span>}
                        </div>
                      </div>
                      <button
                        type="submit"
                        disabled={sending || (!draft.trim() && !image)}
                        className="p-2.5 mb-5 rounded-xl bg-sp-gold hover:bg-white text-black disabled:opacity-40 disabled:hover:bg-sp-gold transition-all cursor-pointer shrink-0"
                        title="Send"
                        aria-label="Send"
                      >
                        {sending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                      </button>
                    </form>
                  </>
                )}
              </div>
            </>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center text-center p-8 space-y-3">
              <div className="w-16 h-16 rounded-full border-2 border-sp-bronze/30 flex items-center justify-center">
                <MessageSquare className="w-8 h-8 text-sp-gold/40" />
              </div>
              <h3 className="font-serif text-sm font-bold text-sp-gold uppercase tracking-wider">Pick someone to message</h3>
              <p className="text-xs text-text-warm/50 max-w-sm">Choose a member or staff member on the left, or search by name.</p>
            </div>
          )}
        </section>
      </div>

      {reportTarget && <ReportDialog target={reportTarget} onClose={() => setReportTarget(null)} />}
    </main>
  );
}

export default function ChatPage() {
  return (
    <Suspense fallback={<div className="flex-1 flex items-center justify-center p-8"><Loader2 className="w-6 h-6 text-sp-gold animate-spin" /></div>}>
      <ChatDeck />
    </Suspense>
  );
}
