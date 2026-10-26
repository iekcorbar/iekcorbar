'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useUser } from '../providers';
import PlayerAvatar from '@/components/PlayerAvatar';
import { startRegistration } from '@simplewebauthn/browser';
import { getWebPushStatus, enableWebPush, disableWebPush } from '@/lib/web-push-client';
import { 
  User, 
  Settings, 
  Gamepad2, 
  Mail, 
  Shield, 
  Check, 
  RefreshCw,
  AlertTriangle,
  Cog,
  BookOpen,
  Flag,
  DollarSign,
  Sparkles,
  Clock,
  Skull,
  Workflow,
  Layers,
  ChevronLeft,
  ChevronRight,
  Plus,
  Edit,
  Trash2,
  ImagePlus,
  X,
  Share2,
  Send,
  Bug,
  Hammer,
  UserMinus,
  Lock,
  KeyRound,
  MessageSquare,
  ChevronDown,
  ChevronUp,
  ShieldCheck,
  Bell,
  LayoutGrid,
  Palette
} from 'lucide-react';
import { humanizePluginStatKey } from '@/lib/plugin-stats';
import { BUG_CATEGORIES, BUG_STATUS_LABELS, bugCategoryLabel } from '@/lib/bug-categories';
import { roleTextClass, roleBadgeClass } from '@/lib/staff-tabs';
import ProfileHero, { ProfileTabs } from '@/components/ProfileHero';
import ProfileCustomize from '@/components/ProfileCustomize';
import BlockedMembersCard from '@/components/BlockedMembersCard';

