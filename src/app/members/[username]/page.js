'use client';

import React, { useState, useEffect, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useUser, useSiteContent } from '../../providers';
import { humanizePluginStatKey } from '@/lib/plugin-stats';
import { roleBadgeClass } from '@/lib/staff-tabs';
import ProfileHero, { ProfileTabs } from '@/components/ProfileHero';
import {
  ArrowLeft,
  Clock,
  DollarSign,
  Sparkles,
  Skull,
  Layers,
  Workflow,
  Flag,
  AlertTriangle,
  ChevronDown,
  UserPlus,
  UserCheck,
  Users,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  X,
  LayoutGrid,
  BarChart3,
  Trophy,
  Lock,
  Pickaxe,
  Swords,
  CalendarDays
} from 'lucide-react';

// Strips Minecraft colour codes (§a / &a) from recipe names.
const cleanMc = (t) => String(t || '').replace(/[§&][0-9a-fk-or]/gi, '');

function SectionCard({ icon, title, tone = 'text-sp-gold', children }) {
  return (
    <div className="bg-neutral-900/30 border border-sp-bronze/45 rounded-xl p-5 space-y-4">
      <h3 className={`font-serif text-sm ${tone} uppercase tracking-wide border-b border-sp-bronze/20 pb-1.5 flex items-center gap-2`}>
        {icon} {title}
      </h3>
      {children}
    </div>
  );
}

function PrivateNote({ what }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 border border-dashed border-sp-bronze/30 rounded-xl text-text-warm/40 text-xs italic">
      <Lock className="w-3.5 h-3.5" /> This player keeps their {what} private.
    </div>
  );
}

function Tile({ label, value, tone = 'text-white', icon }) {
  return (
    <div className="bg-neutral-950/30 p-4 rounded-xl border border-sp-bronze/15 text-left">
      <span className="flex items-center gap-1.5 text-[9px] uppercase font-bold text-text-warm/40">{icon}{label}</span>
      <span className={`block mt-1 font-serif text-2xl font-black ${tone}`}>{value}</span>
    </div>
  );
}

