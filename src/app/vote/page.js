'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { useUser } from '../providers';
import { ThumbsUp, ExternalLink, Clock, CheckCircle, RefreshCw, Sparkles, AlertTriangle, LogIn, Settings, Plus, Trash2, Save, ShieldCheck } from 'lucide-react';

const VOTE_COOLDOWN_MS = 24 * 60 * 60 * 1000; // 24 hours

function CountdownTimer({ lastVotedAt }) {
  const [timeLeft, setTimeLeft] = useState('');
  const [canVote, setCanVote] = useState(false);

  const compute = useCallback(() => {
    if (!lastVotedAt) { setCanVote(true); setTimeLeft(''); return; }
    const elapsed = Date.now() - new Date(lastVotedAt).getTime();
    const remaining = VOTE_COOLDOWN_MS - elapsed;
    if (remaining <= 0) { setCanVote(true); setTimeLeft(''); return; }
    setCanVote(false);
    const h = Math.floor(remaining / 3600000);
    const m = Math.floor((remaining % 3600000) / 60000);
    const s = Math.floor((remaining % 60000) / 1000);
    setTimeLeft(`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`);
  }, [lastVotedAt]);

  useEffect(() => {
    compute();
    const timer = setInterval(compute, 1000);
    return () => clearInterval(timer);
  }, [compute]);

  if (canVote) return <span className="text-xs text-emerald-400 font-bold flex items-center gap-1"><CheckCircle className="w-3.5 h-3.5" /> Ready to vote!</span>;
  return <span className="text-xs text-amber-400 font-mono flex items-center gap-1"><Clock className="w-3.5 h-3.5" /> {timeLeft}</span>;
}

