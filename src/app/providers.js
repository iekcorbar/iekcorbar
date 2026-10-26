'use client';

import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { siteContentDefaultsMap } from '@/lib/site-content';
import { startAuthentication, browserSupportsWebAuthn } from '@simplewebauthn/browser';
import { syncWebPush, unbindWebPush } from '@/lib/web-push-client';
import { 
  Flame, 
  BookOpen, 
  Calendar, 
  User, 
  ShieldAlert, 
  LogIn, 
  LogOut, 
  Menu, 
  X, 
  Cog,
  Compass,
  Heart,
  Users,
  Sparkles,
  Map,
  ThumbsUp,
  Shield,
  Bell,
  BellOff,
  MessageSquare,
  Hammer,
  FlaskConical,
  UserPlus,
  ShoppingBag,
  ChevronDown,
  Users2,
  Wine,
  Trophy,
  HeartHandshake,
  Apple,
  Smartphone,
  Bug,
  KeyRound,
  Newspaper
} from 'lucide-react';

const UserContext = createContext(null);

export function useUser() {
  return useContext(UserContext);
}

// Admin-editable site text (role display names, a couple of page titles â€”
// see src/lib/site-content.js for the registry and the "Site Content"
// staff-panel sub-tab that edits them). Initialized synchronously with the
// code defaults so there's no flash of empty/placeholder text before the
// fetch resolves â€” the fetched values (if any differ) just swap in once
// loaded.
const SiteContentContext = createContext(siteContentDefaultsMap());

export function useSiteContent() {
  return useContext(SiteContentContext);
}

