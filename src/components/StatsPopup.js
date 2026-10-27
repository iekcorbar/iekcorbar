'use client';

import React, { useEffect, useState } from 'react';
import { humanizePluginStatKey } from '@/lib/plugin-stats';
import {
  DollarSign,
  Sparkles,
  Clock,
  Skull,
  Layers,
  Workflow,
  Pickaxe,
  Home,
  Swords,
  RefreshCw,
  AlertTriangle,
  X,
} from 'lucide-react';

// Decorative brass gear, same generated-teeth trick as ProfileHero's banner gears.
function gearPath(teeth, rOut, rIn, hole) {
  const step = (Math.PI * 2) / teeth;
  const o = [-0.3, -0.17, 0.17, 0.3];
  const pts = [];
  for (let i = 0; i < teeth; i++) {
    const a = i * step;
    pts.push([rIn, a + o[0] * step], [rOut, a + o[1] * step], [rOut, a + o[2] * step], [rIn, a + o[3] * step]);
  }
  const outline = pts.map(([r, t], i) => `${i ? 'L' : 'M'}${(50 + r * Math.cos(t)).toFixed(2)} ${(50 + r * Math.sin(t)).toFixed(2)}`).join('') + 'Z';
  const bore = `M${50 + hole} 50A${hole} ${hole} 0 1 0 ${50 - hole} 50A${hole} ${hole} 0 1 0 ${50 + hole} 50Z`;
  return outline + bore;
}

function Gear({ size, teeth, className, style }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} className={`absolute ${className}`} style={style} aria-hidden="true">
      <path d={gearPath(teeth, 48, 39, 12)} fill="currentColor" fillRule="evenodd" />
    </svg>
  );
}

function Tile({ icon, label, value, tone = 'text-white' }) {
  return (
    <div className="bg-neutral-950/30 p-3 rounded-lg border border-sp-bronze/10 text-left">
      <span className="flex items-center gap-1.5 block text-[8px] uppercase font-bold text-text-warm/40">{icon}{label}</span>
      <span className={`font-serif text-base font-black ${tone}`}>{value}</span>
    </div>
  );
}

/**
 * "My Live Stats" popup for /profile. One steampunk display case with the
 * player's animated skin model beside their full in-game telemetry. Closes on
 * a click next to it (the backdrop), on X and on Escape - same overlay idiom
 * as the chat/login modals, same sp-* tokens as the rest of the site. Works
 * for every role: staff accounts get the very same deck as players.
 */