export default function VotePage() {
  // Was reading localStorage directly on mount instead of the shared
  // useUser() context — meant this page never noticed a login/logout that
  // happened after the initial render, and had no way to open the login
  // modal (no setIsLoginOpen), so the "Log in" hint below was just static
  // text with nothing to click.
  const { user, setIsLoginOpen } = useUser();
  const [sites, setSites] = useState([]);
  const [userLog, setUserLog] = useState([]);
  const [loading, setLoading] = useState(true);
  const [voteFeedback, setVoteFeedback] = useState(null); // { type: 'success'|'error', message }

  useEffect(() => {
    fetch('/api/vote/sites')
      .then(r => r.json())
      .then(d => { if (d.sites) setSites(d.sites); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!user?.mcUsername) return;
    fetch(`/api/vote/log?username=${encodeURIComponent(user.mcUsername)}`)
      .then(r => r.json())
      .then(d => { if (d.log) setUserLog(d.log); })
      .catch(console.error);
  }, [user]);

  // 2026-08-13: Direct Voting (a one-click, no-redirect vote sent straight
  // from the website) was removed by explicit user decision — voting here
  // was never tied to any real external server-list ranking (the whole
  // point of voting), so it only ever benefited the player, not the server.
  // Only real external-site voting remains. Rewards are handled entirely by
  // VoteSpeed itself now too (see below) — this page no longer dispatches
  // any reward of its own.

  // Matched by site *name*, not id — vote_log rows only ever stored
  // site_name (see db.js's logVote), but this used to compare against
  // v.siteId, a field that never existed on a vote_log row at all. That
  // silently made every cooldown check `undefined === "1"` → always false
  // → always "no vote found" → the countdown never worked, every site
  // always showed "Ready to vote!" regardless of real history. Found and
  // fixed in the same pass as the reward-duplication cleanup below.
  const getLastVote = (site) => {
    const entries = userLog.filter(v => v.siteName === site.name);
    if (!entries.length) return null;
    return entries.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))[0].timestamp;
  };

  const canVoteOnSite = (site) => {
    const last = getLastVote(site);
    if (!last) return true;
    return Date.now() - new Date(last).getTime() >= VOTE_COOLDOWN_MS;
  };

  const handleVoteClick = async (site) => {
    window.open(site.url, '_blank', 'noopener,noreferrer');
    if (!user?.mcUsername) return;
    try {
      const res = await fetch('/api/vote/log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: user.mcUsername, siteId: site.id })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        if (data.log) setUserLog(data.log);
        setVoteFeedback({ type: 'success', message: `Opened ${site.name} — once your vote lands there, the server verifies it automatically and sends your reward.` });
      } else {
        setVoteFeedback({ type: 'error', message: data.error || 'Failed to record your vote' });
      }
    } catch (err) {
      setVoteFeedback({ type: 'error', message: 'Network error while recording your vote' });
    } finally {
      setTimeout(() => setVoteFeedback(null), 8000);
    }
  };

  const totalVotes = userLog.length;

  return (
    <div className="space-y-8 pb-12">
      <title>Vote — Iekcorbar</title>

      {/* Header */}
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-violet-950/20 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-violet-400/40 bg-violet-900/20 text-violet-300 text-xs font-semibold uppercase tracking-wider">
            <ThumbsUp className="w-3.5 h-3.5" /> Vote for our server
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-widest uppercase steampunk-glow">
            Vote &amp; Support
          </h1>
          <p className="text-text-warm/70 max-w-xl mx-auto text-sm">
            {!loading && sites.length === 0
              ? "We're currently rebuilding our vote-site listings — see below for details."
              : "Every vote below is a real vote on that site's own public ranking — this is what actually helps Iekcorbar climb the charts and get discovered by new players. Vote once every 24 hours on each site."}
          </p>
          {user && (
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-sp-gold/10 border border-sp-gold/30 rounded-full text-sp-gold text-xs font-bold">
              <ThumbsUp className="w-3.5 h-3.5" /> {totalVotes} total votes cast as {user.mcUsername}
            </div>
          )}
        </div>
      </section>

      {voteFeedback && (
        <div className={`border rounded-xl px-4 py-3 text-sm flex items-center gap-2 ${
          voteFeedback.type === 'success'
            ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-300'
            : 'bg-crimson/10 border-crimson/40 text-rose-300'
        }`}>
          {voteFeedback.type === 'success' ? <ShieldCheck className="w-4 h-4" /> : <AlertTriangle className="w-4 h-4" />}
          {voteFeedback.message}
        </div>
      )}

      {!user && (
        <div className="bg-neutral-900/60 border border-sp-bronze/40 rounded-xl p-4 flex flex-col sm:flex-row items-center justify-center gap-3 text-center text-sm text-text-warm/60">
          <span>💡 Log in to track your vote cooldowns and receive in-game rewards automatically.</span>
          <button
            onClick={() => setIsLoginOpen(true)}
            className="shrink-0 px-4 py-2 bg-sp-gold hover:bg-white text-black text-xs font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer flex items-center gap-1.5"
          >
            <LogIn className="w-3.5 h-3.5" /> Log In / Register
          </button>
        </div>
      )}

      {/* Under Construction notice — derived straight from there being zero
          configured vote sites (Settings > Vote Sites), not a separate
          hardcoded flag, so it disappears on its own the moment staff add
          real sites back rather than needing someone to remember to turn
          it off. The external vote sites were pulled while they're being
          redone from scratch (2026-09-13). */}
      {!loading && sites.length === 0 && (
        <div className="bg-amber-500/10 border-2 border-amber-500/40 rounded-2xl p-8 text-center space-y-3">
          <div className="w-14 h-14 rounded-full bg-amber-500/15 border border-amber-500/40 flex items-center justify-center mx-auto">
            <Settings className="w-7 h-7 text-amber-400" />
          </div>
          <h2 className="font-serif text-xl text-amber-300 uppercase tracking-wide">Voting Under Construction</h2>
          <p className="text-sm text-text-warm/70 max-w-lg mx-auto leading-relaxed">
            We're rebuilding our external vote-site listings from scratch, so voting doesn't work here right now — there's nothing wrong with your account, there's simply nothing to vote on yet.
          </p>
          <p className="text-xs text-text-warm/40 max-w-lg mx-auto">
            This page will fill back up with real vote sites (and rewards will resume automatically) the moment they're ready. No action needed on your end — check back soon or watch our Discord for the announcement.
          </p>
        </div>
      )}

      {/* Vote Sites Grid */}
      {loading ? (
        <div className="text-center py-16 text-text-warm/40 italic">Loading vote sites...</div>
      ) : sites.length === 0 ? null : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {sites.map((site) => {
            const lastVoted = user ? getLastVote(site) : null;
            const ready = user ? canVoteOnSite(site) : true;

            return (
              <div key={site.id} className={`group bg-neutral-900/60 border-2 rounded-2xl p-6 steampunk-glass flex flex-col gap-4 transition-all ${ready ? 'border-sp-bronze/60 hover:border-sp-gold' : 'border-sp-bronze/20 opacity-80'}`}>
                {/* Icon & Name */}
                <div className="flex items-center gap-3">
                  <span className="text-3xl">{site.icon || '🗳️'}</span>
                  <div>
                    <h2 className="font-serif text-base text-white font-bold uppercase tracking-wide">{site.name}</h2>
                    <p className="text-[10px] text-text-warm/40 font-mono truncate max-w-[160px]">{site.url}</p>
                  </div>
                </div>

                {/* Countdown or ready state */}
                {user ? (
                  <CountdownTimer lastVotedAt={lastVoted} />
                ) : (
                  <span className="text-xs text-text-warm/40 italic">Log in to track cooldown</span>
                )}

                {/* Vote Button */}
                <button
                  onClick={() => handleVoteClick(site)}
                  className={`w-full py-3 rounded-xl font-serif font-bold uppercase tracking-wider text-sm flex items-center justify-center gap-2 transition-all border-2 cursor-pointer
                    ${ready
                      ? 'bg-sp-gold hover:bg-white text-black border-sp-gold hover:border-white shadow-lg shadow-sp-gold/20'
                      : 'bg-neutral-800/40 text-text-warm/30 border-sp-bronze/20 cursor-not-allowed'
                    }`}
                  disabled={!ready}
                >
                  <ExternalLink className="w-4 h-4" />
                  {ready ? 'Vote Now' : 'Voted'}
                </button>
              </div>
            );
          })}
        </div>
      )}

      {/* How It Works — hidden while there's nothing to vote on yet (see the
          Under Construction notice above), since it walks through a flow
          that isn't actually running right now. */}
      {sites.length > 0 && (
      <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-6 space-y-4">
        <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wider flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-sp-copper" /> How Vote Rewards Work
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs text-text-warm/70">
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">1</div>
            <div>
              <span className="block text-white font-semibold text-sm">Click "Vote Now"</span>
              You&apos;re sent to that site&apos;s real vote page — this is a real vote for Iekcorbar on their public ranking.
            </div>
          </div>
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">2</div>
            <div>
              <span className="block text-white font-semibold text-sm">The server verifies it, for real</span>
              The moment that site confirms your vote, our Minecraft server receives it directly and checks it — not an honor system.
            </div>
          </div>
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">3</div>
            <div>
              <span className="block text-white font-semibold text-sm">Reward lands automatically</span>
              No claim button needed — it&apos;s delivered straight to your inventory once you&apos;re online in-game.
            </div>
          </div>
        </div>
      </div>
      )}

      {user?.role === 'admin' && <StaffRewardsEditor />}
    </div>
  );
}