// Grouped desktop nav dropdown (e.g. "Community", "Play") â€” the nav had
// grown to 11 flat top-level links, unreadable as one row, so related pages
// are grouped under a click-to-open menu instead. Closes on an outside
// click or when a link inside it is followed.
function NavDropdown({ label, icon: Icon, items, pathname }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const isActive = items.some(i => i.match(pathname));

  useEffect(() => {
    function handleClickOutside(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(o => !o)}
        className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-md transition-all cursor-pointer ${
          isActive || open
            ? 'text-sp-gold bg-sp-gold/10'
            : 'text-text-warm/80 hover:text-sp-gold hover:bg-sp-gold/5'
        }`}
      >
        <Icon className="w-3.5 h-3.5" /> {label}
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute top-full left-0 mt-1.5 min-w-[170px] bg-sp-dark border border-sp-bronze/40 rounded-lg shadow-xl shadow-black/40 py-1.5 z-50">
          {items.map(({ href, label: itemLabel, icon: ItemIcon, match }) => (
            <Link
              key={href}
              href={href}
              onClick={() => setOpen(false)}
              className={`flex items-center gap-2 px-3 py-2 text-xs font-medium transition-all ${
                match(pathname) ? 'text-sp-gold bg-sp-gold/10' : 'text-text-warm/80 hover:text-sp-gold hover:bg-sp-gold/5'
              }`}
            >
              <ItemIcon className="w-3.5 h-3.5" /> {itemLabel}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

// Single notification bell for everyone. Every logged-in user gets their
// personal feed (friend requests/accepts, journal shares â€” /api/profile/
// notifications); admin/moderator additionally get the global staff feed
// (/api/notifications) merged into the same dropdown, tagged internally so
// dismiss/clear route to the right endpoint. Used to be two separate bell
// icons (one per feed) â€” merged after that read as visually duplicated.
function NotificationBell({ user, isMobile = false }) {
  const [personalNotifications, setPersonalNotifications] = useState([]);
  const [staffNotifications, setStaffNotifications] = useState([]);
  // Was a hardcoded `role === 'admin' || role === 'moderator'` check, which
  // went stale the moment custom staff roles (Staff Panel > Settings >
  // Roles & Permissions) shipped â€” a "Helper" role is neither of those two
  // strings but can be real staff. Sourced from the same real permission
  // check the Staff Panel itself uses instead.
  const [isStaff, setIsStaff] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const router = useRouter();

  useEffect(() => {
    fetch('/api/permissions/me')
      .then(res => res.json())
      .then(data => setIsStaff(!!data.isStaff))
      .catch(() => setIsStaff(false));
  }, [user]);

  const fetchPersonal = async () => {
    try {
      const res = await fetch(`/api/profile/notifications?email=${encodeURIComponent(user.email)}`);
      const data = await res.json();
      if (res.ok && data.notifications) setPersonalNotifications(data.notifications);
    } catch (err) {
      console.error('Failed to fetch notifications:', err);
    }
  };

  const fetchStaff = async () => {
    try {
      const res = await fetch(`/api/notifications?role=${user.role}`);
      const data = await res.json();
      if (res.ok && data.notifications) setStaffNotifications(data.notifications);
    } catch (err) {
      console.error('Failed to fetch staff notifications:', err);
    }
  };

  useEffect(() => {
    fetchPersonal();
    if (isStaff) fetchStaff();
    const interval = setInterval(() => {
      fetchPersonal();
      if (isStaff) fetchStaff();
    }, 10000); // poll every 10s
    return () => clearInterval(interval);
    // isStaff arrives asynchronously (see the /api/permissions/me effect
    // above) so this must re-run once it flips true, or a custom-role
    // staffer's staff feed would never actually get fetched.
  }, [user, isStaff]);

  // Merge both feeds into one chronological list, newest first, each item
  // tagged with which feed it came from so dismiss/click know which API to
  // hit â€” the dropdown itself doesn't otherwise distinguish them.
  const notifications = [
    ...personalNotifications.map(n => ({ ...n, _source: 'personal' })),
    ...staffNotifications.map(n => ({ ...n, _source: 'staff' })),
  ].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

  const handleDismiss = async (e, n) => {
    e.stopPropagation();
    if (n._source === 'personal') {
      setPersonalNotifications(prev => prev.filter(x => x.id !== n.id));
      try {
        const res = await fetch(`/api/profile/notifications?id=${n.id}&email=${encodeURIComponent(user.email)}`, { method: 'DELETE' });
        const data = await res.json();
        if (res.ok && Array.isArray(data.notifications)) setPersonalNotifications(data.notifications);
      } catch (err) { console.error(err); }
    } else {
      setStaffNotifications(prev => prev.filter(x => x.id !== n.id));
      try {
        const res = await fetch(`/api/notifications?id=${n.id}&role=${user.role}`, { method: 'DELETE' });
        const data = await res.json();
        if (res.ok && Array.isArray(data.notifications)) setStaffNotifications(data.notifications);
      } catch (err) { console.error(err); }
    }
  };

  const handleClearAll = async (e) => {
    e.stopPropagation();
    setPersonalNotifications([]);
    setStaffNotifications([]);
    try {
      await fetch('/api/profile/notifications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: user.email, action: 'clear_all' })
      });
      if (isStaff) {
        await fetch('/api/notifications', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ role: user.role, action: 'clear_all' })
        });
      }
    } catch (err) { console.error(err); }
  };

  const handleNotificationClick = (n) => {
    setIsOpen(false);
    if (n._source === 'personal') {
      fetch('/api/profile/notifications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: user.email, action: 'mark_read', id: n.id })
      }).catch(() => {});
    }
    if (n.link) router.push(n.link);
  };

  const getIcon = (type) => {
    switch (type) {
      case 'report':
        return <ShieldAlert className="w-4 h-4 text-crimson" />;
      case 'wiki':
        return <BookOpen className="w-4 h-4 text-sp-gold" />;
      case 'updates':
        return <Sparkles className="w-4 h-4 text-sp-copper" />;
      case 'news':
        return <Newspaper className="w-4 h-4 text-sp-copper" />;
      case 'chat':
        return <MessageSquare className="w-4 h-4 text-blue-400" />;
      case 'friend_request':
        return <UserPlus className="w-4 h-4 text-sp-gold" />;
      case 'friend_accepted':
        return <Users className="w-4 h-4 text-emerald-400" />;
      case 'journal_share':
        return <BookOpen className="w-4 h-4 text-sakura-accent" />;
      case 'event':
      case 'event_reminder':
        return <Calendar className="w-4 h-4 text-sakura-pink" />;
      case 'poll':
        return <ThumbsUp className="w-4 h-4 text-sp-gold" />;
      case 'project':
        return <Hammer className="w-4 h-4 text-sp-copper" />;
      case 'rules':
        return <Shield className="w-4 h-4 text-sp-gold" />;
      case 'wiki_category':
        return <BookOpen className="w-4 h-4 text-sp-gold" />;
      case 'tip':
        return <Sparkles className="w-4 h-4 text-sp-gold" />;
      case 'carousel':
        return <Compass className="w-4 h-4 text-sp-copper" />;
      case 'vote_site':
        return <ThumbsUp className="w-4 h-4 text-sp-gold" />;
      case 'shop':
        return <ShoppingBag className="w-4 h-4 text-sp-copper" />;
      case 'carousel_set':
      case 'site_content':
        return <Compass className="w-4 h-4 text-sp-copper" />;
      case 'achievement':
        return <Trophy className="w-4 h-4 text-sp-gold" />;
      case 'brewing':
        return <FlaskConical className="w-4 h-4 text-sakura-pink" />;
      case 'guide':
        return <BookOpen className="w-4 h-4 text-emerald-400" />;
      default:
        return <Bell className="w-4 h-4 text-text-warm/40" />;
    }
  };

  const timeAgo = (dateStr) => {
    const elapsed = Date.now() - new Date(dateStr).getTime();
    const seconds = Math.floor(elapsed / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    if (seconds < 60) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    return new Date(dateStr).toLocaleDateString();
  };

  return (
    <div className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 rounded-lg border border-sp-bronze/30 hover:border-sp-gold bg-neutral-950/40 text-text-warm hover:text-sp-gold transition-all cursor-pointer flex items-center justify-center"
        title="Notifications"
      >
        <Bell className={`w-4.5 h-4.5 ${notifications.length > 0 ? 'animate-bounce' : ''}`} />
        {notifications.length > 0 && (
          <span className="absolute -top-1.5 -right-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-crimson text-[9px] font-bold text-white ring-2 ring-sp-dark">
            {notifications.length}
          </span>
        )}
      </button>

      {isOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)} />
          <div className={`absolute ${isMobile ? 'right-[-50px]' : 'right-0'} mt-3 w-80 bg-sp-dark/95 border-2 border-sp-bronze shadow-2xl rounded-xl p-4 z-50 text-left steampunk-glass max-h-96 overflow-y-auto space-y-3`}>
            <div className="flex items-center justify-between border-b border-sp-bronze/25 pb-2">
              <span className="font-serif text-xs font-bold text-sp-gold uppercase tracking-wider flex items-center gap-1.5">
                <Bell className="w-3.5 h-3.5 text-sp-copper" /> Notifications
              </span>
              {notifications.length > 0 && (
                <button
                  onClick={handleClearAll}
                  className="text-[9px] uppercase font-bold text-rose-300 hover:text-rose-400 hover:underline"
                >
                  Clear All
                </button>
              )}
            </div>

            {notifications.length === 0 ? (
              <div className="text-center py-8 text-text-warm/40 text-xs italic flex flex-col items-center justify-center gap-2">
                <BellOff className="w-8 h-8 opacity-25" />
                All lines silent. No pending signals.
              </div>
            ) : (
              <div className="divide-y divide-sp-bronze/10 max-h-64 overflow-y-auto">
                {notifications.map((n) => (
                  <div
                    key={`${n._source}-${n.id}`}
                    onClick={() => handleNotificationClick(n)}
                    className="py-2.5 flex items-start gap-3 hover:bg-neutral-900/40 rounded-lg px-2 transition-colors cursor-pointer group"
                  >
                    <div className="mt-0.5 shrink-0">{getIcon(n.type)}</div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs text-text-warm/85 group-hover:text-white leading-relaxed font-sans select-none">
                        {n.message}
                      </p>
                      <span className="text-[8px] font-mono text-text-warm/40 mt-1 block">
                        {timeAgo(n.createdAt)}
                      </span>
                    </div>
                    <button
                      onClick={(e) => handleDismiss(e, n)}
                      className="text-text-warm/20 hover:text-rose-400 p-0.5 shrink-0 self-center"
                      title="Dismiss"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

export default function ClientProviders({ children }) {
  const [user, setUser] = useState(null);
  const [isLoginOpen, setIsLoginOpen] = useState(false);
  const [isRegisterMode, setIsRegisterMode] = useState(false);
  
  // Auth Form State
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mcUsername, setMcUsername] = useState('');
  const [verificationRequired, setVerificationRequired] = useState(false);
  const [verificationCode, setVerificationCode] = useState('');
  const [devVerificationCode, setDevVerificationCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [error, setError] = useState('');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isPrivacyOpen, setIsPrivacyOpen] = useState(false);

  // Staff 2FA state â€” populated once /api/auth/login's phase 1 (password
  // check) comes back and says the account needs a TOTP step. 'setup' means
  // first-time (show the QR code to scan), 'verify' means the account
  // already has 2FA enabled (just ask for the 6-digit code).
  const [twoFactorStage, setTwoFactorStage] = useState(null); // null | 'setup' | 'verify'
  const [twoFactorTicket, setTwoFactorTicket] = useState('');
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [twoFactorQrCode, setTwoFactorQrCode] = useState('');
  const [twoFactorSecret, setTwoFactorSecret] = useState('');
  const [twoFactorSubmitting, setTwoFactorSubmitting] = useState(false);
  // Whether THIS account has any passkeys registered (from login phase 1's
  // hasPasskeys, see api/auth/login) â€” decides whether "Use a passkey
  // instead" shows at all on the TOTP-code step. Passkeys are added from
  // /profile after a normal TOTP login, see [[passkey-login]] equivalent.
  const [twoFactorHasPasskeys, setTwoFactorHasPasskeys] = useState(false);
  const [passkeySubmitting, setPasskeySubmitting] = useState(false);

  // "Forgot password?" â€” a separate lightweight mode inside the same login
  // modal rather than a whole new page/route, so it's reachable from
  // wherever the login modal already is. See api/auth/forgot-password.
  const [isForgotMode, setIsForgotMode] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotSubmitting, setForgotSubmitting] = useState(false);
  const [forgotMessage, setForgotMessage] = useState('');

  const pathname = usePathname();
  const router = useRouter();

  // Load user from localStorage on mount
  useEffect(() => {
    const savedUser = localStorage.getItem('iekcorbar_user');
    if (savedUser) {
      const parsed = JSON.parse(savedUser);
      setUser(parsed);
      // Refresh from the server so a role (or IGN) change made by an admin
      // since this login shows up â€” localStorage alone froze it at login.
      fetch('/api/auth/me')
        .then(res => (res.ok ? res.json() : null))
        .then(data => {
          if (data?.user && data.user.email?.toLowerCase() === parsed.email?.toLowerCase()) {
            const fresh = { ...parsed, ...data.user };
            setUser(fresh);
            localStorage.setItem('iekcorbar_user', JSON.stringify(fresh));
          }
        })
        .catch(() => {});
    }
  }, []);

  // Unread private messages for the Chat nav badge. Polled lightly (and
  // re-checked on every navigation, so leaving /chat clears it promptly).
  const [chatUnread, setChatUnread] = useState(0);
  useEffect(() => {
    if (!user?.mcUsername) { setChatUnread(0); return; }
    let cancelled = false;
    const check = () => {
      if (document.visibilityState !== 'visible') return;
      fetch('/api/chat?unread=1')
        .then(res => (res.ok ? res.json() : null))
        .then(data => { if (!cancelled && data) setChatUnread(Number(data.unread) || 0); })
        .catch(() => {});
    };
    check();
    const interval = setInterval(check, 30000);
    document.addEventListener('visibilitychange', check);
    return () => { cancelled = true; clearInterval(interval); document.removeEventListener('visibilitychange', check); };
  }, [user?.mcUsername, pathname]);

  // Site content (admin-editable text labels) â€” fetched once for the whole
  // app; siteContent state starts at the code defaults (see
  // SiteContentContext above) so nothing needs to wait for this to render.
  const [siteContent, setSiteContent] = useState(siteContentDefaultsMap());
  useEffect(() => {
    fetch('/api/site-content')
      .then(res => res.json())
      .then(data => { if (data.content) setSiteContent(data.content); })
      .catch(() => {});
  }, []);

  // Public join IP:port â€” staff-editable (Staff Panel > Settings > General
  // Settings), shown in the footer and the registration flow below. Starts
  // at the real current address so there's nothing stale to flash before
  // this resolves; see db.getSettings()'s comment for why it's no longer
  // hardcoded at each callsite.
  const [serverAddress, setServerAddress] = useState('play.iekcorbar.com');
  useEffect(() => {
    fetch('/api/settings')
      .then(res => res.json())
      .then(data => { if (data.settings?.server_address) setServerAddress(data.settings.server_address); })
      .catch(() => {});
  }, []);

  // Real staff check (mirrors NotificationBell's own /api/permissions/me
  // call above) used only to hide the Updates nav link from non-staff now
  // that /updates itself is staff-only â€” a hardcoded role check would miss
  // custom Staff Panel roles.
  const [navIsStaff, setNavIsStaff] = useState(false);
  useEffect(() => {
    fetch('/api/permissions/me')
      .then(res => res.json())
      .then(data => setNavIsStaff(!!data.isStaff))
      .catch(() => setNavIsStaff(false));
  }, [user]);

  // PWA installability â€” registers the no-op service worker (see
  // public/sw.js) so mobile browsers offer "Add to Home Screen" /
  // "Install app". Silently no-ops in browsers without support.
  useEffect(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(err => {
        console.error('Service worker registration failed:', err);
      });
    }
  }, []);

  const resetTwoFactorState = () => {
    setTwoFactorStage(null);
    setTwoFactorTicket('');
    setTwoFactorCode('');
    setTwoFactorQrCode('');
    setTwoFactorSecret('');
    setTwoFactorSubmitting(false);
    setTwoFactorHasPasskeys(false);
    setPasskeySubmitting(false);
  };

  const resetForgotPasswordState = () => {
    setIsForgotMode(false);
    setForgotEmail('');
    setForgotSubmitting(false);
    setForgotMessage('');
  };

  const handleForgotPasswordSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setForgotSubmitting(true);
    try {
      const res = await fetch('/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'request', email: forgotEmail })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        // Always the same generic message, whether or not that email has an
        // account â€” see the route for why.
        setForgotMessage(data.message || "If an account exists for that email, we've sent a password reset link.");
      } else {
        setError(data.error || 'Something went wrong. Please try again.');
      }
    } catch {
      setError('Network error â€” please try again.');
    } finally {
      setForgotSubmitting(false);
    }
  };

  const finishLogin = (data) => {
    setUser(data.user);
    localStorage.setItem('iekcorbar_user', JSON.stringify(data.user));
    setIsLoginOpen(false);
    setEmail('');
    setPassword('');
    resetTwoFactorState();
    router.refresh();
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, action: 'login' })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        if (data.needsTotpSetup) {
          setTwoFactorStage('setup');
          setTwoFactorTicket(data.ticket);
          setTwoFactorQrCode(data.qrCodeDataUrl);
          setTwoFactorSecret(data.secretForManualEntry);
        } else if (data.needsTotpCode) {
          setTwoFactorStage('verify');
          setTwoFactorTicket(data.ticket);
          setTwoFactorHasPasskeys(!!data.hasPasskeys);
        } else {
          finishLogin(data);
        }
      } else {
        setError(data.error || "Login failed");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleTwoFactorSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setTwoFactorSubmitting(true);
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: twoFactorStage === 'setup' ? 'confirm_totp_setup' : 'verify_totp',
          ticket: twoFactorTicket,
          code: twoFactorCode
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        finishLogin(data);
      } else {
        setError(data.error || "Verification failed");
      }
    } catch (err) {
      setError("Network error");
    } finally {
      setTwoFactorSubmitting(false);
    }
  };

  // "Use a passkey instead" on the TOTP-code step â€” reuses the same ticket
  // phase 1 already issued (see api/auth/login's passkey_auth_* actions).
  // Cancelling the browser's own passkey prompt lands here as a thrown
  // error too (not just a real failure), so that path just quietly returns
  // to the code-entry form rather than showing a scary error for something
  // the person did on purpose.
  const handlePasskeyLogin = async () => {
    setError('');
    setPasskeySubmitting(true);
    try {
      const optionsRes = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'passkey_auth_options', ticket: twoFactorTicket })
      });
      const optionsData = await optionsRes.json();
      if (!optionsRes.ok || !optionsData.success) {
        setError(optionsData.error || 'Could not start passkey sign-in');
        return;
      }

      let assertion;
      try {
        assertion = await startAuthentication({ optionsJSON: optionsData.options });
      } catch (webauthnErr) {
        // User cancelled, or this device/browser doesn't support it â€” not a
        // real error, just fall back to typing the code.
        return;
      }

      const verifyRes = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'passkey_auth_verify', ticket: twoFactorTicket, response: assertion })
      });
      const verifyData = await verifyRes.json();
      if (verifyRes.ok && verifyData.success) {
        finishLogin(verifyData);
      } else {
        setError(verifyData.error || 'Passkey sign-in failed');
      }
    } catch (err) {
      setError('Network error');
    } finally {
      setPasskeySubmitting(false);
    }
  };

  const resetRegisterForm = () => {
    setVerificationRequired(false);
    setVerificationCode('');
    setDevVerificationCode('');
    setCodeSent(false);
    setSendingCode(false);
  };

  const handleSendVerificationCode = async () => {
    setError('');
    setSendingCode(true);
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mcUsername, action: 'send_code' })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setCodeSent(true);
        if (data.devCode) {
          setDevVerificationCode(data.devCode);
        }
        setTimeout(() => setCodeSent(false), 5000);
      } else {
        setError(data.error || "Failed to dispatch verification code");
      }
    } catch (err) {
      setError("Network error sending code");
    } finally {
      setSendingCode(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (!verificationRequired) {
        // Step 1: Initiate & Whitelist
        const res = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password, mcUsername, action: 'initiate' })
        });
        const data = await res.json();
        if (res.ok && data.success) {
          // Account is created + logged in right away; linking the Minecraft
          // account happens on /link (3 days, see api/auth/register).
          setUser(data.user);
          localStorage.setItem('iekcorbar_user', JSON.stringify(data.user));
          setIsLoginOpen(false);
          setEmail('');
          setPassword('');
          setMcUsername('');
          resetRegisterForm();
          router.push('/link');
        } else {
          setError(data.error || "Registration failed");
        }
      } else {
        // Step 3: Verify Code
        const res = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ mcUsername, code: verificationCode, action: 'verify' })
        });
        const data = await res.json();
        if (res.ok && data.success) {
          setUser(data.user);
          localStorage.setItem('iekcorbar_user', JSON.stringify(data.user));
          setIsLoginOpen(false);
          setEmail('');
          setPassword('');
          setMcUsername('');
          resetRegisterForm();
          router.refresh();
        } else {
          setError(data.error || "Verification failed");
        }
      }
    } catch (err) {
      setError("Network error");
    }
  };

  // True inside the Android app or the iPhone home-screen version â€” used to
  // hide "get the app" prompts from people who already have it.
  const [usingApp, setUsingApp] = useState(false);
  useEffect(() => {
    try {
      setUsingApp(navigator.userAgent.includes('IekcorbarApp/') ||
        window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true);
    } catch { /* ignore */ }
  }, []);

  // New registrations that still have to link their Minecraft account
  // (api/account/link): shows a banner under the header on every page.
  const [linkInfo, setLinkInfo] = useState(null);
  useEffect(() => {
    if (!user) { setLinkInfo(null); return; }
    const load = () => fetch('/api/account/link').then(r => (r.ok ? r.json() : null)).then(setLinkInfo).catch(() => {});
    load();
    window.addEventListener('iekcorbar-link-changed', load);
    return () => window.removeEventListener('iekcorbar-link-changed', load);
  }, [user, pathname]);

  // First-party analytics (Staff Panel â†’ Analytics): one beacon per page
  // navigation. Path only (no query string); cookie-free â€” see src/lib/analytics.js.
  useEffect(() => {
    if (!pathname) return;
    try {
      const standalone = window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true;
      // document.referrer never changes during client-side navigation, so only
      // the first page view of a visit carries it (else every page would count
      // as another visit "from Discord/Google").
      const referrer = !window.__iekcorbarReferrerSent && document.referrer && !document.referrer.startsWith(window.location.origin) ? document.referrer : null;
      window.__iekcorbarReferrerSent = true;
      fetch('/api/analytics/track', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: pathname, referrer, standalone }),
        keepalive: true
      }).catch(() => {});
    } catch { /* analytics must never break the page */ }
  }, [pathname]);

  // Web Push (browsers / iPhone home-screen app): keep this browser's
  // subscription bound to whoever is logged in. Never prompts by itself.
  useEffect(() => {
    if (user) syncWebPush();
  }, [user]);

  const handleLogout = () => {
    unbindWebPush();
    setUser(null);
    localStorage.removeItem('iekcorbar_user');
    fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
    router.push('/');
    router.refresh();
  };


  // Generate falling sakura petals
  const [petals, setPetals] = useState([]);
  useEffect(() => {
    // Generate 16 petals with random positions, delays and animations
    const generated = Array.from({ length: 16 }).map((_, i) => ({
      id: i,
      left: `${Math.random() * 100}%`,
      delay: `${Math.random() * 12}s`,
      size: `${Math.random() * 12 + 8}px`,
      anim: i % 3 === 0 ? 'animate-sakura-fall-1' : i % 3 === 1 ? 'animate-sakura-fall-2' : 'animate-sakura-fall-3'
    }));
    setPetals(generated);
  }, []);

  return (
    <SiteContentContext.Provider value={siteContent}>
    <UserContext.Provider value={{ user, setUser, setIsLoginOpen, setIsRegisterMode, handleLogout }}>
      {/* Background Falling Sakura Overlay */}
      <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden select-none">
        {petals.map((petal) => (
          <div
            key={petal.id}
            className={`absolute rounded-tr-[100%] rounded-bl-[100%] rounded-tl-[40%] rounded-br-[40%] bg-linear-to-br from-sakura-pink to-sakura-accent opacity-0 ${petal.anim}`}
            style={{
              left: petal.left,
              animationDelay: petal.delay,
              width: petal.size,
              height: petal.size,
              filter: 'blur(0.5px)',
            }}
          />
        ))}
      </div>

      {/* Background Animated Steampunk Gears */}
      <div className="fixed -bottom-16 -left-16 pointer-events-none z-0 opacity-10 select-none">
        <Cog className="w-48 h-48 text-sp-gold animate-spin-gear-cw" />
      </div>
      <div className="fixed -top-16 -right-16 pointer-events-none z-0 opacity-10 select-none">
        <Cog className="w-64 h-64 text-sp-copper animate-spin-gear-ccw" />
      </div>

      {/* Main Container */}
      <div className="relative z-10 min-h-screen flex flex-col justify-between">
        
        {/* Navigation Bar */}
        <header className="w-full bg-sp-dark/90 border-b-2 border-sp-bronze sticky top-0 z-50 backdrop-blur-md shadow-lg">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex items-center justify-between h-12">
              
              {/* Brand Logo & Name */}
              <div className="flex items-center gap-2 shrink-0">
                <div className="w-8 h-8 rounded-md border border-sp-gold overflow-hidden flex items-center justify-center shadow-sm shadow-sp-gold/15">
                  <img src="/images/logo.png" alt="Logo" className="w-full h-full object-cover" />
                </div>
                <Link href="/" className="font-serif text-lg font-bold tracking-widest text-sp-gold hover:text-white transition-colors steampunk-glow">
                  iekcorbar
                </Link>
              </div>

              {/* Desktop Nav Items â€” Home/Wiki/Members stay direct links (most
                  visited); the rest is grouped into two dropdowns since 11 flat
                  top-level links no longer fit as one readable row. */}
              <nav className="hidden lg:flex items-center gap-1">
                {[
                  { href: '/', label: 'Home', icon: Compass, match: (p) => p === '/' },
                  { href: '/wiki', label: 'Wiki', icon: BookOpen, match: (p) => p.startsWith('/wiki') },
                  { href: '/members', label: 'Members', icon: Users, match: (p) => p === '/members' },
                ].map(({ href, label, icon: Icon, match }) => (
                  <Link
                    key={href}
                    href={href}
                    className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-md transition-all ${
                      match(pathname)
                        ? 'text-sp-gold bg-sp-gold/10'
                        : 'text-text-warm/80 hover:text-sp-gold hover:bg-sp-gold/5'
                    }`}
                  >
                    <Icon className="w-3.5 h-3.5" /> {label}
                  </Link>
                ))}

                <NavDropdown
                  label="Community"
                  icon={Users2}
                  pathname={pathname}
                  items={[
                    { href: '/news', label: 'News & Upcoming', icon: Newspaper, match: (p) => p.startsWith('/news') },
                    ...(navIsStaff ? [{ href: '/updates', label: 'Updates', icon: Sparkles, match: (p) => p.startsWith('/updates') }] : []),
                    { href: '/rules', label: 'Rules', icon: Shield, match: (p) => p === '/rules' },
                    { href: '/events', label: 'Events', icon: Calendar, match: (p) => p.startsWith('/events') },
                    { href: '/projects', label: 'Projects', icon: Hammer, match: (p) => p.startsWith('/projects') },
                    { href: '/known-bugs', label: 'Known Bugs', icon: Bug, match: (p) => p.startsWith('/known-bugs') },
                  ]}
                />

                <NavDropdown
                  label="Play"
                  icon={FlaskConical}
                  pathname={pathname}
                  items={[
                    { href: '/map', label: 'Map', icon: Map, match: (p) => p === '/map' },
                    { href: '/vote', label: 'Vote', icon: ThumbsUp, match: (p) => p === '/vote' },
                    { href: '/steamworks', label: 'Steamworks Guide', icon: Cog, match: (p) => p.startsWith('/steamworks') },
                    { href: '/sakagura', label: 'Sakagura Guide', icon: Wine, match: (p) => p.startsWith('/sakagura') },
                    { href: '/recipes', label: 'Recipes', icon: BookOpen, match: (p) => p.startsWith('/recipes') },
                    { href: '/shop', label: 'Shop', icon: ShoppingBag, match: (p) => p.startsWith('/shop') },
                    { href: '/donate', label: 'Support', icon: HeartHandshake, match: (p) => p.startsWith('/donate') },
                    { href: '/achievements', label: 'Achievements', icon: Trophy, match: (p) => p.startsWith('/achievements') },
                  ]}
                />

                {user && (
                  <>
                    <div className="w-px h-5 bg-sp-bronze/30 mx-1" />
                    <Link 
                      href="/profile" 
                      className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-md transition-all ${
                        pathname === '/profile' 
                          ? 'text-sp-gold bg-sp-gold/10' 
                          : 'text-text-warm/80 hover:text-sp-gold hover:bg-sp-gold/5'
                      }`}
                    >
                      <User className="w-3.5 h-3.5" /> {user.mcUsername}
                    </Link>
                    <Link
                      href="/chat"
                      className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-md transition-all ${
                        pathname.startsWith('/chat')
                          ? 'text-sp-gold bg-sp-gold/10'
                          : 'text-text-warm/80 hover:text-sp-gold hover:bg-sp-gold/5'
                      }`}
                    >
                      <MessageSquare className="w-3.5 h-3.5" /> Chat
                      {chatUnread > 0 && (
                        <span className="ml-0.5 min-w-4 h-4 px-1 rounded-full bg-sp-gold text-black text-[9px] font-bold flex items-center justify-center">
                          {chatUnread > 99 ? '99+' : chatUnread}
                        </span>
                      )}
                    </Link>
                    <Link
                      href="/friends"
                      className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-md transition-all ${
                        pathname.startsWith('/friends')
                          ? 'text-sp-gold bg-sp-gold/10'
                          : 'text-text-warm/80 hover:text-sp-gold hover:bg-sp-gold/5'
                      }`}
                    >
                      <UserPlus className="w-3.5 h-3.5" /> Friends
                    </Link>
                  </>
                )}

                {user && (user.role === 'admin' || user.role === 'moderator') && (
                  <>
                    <div className="w-px h-5 bg-crimson/30 mx-1" />
                    <Link 
                      href="/moderation" 
                      className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-md transition-all ${
                        pathname === '/moderation' 
                          ? 'text-rose-400 bg-crimson/15' 
                          : 'text-rose-300/80 hover:text-rose-400 hover:bg-crimson/10'
                      }`}
                    >
                      <ShieldAlert className="w-3.5 h-3.5" /> Staff
                    </Link>
                  </>
                )}
              </nav>

              {/* Desktop Right Actions */}
              <div className="hidden lg:flex items-center gap-2 shrink-0">
                {user && <NotificationBell user={user} />}
                {user ? (
                  <button
                    onClick={handleLogout}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border border-sp-bronze/40 hover:border-crimson hover:bg-crimson/15 text-text-warm/70 hover:text-white transition-all cursor-pointer"
                  >
                    <LogOut className="w-3 h-3" /> Logout
                  </button>
                ) : (
                  <>
                    <button
                      onClick={() => { setIsLoginOpen(true); setIsRegisterMode(false); }}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[10px] font-semibold uppercase tracking-wider text-text-warm/70 hover:text-sp-gold transition-all cursor-pointer"
                    >
                      <LogIn className="w-3 h-3" /> Log In
                    </button>
                    <button
                      onClick={() => { setIsLoginOpen(true); setIsRegisterMode(true); }}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[10px] font-bold uppercase tracking-wider border border-sp-gold bg-sp-gold hover:bg-white text-black transition-all cursor-pointer shadow-sm shadow-sp-gold/20"
                    >
                      <UserPlus className="w-3 h-3" /> Register
                    </button>
                  </>
                )}
              </div>

              {/* Mobile Menu Trigger */}
              <div className="flex lg:hidden items-center gap-2">
                {user && <NotificationBell user={user} isMobile={true} />}
                {!user && (
                  <button
                    onClick={() => { setIsLoginOpen(true); setIsRegisterMode(false); setMobileMenuOpen(false); }}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-bold uppercase tracking-wider border border-sp-gold bg-sp-gold text-black hover:bg-white transition-all"
                  >
                    <LogIn className="w-3.5 h-3.5" /> Log In
                  </button>
                )}
                <button
                  onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                  className="text-text-warm hover:text-sp-gold p-1.5 rounded-md hover:bg-sp-gold/5 transition-colors"
                >
                  {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
                </button>
              </div>

            </div>
          </div>

          {/* Mobile Menu Content */}
          {mobileMenuOpen && (
            <div className="lg:hidden bg-sp-dark border-t border-sp-bronze/40 px-3 pt-2 pb-6 space-y-0.5 h-[calc(100dvh-3rem)] overflow-y-auto overscroll-contain">

              {/* Logged out: account actions first, so logging in doesn't
                  require scrolling past the whole menu (app feedback). */}
              {!user && (
                <div className="px-1 pt-1 pb-2 mb-1 border-b border-sp-bronze/20 grid grid-cols-2 gap-2">
                  <button
                    onClick={() => { setIsLoginOpen(true); setIsRegisterMode(false); setMobileMenuOpen(false); }}
                    className="flex items-center justify-center gap-2 px-3 py-2.5 border border-sp-gold bg-sp-gold rounded-md text-xs font-bold uppercase tracking-wider text-black hover:bg-white transition-all"
                  >
                    <LogIn className="w-3.5 h-3.5" /> Log In
                  </button>
                  <button
                    onClick={() => { setIsLoginOpen(true); setIsRegisterMode(true); setMobileMenuOpen(false); }}
                    className="flex items-center justify-center gap-2 px-3 py-2.5 border border-sp-bronze/60 rounded-md text-xs font-semibold uppercase tracking-wider text-text-warm/80 hover:border-sp-gold hover:text-sp-gold transition-all"
                  >
                    <UserPlus className="w-3.5 h-3.5" /> Register
                  </button>
                </div>
              )}
              
              {/* Get the app â€” hidden inside the app itself / home-screen version */}
              {!usingApp && (
                <Link
                  href="/app"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center gap-2.5 mx-1 mb-1 px-3 py-2.5 rounded-md border border-sp-gold/50 bg-sp-gold/10 text-sp-gold text-sm font-semibold"
                >
                  <Smartphone className="w-4 h-4 shrink-0" /> Get the Iekcorbar app
                  <span className="ml-auto text-[10px] uppercase tracking-wider text-sp-gold/70">Free</span>
                </Link>
              )}

              {/* Primary Navigation â€” grouped to match the desktop dropdowns */}
              <span className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest px-2 pt-1 pb-0.5">Navigation</span>
              {[
                { href: '/', label: 'Home', icon: Compass, match: (p) => p === '/' },
                { href: '/wiki', label: 'Wiki', icon: BookOpen, match: (p) => p.startsWith('/wiki') },
                { href: '/members', label: 'Members', icon: Users, match: (p) => p === '/members' },
              ].map(({ href, label, icon: Icon, match }) => (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-all ${
                    match(pathname) ? 'bg-sp-gold/10 text-sp-gold' : 'text-text-warm/80 hover:bg-sp-charcoal hover:text-white'
                  }`}
                >
                  <Icon className="w-4 h-4 shrink-0" /> {label}
                </Link>
              ))}

              <span className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest px-2 pt-2 pb-0.5">Community</span>
              {[
                { href: '/news', label: 'News & Upcoming', icon: Newspaper, match: (p) => p.startsWith('/news') },
                ...(navIsStaff ? [{ href: '/updates', label: 'Updates', icon: Sparkles, match: (p) => p.startsWith('/updates') }] : []),
                { href: '/rules', label: 'Rules', icon: Shield, match: (p) => p === '/rules' },
                { href: '/events', label: 'Events', icon: Calendar, match: (p) => p.startsWith('/events') },
                { href: '/projects', label: 'Projects', icon: Hammer, match: (p) => p.startsWith('/projects') },
                { href: '/known-bugs', label: 'Known Bugs', icon: Bug, match: (p) => p.startsWith('/known-bugs') },
              ].map(({ href, label, icon: Icon, match }) => (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-all ${
                    match(pathname) ? 'bg-sp-gold/10 text-sp-gold' : 'text-text-warm/80 hover:bg-sp-charcoal hover:text-white'
                  }`}
                >
                  <Icon className="w-4 h-4 shrink-0" /> {label}
                </Link>
              ))}

              <span className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest px-2 pt-2 pb-0.5">Play</span>
              {[
                { href: '/map', label: 'Map', icon: Map, match: (p) => p === '/map' },
                { href: '/vote', label: 'Vote', icon: ThumbsUp, match: (p) => p === '/vote' },
                { href: '/steamworks', label: 'Steamworks Guide', icon: Cog, match: (p) => p.startsWith('/steamworks') },
                { href: '/sakagura', label: 'Sakagura Guide', icon: Wine, match: (p) => p.startsWith('/sakagura') },
                { href: '/recipes', label: 'Recipes', icon: BookOpen, match: (p) => p.startsWith('/recipes') },
                { href: '/shop', label: 'Shop', icon: ShoppingBag, match: (p) => p.startsWith('/shop') },
                { href: '/donate', label: 'Support', icon: HeartHandshake, match: (p) => p.startsWith('/donate') },
                { href: '/achievements', label: 'Achievements', icon: Trophy, match: (p) => p.startsWith('/achievements') },
              ].map(({ href, label, icon: Icon, match }) => (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-all ${
                    match(pathname) ? 'bg-sp-gold/10 text-sp-gold' : 'text-text-warm/80 hover:bg-sp-charcoal hover:text-white'
                  }`}
                >
                  <Icon className="w-4 h-4 shrink-0" /> {label}
                </Link>
              ))}

              {/* Authenticated Links */}
              {user && (
                <>
                  <div className="border-t border-sp-bronze/20 mt-1.5 pt-1.5" />
                  <span className="block text-[10px] uppercase font-bold text-sp-gold/80 tracking-widest px-2 pt-0.5 pb-0.5">Account</span>
                  <Link 
                    href="/profile" 
                    onClick={() => setMobileMenuOpen(false)}
                    className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-all ${
                      pathname === '/profile' ? 'bg-sp-gold/10 text-sp-gold' : 'text-text-warm/80 hover:bg-sp-charcoal hover:text-white'
                    }`}
                  >
                    <User className="w-4 h-4 shrink-0" /> Profile ({user.mcUsername})
                  </Link>
                  <Link
                    href="/chat"
                    onClick={() => setMobileMenuOpen(false)}
                    className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-all ${
                      pathname.startsWith('/chat') ? 'bg-sp-gold/10 text-sp-gold' : 'text-text-warm/80 hover:bg-sp-charcoal hover:text-white'
                    }`}
                  >
                    <MessageSquare className="w-4 h-4 shrink-0" /> Messages
                    {chatUnread > 0 && (
                      <span className="ml-auto min-w-5 h-5 px-1.5 rounded-full bg-sp-gold text-black text-[10px] font-bold flex items-center justify-center">
                        {chatUnread > 99 ? '99+' : chatUnread}
                      </span>
                    )}
                  </Link>
                  <Link
                    href="/friends"
                    onClick={() => setMobileMenuOpen(false)}
                    className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-all ${
                      pathname.startsWith('/friends') ? 'bg-sp-gold/10 text-sp-gold' : 'text-text-warm/80 hover:bg-sp-charcoal hover:text-white'
                    }`}
                  >
                    <UserPlus className="w-4 h-4 shrink-0" /> Friends
                  </Link>
                </>
              )}

              {/* Staff Links */}
              {user && (user.role === 'admin' || user.role === 'moderator') && (
                <>
                  <div className="border-t border-crimson/20 mt-1.5 pt-1.5" />
                  <span className="block text-[10px] uppercase font-bold text-rose-300/80 tracking-widest px-2 pt-0.5 pb-0.5">Staff</span>
                  <Link 
                    href="/moderation" 
                    onClick={() => setMobileMenuOpen(false)}
                    className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-all ${
                      pathname === '/moderation' ? 'bg-crimson/15 text-rose-300' : 'text-rose-200/70 hover:bg-sp-charcoal hover:text-rose-300'
                    }`}
                  >
                    <ShieldAlert className="w-4 h-4 shrink-0" /> Staff Panel
                  </Link>
                </>
              )}

              {/* Logout Action */}
              {user && (
                <div className="pt-2 border-t border-sp-bronze/20 mt-1.5 px-1">
                  <button
                    onClick={() => { handleLogout(); setMobileMenuOpen(false); }}
                    className="w-full flex items-center justify-center gap-2 px-3 py-2 border border-crimson/60 rounded-md text-xs font-semibold uppercase tracking-wider text-rose-400 hover:bg-crimson/15 transition-all"
                  >
                    <LogOut className="w-3.5 h-3.5" /> Logout
                  </button>
                </div>
              )}
            </div>
          )}
        </header>

        {/* Account-link banner (new registrations) */}
        {linkInfo && (linkInfo.status === 'pending' || linkInfo.status === 'on_hold') && pathname !== '/link' && (
          <Link
            href="/link"
            className={`block w-full text-center text-xs sm:text-sm px-4 py-2 font-semibold relative z-40 ${
              linkInfo.status === 'on_hold' ? 'bg-rose-900/90 text-rose-100 hover:bg-rose-800' : 'bg-orange-500 text-black hover:bg-orange-400'
            }`}
          >
            {linkInfo.status === 'on_hold'
              ? 'â¸ï¸ Your account is on hold â€” link your Minecraft account to get back on the server. Tap here â†’'
              : `ðŸ”— Link your Minecraft account "${linkInfo.mcUsername}" â€” ${(() => {
                  const ms = new Date(linkInfo.deadline).getTime() - Date.now();
                  const d = Math.floor(ms / 86400000), h = Math.floor((ms % 86400000) / 3600000);
                  return ms <= 0 ? 'time is up' : d > 0 ? `${d}d ${h}h left` : `${Math.max(h, 0)}h left`;
                })()}. Tap here â†’`}
          </Link>
        )}

        {/* Dynamic Page Content */}
        <main className="flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 relative z-10">
          {children}
        </main>

        {/* Footer */}
        <footer className="w-full bg-neutral-950 border-t-2 border-sp-bronze pt-12 pb-6 relative z-10 text-xs text-text-warm/75">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
            
            {/* Sitemap Grid */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
              
              {/* Column 1: About */}
              <div className="space-y-4 text-center md:text-left">
                <div className="flex items-center justify-center md:justify-start gap-2">
                  <img src="/images/logo.png" alt="Logo" className="w-12 h-12 rounded-md border border-sp-gold" />
                  <span className="font-serif text-lg font-bold text-sp-gold uppercase tracking-wider steampunk-glow">iekcorbar</span>
                </div>
                <p className="text-[11px] text-text-warm/60 leading-relaxed max-w-xs mx-auto md:mx-0">
                  A challenging Minecraft server blending historical steam-power ingenuity with ancient Japanese folklore, custom trinkets, land protection, and community brewing.
                </p>
              </div>

              {/* Column 2: Navigation Links */}
              <div className="space-y-3 text-center md:text-left">
                <span className="block text-[10px] uppercase font-bold text-sp-gold tracking-widest">Navigation</span>
                <ul className="space-y-1.5 font-medium">
                  <li><Link href="/" className="hover:text-sp-copper transition-colors">Home Dashboard</Link></li>
                  <li><Link href="/wiki" className="hover:text-sp-copper transition-colors">Wiki Chronicles</Link></li>
                  <li><Link href="/events" className="hover:text-sp-copper transition-colors">Calendar & Events</Link></li>
                  <li><Link href="/profile" className="hover:text-sp-copper transition-colors">Player Profile</Link></li>
                </ul>
              </div>

              {/* Column 3: Wiki Lore Categories */}
              <div className="space-y-3 text-center md:text-left">
                <span className="block text-[10px] uppercase font-bold text-sp-gold tracking-widest">Wiki Categories</span>
                <ul className="space-y-1.5 text-text-warm/60 font-medium">
                  <li><Link href="/wiki#races" className="hover:text-sp-copper transition-colors">Custom Races</Link></li>
                  <li><Link href="/wiki#jobs" className="hover:text-sp-copper transition-colors">RPG Jobs & Skills</Link></li>
                  <li><Link href="/sakagura" className="hover:text-sp-copper transition-colors">Sakagura Guide</Link></li>
                  <li><Link href="/wiki#lands" className="hover:text-sp-copper transition-colors">Lands claim policies</Link></li>
                </ul>
              </div>

              {/* Column 4: Signals & Connections */}
              <div className="space-y-3 text-center md:text-left">
                <span className="block text-[10px] uppercase font-bold text-sp-gold tracking-widest">Transmitter Signals</span>
                <ul className="space-y-2">
                  <li>
                    <span className="block text-[9px] uppercase font-bold text-text-warm/40">Server IP</span>
                    <span className="font-mono text-white font-bold select-all">{serverAddress}</span>
                  </li>
                  <li>
                    <span className="block text-[9px] uppercase font-bold text-text-warm/40">Discord invite</span>
                    <a href="https://discord.gg/G9P2khU7F4" target="_blank" rel="noreferrer" className="text-sakura-pink hover:underline font-bold">discord.gg/G9P2khU7F4</a>
                  </li>
                  <li className="pt-1">
                    <span className="block text-[9px] uppercase font-bold text-text-warm/40 mb-1.5">Mobile app</span>
                    {/* Deliberately our own plain badges, not the real
                        App Store/Google Play artwork (those are Apple/
                        Google trademarks with strict usage guidelines,
                        and this app isn't published on either store yet)
                        â€” non-clickable, clearly marked "Coming Soon". */}
                    <div className="flex items-center justify-center md:justify-start gap-2">
                      <div className="flex items-center gap-1.5 bg-neutral-900/60 border border-sp-bronze/30 rounded-lg px-2.5 py-1.5">
                        <Apple className="w-4 h-4 text-text-warm/70 shrink-0" />
                        <div className="leading-tight text-left">
                          <span className="block text-[7px] uppercase tracking-wider text-text-warm/40">Coming Soon</span>
                          <span className="block text-[10px] font-bold text-white">App Store</span>
                        </div>
                      </div>
                      {/* Android app is out (sideloaded APK via /app, Google
                          Play later) â€” so this one is a real link now. */}
                      <Link href="/app" className="flex items-center gap-1.5 bg-neutral-900/60 border border-sp-gold/60 hover:border-sp-gold hover:bg-sp-gold/10 rounded-lg px-2.5 py-1.5 transition-all">
                        <Smartphone className="w-4 h-4 text-sp-gold shrink-0" />
                        <div className="leading-tight text-left">
                          <span className="block text-[7px] uppercase tracking-wider text-sp-gold/80">Download now</span>
                          <span className="block text-[10px] font-bold text-white">Android App</span>
                        </div>
                      </Link>
                    </div>
                  </li>
                </ul>
              </div>

            </div>

            {/* Brand Banner â€” shown at its native aspect ratio (no
                object-cover crop/stretch, unlike the hero background image),
                framed like the site's other steampunk-bordered panels. */}
            <div className="flex justify-center border-t border-sp-bronze/20 pt-8">
              <img
                src="/images/banner-strip.jpg"
                alt="Iekcorbar â€” æ¡œã¨è’¸æ°— â€” Steampunk Japanese RPG Server"
                className="w-full max-w-[728px] h-auto rounded-lg border border-sp-gold/40 shadow-lg shadow-sp-gold/5"
              />
            </div>

            {/* Bottom Section */}
            <div className="border-t border-sp-bronze/20 pt-6 flex flex-col md:flex-row items-center justify-between gap-4 text-[10px] text-text-warm/40">
              <div className="flex items-center gap-2">
                <Compass className="w-3.5 h-3.5 text-sp-copper" />
                <span>&copy; {new Date().getFullYear()} iekcorbar. All rights reserved.</span>
                <span className="text-sp-bronze/30">|</span>
                <button
                  onClick={() => setIsPrivacyOpen(true)}
                  className="hover:text-sp-gold underline cursor-pointer transition-colors"
                >
                  Privacy Policy (GDPR)
                </button>
                <span className="text-sp-bronze/30">|</span>
                <Link href="/terms" className="hover:text-sp-gold underline transition-colors">
                  Terms of Service
                </Link>
              </div>
              <div className="flex items-center gap-1.5 text-center">
                <span>Not affiliated with Mojang Studios or Microsoft. Steam-powered, sakura-hearted.</span>
                <Heart className="w-3 h-3 text-sakura-accent fill-sakura-accent" />
              </div>
            </div>

          </div>
        </footer>

        {/* Auth Modals */}
        {isLoginOpen && (
          <div className="fixed inset-0 bg-black/80 flex z-50 p-4 backdrop-blur-sm overflow-y-auto">
            <div className="m-auto w-full max-w-md bg-sp-dark border-2 border-sp-gold p-6 rounded-xl relative steampunk-border shadow-2xl animate-pulse-glow">
              
              <button
                onClick={() => { setIsLoginOpen(false); resetRegisterForm(); resetTwoFactorState(); resetForgotPasswordState(); }}
                className="absolute top-4 right-4 text-text-warm/60 hover:text-white"
              >
                <X className="w-6 h-6" />
              </button>

              <h2 className="font-serif text-2xl text-sp-gold mb-6 border-b border-sp-bronze pb-2 tracking-wide text-center">
                {twoFactorStage ? "Staff Verification" : (isForgotMode ? "Reset Password" : (isRegisterMode ? "Create Account" : "Log In"))}
              </h2>

              {error && (
                <div className="mb-4 bg-crimson/20 border border-crimson text-rose-200 text-sm px-3 py-2 rounded-lg">
                  {error}
                </div>
              )}

              {isForgotMode ? (
                forgotMessage ? (
                  <div className="text-center space-y-4">
                    <p className="text-sm text-text-warm/80 leading-relaxed">{forgotMessage}</p>
                    <button
                      type="button"
                      onClick={() => { resetForgotPasswordState(); }}
                      className="text-xs text-sp-copper hover:text-sp-gold hover:underline cursor-pointer"
                    >
                      Back to login
                    </button>
                  </div>
                ) : (
                  <form onSubmit={handleForgotPasswordSubmit} className="space-y-4">
                    <p className="text-xs text-text-warm/80 text-center">
                      Enter your account's email address and we'll send you a link to set a new password.
                    </p>
                    <div>
                      <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">Email Address</label>
                      <input
                        type="email"
                        required
                        value={forgotEmail}
                        onChange={e => setForgotEmail(e.target.value)}
                        className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                        placeholder="pilot@iekcorbar.com"
                        autoFocus
                      />
                    </div>
                    <button
                      type="submit"
                      disabled={forgotSubmitting}
                      className="w-full py-2.5 rounded-lg text-sm font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white hover:shadow-lg transition-all cursor-pointer font-serif border border-transparent shadow-md disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {forgotSubmitting ? "Sending..." : "Send Reset Link"}
                    </button>
                    <button
                      type="button"
                      onClick={() => { resetForgotPasswordState(); setError(''); }}
                      className="w-full text-center text-xs text-sp-copper hover:text-sp-gold hover:underline cursor-pointer"
                    >
                      Back to login
                    </button>
                  </form>
                )
              ) : !isRegisterMode && twoFactorStage ? (
                <form onSubmit={handleTwoFactorSubmit} className="space-y-4">
                  {twoFactorStage === 'setup' ? (
                    <div className="bg-sp-gold/5 border border-sp-gold/30 rounded-lg p-4 text-xs text-text-warm/80 leading-relaxed space-y-2.5 text-left">
                      <p className="font-bold text-sp-gold uppercase tracking-wider text-[10px]">ðŸ” Set Up Staff 2FA</p>
                      <p>This account has staff privileges, so it needs an authenticator app (Google Authenticator, Authy, ...) linked before you can log in. Scan the code below:</p>
                      {twoFactorQrCode && (
                        <div className="flex justify-center py-1.5">
                          <img src={twoFactorQrCode} alt="2FA setup QR code" className="w-40 h-40 rounded-lg border border-sp-bronze bg-white p-1.5" />
                        </div>
                      )}
                      {twoFactorSecret && (
                        <p className="text-center">
                          Can&apos;t scan? Enter manually:
                          <span className="block mt-1 font-mono text-white select-all break-all">{twoFactorSecret}</span>
                        </p>
                      )}
                      <p>Then enter the 6-digit code it generates below to confirm.</p>
                    </div>
                  ) : (
                    <p className="text-xs text-text-warm/80 text-center">
                      Enter the 6-digit code from your authenticator app to finish logging in.
                    </p>
                  )}
                  <div>
                    <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">Authentication Code</label>
                    <input
                      type="text"
                      required
                      pattern="[0-9]{6}"
                      maxLength={6}
                      value={twoFactorCode}
                      onChange={e => setTwoFactorCode(e.target.value)}
                      className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold font-mono text-center text-lg tracking-widest"
                      placeholder="123456"
                      autoFocus
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={twoFactorSubmitting}
                    className="w-full py-2.5 rounded-lg text-sm font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white hover:shadow-lg transition-all cursor-pointer font-serif border border-transparent shadow-md disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {twoFactorSubmitting ? "Verifying..." : (twoFactorStage === 'setup' ? "Confirm & Enable 2FA" : "Verify & Enter")}
                  </button>
                  {twoFactorStage === 'verify' && twoFactorHasPasskeys && browserSupportsWebAuthn() && (
                    <button
                      type="button"
                      onClick={handlePasskeyLogin}
                      disabled={passkeySubmitting}
                      className="w-full py-2.5 rounded-lg text-sm font-semibold uppercase tracking-wider text-sp-gold hover:text-white border border-sp-gold hover:border-white transition-all cursor-pointer font-serif disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                    >
                      <KeyRound className="w-4 h-4" /> {passkeySubmitting ? "Waiting for passkey..." : "Use a Passkey Instead"}
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => { resetTwoFactorState(); setPassword(''); setError(''); }}
                    className="w-full text-center text-xs text-sp-copper hover:text-sp-gold hover:underline cursor-pointer"
                  >
                    Back to login
                  </button>
                </form>
              ) : (
              <form onSubmit={isRegisterMode ? handleRegister : handleLogin} className="space-y-4">
                {isRegisterMode && verificationRequired ? (
                  <div className="space-y-4 text-left">
                    <div className="bg-sp-gold/5 border border-sp-gold/30 rounded-lg p-4 text-xs text-text-warm/80 leading-relaxed space-y-2.5">
                      <p className="font-bold text-sp-gold uppercase tracking-wider text-[10px]">âœ¨ Whitelist Approved!</p>
                      <p>
                        We have whitelisted the username <strong className="text-white font-mono">{mcUsername}</strong> on our server.
                      </p>
                      <p>
                        Please log in to the server now (<strong className="text-white font-mono">{serverAddress}</strong>). Once you are online in-game, click below to retrieve your code:
                      </p>
                      
                      <button
                        type="button"
                        onClick={handleSendVerificationCode}
                        disabled={sendingCode}
                        className="w-full mt-1.5 py-2 bg-sp-copper hover:bg-sp-gold text-black font-bold uppercase text-[10px] tracking-wider rounded-md transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {sendingCode ? "Dispatching Signal..." : "Send Verification Code"}
                      </button>

                      {codeSent && (
                        <p className="text-[10px] text-emerald-400 text-center font-bold animate-pulse mt-1">
                          âœ“ Code successfully printed to your in-game chat interface!
                        </p>
                      )}

                      {devVerificationCode && (
                        <div className="mt-2 pt-2 border-t border-sp-gold/20 text-sp-copper bg-neutral-950/40 p-2 rounded text-center">
                          <span className="block font-bold text-[10px] uppercase tracking-wider text-sp-gold mb-1">Developer Debug Stream</span>
                          <span>Use temporary code: <strong className="text-white font-mono select-all text-sm">{devVerificationCode}</strong></span>
                        </div>
                      )}
                    </div>
                    <div>
                      <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">Enter Verification Code</label>
                      <input 
                        type="text"
                        required
                        pattern="[0-9]{6}"
                        maxLength={6}
                        value={verificationCode}
                        onChange={e => setVerificationCode(e.target.value)}
                        className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold font-mono text-center text-lg tracking-widest"
                        placeholder="123456"
                      />
                    </div>
                  </div>
                ) : (
                  <>
                    <div>
                      <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">Email Address</label>
                      <input 
                        type="email"
                        required
                        value={email}
                        onChange={e => setEmail(e.target.value)}
                        className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                        placeholder="pilot@iekcorbar.com"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">Password</label>
                      <input 
                        type="password"
                        required
                        value={password}
                        onChange={e => setPassword(e.target.value)}
                        className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                        placeholder="â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢"
                      />
                      {!isRegisterMode && (
                        <button
                          type="button"
                          onClick={() => { setIsForgotMode(true); setError(''); setForgotEmail(email); }}
                          className="mt-1.5 text-[11px] text-sp-copper hover:text-sp-gold hover:underline cursor-pointer"
                        >
                          Forgot password?
                        </button>
                      )}
                    </div>

                    {isRegisterMode && (
                      <div>
                        <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">Minecraft IGN (In-Game Name)</label>
                        <input 
                          type="text"
                          required
                          value={mcUsername}
                          onChange={e => setMcUsername(e.target.value)}
                          className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                          placeholder="e.g. Notch"
                        />
                        <p className="mt-2 text-[11px] text-text-warm/60 leading-relaxed">
                          Your exact Minecraft Java name. After creating your account you'll <strong className="text-text-warm/90">link it in-game with a code</strong> (takes a minute) â€” you have <strong className="text-text-warm/90">3 days</strong>, otherwise you're removed from the whitelist until you do.
                        </p>
                      </div>
                    )}
                  </>
                )}

                <button
                  type="submit"
                  className="w-full py-2.5 rounded-lg text-sm font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white hover:shadow-lg transition-all cursor-pointer font-serif border border-transparent shadow-md"
                >
                  {isRegisterMode
                    ? (verificationRequired ? "Verify & Create Account" : "Create Account")
                    : "Log In"}
                </button>
              </form>
              )}

              {!twoFactorStage && !isForgotMode && (
              <div className="mt-4 text-center">
                <button
                  onClick={() => { setIsRegisterMode(!isRegisterMode); setError(''); resetRegisterForm(); }}
                  className="text-xs text-sp-copper hover:text-sp-gold hover:underline cursor-pointer"
                >
                  {isRegisterMode
                    ? "Already have an account? Log in"
                    : "No account yet? Create one"}
                </button>
              </div>
              )}

            </div>
          </div>
        )}

        {/* Privacy Policy / AVG Modal */}
        {isPrivacyOpen && (
          <div className="fixed inset-0 bg-black/85 flex items-center justify-center z-50 p-4 backdrop-blur-md">
            <div className="w-full max-w-lg bg-sp-dark border-2 border-sp-gold p-6 rounded-xl relative steampunk-border shadow-2xl overflow-y-auto max-h-[85vh] text-left">
              
              <button 
                onClick={() => setIsPrivacyOpen(false)}
                className="absolute top-4 right-4 text-text-warm/60 hover:text-white"
              >
                <X className="w-6 h-6" />
              </button>

              <h2 className="font-serif text-2xl text-sp-gold mb-4 border-b border-sp-bronze pb-2 tracking-wide flex items-center gap-2 uppercase">
                <ShieldAlert className="w-6 h-6 text-sp-copper" /> Privacy Policy (GDPR)
              </h2>

              <div className="space-y-4 text-xs text-text-warm/80 leading-relaxed font-sans">
                <p>
                  <strong>Iekcorbar</strong> places high value on protecting the personal data of its players. In accordance with the General Data Protection Regulation (GDPR), we inform you about the data usage on this platform:
                </p>

                <div className="space-y-3">
                  <div>
                    <h4 className="font-bold text-sp-gold uppercase text-[10px] tracking-wider mb-0.5">1. What Data Do We Collect?</h4>
                    <p>When you create a profile on our website, we collect the following data:</p>
                    <ul className="list-disc pl-4 mt-1 space-y-0.5">
                      <li>Your Minecraft username (IGN)</li>
                      <li>Your email address</li>
                      <li>Your password (cryptographically secured via hashing)</li>
                    </ul>
                    <p className="mt-1">If you donate via our Support page, PayPal handles the payment entirely — we never see or store bank or card details.</p>
                  </div>

                  <div>
                    <h4 className="font-bold text-sp-gold uppercase text-[10px] tracking-wider mb-0.5">2. Purpose of Data Processing</h4>
                    <p>Your personal data is used exclusively for the operation and security of our services:</p>
                    <ul className="list-disc pl-4 mt-1 space-y-0.5">
                      <li>Verifying and whitelisting your account on our Minecraft server.</li>
                      <li>Allowing authentication on this website and managing your player profile.</li>
                      <li>Preventing abuse, account theft, and unauthorized registration under someone else's name.</li>
                    </ul>
                  </div>

                  <div>
                    <h4 className="font-bold text-sp-gold uppercase text-[10px] tracking-wider mb-0.5">3. Retention & Security</h4>
                    <p>Passwords are hashed with a per-account random salt, never stored or logged in plain text. Your account data is stored in a database run by our infrastructure host, who processes it only on our behalf â€” we don't sell it or hand it to anyone else. Your data is retained as long as your account remains active, or until you request deletion (see below).</p>
                  </div>

                  <div>
                    <h4 className="font-bold text-sp-gold uppercase text-[10px] tracking-wider mb-0.5">4. Your Rights (Access & Erasure)</h4>
                    <p>You have the right at all times to inspect, correct, or completely delete your data (Right to be Forgotten). You can delete your own account, whitelist entry, and associated data (journal, friendships, notifications) at any time yourself from the Danger Zone on your Profile page â€” no need to contact anyone. If you'd rather have staff do it for you, contact server administrators via our official Discord server instead.</p>
                  </div>

                  <div>
                    <h4 className="font-bold text-sp-gold uppercase text-[10px] tracking-wider mb-0.5">5. Cookies & Local Storage</h4>
                    <p>This website does not use tracking, advertising, or marketing cookies. We exclusively use browser local storage (localStorage) to remember that you are logged into our website.</p>
                  </div>

                  <div>
                    <h4 className="font-bold text-sp-gold uppercase text-[10px] tracking-wider mb-0.5">6. Account Eligibility</h4>
                    <p>Registering requires you to meet the age requirement in our <Link href="/terms" onClick={() => setIsPrivacyOpen(false)} className="text-sp-gold hover:text-white underline font-bold">Terms of Service</Link>.</p>
                  </div>
                </div>

                <p>
                  <Link href="/privacy" onClick={() => setIsPrivacyOpen(false)} className="text-sp-gold hover:text-white underline font-bold">Read the full Privacy Policy</Link>, including what the Android app does with your data.
                </p>

                <div className="pt-2 border-t border-sp-bronze/20 text-[10px] text-text-warm/40 text-center">
                  {/* A real, fixed date â€” this used to be new Date(), which
                      just always showed today regardless of whether the
                      policy text had actually changed. Update this whenever
                      the text above genuinely changes. */}
                  <span>Last updated: 7 September 2026</span>
                </div>
              </div>

              <button
                onClick={() => setIsPrivacyOpen(false)}
                className="w-full mt-5 py-2.5 bg-sp-gold hover:bg-white text-black font-bold uppercase text-[10px] tracking-wider rounded-lg transition-all cursor-pointer font-serif"
              >
                Close
              </button>

            </div>
          </div>
        )}

      </div>
    </UserContext.Provider>
    </SiteContentContext.Provider>
  );
}