function PlayerStatsContent({ params: paramsPromise }) {
  const params = React.use(paramsPromise);
  const username = decodeURIComponent(params.username);
  const { user } = useUser();
  const siteContent = useSiteContent();
  const searchParams = useSearchParams();
  const sharedJournalId = searchParams.get('journal');

  const [dbUser, setDbUser] = useState(null);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  // Live stats can be slow (RCON/DB round-trips), so the page renders as soon as
  // the member row is known and the numbers fill in afterwards.
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState(false);

  // Personalisation (bio / banner / hidden sections) and earned achievements
  // — all public-by-design, each loaded non-fatally.
  const [profile, setProfile] = useState({ bio: '', banner: null, hidden: [], bannerImage: null, bannerPos: 50, createdAt: null });
  const [achievements, setAchievements] = useState([]);
  const [tab, setTab] = useState('overview');
  // Staff with the Moderation permission get a "Clear bio" action on profiles.
  const [canModerate, setCanModerate] = useState(false);
  // "Report bio" (anonymous, goes to the Reports Inbox like any report).
  const [bioReport, setBioReport] = useState('idle'); // idle | open | sending | sent | error
  const [bioReportNote, setBioReportNote] = useState('');

  // Friendship state relative to the logged-in viewer
  const [friendRelation, setFriendRelation] = useState(null); // null (loading/logged out) | 'self' | 'friends' | 'incoming' | 'outgoing' | 'none'
  const [friendshipId, setFriendshipId] = useState(null);
  const [friendActionBusy, setFriendActionBusy] = useState(false);
  const [friendError, setFriendError] = useState('');

  // Read-only logbook view (own page or a friend's)
  const [journalEntries, setJournalEntries] = useState([]);
  const [journalLoading, setJournalLoading] = useState(false);
  const [journalIndex, setJournalIndex] = useState(0);
  const [journalFlipDirection, setJournalFlipDirection] = useState(null);
  const [journalOutgoingEntry, setJournalOutgoingEntry] = useState(null);

  useEffect(() => {
    async function fetchData() {
      const q = encodeURIComponent(username);
      // The profile (banner, bio) is fetched in parallel with the member row and
      // awaited before the page appears, so the hero never flashes the default
      // banner and then jumps to the player's own image. Failure just means defaults.
      const profileReq = fetch(`/api/profile/public?username=${q}`).then(r => r.json()).catch(() => null);

      // Fetch user role from members API
      try {
        const res = await fetch('/api/members');
        const data = await res.json();
        if (res.ok) {
          const match = data.users.find(u => u.mcUsername && u.mcUsername.toLowerCase() === username.toLowerCase());
          setDbUser(match || { mcUsername: username, role: 'player' });
        }
      } catch (err) {
        console.error(err);
        setDbUser({ mcUsername: username, role: 'player' });
      }
      const profileData = await profileReq;
      if (profileData && !profileData.error) setProfile(profileData);
      setLoading(false);

      // Everything below is optional — a failure just leaves that section empty.
      fetch(`/api/achievements/earned?username=${q}`)
        .then(r => r.json())
        .then(a => { if (Array.isArray(a.earned)) setAchievements(a.earned); })
        .catch(() => {});

      // Fetch real player stats from the server
      try {
        const statsRes = await fetch(`/api/player-stats?username=${q}`);
        const statsData = await statsRes.json();
        if (statsRes.ok && !statsData.error) {
          setStats(statsData);
        } else {
          setStatsError(true);
        }
      } catch (err) {
        console.error(err);
        setStatsError(true);
      } finally {
        setStatsLoading(false);
      }
    }
    fetchData();
  }, [username]);

  useEffect(() => {
    if (!user) { setCanModerate(false); return; }
    fetch('/api/permissions/me')
      .then(r => r.json())
      .then(d => setCanModerate(Array.isArray(d.tabs) && d.tabs.includes('moderation')))
      .catch(() => setCanModerate(false));
  }, [user]);

  async function submitBioReport() {
    setBioReport('sending');
    try {
      // The bio text is copied into the report: the owner can edit or delete
      // it before staff look, and staff need to see what was actually reported.
      const details = `Reported profile bio: "${profile.bio}"${bioReportNote.trim() ? `\nNote from reporter: ${bioReportNote.trim()}` : ''}`;
      const res = await fetch('/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUsername: username, category: 'Profile bio', details })
      });
      const data = await res.json();
      if (!res.ok || !data.success) throw new Error(data.error || 'failed');
      setBioReport('sent');
      setBioReportNote('');
    } catch {
      setBioReport('error');
    }
  }

  // "Report banner": anonymous report to the Reports Inbox, same route as every report.
  async function reportBanner() {
    const note = window.prompt('Report this banner to staff? You can add a short note (optional):', '');
    if (note === null) return;
    try {
      const details = `Reported profile banner: ${window.location.origin}${profile.bannerImage}${note.trim() ? `\nNote from reporter: ${note.trim()}` : ''}`;
      const res = await fetch('/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUsername: username, category: 'Profile banner', details })
      });
      const data = await res.json();
      if (!res.ok || !data.success) throw new Error(data.error || 'failed');
      window.alert('Thanks — staff will take a look. Your report is anonymous.');
    } catch {
      window.alert('Could not send the report. Please try again.');
    }
  }

  async function clearBio() {
    const reason = window.prompt(`Clear ${username}'s bio? Optionally give a reason (shown to the player):`, '');
    if (reason === null) return; // cancelled
    try {
      const params = new URLSearchParams({ username });
      if (reason.trim()) params.set('reason', reason.trim());
      const res = await fetch(`/api/admin/profile-bios?${params}`, { method: 'DELETE' });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not clear the bio');
      setProfile(p => ({ ...p, bio: '' }));
    } catch (err) {
      window.alert(err.message || 'Could not clear the bio');
    }
  }

  // Determine relationship to the logged-in viewer (self / friends / pending / none)
  useEffect(() => {
    async function fetchRelation() {
      if (!user) { setFriendRelation(null); return; }
      if (user.mcUsername && user.mcUsername.toLowerCase() === username.toLowerCase()) {
        setFriendRelation('self');
        return;
      }
      try {
        const res = await fetch(`/api/friends?email=${encodeURIComponent(user.email)}`);
        const data = await res.json();
        if (res.ok) {
          const match = (list) => (list || []).find(f => f.username && f.username.toLowerCase() === username.toLowerCase());
          const asFriend = match(data.friends);
          const asIncoming = match(data.incoming);
          const asOutgoing = match(data.outgoing);
          if (asFriend) { setFriendRelation('friends'); setFriendshipId(asFriend.id); }
          else if (asIncoming) { setFriendRelation('incoming'); setFriendshipId(asIncoming.id); }
          else if (asOutgoing) { setFriendRelation('outgoing'); setFriendshipId(asOutgoing.id); }
          else { setFriendRelation('none'); setFriendshipId(null); }
        }
      } catch (err) {
        console.error("Failed to load friend relation:", err);
      }
    }
    fetchRelation();
  }, [user, username]);

  // Load the logbook once we know we're allowed to see it (self or friends)
  useEffect(() => {
    async function fetchJournal() {
      if (friendRelation !== 'self' && friendRelation !== 'friends') return;
      setJournalLoading(true);
      try {
        const res = await fetch(`/api/profile/journal?username=${encodeURIComponent(username)}`);
        const data = await res.json();
        if (res.ok && data.entries) {
          setJournalEntries(data.entries);
          const sharedIdx = sharedJournalId ? data.entries.findIndex(e => e.id === sharedJournalId) : -1;
          setJournalIndex(sharedIdx >= 0 ? sharedIdx : Math.max(0, data.entries.length - 1));
          if (sharedIdx >= 0) setTab('logbook'); // a shared logbook link lands on the logbook
        }
      } catch (err) {
        console.error("Failed to load journal:", err);
      } finally {
        setJournalLoading(false);
      }
    }
    fetchJournal();
  }, [friendRelation, username, sharedJournalId]);

  async function handleFriendAction(body) {
    setFriendActionBusy(true);
    setFriendError('');
    try {
      const res = await fetch('/api/friends', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: user.email, ...body })
      });
      const data = await res.json();
      if (res.ok) {
        const match = (list) => (list || []).find(f => f.username && f.username.toLowerCase() === username.toLowerCase());
        const asFriend = match(data.friends);
        const asIncoming = match(data.incoming);
        const asOutgoing = match(data.outgoing);
        if (asFriend) { setFriendRelation('friends'); setFriendshipId(asFriend.id); }
        else if (asIncoming) { setFriendRelation('incoming'); setFriendshipId(asIncoming.id); }
        else if (asOutgoing) { setFriendRelation('outgoing'); setFriendshipId(asOutgoing.id); }
        else { setFriendRelation('none'); setFriendshipId(null); }
      } else {
        setFriendError(data.error || "Something went wrong");
      }
    } catch (err) {
      setFriendError("Network error");
    } finally {
      setFriendActionBusy(false);
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

  const journalDateFormatter = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
  function renderReadOnlyJournalPage(entry) {
    return (
      <div className="h-full flex flex-col">
        <span className="font-serif text-sm text-sp-gold tracking-wide mb-3 shrink-0">
          {journalDateFormatter.format(new Date(entry.entryDate))}
        </span>
        <div className="flex-1 overflow-y-auto pr-1 space-y-3">
          {entry.imageUrl && (
            <img src={entry.imageUrl} alt="Logbook attachment" className="w-full max-h-48 object-cover rounded-lg border border-sp-bronze/30" />
          )}
          <p className="text-sm text-text-warm/85 leading-relaxed whitespace-pre-wrap font-serif">{entry.content}</p>
        </div>
      </div>
    );
  }

  // Derived values from real stats (with safe fallbacks)
  const balance = stats?.balance ?? 0;
  const playtime = stats?.playtimeHours ?? 0;
  const kills = stats?.kills ?? 0;
  const deaths = Math.max(1, stats?.deaths ?? 1);
  const kd = (kills / deaths).toFixed(2);
  const mobKills = stats?.mobKills ?? 0;
  const blocksMined = stats?.blocksMined ?? 0;
  const landsClaimed = stats?.landsClaimed ?? 0;
  const eliteCoins = stats?.eliteCoins ?? 0;
  const jobs = stats?.jobs ?? [];
  const pluginStats = stats?.pluginStats ?? {};
  const lastSeen = stats?.lastSeen ? new Date(stats.lastSeen) : null;

  const role = dbUser?.role || 'player';
  const T = (props) => <Tile {...props} value={statsLoading ? '…' : props.value} />;
  const isSelf = friendRelation === 'self';
  // Hidden sections only apply to visitors — you always see your own full profile.
  const hidden = isSelf ? [] : (profile.hidden || []);
  const isHidden = (key) => hidden.includes(key);
  const canSeeLogbook = friendRelation === 'self' || friendRelation === 'friends';

  const roleLabel = role === 'admin' ? siteContent.role_label_admin : role === 'moderator' ? siteContent.role_label_moderator : (dbUser?.roleLabel || siteContent.role_label_player);

  const chips = [
    { icon: <Clock />, label: 'Playtime', value: statsLoading ? '…' : `${playtime} h` },
    ...(stats?.cmi?.rank ? [{ icon: <Trophy />, label: 'Rank', value: cleanMc(stats.cmi.rank) }] : []),
    ...(profile.createdAt ? [{ icon: <CalendarDays />, label: 'Joined', value: new Date(profile.createdAt).toLocaleDateString(undefined, { month: 'short', year: 'numeric' }) }] : []),
    ...(lastSeen ? [{ icon: <Clock />, label: 'Last seen', value: lastSeen.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) }] : [])
  ];

  const friendActions = user && friendRelation && friendRelation !== 'self' && (
    <>
      {friendRelation === 'none' && (
        <button
          onClick={() => handleFriendAction({ action: 'request', targetUsername: username })}
          disabled={friendActionBusy}
          className="w-full py-2.5 px-4 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center justify-center gap-1.5 transition-all cursor-pointer"
        >
          <UserPlus className="w-3.5 h-3.5" /> Add Friend
        </button>
      )}
      {friendRelation === 'outgoing' && (
        <button
          onClick={() => handleFriendAction({ action: 'cancel', requestId: friendshipId })}
          disabled={friendActionBusy}
          className="w-full py-2.5 px-4 border border-sp-bronze/40 hover:border-crimson text-text-warm/60 hover:text-rose-400 text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center justify-center gap-1.5 transition-all cursor-pointer disabled:opacity-50"
        >
          <X className="w-3.5 h-3.5" /> Cancel Request
        </button>
      )}
      {friendRelation === 'incoming' && (
        <button
          onClick={() => handleFriendAction({ action: 'accept', requestId: friendshipId })}
          disabled={friendActionBusy}
          className="w-full py-2.5 px-4 bg-emerald-600/20 hover:bg-emerald-600/40 border border-emerald-500/40 text-emerald-400 text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center justify-center gap-1.5 transition-all cursor-pointer disabled:opacity-50"
        >
          <UserCheck className="w-3.5 h-3.5" /> Accept Request
        </button>
      )}
      {friendRelation === 'friends' && (
        <button
          onClick={() => { if (confirm(`Remove ${username} from your friends?`)) handleFriendAction({ action: 'remove', friendshipId }); }}
          disabled={friendActionBusy}
          className="w-full py-2.5 px-4 bg-emerald-600/10 border border-emerald-500/30 text-emerald-400 hover:bg-crimson/15 hover:border-crimson hover:text-rose-400 text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center justify-center gap-1.5 transition-all cursor-pointer disabled:opacity-50 group"
        >
          <Users className="w-3.5 h-3.5" /> Friends <span className="group-hover:hidden">✓</span><span className="hidden group-hover:inline">— Remove</span>
        </button>
      )}
      {friendError && <p className="text-[10px] text-rose-400">{friendError}</p>}
    </>
  );

  const tabs = [
    { key: 'overview', label: 'Overview', icon: <LayoutGrid /> },
    { key: 'stats', label: 'Stats', icon: <BarChart3 /> },
    { key: 'achievements', label: 'Achievements', icon: <Trophy />, badge: isHidden('achievements') ? '' : achievements.length },
    ...(canSeeLogbook ? [{ key: 'logbook', label: 'Logbook', icon: <BookOpen />, badge: journalEntries.length || '' }] : [])
  ];
  const activeTab = tabs.some(t => t.key === tab) ? tab : 'overview';

  return (
    <div className="space-y-6">

      {/* Back link */}
      <Link
        href="/members"
        className="inline-flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-text-warm/60 hover:text-sp-gold transition-colors"
      >
        <ArrowLeft className="w-4 h-4" /> All members
      </Link>

      {loading ? (
        <div className="py-20 text-center text-xs text-text-warm/40 italic">Retrieving telemetry data...</div>
      ) : (
        <>
          <ProfileHero
            username={username}
            banner={profile.banner}
            bannerImage={profile.bannerImage}
            bannerPos={profile.bannerPos}
            onReportBanner={!isSelf ? reportBanner : null}
            roleLabel={roleLabel}
            roleClass={roleBadgeClass(role, dbUser?.roleColor)}
            bio={profile.bio}
            bioExtra={!isSelf && (
              <div className="mt-2 text-[11px]">
                {bioReport === 'sent' ? (
                  <span className="text-emerald-400">Thanks — staff will take a look. Your report is anonymous.</span>
                ) : bioReport === 'open' || bioReport === 'sending' || bioReport === 'error' ? (
                  <div className="flex flex-wrap items-center gap-2 max-w-xl">
                    <input
                      type="text"
                      value={bioReportNote}
                      onChange={e => setBioReportNote(e.target.value)}
                      maxLength={200}
                      placeholder="What's wrong with it? (optional)"
                      className="flex-1 min-w-48 bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-1.5 focus:outline-hidden focus:border-sp-gold"
                    />
                    <button type="button" disabled={bioReport === 'sending'} onClick={submitBioReport} className="px-3 py-1.5 rounded-lg bg-rose-900/60 hover:bg-rose-700 border border-rose-700/50 text-rose-100 text-[10px] font-bold uppercase tracking-wider cursor-pointer disabled:opacity-50">
                      {bioReport === 'sending' ? 'Sending…' : 'Send report'}
                    </button>
                    <button type="button" onClick={() => setBioReport('idle')} className="text-text-warm/50 hover:text-white cursor-pointer">Cancel</button>
                    {bioReport === 'error' && <span className="w-full text-rose-400">Could not send the report. Please try again.</span>}
                  </div>
                ) : (
                  <button type="button" onClick={() => setBioReport('open')} className="text-text-warm/40 hover:text-rose-300 underline-offset-2 hover:underline cursor-pointer">
                    Report this bio
                  </button>
                )}
              </div>
            )}
            chips={chips}
            actions={(friendActions || canModerate && profile.bio) && !isSelf ? (
              <>
                {friendActions}
                {canModerate && profile.bio && (
                  <button
                    type="button"
                    onClick={clearBio}
                    className="w-full py-2 px-4 border border-crimson/45 text-rose-300 hover:bg-crimson hover:text-white text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer"
                  >
                    Staff: clear bio
                  </button>
                )}
              </>
            ) : (isSelf ? (
              <Link href="/profile" className="w-full py-2.5 px-4 border border-sp-gold/50 hover:bg-sp-gold hover:text-black text-sp-gold text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center justify-center gap-1.5 transition-all">
                Edit my profile
              </Link>
            ) : null)}
          />

          {statsError && (
            <div className="bg-amber-950/30 border border-amber-700/30 rounded-lg p-3 flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
              <p className="text-[11px] text-amber-300/80 leading-relaxed">
                Live server stats are currently unavailable. The data shown below may be incomplete. Make sure the <strong>IekcorbarSiteSync</strong> plugin is running on the server.
              </p>
            </div>
          )}

          <ProfileTabs tabs={tabs} active={activeTab} onChange={setTab} />

          <div key={activeTab} className="pt-panel space-y-6">

            {/* ---------- OVERVIEW ---------- */}
            {activeTab === 'overview' && (
              <>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                  <T label="Playtime" value={`${playtime} h`} icon={<Clock className="w-3 h-3 text-sp-copper" />} />
                  {!isHidden('combat') && <T label="K/D ratio" value={kd} tone="text-sp-gold" icon={<Swords className="w-3 h-3 text-crimson" />} />}
                  {!isHidden('claims') && <T label="Land claims" value={landsClaimed} icon={<Layers className="w-3 h-3 text-sp-copper" />} />}
                  {!isHidden('economy') && <T label="Yen" value={balance.toLocaleString()} tone="text-sp-gold" icon={<DollarSign className="w-3 h-3 text-sp-gold" />} />}
                  {!isHidden('achievements') && <T label="Achievements" value={achievements.length} icon={<Trophy className="w-3 h-3 text-amber-400" />} />}
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {!isHidden('jobs') && (
                    <SectionCard icon={<Workflow className="w-4 h-4 text-sp-copper" />} title="Active Jobs">
                      <div className="space-y-3 text-left">
                        {jobs.length > 0 ? jobs.map((job, idx) => {
                          const colors = ['text-sp-gold', 'text-sp-copper', 'text-sakura-pink', 'text-emerald-400'];
                          const bgColors = ['bg-sp-gold', 'bg-sp-copper', 'bg-sakura-accent', 'bg-emerald-500'];
                          const pct = Math.min(100, (job.level / 100) * 100);
                          return (
                            <div key={job.name} className="space-y-1">
                              <div className="flex justify-between text-xs font-mono">
                                <span className="text-text-warm/80">{job.name}</span>
                                <span className={`${colors[idx % colors.length]} font-bold`}>Lvl {job.level}</span>
                              </div>
                              <div className="w-full bg-neutral-950 border border-sp-bronze/20 rounded-full h-2.5 overflow-hidden">
                                <div className={`${bgColors[idx % bgColors.length]} h-full rounded-full transition-all duration-500`} style={{ width: `${pct}%` }}></div>
                              </div>
                            </div>
                          );
                        }) : <p className="text-xs text-text-warm/40 italic">No active jobs</p>}
                      </div>
                    </SectionCard>
                  )}

                  {!isHidden('achievements') && (
                    <SectionCard icon={<Trophy className="w-4 h-4 text-amber-400" />} title="Latest Achievements" tone="text-amber-300">
                      {achievements.length === 0 ? (
                        <p className="text-xs text-text-warm/40 italic">No achievements earned yet.</p>
                      ) : (
                        <ul className="space-y-2">
                          {[...achievements].reverse().slice(0, 4).map(a => (
                            <li key={a.id} className="flex items-center gap-3 bg-neutral-950/30 border border-sp-bronze/15 rounded-lg px-3 py-2">
                              <span className="text-xl leading-none">{a.icon || '🏆'}</span>
                              <span className="min-w-0 flex-1">
                                <span className="block text-xs font-bold text-white truncate">{a.title}</span>
                                <span className="block text-[10px] text-text-warm/45 truncate">{a.description}</span>
                              </span>
                            </li>
                          ))}
                        </ul>
                      )}
                      {achievements.length > 4 && (
                        <button type="button" onClick={() => setTab('achievements')} className="text-[10px] font-bold uppercase tracking-wider text-sp-gold hover:text-white cursor-pointer">
                          View all {achievements.length} →
                        </button>
                      )}
                    </SectionCard>
                  )}
                </div>
              </>
            )}

            {/* ---------- STATS ---------- */}
            {activeTab === 'stats' && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {isHidden('combat') ? <PrivateNote what="combat stats" /> : (
                  <SectionCard icon={<Skull className="w-4 h-4 text-crimson" />} title="Combat Logs (PVP & PVE)" tone="text-rose-300">
                    <div className="grid grid-cols-2 gap-4">
                      <T label="Player kills" value={kills} />
                      <T label="Total deaths" value={deaths} />
                      <T label="K/D ratio" value={kd} tone="text-sp-gold" />
                      <T label="Mob kills" value={mobKills.toLocaleString()} />
                    </div>
                  </SectionCard>
                )}

                {isHidden('economy') ? <PrivateNote what="balance" /> : (
                  <SectionCard icon={<DollarSign className="w-4 h-4 text-sp-gold" />} title="Economy Overview">
                    <div className="grid grid-cols-2 gap-4">
                      <T label="Yen balance" value={balance.toLocaleString()} tone="text-sp-gold" />
                      <T label="Elite coins" value={eliteCoins.toLocaleString()} tone="text-amber-400" icon={<Sparkles className="w-3 h-3" />} />
                    </div>
                  </SectionCard>
                )}

                {isHidden('claims') ? <PrivateNote what="claims and mining stats" /> : (
                  <SectionCard icon={<Pickaxe className="w-4 h-4 text-sp-copper" />} title="Claims & Labor" tone="text-sp-copper">
                    <div className="space-y-3 text-left">
                      <div className="flex items-center justify-between border-b border-sp-bronze/10 pb-1 text-xs">
                        <span className="text-text-warm/40 font-mono text-[9px] uppercase font-bold">Mined Blocks</span>
                        <span className="text-white font-bold font-mono">{blocksMined.toLocaleString()}</span>
                      </div>
                      <div className="flex items-center justify-between border-b border-sp-bronze/10 pb-1 text-xs">
                        <span className="text-text-warm/40 font-mono text-[9px] uppercase font-bold">Land Claims</span>
                        <span className="text-white font-bold font-mono">{landsClaimed}</span>
                      </div>
                      {Object.entries(pluginStats).filter(([key]) => key !== 'lands_claims').map(([key, value]) => (
                        <div key={key} className="flex items-center justify-between border-b border-sp-bronze/10 pb-1 text-xs">
                          <span className="text-text-warm/40 font-mono text-[9px] uppercase font-bold">{humanizePluginStatKey(key)}</span>
                          <span className="text-white font-bold font-mono">{value}</span>
                        </div>
                      ))}
                    </div>
                  </SectionCard>
                )}

                {isHidden('jobs') ? <PrivateNote what="jobs" /> : (
                  <SectionCard icon={<Workflow className="w-4 h-4 text-sp-copper" />} title="Active Jobs">
                    <div className="space-y-3 text-left">
                      {jobs.length > 0 ? jobs.map((job, idx) => {
                        const bgColors = ['bg-sp-gold', 'bg-sp-copper', 'bg-sakura-accent', 'bg-emerald-500'];
                        const pct = Math.min(100, (job.level / 100) * 100);
                        return (
                          <div key={job.name} className="space-y-1">
                            <div className="flex justify-between text-xs font-mono">
                              <span className="text-text-warm/80">{job.name}</span>
                              <span className="text-sp-gold font-bold">Lvl {job.level}</span>
                            </div>
                            <div className="w-full bg-neutral-950 border border-sp-bronze/20 rounded-full h-2.5 overflow-hidden">
                              <div className={`${bgColors[idx % bgColors.length]} h-full rounded-full`} style={{ width: `${pct}%` }}></div>
                            </div>
                          </div>
                        );
                      }) : <p className="text-xs text-text-warm/40 italic">No active jobs</p>}
                    </div>
                  </SectionCard>
                )}
              </div>
            )}

            {/* ---------- ACHIEVEMENTS ---------- */}
            {activeTab === 'achievements' && (
              isHidden('achievements') ? <PrivateNote what="achievements" /> : achievements.length === 0 ? (
                <div className="text-center py-14 border border-dashed border-sp-bronze/30 rounded-xl text-text-warm/40 text-xs italic">
                  {isSelf ? "You haven't earned any achievements yet." : `${username} hasn't earned any achievements yet.`}
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                  {achievements.map(a => (
                    <div key={a.id} className="flex items-start gap-3 bg-neutral-900/40 border border-sp-bronze/40 rounded-xl p-4">
                      <span className="text-3xl leading-none">{a.icon || '🏆'}</span>
                      <span className="min-w-0">
                        <span className="block font-serif font-bold text-white">{a.title}</span>
                        <span className="block text-[11px] text-text-warm/55 mt-0.5">{a.description}</span>
                        {a.earnedAt && <span className="block text-[9px] uppercase font-bold text-sp-copper mt-1.5">{new Date(a.earnedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}</span>}
                      </span>
                    </div>
                  ))}
                </div>
              )
            )}

            {/* ---------- LOGBOOK (own page, or a friend's) ---------- */}
            {activeTab === 'logbook' && canSeeLogbook && (
              <SectionCard icon={<BookOpen className="w-4 h-4 text-sp-copper" />} title={friendRelation === 'self' ? 'Your Logbook' : `${username}'s Logbook`}>
                {journalLoading ? (
                  <div className="text-xs text-text-warm/40 italic text-center py-8">Retrieving logbook pages...</div>
                ) : journalEntries.length === 0 ? (
                  <div className="text-center py-10 border border-dashed border-sp-bronze/30 rounded-xl text-text-warm/40 text-xs italic">
                    {friendRelation === 'self' ? 'Your logbook is empty. Write your first page from your profile.' : `${username} hasn't written any logbook pages yet.`}
                  </div>
                ) : (
                  <div className="journal-book relative mx-auto" style={{ maxWidth: '520px' }}>
                    <div className="journal-page-edge -left-1" />
                    <div className="journal-page-edge -right-1" />
                    <div className="journal-page-stack min-h-[300px] bg-neutral-950/50 border-2 border-sp-bronze/50 rounded-lg p-6 relative overflow-hidden shadow-inner">
                      <div key={journalEntries[journalIndex].id} className="journal-page-enter h-full">
                        {renderReadOnlyJournalPage(journalEntries[journalIndex])}
                      </div>
                      {journalFlipDirection && journalOutgoingEntry && (
                        <div className={`bg-neutral-950/95 p-6 ${journalFlipDirection === 'next' ? 'journal-page-flip-next' : 'journal-page-flip-prev'}`}>
                          {renderReadOnlyJournalPage(journalOutgoingEntry)}
                        </div>
                      )}
                    </div>
                    <div className="flex items-center justify-between mt-3">
                      <button
                        onClick={() => journalFlipTo(journalIndex - 1, 'prev')}
                        disabled={journalIndex <= 0}
                        className="p-2 border border-sp-bronze/40 hover:border-sp-gold rounded-lg text-text-warm/60 hover:text-sp-gold disabled:opacity-25 disabled:pointer-events-none transition-all cursor-pointer"
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
                      >
                        <ChevronRight className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                )}
              </SectionCard>
            )}

          </div>

          {/* ANONYMOUS REPORT SECTION (never on your own profile) */}
          {!isSelf && <ReportForm targetUsername={username} />}
        </>
      )}

    </div>
  );
}