// ---------- staff: live VoteSpeed rewards editor ----------
// Admin-only real remote editor for VoteSpeed's actual reward config — see
// api/vote/rewards/route.js + WebRewardsCommand.java. Edits the "default"
// reward (applies to every vote) and the vote-count milestones list.

function commandsToText(commands) {
  return (commands || []).join('\n');
}
function textToCommands(text) {
  return text.split('\n').map(s => s.trim()).filter(Boolean);
}

function StaffRewardsEditor() {
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [defaultReward, setDefaultReward] = useState({ commandsText: '', playerMessage: '', broadcastMessage: '', chance: 100 });
  const [milestones, setMilestones] = useState([]); // [{ key, votes, commandsText }]
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState(null); // { type, message }

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const res = await fetch('/api/vote/rewards');
      const data = await res.json();
      if (res.ok && data.success) {
        setDefaultReward({
          commandsText: commandsToText(data.default.commands),
          playerMessage: data.default.playerMessage || '',
          broadcastMessage: data.default.broadcastMessage || '',
          chance: data.default.chance ?? 100
        });
        setMilestones((data.milestones || []).map(m => ({ key: m.key, votes: m.votes, commandsText: commandsToText(m.commands) })));
      } else {
        setLoadError(data.error || 'Failed to load live reward config');
      }
    } catch (err) {
      setLoadError('Network error while loading live reward config');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const addMilestone = () => setMilestones(prev => [...prev, { key: 'new_milestone', votes: 10, commandsText: '' }]);
  const removeMilestone = (i) => setMilestones(prev => prev.filter((_, idx) => idx !== i));
  const updateMilestone = (i, patch) => setMilestones(prev => prev.map((m, idx) => idx === i ? { ...m, ...patch } : m));

  const handleSave = async () => {
    setSaving(true);
    setFeedback(null);
    try {
      const body = {
        default: {
          commands: textToCommands(defaultReward.commandsText),
          playerMessage: defaultReward.playerMessage,
          broadcastMessage: defaultReward.broadcastMessage,
          chance: parseInt(defaultReward.chance) || 0
        },
        milestones: milestones.map(m => ({
          key: m.key.trim().toLowerCase().replace(/\s+/g, '_'),
          votes: parseInt(m.votes) || 0,
          commands: textToCommands(m.commandsText)
        }))
      };
      const res = await fetch('/api/vote/rewards', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setFeedback({ type: 'success', message: 'Saved to VoteSpeed! (Original files backed up with a .bak-<timestamp> copy — a reload was attempted automatically, but verify in-game or restart if changes don\'t seem to apply.)' });
        load();
      } else {
        setFeedback({ type: 'error', message: data.error || 'Failed to save' });
      }
    } catch (err) {
      setFeedback({ type: 'error', message: 'Network error while saving' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-sp-dark/60 border-2 border-sp-gold/30 rounded-xl p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wider flex items-center gap-2">
          <Settings className="w-5 h-5 text-sp-copper" /> Staff: Live VoteSpeed Rewards
        </h3>
        <button onClick={load} disabled={loading} className="text-text-warm/40 hover:text-sp-gold cursor-pointer">
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>
      <p className="text-[11px] text-text-warm/40">
        Edits VoteSpeed&apos;s real config files on the live server via RCON. Note: saving strips YAML comments from those files (a timestamped backup is made first automatically).
      </p>

      {loading ? (
        <div className="text-xs text-text-warm/40 italic py-6 text-center">Loading live config...</div>
      ) : loadError ? (
        <div className="bg-crimson/10 border border-crimson/30 rounded-lg px-4 py-3 text-xs text-rose-300 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" /> {loadError}
        </div>
      ) : (
        <>
          <div className="space-y-2">
            <h4 className="text-xs font-bold text-sp-gold uppercase tracking-wide">Default Reward (every vote)</h4>
            <div>
              <label className="text-[10px] text-text-warm/40 uppercase">Commands (one per line, %player% placeholder)</label>
              <textarea
                value={defaultReward.commandsText}
                onChange={e => setDefaultReward(r => ({ ...r, commandsText: e.target.value }))}
                rows={3}
                className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2 font-mono focus:outline-hidden focus:border-sp-gold"
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <div>
                <label className="text-[10px] text-text-warm/40 uppercase">Player Message</label>
                <input value={defaultReward.playerMessage} onChange={e => setDefaultReward(r => ({ ...r, playerMessage: e.target.value }))} className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold" />
              </div>
              <div>
                <label className="text-[10px] text-text-warm/40 uppercase">Chance (%)</label>
                <input type="number" min="0" max="100" value={defaultReward.chance} onChange={e => setDefaultReward(r => ({ ...r, chance: e.target.value }))} className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold" />
              </div>
            </div>
          </div>

          <div className="space-y-2 border-t border-sp-bronze/20 pt-4">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold text-sp-gold uppercase tracking-wide">Vote Milestones</h4>
              <button onClick={addMilestone} className="flex items-center gap-1 text-[10px] text-sp-gold hover:text-white cursor-pointer">
                <Plus className="w-3.5 h-3.5" /> Add
              </button>
            </div>
            {milestones.map((m, i) => (
              <div key={i} className="border border-sp-bronze/20 rounded-lg p-3 space-y-1.5">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 items-end">
                  <div>
                    <label className="text-[9px] text-text-warm/40 uppercase">Key</label>
                    <input value={m.key} onChange={e => updateMilestone(i, { key: e.target.value })} className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-2 py-1.5" />
                  </div>
                  <div>
                    <label className="text-[9px] text-text-warm/40 uppercase">Votes Needed</label>
                    <input type="number" value={m.votes} onChange={e => updateMilestone(i, { votes: e.target.value })} className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-2 py-1.5" />
                  </div>
                  <button onClick={() => removeMilestone(i)} className="flex items-center justify-center gap-1 text-[10px] text-rose-400/70 hover:text-rose-400 border border-crimson/20 hover:bg-crimson/10 rounded-lg py-1.5 cursor-pointer">
                    <Trash2 className="w-3.5 h-3.5" /> Remove
                  </button>
                </div>
                <div>
                  <label className="text-[9px] text-text-warm/40 uppercase">Commands (one per line)</label>
                  <textarea value={m.commandsText} onChange={e => updateMilestone(i, { commandsText: e.target.value })} rows={2} className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-2 py-1.5 font-mono" />
                </div>
              </div>
            ))}
            {milestones.length === 0 && <p className="text-[10px] text-text-warm/30 italic">No milestones configured.</p>}
          </div>

          {feedback && (
            <div className={`rounded-lg px-4 py-3 text-xs flex items-start gap-2 ${
              feedback.type === 'success' ? 'bg-emerald-500/10 border border-emerald-500/30 text-emerald-300' : 'bg-crimson/10 border border-crimson/30 text-rose-300'
            }`}>
              {feedback.type === 'success' ? <CheckCircle className="w-4 h-4 shrink-0 mt-0.5" /> : <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />}
              <span>{feedback.message}</span>
            </div>
          )}

          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 px-5 py-2.5 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-xs font-bold uppercase tracking-wider rounded-lg cursor-pointer transition-all"
          >
            {saving ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
            {saving ? 'Saving...' : 'Save to Live Server'}
          </button>
        </>
      )}
    </div>
  );
}