export default function StatsPopup({ open, onClose, username, roleLabel, rank, gameStats, statsLoading, onRefresh }) {
  const [imgFailed, setImgFailed] = useState(false);

  // Escape closes, and lock page scroll while the deck is up (the popup scrolls itself).
  useEffect(() => {
    if (!open) return;
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = prev; };
  }, [open, onClose]);

  if (!open) return null;

  const s = gameStats;
  const kills = s?.kills ?? 0;
  const deaths = s?.deaths ?? 0;
  const kd = (kills / Math.max(1, deaths)).toFixed(2);
  const playtime = s?.cmi?.totalPlayTimeHours ?? s?.playtimeHours ?? 0;
  const online = s?.online === true;

  return (
    <div className="fixed inset-0 bg-black/80 flex z-50 p-4 backdrop-blur-sm overflow-y-auto animate-fade-in" onClick={onClose} role="dialog" aria-modal="true" aria-label={`${username}'s live statistics`}>
      <div
        className="m-auto w-full max-w-3xl bg-sp-dark border-2 border-sp-gold rounded-xl steampunk-border shadow-2xl animate-modal-pop"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between gap-3 border-b border-sp-bronze/30 px-5 py-4">
          <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2 min-w-0">
            <Sparkles className="w-5 h-5 text-amber-400 shrink-0" />
            <span className="truncate">Live Telemetry — {username}</span>
            {rank && <span className="hidden sm:inline text-[9px] font-mono normal-case bg-sp-gold/10 border border-sp-gold/30 text-sp-gold px-2 py-0.5 rounded-full shrink-0">{rank}</span>}
          </h2>
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={onRefresh}
              disabled={statsLoading}
              className="p-2 rounded-lg border border-sp-bronze/40 hover:border-sp-gold text-text-warm/60 hover:text-sp-gold disabled:opacity-40 transition-all cursor-pointer"
              title="Refresh from the server"
            >
              <RefreshCw className={`w-4 h-4 ${statsLoading ? 'animate-spin' : ''}`} />
            </button>
            <button type="button" onClick={onClose} className="p-2 rounded-lg text-text-warm/60 hover:text-white cursor-pointer" aria-label="Close">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="p-5 grid grid-cols-1 md:grid-cols-[210px_1fr] gap-5">
          {/* Animated model on a brass display pedestal */}
          <div className="stats-case relative mx-auto w-full max-w-[210px] rounded-xl border-2 border-sp-bronze/60 bg-gradient-to-b from-neutral-950/70 via-neutral-900/50 to-neutral-950/80 overflow-hidden" style={{ height: '300px' }}>
            <Gear size={90} teeth={9} className="text-sp-gold/10 animate-spin-gear-cw" style={{ top: '-18px', left: '-16px' }} />
            <Gear size={64} teeth={12} className="text-sp-copper/10 animate-spin-gear-ccw" style={{ top: '34px', right: '-14px' }} />
            <Gear size={46} teeth={8} className="text-sp-bronze/15 animate-spin-gear-cw" style={{ bottom: '58px', left: '8px', animationDuration: '20s' }} />

            <div className="absolute inset-x-0 top-8 flex justify-center animate-model-bob">
              <div className="animate-model-sway" style={{ perspective: '700px' }}>
                <img
                  src={`https://mc-heads.net/body/${encodeURIComponent(imgFailed ? 'Steve' : username)}`}
                  alt={`${username}'s skin`}
                  draggable={false}
                  onError={(e) => { e.target.onerror = null; setImgFailed(true); }}
                  className="h-44 w-auto select-none drop-shadow-[0_10px_14px_rgba(0,0,0,0.8)]"
                  style={{ imageRendering: 'pixelated' }}
                />
              </div>
            </div>

            {/* steam wisps rising off the pedestal */}
            <span className="sp-steam absolute bottom-9 left-9 h-6 w-2 rounded-full bg-white/20 blur-[2px] animate-steam-rise" />
            <span className="sp-steam absolute bottom-8 left-1/2 h-7 w-2.5 rounded-full bg-white/15 blur-[2px] animate-steam-rise" style={{ animationDelay: '1.3s' }} />
            <span className="sp-steam absolute bottom-9 right-9 h-5 w-2 rounded-full bg-white/20 blur-[2px] animate-steam-rise" style={{ animationDelay: '2.6s' }} />

            <div className="absolute bottom-5 left-1/2 -translate-x-1/2 h-3 w-32 rounded-[50%] bg-gradient-to-b from-sp-gold/40 to-sp-bronze/20 blur-[1px]" />
            <div className="absolute bottom-0 inset-x-0 border-t border-sp-bronze/40 bg-neutral-950/80 px-3 py-2 flex items-center justify-between gap-2">
              <span className="text-[9px] uppercase font-bold tracking-wider text-text-warm/60 truncate">{username}</span>
              {s && (
                <span className="flex items-center gap-1 text-[8px] uppercase font-bold shrink-0">
                  <span className={`w-1.5 h-1.5 rounded-full ${online ? 'bg-emerald-400 animate-pulse' : 'bg-neutral-500'}`} />
                  <span className={online ? 'text-emerald-400' : 'text-text-warm/40'}>{online ? 'Online' : 'Offline'}</span>
                </span>
              )}
            </div>
          </div>

          {/* The numbers */}
          <div className="space-y-3 text-left min-w-0">
            {statsLoading && !s ? (
              <div className="text-xs text-text-warm/40 italic py-10 text-center">Retrieving server telemetry...</div>
            ) : !s ? (
              <div className="flex items-start gap-2.5 bg-amber-950/30 border border-amber-700/30 rounded-lg p-3">
                <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                <p className="text-[11px] text-amber-300/80 leading-relaxed">
                  The server did not answer, so there is no live data to show. Try the refresh button above —
                  stats appear as soon as <strong>IekcorbarSiteSync</strong> answers again.
                </p>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 gap-2">
                  <Tile icon={<DollarSign className="w-3 h-3 text-sp-gold" />} label="Yen Balance" value={(s.balance ?? 0).toLocaleString()} tone="text-sp-gold" />
                  <Tile icon={<Sparkles className="w-3 h-3 text-amber-400" />} label="Elite Coins" value={(s.eliteCoins ?? 0).toLocaleString()} tone="text-amber-400" />
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <Tile icon={<Clock className="w-3 h-3 text-sp-copper" />} label="Playtime" value={`${playtime} h`} />
                  <Tile icon={<Swords className="w-3 h-3 text-crimson" />} label="K/D" value={kd} tone="text-sp-gold" />
                  <Tile icon={<Skull className="w-3 h-3 text-rose-400" />} label="Deaths" value={deaths} />
                  <Tile icon={<Skull className="w-3 h-3 text-rose-300" />} label="Player Kills" value={kills} />
                  <Tile icon={<Skull className="w-3 h-3 text-text-warm/50" />} label="Mob Kills" value={(s.mobKills ?? 0).toLocaleString()} />
                  <Tile icon={<Pickaxe className="w-3 h-3 text-sp-copper" />} label="Blocks Mined" value={(s.blocksMined ?? 0).toLocaleString()} />
                  <Tile icon={<Layers className="w-3 h-3 text-sp-copper" />} label="Land Claims" value={s.landsClaimed ?? 0} />
                  {s.cmi && <Tile icon={<Home className="w-3 h-3 text-sp-gold" />} label="Homes Set" value={s.cmi.homesCount} />}
                  {roleLabel && <Tile icon={<Sparkles className="w-3 h-3 text-sakura-pink" />} label="Rank" value={String(rank || roleLabel)} tone="text-sakura-pink" />}
                </div>

                {s.jobs && s.jobs.length > 0 && (
                  <div className="space-y-1.5">
                    <span className="block text-[9px] uppercase font-bold text-text-warm/40 tracking-wider">Active Jobs</span>
                    {s.jobs.map((job) => {
                      const hasXp = typeof job.experience === 'number' && typeof job.maxExperience === 'number' && job.maxExperience > 0;
                      const xpPct = hasXp ? Math.max(0, Math.min(100, (job.experience / job.maxExperience) * 100)) : null;
                      return (
                        <div key={job.name} className="bg-neutral-950/20 px-3 py-1.5 rounded-lg border border-sp-bronze/10 text-xs space-y-1">
                          <div className="flex items-center justify-between">
                            <span className="text-text-warm/80 font-mono flex items-center gap-1.5"><Workflow className="w-3 h-3 text-sp-copper" />{job.name}</span>
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

                {s.pluginStats && Object.keys(s.pluginStats).length > 0 && (
                  <div className="space-y-1.5">
                    <span className="block text-[9px] uppercase font-bold text-text-warm/40 tracking-wider">Adventurer&apos;s Ledger</span>
                    <div className="grid grid-cols-2 gap-1.5">
                      {Object.entries(s.pluginStats).map(([key, value]) => (
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

            <p className="text-[9px] text-text-warm/30 uppercase tracking-wider">
              Live from the Minecraft server — visible to you{roleLabel ? ` (${roleLabel})` : ''}, never to visitors.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