export default function PlayerStatsPage({ params: paramsPromise }) {
  return (
    <Suspense fallback={
      <div className="py-20 text-center text-xs text-text-warm/40 italic">Retrieving telemetry data...</div>
    }>
      <PlayerStatsContent params={paramsPromise} />
    </Suspense>
  );
}

function ReportForm({ targetUsername }) {
  const [open, setOpen] = useState(true);
  const [category, setCategory] = useState('Hacking');
  const [details, setDetails] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!details.trim()) return;
    setSubmitting(true);
    setError('');
    try {
      const res = await fetch('/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUsername, category, details })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setSubmitted(true);
        setDetails('');
      } else {
        setError(data.error || 'Failed to submit report');
      }
    } catch {
      setError('Network error. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mt-6 bg-neutral-950/60 border border-sp-bronze/20 rounded-xl overflow-hidden">
      <button
        onClick={() => { setOpen(!open); setSubmitted(false); }}
        className="w-full flex items-center justify-between px-5 py-4 text-left hover:bg-neutral-900/30 transition-all cursor-pointer group"
      >
        <div className="flex items-center gap-2.5">
          <Flag className="w-4 h-4 text-rose-400 shrink-0" />
          <span className="text-xs font-bold uppercase tracking-wider text-text-warm/60 group-hover:text-rose-300 transition-colors">Report this player anonymously</span>
        </div>
        <ChevronDown className={`w-4 h-4 text-text-warm/30 transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="border-t border-sp-bronze/10 px-5 pb-5 pt-4 space-y-4">
          <div className="flex items-start gap-2.5 bg-amber-950/30 border border-amber-700/30 rounded-lg p-3">
            <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <p className="text-[11px] text-amber-300/80 leading-relaxed">
              This report is <strong>100% anonymous</strong>. Your identity will never be stored or shown to staff. Only use this for genuine rule violations.
            </p>
          </div>

          {submitted ? (
            <div className="text-center py-6 space-y-2">
              <div className="w-12 h-12 mx-auto rounded-full bg-green-900/30 border border-green-500/40 flex items-center justify-center">
                <Flag className="w-6 h-6 text-green-400" />
              </div>
              <p className="text-sm font-bold text-green-400">Report submitted successfully</p>
              <p className="text-xs text-text-warm/40">Staff will review your report. Thank you for helping keep Iekcorbar safe.</p>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Violation Category</label>
                <select
                  value={category}
                  onChange={e => setCategory(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-none focus:border-sp-gold cursor-pointer"
                >
                  <option>Hacking</option>
                  <option>Harassment</option>
                  <option>Griefing</option>
                  <option>Spam / Advertising</option>
                  <option>Other</option>
                </select>
              </div>
              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Details <span className="text-text-warm/40">(min. 20 chars)</span></label>
                <textarea
                  required
                  minLength={20}
                  rows={4}
                  value={details}
                  onChange={e => setDetails(e.target.value)}
                  placeholder="Describe what happened, when, and where on the server..."
                  className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-none focus:border-sp-gold leading-relaxed resize-none"
                />
              </div>
              {error && <p className="text-xs text-rose-400">{error}</p>}
              <button
                type="submit"
                disabled={submitting || details.trim().length < 20}
                className="w-full py-2.5 bg-rose-900/60 hover:bg-rose-700 border border-rose-700/50 hover:border-rose-500 text-rose-200 hover:text-white font-bold uppercase text-xs rounded-lg transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                <Flag className="w-3.5 h-3.5" />
                {submitting ? 'Submitting...' : 'Submit Anonymous Report'}
              </button>
            </form>
          )}
        </div>
      )}
    </div>
  );
}
