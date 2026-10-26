'use client';
import { useState } from 'react';

// Interactive stress budget for the Steamworks guide: pick a power source, gear it up or down,
// hang machines on the axle and watch the load (Tq) climb against the source's capacity.
// The numbers are the live ones from the plugin (see docs/wiki-steamworks/02-power.md):
// a machine costs its Tq-per-RPM times the speed it actually turns at.

const S = (id) => `/recipes/steamworks/${id}.png`;

const SOURCES = [
    { id: 'crank', img: 'hand_crank', name: 'Hand Crank', rpm: 16, tq: 128 },
    { id: 'water', img: 'water_wheel', name: 'Water Wheel', rpm: 8, tq: 256 },
    { id: 'wind', img: 'windmill', name: 'Windmill (Y80)', rpm: 8, tq: 512 },
    { id: 'steam', img: 'steam_engine', name: 'Steam Engine', rpm: 24, tq: 1536 },
];

const MACHINES = [
    { id: 'millstone', img: 'millstone', name: 'Millstone', tq: 4, note: '4 Tq per RPM' },
    { id: 'hammer', img: 'steam_hammer', name: 'Steam Hammer', tq: 8, note: '8 Tq per RPM' },
    { id: 'crusher', img: 'crushing_wheel', name: 'Crushing Wheels (pair)', tq: 16, note: '8 Tq per RPM per wheel' },
    { id: 'fan', img: 'bellows_fan', name: 'Bellows Fan', tq: 4, note: '4 Tq per RPM' },
    { id: 'pump', img: 'pumpjack', name: 'Pumpjack', tq: 8, note: '8 Tq per RPM' },
    { id: 'belt', img: 'belt', name: 'Conveyor piece', tq: 0.5, note: '0.5 Tq per RPM each' },
];

const fmt = (n) => (Number.isInteger(n) ? n : n.toFixed(1));

export default function SteamworksPlayground() {
    const [srcId, setSrcId] = useState('crank');
    const [gear, setGear] = useState(1);
    const [counts, setCounts] = useState({});

    const source = SOURCES.find(s => s.id === srcId);
    const rpm = source.rpm * gear;
    let load = 0;
    for (const m of MACHINES) load += (counts[m.id] || 0) * m.tq * rpm;
    const pct = (load / source.tq) * 100;
    const jam = load > source.tq;
    const bar = jam ? 'bg-red-600' : pct >= 80 ? 'bg-amber-500' : 'bg-emerald-600';

    const set = (id, n) => setCounts(c => ({ ...c, [id]: Math.max(0, Math.min(8, n)) }));
    const reset = () => { setSrcId('crank'); setGear(1); setCounts({}); };

    return (
        <div className="rounded-xl border-2 border-sp-copper/50 bg-black/40 p-4 sm:p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">Stress playground - can your network take it?</p>
                <button onClick={reset} className="text-[11px] px-2 py-1 rounded border border-sp-bronze/40 text-text-warm/60 hover:text-sp-gold">reset</button>
            </div>

            <div className="flex flex-wrap gap-2">
                {SOURCES.map(s => (
                    <button key={s.id} onClick={() => setSrcId(s.id)}
                        className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-left transition-all ${s.id === srcId ? 'border-sp-gold bg-sp-gold/10 text-sp-gold' : 'border-sp-bronze/40 bg-black/30 text-text-warm/70 hover:border-sp-copper'}`}>
                        <img src={S(s.img)} alt="" className="h-8 w-8 object-contain" />
                        <span className="text-sm leading-tight">
                            {s.name}
                            <span className="block text-[10px] opacity-70">{s.rpm} RPM · {s.tq} Tq</span>
                        </span>
                    </button>
                ))}
            </div>

            <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="text-text-warm/60 text-xs uppercase tracking-wider">gearing the axle:</span>
                {[1, 2, 4].map(g => (
                    <button key={g} onClick={() => setGear(g)}
                        className={`px-2.5 py-1 rounded-md border text-xs font-semibold ${g === gear ? 'border-sp-gold text-sp-gold bg-sp-gold/10' : 'border-sp-bronze/40 text-text-warm/60 hover:text-sp-gold'}`}>
                        ×{g}
                    </button>
                ))}
                <span className="text-xs text-text-warm/50">big cog driving small cogs = ×2 per stage</span>
            </div>

            <div className="grid sm:grid-cols-2 gap-2">
                {MACHINES.map(m => {
                    const c = counts[m.id] || 0;
                    return (
                        <div key={m.id} className="flex items-center gap-3 rounded-lg border border-sp-bronze/25 bg-black/30 px-3 py-2">
                            <img src={S(m.img)} alt="" className={`h-9 w-9 object-contain ${c > 0 ? '' : 'opacity-40'}`} />
                            <div className="flex-1 min-w-0">
                                <p className="text-sm text-text-warm/85 leading-tight">{m.name}</p>
                                <p className="text-[10px] text-text-warm/45">{m.note}{c > 0 && <> - now <b className="text-sp-copper">{fmt(c * m.tq * rpm)} Tq</b></>}</p>
                            </div>
                            <div className="flex items-center gap-1.5">
                                <button onClick={() => set(m.id, c - 1)} className="w-7 h-7 rounded-md border border-sp-bronze/40 text-sp-gold/80 hover:bg-sp-bronze/30">−</button>
                                <span className={`w-5 text-center text-sm font-semibold ${c > 0 ? 'text-sp-gold' : 'text-text-warm/40'}`}>{c}</span>
                                <button onClick={() => set(m.id, c + 1)} className="w-7 h-7 rounded-md border border-sp-bronze/40 text-sp-gold/80 hover:bg-sp-bronze/30">+</button>
                            </div>
                        </div>
                    );
                })}
            </div>

            <div>
                <div className="flex justify-between text-xs mb-1">
                    <span className="text-text-warm/60">network speed: <b className="text-sp-gold">{rpm} RPM</b></span>
                    <span className={jam ? 'text-red-300' : 'text-text-warm/60'}>{fmt(load)} / {source.tq} Tq</span>
                </div>
                <div className="h-4 rounded-full bg-black/60 border border-sp-bronze/40 overflow-hidden">
                    <div className={`h-full ${bar} transition-all duration-300`} style={{ width: `${Math.min(100, pct)}%` }} />
                </div>
                <p className={`text-sm mt-2 ${jam ? 'text-red-300' : pct >= 100 ? 'text-amber-300' : 'text-emerald-300'}`}>
                    {load === 0
                        ? 'Nothing connected yet - the axle would spin happily.'
                        : jam
                            ? `JAMMING - ${fmt(load)} Tq of machines on a ${source.tq} Tq source. The network slows to a stop (nothing breaks!) until you unplug something or add more power.`
                            : pct >= 95
                                ? `Running - ${fmt(load)} / ${source.tq} Tq, right at the limit. One more machine and it jams.`
                                : `Running fine - ${fmt(load)} / ${source.tq} Tq (${Math.round(pct)}%) used.`}
                </p>
                <p className="text-xs text-text-warm/50 pt-1">
                    Notice: gearing up speed multiplies every machine&apos;s cost too - faster work, more Tq. And past 192 RPM parts pop their breaker pin regardless.
                </p>
            </div>
        </div>
    );
}