export default function ProfilePage() {
  const { user, setUser, setIsLoginOpen, handleLogout } = useUser();
  const [mcNameInput, setMcNameInput] = useState('');
  const [updating, setUpdating] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');

  // Account Security: change password + self-service delete
  const [currentPasswordInput, setCurrentPasswordInput] = useState('');
  const [newPasswordInput, setNewPasswordInput] = useState('');
  const [confirmPasswordInput, setConfirmPasswordInput] = useState('');
  const [pwSaving, setPwSaving] = useState(false);
  const [pwSuccess, setPwSuccess] = useState(false);
  const [pwError, setPwError] = useState('');
  const [deleteConfirming, setDeleteConfirming] = useState(false);
  const [deletePasswordInput, setDeletePasswordInput] = useState('');
  const [deleteSaving, setDeleteSaving] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  // Passkeys (WebAuthn) — only meaningful for accounts that go through the
  // TOTP step at all (see api/auth/passkey/*), which user.totpEnabled
  // already reflects without needing a separate role check here.
  const [passkeys, setPasskeys] = useState([]);
  const [loadingPasskeys, setLoadingPasskeys] = useState(true);
  const [passkeyNameInput, setPasskeyNameInput] = useState('');
  const [passkeyAdding, setPasskeyAdding] = useState(false);
  const [passkeyError, setPasskeyError] = useState('');
  const [passkeyRemovingId, setPasskeyRemovingId] = useState(null);

  // Newsletter opt-out — same flag the one-click unsubscribe email link
  // flips, just reachable here too.
  const [newsletterOptedOut, setNewsletterOptedOut] = useState(false);
  const [newsletterLoading, setNewsletterLoading] = useState(true);
  const [newsletterSaving, setNewsletterSaving] = useState(false);
  // Push notification settings for the Android app (api/push/preferences).
  const [pushPrefs, setPushPrefs] = useState(null);
  const [pushIsStaff, setPushIsStaff] = useState(false);
  const [pushSaving, setPushSaving] = useState(false);
  // This browser / iPhone home-screen app (Web Push) — see web-push-client.js.
  const [webPushStatus, setWebPushStatus] = useState(null);
  const [webPushBusy, setWebPushBusy] = useState(false);
  const [webPushError, setWebPushError] = useState('');

  const [events, setEvents] = useState([]);
  const [registrations, setRegistrations] = useState([]);
  const [loadingEvents, setLoadingEvents] = useState(true);

  // Community Projects (joined) states
  const [projects, setProjects] = useState([]);
  const [projectSignups, setProjectSignups] = useState([]);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [leavingProjectId, setLeavingProjectId] = useState(null);

  // Anonymous Report Form States
  const [reportTarget, setReportTarget] = useState('');
  const [reportCategory, setReportCategory] = useState('Hacking');
  const [reportDetails, setReportDetails] = useState('');
  const [reportSubmitting, setReportSubmitting] = useState(false);
  const [reportSubmitted, setReportSubmitted] = useState(false);
  const [reportError, setReportError] = useState('');

  // Bug Report Form States
  const [bugSubject, setBugSubject] = useState('');
  const [bugDescription, setBugDescription] = useState('');
  const [bugCategory, setBugCategory] = useState('');
  const [bugCustomCategory, setBugCustomCategory] = useState('');
  const [bugSubmitting, setBugSubmitting] = useState(false);
  const [bugSubmitted, setBugSubmitted] = useState(false);
  const [bugError, setBugError] = useState('');

  // Duplicate-check panel — shown once a category is picked, so a reporter
  // can see "is this already known?" before filing a fresh report. See
  // POST action 'bump_duplicate'.
  const [duplicateCandidates, setDuplicateCandidates] = useState([]);
  const [duplicateLoading, setDuplicateLoading] = useState(false);
  const [duplicateBumpingId, setDuplicateBumpingId] = useState(null);
  const [duplicateBumpedIds, setDuplicateBumpedIds] = useState([]);

  // "My Bug Reports" — the reporter's own submission history + the
  // staff/reporter conversation thread per report.
  const [myBugReports, setMyBugReports] = useState([]);
  const [myBugReportsLoading, setMyBugReportsLoading] = useState(true);
  const [expandedBugReportId, setExpandedBugReportId] = useState(null);
  const [bugThreads, setBugThreads] = useState({}); // reportId -> messages[]
  const [bugReplyDrafts, setBugReplyDrafts] = useState({}); // reportId -> string
  const [bugReplySendingId, setBugReplySendingId] = useState(null);

  // In-Game Stats
  const [gameStats, setGameStats] = useState(null);
  const [statsLoading, setStatsLoading] = useState(true);

  // Steampunk Logbook (Journal) states
  const [journalEntries, setJournalEntries] = useState([]);
  const [journalLoading, setJournalLoading] = useState(true);
  const [journalIndex, setJournalIndex] = useState(0);
  const [journalFlipDirection, setJournalFlipDirection] = useState(null); // null | 'next' | 'prev'
  const [journalOutgoingEntry, setJournalOutgoingEntry] = useState(null);
  const [journalFormOpen, setJournalFormOpen] = useState(false);
  const [journalEditId, setJournalEditId] = useState(null);
  const [journalDateInput, setJournalDateInput] = useState('');
  const [journalContentInput, setJournalContentInput] = useState('');
  const [journalImageFile, setJournalImageFile] = useState(null);
  const [journalImagePreview, setJournalImagePreview] = useState('');
  const [journalRemoveImage, setJournalRemoveImage] = useState(false);
  const [journalSaving, setJournalSaving] = useState(false);
  const [journalError, setJournalError] = useState('');
  const [journalFriends, setJournalFriends] = useState([]);
  const [sharePickerEntryId, setSharePickerEntryId] = useState(null);
  const [shareTargetUsername, setShareTargetUsername] = useState('');
  const [sharingBusy, setSharingBusy] = useState(false);
  const [shareFeedback, setShareFeedback] = useState('');

  // Staff Application — just enough here to show a status badge and steer
  // the "Apply for Staff" button; the actual form lives on its own page
  // (src/app/staff-application/page.js), not inline on Profile.
  const [myStaffApplication, setMyStaffApplication] = useState(null);

  // Profile page tabs + personalisation (bio / banner / hidden sections).
  // `draft` is the unsaved state from the Customize tab, used to preview the
  // hero live; `profile` is what's actually stored.
  const [tab, setTab] = useState('overview');
  const [profile, setProfile] = useState({ bio: '', banner: 'gears', hidden: [], bannerImage: null, bannerPos: 50 });
  const [draft, setDraft] = useState(null);

  useEffect(() => {
    if (!user) return;
    fetch('/api/profile/settings')
      .then(res => res.json())
      .then(data => { if (!data.error) setProfile({ bio: data.bio || '', banner: data.banner || 'gears', hidden: data.hidden || [], bannerImage: data.bannerImage || null, bannerPos: data.bannerPos ?? 50 }); })
      .catch(() => {});
  }, [user?.email]); // eslint-disable-line react-hooks/exhaustive-deps

  // Notification deep links (#bug-reports, #report-a-bug) point at cards that
  // now live on the Feedback tab: open it, then scroll once it is visible.
  useEffect(() => {
    const hash = window.location.hash.replace('#', '');
    if (hash === 'bug-reports' || hash === 'report-a-bug') {
      setTab('feedback');
      setTimeout(() => document.getElementById(hash)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150);
    }
  }, []);

  // Sync state with user IGN
  useEffect(() => {
    if (user) {
      setMcNameInput(user.mcUsername || '');
      fetchEvents();
      fetchProjects();
      fetchJournalEntries();
      fetchJournalFriends();
      fetchMyBugReports();
      fetchMyStaffApplication();

      // Fetch in-game stats
      fetch(`/api/player-stats?username=${encodeURIComponent(user.mcUsername)}`)
        .then(res => res.json())
        .then(data => {
          if (!data.error) setGameStats(data);
        })
        .catch(() => {})
        .finally(() => setStatsLoading(false));

      fetch('/api/newsletter/preference')
        .then(res => res.json())
        .then(data => { if (!data.error) setNewsletterOptedOut(!!data.optedOut); })
        .catch(() => {})
        .finally(() => setNewsletterLoading(false));

      getWebPushStatus().then(setWebPushStatus).catch(() => setWebPushStatus('unsupported'));

      fetch('/api/push/preferences')
        .then(res => res.json())
        .then(data => { if (data.prefs) { setPushPrefs(data.prefs); setPushIsStaff(!!data.isStaff); } })
        .catch(() => {});

      if (user.totpEnabled) fetchPasskeys();
      else setLoadingPasskeys(false);
    }
  }, [user]);

  async function fetchPasskeys() {
    try {
      const res = await fetch('/api/auth/passkey');
      const data = await res.json();
      if (data.passkeys) setPasskeys(data.passkeys);
    } catch (err) {
      console.error('Failed to load passkeys', err);
    } finally {
      setLoadingPasskeys(false);
    }
  }

  async function fetchJournalEntries() {
    try {
      const res = await fetch(`/api/profile/journal?email=${encodeURIComponent(user.email)}`);
      const data = await res.json();
      if (res.ok && data.entries) {
        setJournalEntries(data.entries);
        setJournalIndex(Math.max(0, data.entries.length - 1)); // open to the most recent page
      }
    } catch (err) {
      console.error("Failed to load journal entries:", err);
    } finally {
      setJournalLoading(false);
    }
  }

  async function fetchJournalFriends() {
    try {
      const res = await fetch(`/api/friends?email=${encodeURIComponent(user.email)}`);
      const data = await res.json();
      if (res.ok && data.friends) setJournalFriends(data.friends);
    } catch (err) {
      console.error("Failed to load friends for sharing:", err);
    }
  }

  async function fetchMyBugReports() {
    try {
      const res = await fetch('/api/bug-reports?mine=1');
      const data = await res.json();
      if (res.ok && data.reports) setMyBugReports(data.reports);
    } catch (err) {
      console.error("Failed to load my bug reports:", err);
    } finally {
      setMyBugReportsLoading(false);
    }
  }

  async function fetchMyStaffApplication() {
    try {
      const res = await fetch('/api/staff-applications?mine=1');
      const data = await res.json();
      if (res.ok && data.applications) setMyStaffApplication(data.applications[0] || null);
    } catch (err) {
      console.error("Failed to load staff application status:", err);
    }
  }

  async function toggleBugThread(reportId) {
    if (expandedBugReportId === reportId) {
      setExpandedBugReportId(null);
      return;
    }
    setExpandedBugReportId(reportId);
    if (!bugThreads[reportId]) {
      try {
        const res = await fetch(`/api/bug-reports?messagesFor=${encodeURIComponent(reportId)}`);
        const data = await res.json();
        if (res.ok && data.messages) setBugThreads(prev => ({ ...prev, [reportId]: data.messages }));
      } catch (err) {
        console.error("Failed to load bug report thread:", err);
      }
    }
  }

  async function handleBugReplySubmit(reportId) {
    const message = (bugReplyDrafts[reportId] || '').trim();
    if (!message) return;
    setBugReplySendingId(reportId);
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'reply', id: reportId, message })
      });
      const data = await res.json();
      if (res.ok && data.messages) {
        setBugThreads(prev => ({ ...prev, [reportId]: data.messages }));
        setBugReplyDrafts(prev => ({ ...prev, [reportId]: '' }));
      }
    } catch (err) {
      console.error("Failed to send bug report reply:", err);
    } finally {
      setBugReplySendingId(null);
    }
  }

  // Live duplicate-check panel — refetches whenever the category changes,
  // so a reporter sees "already known?" before writing out a full report.
  useEffect(() => {
    if (!bugCategory || bugCategory === 'other') {
      setDuplicateCandidates([]);
      return;
    }
    let cancelled = false;
    setDuplicateLoading(true);
    fetch(`/api/bug-reports?public=1&category=${encodeURIComponent(bugCategory)}`)
      .then(res => res.json())
      .then(data => {
        if (!cancelled && data.reports) setDuplicateCandidates(data.reports.filter(r => r.status === 'open'));
      })
      .catch(() => {})
      .finally(() => { if (!cancelled) setDuplicateLoading(false); });
    return () => { cancelled = true; };
  }, [bugCategory]);

  async function handleConfirmDuplicate(reportId) {
    setDuplicateBumpingId(reportId);
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'bump_duplicate', id: reportId })
      });
      if (res.ok) setDuplicateBumpedIds(prev => [...prev, reportId]);
    } catch (err) {
      console.error("Failed to confirm duplicate:", err);
    } finally {
      setDuplicateBumpingId(null);
    }
  }

  function todayIso() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  async function handleShareToFriend(entryId) {
    if (!shareTargetUsername) return;
    setSharingBusy(true);
    setShareFeedback('');
    try {
      const res = await fetch('/api/profile/journal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'share', id: entryId, email: user.email, targetUsername: shareTargetUsername })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setShareFeedback(`Shared with ${shareTargetUsername}!`);
        setTimeout(() => { setSharePickerEntryId(null); setShareFeedback(''); setShareTargetUsername(''); }, 1800);
      } else {
        setShareFeedback(data.error || "Failed to share");
      }
    } catch (err) {
      setShareFeedback("Network error");
    } finally {
      setSharingBusy(false);
    }
  }

  async function handleShareExternally(entry) {
    // Previously this only ever passed navigator.share() a `text` field with
    // no `url` — so Twitter/X, WhatsApp, Facebook etc. had nothing to open
    // or preview, and the recipient (even if they had an account) had no
    // link to click. Fixed by minting a real public, unauthenticated share
    // link first (POST .../journal { action: 'get-share-link' } — reuses
    // the existing token if this entry was already shared before) and
    // including that URL, so anyone can open it without logging in.
    let shareUrl = null;
    try {
      const res = await fetch('/api/profile/journal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'get-share-link', id: entry.id, email: user.email })
      });
      const data = await res.json();
      if (res.ok && data.shareUrl) shareUrl = data.shareUrl;
    } catch (err) {
      // fall through — still share the text below even if the link mint failed
    }

    const shareText = `From my Iekcorbar logbook (${journalDateFormatter.format(new Date(entry.entryDate))}):\n\n${entry.content}`;
    if (navigator.share) {
      try {
        await navigator.share(shareUrl ? { title: 'Iekcorbar Logbook', text: shareText, url: shareUrl } : { title: 'Iekcorbar Logbook', text: shareText });
      } catch (err) {
        // AbortError when the user just cancels the share sheet — not a real failure
      }
    } else if (navigator.clipboard) {
      await navigator.clipboard.writeText(shareUrl ? `${shareText}\n\n${shareUrl}` : shareText);
      alert('Logbook entry copied to clipboard!');
    }
  }

  function openNewJournalForm() {
    setJournalEditId(null);
    setJournalDateInput(todayIso());
    setJournalContentInput('');
    setJournalImageFile(null);
    setJournalImagePreview('');
    setJournalRemoveImage(false);
    setJournalError('');
    setJournalFormOpen(true);
  }

  function openEditJournalForm(entry) {
    setJournalEditId(entry.id);
    setJournalDateInput(entry.entryDate ? String(entry.entryDate).slice(0, 10) : todayIso());
    setJournalContentInput(entry.content);
    setJournalImageFile(null);
    setJournalImagePreview(entry.imageUrl || '');
    setJournalRemoveImage(false);
    setJournalError('');
    setJournalFormOpen(true);
  }

  function handleJournalImageSelect(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    setJournalImageFile(file);
    setJournalRemoveImage(false);
    setJournalImagePreview(URL.createObjectURL(file));
  }

  async function handleJournalSubmit(e) {
    e.preventDefault();
    if (!journalContentInput.trim() || !journalDateInput) return;
    setJournalSaving(true);
    setJournalError('');

    try {
      const formData = new FormData();
      if (journalEditId) formData.append('id', journalEditId);
      formData.append('email', user.email);
      formData.append('entryDate', journalDateInput);
      formData.append('content', journalContentInput);
      if (journalImageFile) formData.append('file', journalImageFile);
      if (journalRemoveImage) formData.append('removeImage', 'true');

      const res = await fetch('/api/profile/journal', { method: 'POST', body: formData });
      const data = await res.json();
      if (res.ok && data.success) {
        setJournalEntries(data.entries);
        const newIndex = data.entries.findIndex(en => en.id === data.id);
        setJournalIndex(newIndex >= 0 ? newIndex : Math.max(0, data.entries.length - 1));
        setJournalFormOpen(false);
      } else {
        setJournalError(data.error || "Failed to save entry");
      }
    } catch (err) {
      setJournalError("Network error");
    } finally {
      setJournalSaving(false);
    }
  }

  async function handleJournalDelete(entry) {
    if (!confirm('Tear this page out of your logbook? This cannot be undone.')) return;
    try {
      const res = await fetch(`/api/profile/journal?id=${encodeURIComponent(entry.id)}&email=${encodeURIComponent(user.email)}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) {
        setJournalEntries(data.entries);
        setJournalIndex(i => Math.min(i, Math.max(0, data.entries.length - 1)));
      }
    } catch (err) {
      console.error("Failed to delete journal entry:", err);
    }
  }

  function journalFlipTo(targetIndex, direction) {
    if (journalFlipDirection || targetIndex < 0 || targetIndex >= journalEntries.length) return;
    setJournalOutgoingEntry(journalEntries[journalIndex]);
    setJournalFlipDirection(direction);
    setJournalIndex(targetIndex);
    setTimeout(() => {
      setJournalFlipDirection(null);
      setJournalOutgoingEntry(null);
    }, 500);
  }

  async function fetchEvents() {
    try {
      const res = await fetch('/api/events');
      const data = await res.json();
      setEvents(data.events || []);
      setRegistrations(data.registrations || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingEvents(false);
    }
  }

  async function fetchProjects() {
    try {
      const res = await fetch('/api/projects');
      const data = await res.json();
      setProjects(data.projects || []);
      setProjectSignups(data.signups || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingProjects(false);
    }
  }

  const handleLeaveProject = async (projectId) => {
    if (!user) return;
    if (!confirm('Leave this project? You can always sign up again later if there\'s room.')) return;
    setLeavingProjectId(projectId);
    try {
      const res = await fetch(`/api/projects/signup?projectId=${projectId}&username=${encodeURIComponent(user.mcUsername)}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setProjectSignups(prev => prev.filter(s => !(s.projectId === projectId && s.username === user.mcUsername)));
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLeavingProjectId(null);
    }
  };

  const handleRegister = async (eventId, action) => {
    try {
      const res = await fetch('/api/events/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventId,
          username: user.mcUsername,
          action
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setRegistrations(data.registrations);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleUpdateIGN = async (e) => {
    e.preventDefault();
    if (!user) return;
    setUpdating(true);
    setSuccess(false);
    setError('');

    try {
      const res = await fetch('/api/moderation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: user.role, // Current user role
          targetEmail: user.email,
          newIGN: mcNameInput,
          action: 'update_profile'
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        // Find updated user details
        const updatedUser = { ...user, mcUsername: mcNameInput };
        setUser(updatedUser);
        localStorage.setItem('iekcorbar_user', JSON.stringify(updatedUser));
        setSuccess(true);
        setTimeout(() => setSuccess(false), 3000);
      } else {
        setError(data.error || "Failed to update IGN");
      }
    } catch (err) {
      setError("Network error");
    } finally {
      setUpdating(false);
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setPwSuccess(false);
    setPwError('');

    if (newPasswordInput !== confirmPasswordInput) {
      setPwError("New password and confirmation don't match");
      return;
    }

    setPwSaving(true);
    try {
      const res = await fetch('/api/account', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword: currentPasswordInput, newPassword: newPasswordInput })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setPwSuccess(true);
        setCurrentPasswordInput('');
        setNewPasswordInput('');
        setConfirmPasswordInput('');
        setTimeout(() => setPwSuccess(false), 3000);
      } else {
        setPwError(data.error || "Failed to change password");
      }
    } catch (err) {
      setPwError("Network error");
    } finally {
      setPwSaving(false);
    }
  };

  const handleAddPasskey = async () => {
    setPasskeyError('');
    setPasskeyAdding(true);
    try {
      const optionsRes = await fetch('/api/auth/passkey/register-options', { method: 'POST' });
      const optionsData = await optionsRes.json();
      if (!optionsRes.ok) {
        setPasskeyError(optionsData.error || 'Could not start passkey registration');
        return;
      }

      let attestation;
      try {
        attestation = await startRegistration({ optionsJSON: optionsData.options });
      } catch (webauthnErr) {
        // Cancelled the prompt, or the device doesn't support it — not
        // worth surfacing as an error, just stop quietly.
        return;
      }

      const verifyRes = await fetch('/api/auth/passkey/register-verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: passkeyNameInput, response: attestation })
      });
      const verifyData = await verifyRes.json();
      if (verifyRes.ok && verifyData.success) {
        setPasskeys(verifyData.passkeys);
        setPasskeyNameInput('');
      } else {
        setPasskeyError(verifyData.error || 'Could not save the passkey');
      }
    } catch (err) {
      setPasskeyError('Network error');
    } finally {
      setPasskeyAdding(false);
    }
  };

  const handleRemovePasskey = async (id) => {
    setPasskeyError('');
    setPasskeyRemovingId(id);
    try {
      const res = await fetch(`/api/auth/passkey?id=${encodeURIComponent(id)}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) {
        setPasskeys(data.passkeys);
      } else {
        setPasskeyError(data.error || 'Could not remove that passkey');
      }
    } catch (err) {
      setPasskeyError('Network error');
    } finally {
      setPasskeyRemovingId(null);
    }
  };

  const handleDeleteAccount = async (e) => {
    e.preventDefault();
    setDeleteError('');
    setDeleteSaving(true);
    try {
      const res = await fetch('/api/account', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password: deletePasswordInput })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        handleLogout();
      } else {
        setDeleteError(data.error || "Failed to delete account");
        setDeleteSaving(false);
      }
    } catch (err) {
      setDeleteError("Network error");
      setDeleteSaving(false);
    }
  };

  const toggleWebPush = async () => {
    setWebPushBusy(true);
    setWebPushError('');
    try {
      if (webPushStatus === 'enabled') await disableWebPush();
      else await enableWebPush();
    } catch (err) {
      setWebPushError(err.message === 'denied'
        ? 'Notifications are blocked for this site. Allow them in your browser or phone settings, then try again.'
        : err.message === 'dismissed' ? 'No permission given — tap the button again to retry.' : 'Could not enable notifications on this device.');
    } finally {
      setWebPushStatus(await getWebPushStatus().catch(() => 'unsupported'));
      setWebPushBusy(false);
    }
  };

  const togglePushPref = async (key) => {
    if (!pushPrefs) return;
    const previous = pushPrefs;
    const next = { ...pushPrefs, [key]: !pushPrefs[key] };
    setPushPrefs(next); // optimistic — reverted below on failure
    setPushSaving(true);
    try {
      const res = await fetch('/api/push/preferences', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prefs: next })
      });
      if (!res.ok) setPushPrefs(previous);
    } catch {
      setPushPrefs(previous);
    } finally {
      setPushSaving(false);
    }
  };

  const toggleNewsletter = async () => {
    const next = !newsletterOptedOut;
    setNewsletterOptedOut(next); // optimistic — reverted below on failure
    setNewsletterSaving(true);
    try {
      const res = await fetch('/api/newsletter/preference', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ optedOut: next })
      });
      if (!res.ok) setNewsletterOptedOut(!next);
    } catch {
      setNewsletterOptedOut(!next);
    } finally {
      setNewsletterSaving(false);
    }
  };

  const handleReportSubmit = async (e) => {
    e.preventDefault();
    if (!reportTarget.trim() || !reportDetails.trim() || reportDetails.trim().length < 20) return;
    setReportSubmitting(true);
    setReportError('');
    try {
      const res = await fetch('/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          targetUsername: reportTarget.trim(),
          category: reportCategory,
          details: reportDetails.trim()
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setReportSubmitted(true);
        setReportTarget('');
        setReportDetails('');
        setTimeout(() => setReportSubmitted(false), 5000);
      } else {
        setReportError(data.error || 'Failed to submit report');
      }
    } catch {
      setReportError('Network error. Please try again.');
    } finally {
      setReportSubmitting(false);
    }
  };

  const handleBugSubmit = async (e) => {
    e.preventDefault();
    if (!bugSubject.trim() || !bugDescription.trim() || !bugCategory) return;
    if (bugCategory === 'other' && !bugCustomCategory.trim()) return;
    setBugSubmitting(true);
    setBugError('');
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'submit',
          subject: bugSubject.trim(),
          description: bugDescription.trim(),
          category: bugCategory,
          customCategory: bugCategory === 'other' ? bugCustomCategory.trim() : undefined
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setBugSubmitted(true);
        setBugSubject('');
        setBugDescription('');
        setBugCategory('');
        setBugCustomCategory('');
        setDuplicateCandidates([]);
        setDuplicateBumpedIds([]);
        fetchMyBugReports();
        setTimeout(() => setBugSubmitted(false), 5000);
      } else {
        setBugError(data.error || 'Failed to submit bug report');
      }
    } catch {
      setBugError('Network error. Please try again.');
    } finally {
      setBugSubmitting(false);
    }
  };

  if (!user) {
    return (
      <div className="max-w-md mx-auto my-12 bg-sp-dark/95 border-2 border-crimson p-8 rounded-xl text-center steampunk-border shadow-2xl space-y-6">
        <div className="w-16 h-16 bg-crimson/15 rounded-full flex items-center justify-center border border-crimson text-rose-400 mx-auto">
          <AlertTriangle className="w-8 h-8" />
        </div>
        <h2 className="font-serif text-2xl font-bold text-rose-300 uppercase tracking-wider">Access Restrained</h2>
        <p className="text-sm text-text-warm/85 leading-relaxed">
          You must align your transmitter and authenticate to view your pilot profile deck.
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


  const journalDateFormatter = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
  function renderJournalPageContent(entry, interactive = true) {
    return (
      <div className="h-full flex flex-col">
        <div className="flex items-center justify-between gap-2 mb-3 shrink-0">
          <span className="font-serif text-sm text-sp-gold tracking-wide">
            {journalDateFormatter.format(new Date(entry.entryDate))}
          </span>
          {/* This is a case-insensitive compare on purpose — every other email
              comparison in this codebase already lowercases both sides
              (login, registration, session lookup, journal API...), but this
              one didn't, and it's a real bug: if the account's stored email
              casing differs at all from what's in the client's `user`
              context, entry.userEmail !== user.email fails for every single
              entry on the owner's OWN profile page, silently hiding Share/
              Edit/Delete entirely (no error — they just never render). This
              exact symptom is what "the whole journal feature doesn't work"
              turned out to be. */}
          {interactive && entry.userEmail && entry.userEmail.toLowerCase() === user.email.toLowerCase() && (
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => handleShareExternally(entry)}
                className="p-1 rounded border border-sp-bronze/30 hover:border-sp-gold text-text-warm/50 hover:text-sp-gold transition-colors cursor-pointer"
                title="Share Externally"
              >
                <Share2 className="w-3 h-3" />
              </button>
              <button
                type="button"
                onClick={() => { setSharePickerEntryId(sharePickerEntryId === entry.id ? null : entry.id); setShareTargetUsername(''); setShareFeedback(''); }}
                className="p-1 rounded border border-sp-bronze/30 hover:border-sp-gold text-text-warm/50 hover:text-sp-gold transition-colors cursor-pointer"
                title="Share With a Friend"
              >
                <Send className="w-3 h-3" />
              </button>
              <button
                type="button"
                onClick={() => openEditJournalForm(entry)}
                className="p-1 rounded border border-sp-bronze/30 hover:border-sp-gold text-text-warm/50 hover:text-sp-gold transition-colors cursor-pointer"
                title="Edit Entry"
              >
                <Edit className="w-3 h-3" />
              </button>
              <button
                type="button"
                onClick={() => handleJournalDelete(entry)}
                className="p-1 rounded border border-sp-bronze/30 hover:border-crimson text-text-warm/50 hover:text-rose-400 transition-colors cursor-pointer"
                title="Delete Entry"
              >
                <Trash2 className="w-3 h-3" />
              </button>
            </div>
          )}
        </div>

        {interactive && sharePickerEntryId === entry.id && (
          <div className="mb-3 bg-neutral-900/60 border border-sp-bronze/30 rounded-lg p-2.5 flex items-center gap-2 shrink-0">
            {journalFriends.length === 0 ? (
              <p className="text-[10px] text-text-warm/40 italic">
                No friends yet — <Link href="/friends" className="text-sp-gold hover:underline">recruit some crew</Link> to share pages with.
              </p>
            ) : (
              <>
                <select
                  value={shareTargetUsername}
                  onChange={e => setShareTargetUsername(e.target.value)}
                  className="flex-1 bg-neutral-950 border border-sp-bronze/40 text-white text-[10px] rounded px-2 py-1.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                >
                  <option value="">Choose a friend...</option>
                  {journalFriends.map(f => <option key={f.id} value={f.username}>{f.username}</option>)}
                </select>
                <button
                  type="button"
                  onClick={() => handleShareToFriend(entry.id)}
                  disabled={!shareTargetUsername || sharingBusy}
                  className="px-2.5 py-1.5 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[9px] font-bold uppercase rounded cursor-pointer transition-all shrink-0"
                >
                  {sharingBusy ? '...' : 'Send'}
                </button>
              </>
            )}
            {shareFeedback && <span className="text-[9px] text-emerald-400 shrink-0">{shareFeedback}</span>}
          </div>
        )}

        <div className="flex-1 overflow-y-auto pr-1 space-y-3">
          {entry.imageUrl && (
            <img
              src={entry.imageUrl}
              alt="Logbook attachment"
              className="w-full max-h-48 object-cover rounded-lg border border-sp-bronze/30"
            />
          )}
          <p className="text-sm text-text-warm/85 leading-relaxed whitespace-pre-wrap font-serif">{entry.content}</p>
        </div>
      </div>
    );
  }
  const shownProfile = draft || profile;
  const vis = (t) => (tab === t ? '' : 'hidden');

  return (
    <div className="space-y-6">
      
      {/* HERO + TABS. The cards below stay mounted when their tab is not
          active (just hidden), so a half-written bug report / logbook entry
          survives switching tabs. */}
      <ProfileHero
        username={user.mcUsername || 'Steve'}
        banner={shownProfile.banner}
        bannerImage={shownProfile.bannerImage}
        bannerPos={shownProfile.bannerPos}
        roleLabel={user.roleLabel || user.role}
        roleClass={roleBadgeClass(user.role, user.roleColor)}
        bio={shownProfile.bio}
        emptyBio={(
          <p className="mt-2 text-sm text-text-warm/45 italic">
            You haven&apos;t written a bio yet.{' '}
            <button type="button" onClick={() => setTab('customize')} className="text-sp-gold hover:underline not-italic cursor-pointer">Add one</button>
          </p>
        )}
        chips={[
          ...(gameStats?.playtimeHours != null ? [{ icon: <Clock />, label: 'Playtime', value: `${gameStats.playtimeHours} h` }] : []),
          ...(gameStats?.cmi?.rank ? [{ icon: <Sparkles />, label: 'Rank', value: gameStats.cmi.rank }] : [])
        ]}
        actions={user.mcUsername ? (
          <Link
            href={`/members/${encodeURIComponent(user.mcUsername)}`}
            className="w-full py-2.5 px-4 border border-sp-gold/50 hover:bg-sp-gold hover:text-black text-sp-gold text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center justify-center gap-1.5 transition-all"
          >
            View public profile
          </Link>
        ) : null}
      />

      <ProfileTabs
        tabs={[
          { key: 'overview', label: 'Overview', icon: <LayoutGrid /> },
          { key: 'logbook', label: 'Logbook', icon: <BookOpen />, badge: journalEntries.length || '' },
          { key: 'account', label: 'Account', icon: <User /> },
          { key: 'customize', label: 'Customize', icon: <Palette /> },
          { key: 'feedback', label: 'Feedback', icon: <MessageSquare />, badge: myBugReports.length || '' }
        ]}
        active={tab}
        onChange={setTab}
      />

      {/* A single shared grid (not two independent stacks) so each row's
          height is driven by its taller card and both columns' next row
          starts flush at the same Y — items-start keeps each card at its
          own natural height instead of stretching the shorter one to fill
          the row. Card order/grouping (left = your account, right = your
          activity) stays the same as before; only the placement mechanism
          changed. lg:col-start/row-start only apply at the two-column
          breakpoint — below it, cards fall back to plain DOM-order stacking
          (still left-group-then-right-group, same as the old mobile view). */}
      <div className="grid grid-cols-1 lg:grid-cols-2 items-start gap-x-8 gap-y-6">

          {tab === 'customize' && (
            <ProfileCustomize
              saved={profile}
              mcUsername={user.mcUsername}
              onDraftChange={setDraft}
              onSaved={(saved) => { setProfile(p => ({ ...p, ...saved })); setDraft(null); }}
              onBannerChange={(url) => setProfile(p => ({ ...p, bannerImage: url }))}
            />
          )}

          <BlockedMembersCard className={`lg:col-span-2 order-last ${vis('account')}`} />

          {/* Card: Account Info */}
          <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('account')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <User className="w-5 h-5 text-sp-copper" /> Access credentials
            </h3>
            
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs text-left">
              <div className="space-y-1">
                <span className="block text-text-warm/40 font-bold uppercase text-[9px] tracking-wider">Pilot Account (Email)</span>
                <span className="text-white font-medium flex items-center gap-1.5">
                  <Mail className="w-3.5 h-3.5 text-sp-copper" /> {user.email}
                </span>
              </div>
              <div className="space-y-1">
                <span className="block text-text-warm/40 font-bold uppercase text-[9px] tracking-wider">Assigned Position (Role)</span>
                <span className={`font-bold flex items-center gap-1.5 uppercase ${roleTextClass(user.role, user.roleColor)}`}>
                  <Shield className="w-3.5 h-3.5" /> {user.roleLabel || user.role}
                </span>
              </div>
            </div>
          </div>

          {/* Card: Bind IGN Form */}
          <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('account')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <Gamepad2 className="w-5 h-5 text-sp-copper" /> Bind Minecraft Identity
            </h3>
            
            <p className="text-xs text-text-warm/85 leading-relaxed text-left">
              Your in-game name connects you to the server statistics, claims, brewery projects, and renders your active skin model in the profile and leaderboards.
            </p>

            {success && (
              <div className="bg-green-500/10 border border-green-500 text-green-400 text-xs px-3 py-2 rounded-lg flex items-center gap-2">
                <Check className="w-4 h-4" /> Identity bounds re-calibrated successfully!
              </div>
            )}

            {error && (
              <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">
                {error}
              </div>
            )}

            <form onSubmit={handleUpdateIGN} className="flex flex-col sm:flex-row gap-3">
              <div className="flex-1 relative">
                <input
                  type="text"
                  required
                  value={mcNameInput}
                  onChange={e => setMcNameInput(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg pl-9 pr-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="Minecraft In-game Name (IGN)"
                />
                <Gamepad2 className="w-4 h-4 text-text-warm/40 absolute left-3 top-3" />
              </div>
              <button
                type="submit"
                disabled={updating}
                className="px-6 py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-black bg-sp-gold hover:bg-white disabled:opacity-50 transition-all cursor-pointer font-serif flex items-center justify-center gap-2"
              >
                {updating ? <RefreshCw className="w-4 h-4 animate-spin" /> : "Save Changes"}
              </button>
            </form>
          </div>

          {/* Pilot's Logbook (Journal) */}
          <div className={`bg-sp-dark/60 border border-sp-bronze/40 rounded-xl p-6 steampunk-glass ${vis('logbook')}`}>
            <div className="flex items-center justify-between flex-wrap gap-3 border-b border-sp-bronze/25 pb-3 mb-5">
              <div>
                <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-sp-copper" /> Pilot&apos;s Logbook
                </h2>
                <p className="text-[11px] text-text-warm/50 mt-0.5">Private by default — share a page with a friend or copy a public link from the icons on each entry.</p>
              </div>
              {!journalFormOpen && (
                <button
                  onClick={openNewJournalForm}
                  className="px-3 py-1.5 bg-sp-gold hover:bg-white text-black text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center gap-1.5 transition-all cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" /> New Entry
                </button>
              )}
            </div>

            {journalFormOpen && (
              <form onSubmit={handleJournalSubmit} className="mb-6 space-y-3 bg-neutral-950/40 border border-sp-bronze/30 rounded-lg p-4 text-left">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80">
                    {journalEditId ? 'Edit Logbook Page' : 'Write a New Page'}
                  </h3>
                  <button type="button" onClick={() => setJournalFormOpen(false)} className="text-text-warm/50 hover:text-white cursor-pointer">
                    <X className="w-4 h-4" />
                  </button>
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Date</label>
                  <input
                    type="date"
                    required
                    value={journalDateInput}
                    onChange={e => setJournalDateInput(e.target.value)}
                    className="bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                  />
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Entry</label>
                  <textarea
                    required
                    rows={5}
                    value={journalContentInput}
                    onChange={e => setJournalContentInput(e.target.value)}
                    placeholder="What happened on your journey today..."
                    className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
                  />
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Screenshot (optional)</label>
                  {journalImagePreview ? (
                    <div className="relative inline-block">
                      <img src={journalImagePreview} alt="Preview" className="max-h-32 rounded-lg border border-sp-bronze/40" />
                      <button
                        type="button"
                        onClick={() => { setJournalImageFile(null); setJournalImagePreview(''); setJournalRemoveImage(true); }}
                        className="absolute -top-2 -right-2 bg-crimson hover:bg-red-600 text-white rounded-full p-1 cursor-pointer"
                        title="Remove image"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  ) : (
                    <label className="flex items-center gap-2 w-fit px-3 py-2 bg-neutral-900 border border-sp-bronze/40 hover:border-sp-gold text-text-warm/60 hover:text-sp-gold text-[10px] uppercase font-bold rounded-lg cursor-pointer transition-colors">
                      <ImagePlus className="w-3.5 h-3.5" /> Attach Screenshot
                      <input type="file" accept="image/*" onChange={handleJournalImageSelect} className="hidden" />
                    </label>
                  )}
                </div>

                {journalError && <p className="text-xs text-rose-400">{journalError}</p>}

                <div className="flex gap-2 pt-1">
                  <button
                    type="submit"
                    disabled={journalSaving || !journalContentInput.trim()}
                    className="px-4 py-2 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer"
                  >
                    {journalSaving ? 'Saving...' : (journalEditId ? 'Save Page' : 'Add Page')}
                  </button>
                  <button
                    type="button"
                    onClick={() => setJournalFormOpen(false)}
                    className="px-4 py-2 border border-sp-bronze/40 hover:border-white/40 text-text-warm/60 hover:text-white text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            )}

            {journalLoading ? (
              <div className="text-xs text-text-warm/40 italic text-center py-10">Retrieving logbook pages...</div>
            ) : journalEntries.length === 0 ? (
              <div className="text-center py-14 border border-dashed border-sp-bronze/30 rounded-xl text-text-warm/40 text-xs italic">
                Your logbook is empty. Write your first page to begin your journey.
              </div>
            ) : (
              <div className="journal-book relative mx-auto" style={{ maxWidth: '560px' }}>
                <div className="journal-page-edge -left-1" />
                <div className="journal-page-edge -right-1" />
                <div className="journal-page-stack min-h-[340px] bg-neutral-950/50 border-2 border-sp-bronze/50 rounded-lg p-6 relative overflow-hidden shadow-inner">
                  <div key={journalEntries[journalIndex].id} className="journal-page-enter h-full">
                    {renderJournalPageContent(journalEntries[journalIndex])}
                  </div>

                  {journalFlipDirection && journalOutgoingEntry && (
                    <div
                      className={`bg-neutral-950/95 p-6 ${journalFlipDirection === 'next' ? 'journal-page-flip-next' : 'journal-page-flip-prev'}`}
                    >
                      {renderJournalPageContent(journalOutgoingEntry, false)}
                    </div>
                  )}
                </div>

                <div className="flex items-center justify-between mt-3">
                  <button
                    onClick={() => journalFlipTo(journalIndex - 1, 'prev')}
                    disabled={journalIndex <= 0}
                    className="p-2 border border-sp-bronze/40 hover:border-sp-gold rounded-lg text-text-warm/60 hover:text-sp-gold disabled:opacity-25 disabled:pointer-events-none transition-all cursor-pointer"
                    title="Previous Page"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <span className="text-[10px] font-mono text-text-warm/40 uppercase tracking-wider">
                    Page {journalIndex + 1} of {journalEntries.length}
                  </span>
                  <button
                    onClick={() => journalFlipTo(journalIndex + 1, 'next')}
                    disabled={journalIndex >= journalEntries.length - 1}
                    className="p-2 border border-sp-bronze/40 hover:border-sp-gold rounded-lg text-text-warm/60 hover:text-sp-gold disabled:opacity-25 disabled:pointer-events-none transition-all cursor-pointer"
                    title="Next Page"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Account Security (Change Password + Danger Zone) */}
          <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-5 text-left ${vis('account')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <Lock className="w-5 h-5 text-sp-copper" /> Account Security
            </h3>

            <div className="space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80 flex items-center gap-1.5">
                <KeyRound className="w-3.5 h-3.5" /> Change Password
              </h4>

              {pwSuccess && (
                <div className="bg-green-500/10 border border-green-500 text-green-400 text-xs px-3 py-2 rounded-lg flex items-center gap-2">
                  <Check className="w-4 h-4" /> Password changed successfully!
                </div>
              )}
              {pwError && (
                <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">
                  {pwError}
                </div>
              )}

              <form onSubmit={handleChangePassword} className="space-y-2.5">
                <input
                  type="password"
                  required
                  value={currentPasswordInput}
                  onChange={e => setCurrentPasswordInput(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="Current Password"
                  autoComplete="current-password"
                />
                <input
                  type="password"
                  required
                  minLength={8}
                  value={newPasswordInput}
                  onChange={e => setNewPasswordInput(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="New Password (min. 8 characters)"
                  autoComplete="new-password"
                />
                <input
                  type="password"
                  required
                  minLength={8}
                  value={confirmPasswordInput}
                  onChange={e => setConfirmPasswordInput(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="Confirm New Password"
                  autoComplete="new-password"
                />
                <button
                  type="submit"
                  disabled={pwSaving}
                  className="w-full px-6 py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-black bg-sp-gold hover:bg-white disabled:opacity-50 transition-all cursor-pointer font-serif flex items-center justify-center gap-2"
                >
                  {pwSaving ? <RefreshCw className="w-4 h-4 animate-spin" /> : "Change Password"}
                </button>
              </form>
            </div>

            {/* Passkeys — a faster alternative to typing a TOTP code on
                every staff login (see api/auth/passkey/*). Only shown to
                accounts that actually go through the TOTP step at all;
                user.totpEnabled already reflects that without needing a
                separate staff-role check here. */}
            {user?.totpEnabled && (
              <div className="space-y-3 border-t border-sp-bronze/15 pt-4">
                <h4 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80 flex items-center gap-1.5">
                  <KeyRound className="w-3.5 h-3.5" /> Passkeys
                </h4>
                <p className="text-[11px] text-text-warm/50 leading-relaxed">
                  Sign in with your fingerprint, face, PIN, or a security key instead of typing a 6-digit code every time. Your authenticator app still works as a fallback — this is optional and additional, not a replacement.
                </p>

                {passkeyError && (
                  <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">
                    {passkeyError}
                  </div>
                )}

                {loadingPasskeys ? (
                  <p className="text-[11px] text-text-warm/40 italic">Loading passkeys...</p>
                ) : passkeys.length > 0 ? (
                  <ul className="space-y-1.5">
                    {passkeys.map(pk => (
                      <li key={pk.id} className="flex items-center justify-between gap-2 bg-neutral-900/60 border border-sp-bronze/25 rounded-lg px-3 py-2">
                        <div className="min-w-0">
                          <span className="block text-xs font-semibold text-white truncate">{pk.name || 'Unnamed passkey'}</span>
                          <span className="block text-[9px] text-text-warm/40" suppressHydrationWarning>
                            Added {new Date(pk.createdAt).toLocaleDateString()}
                            {pk.lastUsedAt ? ` · last used ${new Date(pk.lastUsedAt).toLocaleDateString()}` : ' · never used yet'}
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => handleRemovePasskey(pk.id)}
                          disabled={passkeyRemovingId === pk.id}
                          className="p-1.5 text-text-warm/40 hover:text-crimson disabled:opacity-40 cursor-pointer shrink-0"
                          title="Remove this passkey"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-[11px] text-text-warm/30 italic">No passkeys added yet.</p>
                )}

                <div className="flex gap-2">
                  <input
                    type="text"
                    value={passkeyNameInput}
                    onChange={e => setPasskeyNameInput(e.target.value)}
                    placeholder="Name (e.g. My Phone)"
                    maxLength={60}
                    className="flex-1 min-w-0 bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                  />
                  <button
                    type="button"
                    onClick={handleAddPasskey}
                    disabled={passkeyAdding}
                    className="shrink-0 px-4 py-2 rounded-lg text-[10px] font-bold uppercase tracking-wider text-black bg-sp-gold hover:bg-white disabled:opacity-50 transition-all cursor-pointer font-serif flex items-center justify-center gap-1.5"
                  >
                    {passkeyAdding ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
                    Add Passkey
                  </button>
                </div>
              </div>
            )}

            {/* Email Preferences — same flag the newsletter's one-click
                unsubscribe link flips, reachable here too. */}
            <div className="space-y-2 border-t border-sp-bronze/15 pt-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80 flex items-center gap-1.5">
                <Mail className="w-3.5 h-3.5" /> Email Preferences
              </h4>
              <label className="flex items-center justify-between gap-3 text-xs text-text-warm/70 cursor-pointer select-none">
                <span>Receive the occasional newsletter email from staff</span>
                <input
                  type="checkbox"
                  checked={!newsletterOptedOut}
                  disabled={newsletterLoading || newsletterSaving}
                  onChange={toggleNewsletter}
                  className="accent-sp-gold w-4 h-4 shrink-0 disabled:opacity-40"
                />
              </label>
            </div>

            {/* Push notifications (Android app) — per-category opt-outs plus a
                master switch. Only affects phone pushes; the bell on the site
                keeps showing everything. */}
            {pushPrefs && (
              <div className="space-y-2 border-t border-sp-bronze/15 pt-4">
                <h4 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80 flex items-center gap-1.5">
                  <Bell className="w-3.5 h-3.5" /> Push Notifications
                </h4>

                {/* This device (browser / iPhone home-screen app). The native
                    Android app registers itself, so nothing to show there. */}
                {webPushStatus && webPushStatus !== 'native-app' && (
                  <div className="bg-neutral-900/50 border border-sp-bronze/30 rounded-lg p-3 space-y-2 text-xs">
                    {webPushStatus === 'ios-needs-home-screen' ? (
                      <p className="text-text-warm/75 leading-relaxed">
                        On iPhone, notifications work once Iekcorbar is on your home screen: tap <strong>Share</strong> → <strong>Add to Home Screen</strong>, open Iekcorbar from there, and come back to this page.{' '}
                        <Link href="/app" className="text-sp-gold underline font-bold">How it works</Link>
                      </p>
                    ) : webPushStatus === 'unsupported' ? (
                      <p className="text-text-warm/60">This browser doesn&apos;t support push notifications.</p>
                    ) : webPushStatus === 'denied' ? (
                      <p className="text-rose-300/90">Notifications are blocked for this site on this device. Allow them in your browser or phone settings.</p>
                    ) : (
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-text-warm/80">
                          {webPushStatus === 'enabled' ? '✅ Notifications are on for this device' : 'Get notifications on this device'}
                        </span>
                        <button
                          type="button"
                          onClick={toggleWebPush}
                          disabled={webPushBusy}
                          className={`shrink-0 px-3 py-1.5 rounded-md text-[10px] font-bold uppercase tracking-wider transition-all disabled:opacity-50 ${
                            webPushStatus === 'enabled' ? 'border border-sp-bronze/60 text-text-warm/80 hover:border-crimson hover:text-rose-300' : 'bg-sp-gold text-black hover:bg-white'
                          }`}
                        >
                          {webPushBusy ? '...' : webPushStatus === 'enabled' ? 'Turn off' : 'Enable notifications'}
                        </button>
                      </div>
                    )}
                    {webPushError && <p className="text-rose-300/90">{webPushError}</p>}
                  </div>
                )}
                <label className="flex items-center justify-between gap-3 text-xs text-white font-semibold cursor-pointer select-none">
                  <span>All push notifications</span>
                  <input
                    type="checkbox"
                    checked={pushPrefs.enabled}
                    disabled={pushSaving}
                    onChange={() => togglePushPref('enabled')}
                    className="accent-sp-gold w-4 h-4 shrink-0 disabled:opacity-40"
                  />
                </label>
                <div className={`space-y-2 pl-3 border-l border-sp-bronze/20 ${pushPrefs.enabled ? '' : 'opacity-40'}`}>
                  {[
                    { key: 'chat', label: 'Chat messages' },
                    { key: 'personal', label: 'Friends, shared journals & replies to your bug reports' },
                    { key: 'events', label: 'Events, event reminders, community projects & polls' },
                    { key: 'tips', label: 'Daily tip of the day (10:00)' },
                    { key: 'community', label: 'News, updates, wiki, rules, shop & other site changes' },
                    ...(pushIsStaff ? [{ key: 'staff', label: 'Staff notifications (reports, applications, bug reports)' }] : []),
                  ].map(({ key, label }) => (
                    <label key={key} className="flex items-center justify-between gap-3 text-xs text-text-warm/70 cursor-pointer select-none">
                      <span>{label}</span>
                      <input
                        type="checkbox"
                        checked={pushPrefs[key]}
                        disabled={pushSaving || !pushPrefs.enabled}
                        onChange={() => togglePushPref(key)}
                        className="accent-sp-gold w-4 h-4 shrink-0 disabled:opacity-40"
                      />
                    </label>
                  ))}
                </div>
                <p className="text-[10px] text-text-warm/40 leading-relaxed">
                  These choices apply to every device where you get notifications. The bell on the website still shows everything.
                </p>
              </div>
            )}

            {/* Danger Zone: self-service account deletion — see the Privacy
                Policy's "Right to be Forgotten" section. Available to every
                account, staff included; the only exception is the very last
                admin account, blocked server-side so the site can never end
                up with zero admins. */}
            <div className="space-y-3 border-t border-crimson/30 pt-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-rose-300 flex items-center gap-1.5">
                <AlertTriangle className="w-3.5 h-3.5" /> Danger Zone
              </h4>
              <p className="text-[11px] text-text-warm/70 leading-relaxed">
                Permanently delete your account, whitelist entry, and associated data (journal, friendships, notifications). Your in-game name, moderation history, and other game records are kept, same as if you'd simply stopped playing. This can't be undone.
              </p>

              {!deleteConfirming ? (
                <button
                  onClick={() => { setDeleteConfirming(true); setDeleteError(''); }}
                  className="px-4 py-2 rounded-lg text-[10px] font-bold uppercase tracking-wider border-2 border-crimson/40 bg-crimson/10 hover:bg-crimson/25 text-rose-300 hover:text-rose-200 transition-all cursor-pointer flex items-center gap-2"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Delete Account
                </button>
              ) : (
                <form onSubmit={handleDeleteAccount} className="space-y-2.5 bg-crimson/5 border border-crimson/30 rounded-lg p-3">
                  <p className="text-[10px] text-rose-200 font-bold uppercase tracking-wider">Confirm with your password to permanently delete your account:</p>
                  {deleteError && (
                    <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">
                      {deleteError}
                    </div>
                  )}
                  <input
                    type="password"
                    required
                    value={deletePasswordInput}
                    onChange={e => setDeletePasswordInput(e.target.value)}
                    className="w-full bg-neutral-900 border border-crimson/50 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-crimson"
                    placeholder="Your Password"
                    autoComplete="current-password"
                    autoFocus
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => { setDeleteConfirming(false); setDeletePasswordInput(''); setDeleteError(''); }}
                      className="flex-1 px-4 py-2 rounded-lg text-[10px] font-bold uppercase tracking-wider border border-sp-bronze/40 text-text-warm/70 hover:text-white hover:border-sp-gold transition-all cursor-pointer"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={deleteSaving}
                      className="flex-1 px-4 py-2 bg-crimson hover:bg-rose-600 border border-rose-500 rounded-lg text-[10px] font-bold uppercase tracking-wider text-white disabled:opacity-50 transition-all cursor-pointer flex items-center justify-center gap-2"
                    >
                      {deleteSaving ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : "Permanently Delete"}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>

          {/* Card: In-Game Stats */}
          <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('overview')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center justify-between gap-2">
              <span className="flex items-center gap-2"><Sparkles className="w-5 h-5 text-amber-400" /> In-Game Statistics</span>
              {gameStats?.cmi?.rank && (
                <span className="text-[9px] font-mono normal-case bg-sp-gold/10 border border-sp-gold/30 text-sp-gold px-2 py-0.5 rounded-full">{gameStats.cmi.rank}</span>
              )}
            </h3>

            {statsLoading ? (
              <div className="text-xs text-text-warm/40 italic py-4 text-center">Retrieving server data...</div>
            ) : !gameStats ? (
              <div className="flex items-start gap-2.5 bg-amber-950/30 border border-amber-700/30 rounded-lg p-3">
                <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                <p className="text-[11px] text-amber-300/80 leading-relaxed">
                  Server offline or <strong>IekcorbarSiteSync</strong> plugin not installed. Stats will appear once connected.
                </p>
              </div>
            ) : (
              <>
                {/* Economy row */}
                <div className="grid grid-cols-2 gap-3">
                  <div className="bg-neutral-950/30 p-3 rounded-lg border border-sp-bronze/10 text-left">
                    <span className="block text-[8px] uppercase font-bold text-text-warm/40">Yen Balance</span>
                    <span className="font-serif text-base font-black text-sp-gold flex items-center gap-1">
                      <DollarSign className="w-3.5 h-3.5" /> {(gameStats.balance ?? 0).toLocaleString()}
                    </span>
                  </div>
                  <div className="bg-neutral-950/30 p-3 rounded-lg border border-sp-bronze/10 text-left">
                    <span className="block text-[8px] uppercase font-bold text-text-warm/40">Elite Coins</span>
                    <span className="font-serif text-base font-black text-amber-400 flex items-center gap-1">
                      <Sparkles className="w-3.5 h-3.5" /> {(gameStats.eliteCoins ?? 0).toLocaleString()}
                    </span>
                  </div>
                </div>

                {/* Quick stats row */}
                <div className="grid grid-cols-4 gap-2">
                  <div className="bg-neutral-950/20 p-2 rounded-lg border border-sp-bronze/10 text-center">
                    <span className="block text-[7px] uppercase font-bold text-text-warm/40">Playtime</span>
                    <span className="text-xs font-bold text-white">{gameStats.playtimeHours ?? 0}h</span>
                  </div>
                  <div className="bg-neutral-950/20 p-2 rounded-lg border border-sp-bronze/10 text-center">
                    <span className="block text-[7px] uppercase font-bold text-text-warm/40">Kills</span>
                    <span className="text-xs font-bold text-white">{gameStats.kills ?? 0}</span>
                  </div>
                  <div className="bg-neutral-950/20 p-2 rounded-lg border border-sp-bronze/10 text-center">
                    <span className="block text-[7px] uppercase font-bold text-text-warm/40">Deaths</span>
                    <span className="text-xs font-bold text-white">{gameStats.deaths ?? 0}</span>
                  </div>
                  <div className="bg-neutral-950/20 p-2 rounded-lg border border-sp-bronze/10 text-center">
                    <span className="block text-[7px] uppercase font-bold text-text-warm/40">Lands</span>
                    <span className="text-xs font-bold text-white">{gameStats.landsClaimed ?? 0}</span>
                  </div>
                  {/* Real CMI-only data — see src/lib/cmi-db.js. Only shown
                      when that DB was reachable, so this quietly disappears
                      instead of showing a broken/zero value if it isn't. */}
                  {gameStats.cmi && (
                    <div className="bg-neutral-950/20 p-2 rounded-lg border border-sp-bronze/10 text-center">
                      <span className="block text-[7px] uppercase font-bold text-text-warm/40">Homes Set</span>
                      <span className="text-xs font-bold text-white">{gameStats.cmi.homesCount}</span>
                    </div>
                  )}
                </div>

                {/* Jobs */}
                {gameStats.jobs && gameStats.jobs.length > 0 && (
                  <div className="space-y-2">
                    <span className="block text-[9px] uppercase font-bold text-text-warm/40 tracking-wider">Active Jobs</span>
                    {gameStats.jobs.map(job => {
                      const hasXp = typeof job.experience === 'number' && typeof job.maxExperience === 'number' && job.maxExperience > 0;
                      const xpPct = hasXp ? Math.max(0, Math.min(100, (job.experience / job.maxExperience) * 100)) : null;
                      return (
                        <div key={job.name} className="bg-neutral-950/20 px-3 py-1.5 rounded-lg border border-sp-bronze/10 text-xs space-y-1">
                          <div className="flex items-center justify-between">
                            <span className="text-text-warm/80 font-mono">{job.name}</span>
                            <span className="text-sp-gold font-bold font-serif">Lvl {job.level}</span>
                          </div>
                          {hasXp && (
                            <div className="h-1 bg-neutral-900 rounded-full overflow-hidden" title={`${Math.round(job.experience)} / ${job.maxExperience} XP`}>
                              <div className="h-full bg-sp-gold/70" style={{ width: `${xpPct}%` }} />
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                )}

                {/* Generic plugin stats (Lands/ValhallaMMO/ValhallaRaces/... —
                    whatever staff configured under placeholder-sync in the
                    sync plugin's config.yml) */}
                {gameStats.pluginStats && Object.keys(gameStats.pluginStats).length > 0 && (
                  <div className="space-y-2">
                    <span className="block text-[9px] uppercase font-bold text-text-warm/40 tracking-wider">Adventurer&apos;s Ledger</span>
                    <div className="grid grid-cols-2 gap-2">
                      {Object.entries(gameStats.pluginStats).map(([key, value]) => (
                        <div key={key} className="bg-neutral-950/20 px-3 py-1.5 rounded-lg border border-sp-bronze/10 text-xs flex items-center justify-between gap-2">
                          <span className="text-text-warm/50 uppercase text-[9px] tracking-wide truncate">{humanizePluginStatKey(key)}</span>
                          <span className="text-white font-bold truncate">{value}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
          
          {/* Card: Registered Events */}
          <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('overview')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <BookOpen className="w-5 h-5 text-sp-copper" /> Active Transmissions (Registered Events)
            </h3>
            
            <p className="text-xs text-text-warm/85 leading-relaxed text-left">
              Lit torches represent upcoming events you have signed up for. You can toggle the torch here to cancel your registration.
            </p>
            
            {loadingEvents ? (
              <div className="text-xs text-text-warm/40 italic">Querying outposts...</div>
            ) : events.filter(e => registrations.some(r => r.eventId === e.id && r.username === user.mcUsername)).length === 0 ? (
              <p className="text-xs text-text-warm/60 leading-relaxed italic border-t border-sp-bronze/10 pt-3 text-left">
                You haven&apos;t signed up for any events yet. Go to the <a href="/events" className="text-sp-gold underline hover:text-white">Events Log</a> to light a transmission torch and register.
              </p>
            ) : (
              // Compact layout: with up to 3 events joinable at once (was a
              // de-facto 1-event cap before a registerForEvent bug was
              // fixed), this list can now show up to 3 rows instead of 1 —
              // the torch itself is a fixed 150px-tall CSS element, and a
              // scale-* transform alone doesn't shrink the layout box it
              // reserves, so each row was staying full-height regardless of
              // the visual scale. Clipping it inside a small fixed-size box
              // is what actually makes the row compact; the torch's flame,
              // glow and smoke animations are all still there, just smaller.
              <div className="space-y-2">
                {events.filter(e => registrations.some(r => r.eventId === e.id && r.username === user.mcUsername)).map((event) => (
                  <div key={event.id} className="bg-neutral-950/40 p-2.5 border border-sp-bronze/20 rounded-lg flex items-center justify-between gap-3">
                    <div className="space-y-0.5 text-left min-w-0">
                      <h4 className="text-xs font-bold text-white uppercase tracking-wider truncate">{event.title}</h4>
                      <p className="text-[10px] text-text-warm/60">
                        {event.date} at {event.time}
                      </p>
                    </div>

                    <div className="w-14 h-14 shrink-0 overflow-hidden flex items-start justify-center" title="Click the torch to leave this event">
                      <label className="torch-container scale-50 origin-top cursor-pointer">
                        <input
                          type="checkbox"
                          checked={true}
                          onChange={() => handleRegister(event.id, 'unregister')}
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
                ))}
              </div>
            )}
          </div>

          {/* Card: Joined Community Projects */}
          <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('overview')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <Hammer className="w-5 h-5 text-sp-copper" /> Joined Community Projects
            </h3>

            <p className="text-xs text-text-warm/85 leading-relaxed text-left">
              Blueprints you&apos;ve signed up to help build. Leaving frees your slot for another engineer.
            </p>

            {loadingProjects ? (
              <div className="text-xs text-text-warm/40 italic">Querying construction guild...</div>
            ) : projects.filter(p => projectSignups.some(s => s.projectId === p.id && s.username === user.mcUsername)).length === 0 ? (
              <p className="text-xs text-text-warm/60 leading-relaxed italic border-t border-sp-bronze/10 pt-3 text-left">
                You haven&apos;t joined any projects yet. Head to the <a href="/projects" className="text-sp-gold underline hover:text-white">Projects</a> page to find a blueprint to help build.
              </p>
            ) : (
              <div className="space-y-3">
                {projects.filter(p => projectSignups.some(s => s.projectId === p.id && s.username === user.mcUsername)).map((project) => {
                  const memberCount = projectSignups.filter(s => s.projectId === project.id).length;
                  return (
                    <div key={project.id} className="bg-neutral-950/40 p-4 border border-sp-bronze/20 rounded-lg flex items-center justify-between gap-4">
                      <div className="space-y-1 text-left min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h4 className="text-xs font-bold text-white uppercase tracking-wider truncate">{project.title}</h4>
                          <span className={`text-[9px] font-bold uppercase px-1.5 py-0.5 rounded-sm border ${
                            project.status === 'completed'
                              ? 'text-emerald-300 border-emerald-500/30 bg-emerald-950/30'
                              : project.status === 'in-progress'
                              ? 'text-amber-300 border-amber-500/30 bg-amber-950/30'
                              : 'text-sp-gold border-sp-gold/30 bg-sp-gold/10'
                          }`}>
                            {(project.status || 'open').replace('-', ' ')}
                          </span>
                        </div>
                        <p className="text-[10px] text-text-warm/60">
                          {memberCount}{project.maxParticipants ? ` / ${project.maxParticipants}` : ''} engineers &bull; {project.category}
                        </p>
                      </div>

                      <button
                        onClick={() => handleLeaveProject(project.id)}
                        disabled={leavingProjectId === project.id}
                        className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[10px] font-bold uppercase tracking-wider border-2 border-crimson/40 bg-crimson/10 hover:bg-crimson/25 text-rose-300 hover:text-rose-200 transition-all cursor-pointer disabled:opacity-50"
                      >
                        <UserMinus className="w-3.5 h-3.5" />
                        {leavingProjectId === project.id ? 'Leaving...' : 'Leave'}
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Card: Anonymous Report Form */}
          <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('feedback')}`}>
            <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <Flag className="w-5 h-5 text-crimson" /> Report Player Anonymously
            </h3>
            
            <p className="text-xs text-text-warm/85 leading-relaxed text-left">
              Submit an anonymous report to server staff. Your account information is <strong>never</strong> recorded or linked to this report.
            </p>

            <div className="flex items-start gap-2.5 bg-amber-950/30 border border-amber-700/30 rounded-lg p-3 text-left">
              <AlertTriangle className="w-4.5 h-4.5 text-amber-400 shrink-0 mt-0.5 animate-pulse" />
              <p className="text-[11px] text-amber-300/80 leading-relaxed">
                This transmission is 100% anonymized. Only use this for genuine rule violations, hacking, or harassment.
              </p>
            </div>

            {reportSubmitted ? (
              <div className="text-center py-6 space-y-2">
                <div className="w-12 h-12 mx-auto rounded-full bg-green-900/30 border border-green-500/40 flex items-center justify-center">
                  <Flag className="w-6 h-6 text-green-400" />
                </div>
                <p className="text-sm font-bold text-green-400">Report submitted successfully</p>
                <p className="text-xs text-text-warm/40 font-mono">Anonymous signal dispatched to Staff Station.</p>
              </div>
            ) : (
              <form onSubmit={handleReportSubmit} className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-left">
                  <div>
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Target Player Username</label>
                    <input
                      type="text"
                      required
                      value={reportTarget}
                      onChange={e => setReportTarget(e.target.value)}
                      placeholder="e.g. Steve"
                      className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                    />
                  </div>
                  <div>
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Violation Category</label>
                    <select
                      value={reportCategory}
                      onChange={e => setReportCategory(e.target.value)}
                      className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                    >
                      <option>Hacking</option>
                      <option>Harassment</option>
                      <option>Griefing</option>
                      <option>Spam / Advertising</option>
                      <option>Other</option>
                    </select>
                  </div>
                </div>

                <div className="text-left">
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Changelog / Details <span className="text-text-warm/40">(min. 20 characters)</span></label>
                  <textarea
                    required
                    minLength={20}
                    rows={4}
                    value={reportDetails}
                    onChange={e => setReportDetails(e.target.value)}
                    placeholder="Describe what happened, where, and any relevant details..."
                    className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
                  />
                </div>

                {reportError && <p className="text-xs text-rose-400 text-left">{reportError}</p>}

                <button
                  type="submit"
                  disabled={reportSubmitting || reportDetails.trim().length < 20 || !reportTarget.trim()}
                  className="w-full py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-white bg-crimson hover:bg-red-600 disabled:opacity-50 transition-all cursor-pointer font-serif flex items-center justify-center gap-2 shadow-md shadow-crimson/10"
                >
                  {reportSubmitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <>Dispatches Anonymous Report</>}
                </button>
              </form>
            )}
          </div>

          {/* Card: Report a Bug */}
          <div id="report-a-bug" className={`lg:col-span-2 bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('feedback')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <Bug className="w-5 h-5 text-sp-copper" /> Report a Bug
            </h3>

            <p className="text-xs text-text-warm/85 leading-relaxed text-left max-w-xl">
              Found something broken on the website? Let staff know — unlike the anonymous player report above, this is sent under your account so we can follow up if we need more details.
            </p>

            {bugSubmitted ? (
              <div className="text-center py-6 space-y-2">
                <div className="w-12 h-12 mx-auto rounded-full bg-green-900/30 border border-green-500/40 flex items-center justify-center">
                  <Bug className="w-6 h-6 text-green-400" />
                </div>
                <p className="text-sm font-bold text-green-400">Bug report submitted</p>
                <p className="text-xs text-text-warm/40 font-mono">Thanks for flagging it — staff will take a look. You can follow the status and any staff replies below under &quot;My Bug Reports&quot;.</p>
              </div>
            ) : (
              <form onSubmit={handleBugSubmit} className="space-y-4 max-w-xl">
                <div className="text-left">
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Category</label>
                  <select
                    required
                    value={bugCategory}
                    onChange={e => { setBugCategory(e.target.value); setDuplicateBumpedIds([]); }}
                    className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                  >
                    <option value="" disabled>Pick what this is about...</option>
                    {BUG_CATEGORIES.map(c => (
                      <option key={c.value} value={c.value}>{c.label}</option>
                    ))}
                  </select>
                </div>

                {bugCategory === 'other' && (
                  <div className="text-left">
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Describe the category</label>
                    <input
                      type="text"
                      required
                      value={bugCustomCategory}
                      onChange={e => setBugCustomCategory(e.target.value)}
                      placeholder="e.g. Achievements page"
                      className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                    />
                  </div>
                )}

                {/* Duplicate-check panel — only for predefined categories,
                    since "Other" reports are too varied to usefully match. */}
                {bugCategory && bugCategory !== 'other' && (
                  <div className="bg-neutral-950/40 border border-sp-bronze/20 rounded-lg p-3 space-y-2 text-left">
                    <p className="text-[10px] uppercase font-bold text-text-warm/50 tracking-wide">Already known?</p>
                    {duplicateLoading ? (
                      <p className="text-[11px] text-text-warm/40">Checking existing reports...</p>
                    ) : duplicateCandidates.length === 0 ? (
                      <p className="text-[11px] text-text-warm/40">No open reports in this category yet — looks like this one's new.</p>
                    ) : (
                      <div className="space-y-1.5">
                        {duplicateCandidates.map(c => (
                          <div key={c.id} className="flex items-center justify-between gap-2 bg-neutral-900/50 rounded-md px-2.5 py-1.5">
                            <div className="min-w-0">
                              <p className="text-[11px] font-bold text-white truncate">{c.subject}</p>
                              {c.affectedCount > 1 && <p className="text-[9px] text-sp-gold/70">{c.affectedCount} players affected</p>}
                            </div>
                            {duplicateBumpedIds.includes(c.id) ? (
                              <span className="text-[9px] uppercase font-bold text-emerald-400 shrink-0">Marked</span>
                            ) : (
                              <button
                                type="button"
                                onClick={() => handleConfirmDuplicate(c.id)}
                                disabled={duplicateBumpingId === c.id}
                                className="px-2 py-1 border border-sp-gold/40 hover:bg-sp-gold/15 rounded text-[9px] uppercase font-bold text-sp-gold shrink-0 cursor-pointer disabled:opacity-50"
                              >
                                I have this too
                              </button>
                            )}
                          </div>
                        ))}
                        <p className="text-[10px] text-text-warm/40 italic">If none of these match, keep filling out the form below.</p>
                      </div>
                    )}
                  </div>
                )}

                <div className="text-left">
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Subject</label>
                  <input
                    type="text"
                    required
                    value={bugSubject}
                    onChange={e => setBugSubject(e.target.value)}
                    placeholder="e.g. Skin upload fails on mobile"
                    className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  />
                </div>
                <div className="text-left">
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Description</label>
                  <textarea
                    required
                    rows={4}
                    value={bugDescription}
                    onChange={e => setBugDescription(e.target.value)}
                    placeholder="What happened, what did you expect, and how can we reproduce it?"
                    className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
                  />
                  <p className="text-[10px] text-text-warm/40 mt-1 leading-relaxed">
                    The more detail, the better: what you were doing right before, the exact steps to trigger it, and what you expected instead. A report we can&apos;t reproduce is a report we can&apos;t fix.
                  </p>
                </div>

                {bugError && <p className="text-xs text-rose-400 text-left">{bugError}</p>}

                <button
                  type="submit"
                  disabled={bugSubmitting || !bugSubject.trim() || !bugDescription.trim() || !bugCategory || (bugCategory === 'other' && !bugCustomCategory.trim())}
                  className="w-full py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-black bg-sp-gold hover:bg-white disabled:opacity-50 transition-all cursor-pointer font-serif flex items-center justify-center gap-2 shadow-md"
                >
                  {bugSubmitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <>Submit Bug Report</>}
                </button>
              </form>
            )}
          </div>

          {/* Card: My Bug Reports — status + the staff/reporter conversation
              thread. #bug-reports is where the "Contact Reporter" bell
              notification deep-links to (see api/bug-reports action 'contact'). */}
          <div id="bug-reports" className={`lg:col-span-2 bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-4 ${vis('feedback')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <MessageSquare className="w-5 h-5 text-sp-copper" /> My Bug Reports ({myBugReports.length})
            </h3>
            <p className="text-xs text-text-warm/60 leading-relaxed">
              Click a report to see its status and reply if staff ask for more detail — you&apos;ll also get a bell notification here the moment it&apos;s marked Fixed.
            </p>

            {myBugReportsLoading ? (
              <p className="text-xs text-text-warm/40 text-center py-6">Loading...</p>
            ) : myBugReports.length === 0 ? (
              <p className="text-xs text-text-warm/40 text-center py-6">You haven&apos;t submitted any bug reports yet.</p>
            ) : (
              <div className="space-y-2.5">
                {myBugReports.map(r => (
                  <div key={r.id} className="bg-neutral-950/40 border border-sp-bronze/20 rounded-lg overflow-hidden text-left">
                    <button
                      type="button"
                      onClick={() => toggleBugThread(r.id)}
                      className="w-full flex items-center justify-between gap-3 p-3 cursor-pointer hover:bg-neutral-950/60 transition-colors"
                    >
                      <div className="min-w-0 text-left">
                        <div className="flex items-center gap-2 flex-wrap mb-1">
                          <span className={`text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border ${
                            r.status === 'fixed' ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-400'
                              : r.status === 'wont_fix' ? 'bg-neutral-700/40 border-neutral-500/40 text-text-warm/60'
                              : r.status === 'duplicate' ? 'bg-sp-copper/20 border-sp-copper/40 text-sp-copper'
                              : 'bg-crimson/20 border-crimson/40 text-rose-300'
                          }`}>
                            {BUG_STATUS_LABELS[r.status] || r.status}
                          </span>
                          <span className="text-[9px] text-text-warm/30 font-mono">{bugCategoryLabel(r.category, r.customCategory)}</span>
                        </div>
                        <p className="text-xs font-bold text-white truncate">{r.subject}</p>
                      </div>
                      {expandedBugReportId === r.id ? <ChevronUp className="w-4 h-4 text-text-warm/40 shrink-0" /> : <ChevronDown className="w-4 h-4 text-text-warm/40 shrink-0" />}
                    </button>

                    {expandedBugReportId === r.id && (
                      <div className="p-3 pt-0 space-y-3 border-t border-sp-bronze/10">
                        <p className="text-xs text-text-warm/70 leading-relaxed bg-neutral-900/50 rounded-lg p-2.5 break-words">{r.description}</p>

                        <div className="space-y-2">
                          {(bugThreads[r.id] || []).map(m => (
                            <div key={m.id} className={`text-xs rounded-lg p-2.5 max-w-[85%] ${m.senderType === 'staff' ? 'bg-sp-gold/10 border border-sp-gold/25 ml-auto' : 'bg-neutral-900/60 border border-sp-bronze/20'}`}>
                              <p className="text-[9px] uppercase font-bold text-text-warm/40 mb-1">{m.senderType === 'staff' ? `Staff (${m.senderName || 'Staff'})` : 'You'}</p>
                              <p className="text-text-warm/85 whitespace-pre-wrap break-words">{m.body}</p>
                            </div>
                          ))}
                          {(bugThreads[r.id] || []).length === 0 && (
                            <p className="text-[10px] text-text-warm/30 italic">No replies yet.</p>
                          )}
                        </div>

                        <div className="flex items-center gap-2 pt-1">
                          <input
                            type="text"
                            value={bugReplyDrafts[r.id] || ''}
                            onChange={e => setBugReplyDrafts(prev => ({ ...prev, [r.id]: e.target.value }))}
                            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleBugReplySubmit(r.id); } }}
                            placeholder="Add more detail or reply to staff..."
                            className="flex-1 bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                          />
                          <button
                            type="button"
                            onClick={() => handleBugReplySubmit(r.id)}
                            disabled={bugReplySendingId === r.id || !(bugReplyDrafts[r.id] || '').trim()}
                            className="p-2 bg-sp-gold/15 hover:bg-sp-gold/25 border border-sp-gold/30 rounded-lg text-sp-gold cursor-pointer disabled:opacity-40 shrink-0"
                            title="Send"
                          >
                            <Send className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Card: Staff Application — links out to its own page rather
              than embedding the form here (see src/app/staff-application). */}
          <div className={`lg:col-span-2 bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3 ${vis('account')}`}>
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-sp-copper" /> Join the Staff Team
            </h3>
            <p className="text-xs text-text-warm/85 leading-relaxed max-w-xl">
              Interested in helping run iekcorbar as a Moderator? It&apos;s a volunteer role, not a paid job — apply below and the admin team will review it.
            </p>
            {myStaffApplication && (
              <p className="text-[10px] uppercase font-mono font-bold">
                Your latest application:{' '}
                <span className={
                  myStaffApplication.status === 'approved' ? 'text-emerald-400'
                    : myStaffApplication.status === 'declined' ? 'text-rose-400'
                    : 'text-sp-gold'
                }>
                  {myStaffApplication.status === 'approved' ? 'Approved' : myStaffApplication.status === 'declined' ? 'Declined' : 'Pending Review'}
                </span>
              </p>
            )}
            <Link
              href="/staff-application"
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg text-xs font-bold uppercase tracking-wider text-black bg-sp-gold hover:bg-white transition-all cursor-pointer font-serif shadow-md"
            >
              <ShieldCheck className="w-4 h-4" /> {myStaffApplication?.status === 'pending' ? 'View Application Status' : 'Apply for Staff'}
            </Link>
          </div>

      </div>

    </div>
  );
}
