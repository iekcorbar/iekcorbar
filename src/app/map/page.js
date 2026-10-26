'use client';
import React, { useState, useEffect } from 'react';
import { Map, ExternalLink, Cog, Settings } from 'lucide-react';

export default function MapPage() {
  const [mapUrl, setMapUrl] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/settings')
      .then(r => r.json())
      .then(d => { if (d.settings?.map_url) setMapUrl(d.settings.map_url); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-8 pb-12">
      <title>Live Map — Iekcorbar</title>

      {/* Header */}
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-emerald-950/20 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-emerald-500/40 bg-emerald-900/20 text-emerald-300 text-xs font-semibold uppercase tracking-wider">
            <Map className="w-3.5 h-3.5" /> Live World Map
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-widest uppercase steampunk-glow">
            World Atlas
          </h1>
          <p className="text-text-warm/70 max-w-xl mx-auto text-sm">
            Explore the steampunk territories of Iekcorbar in real-time. See active players, settlements, and land claims on our interactive map.
          </p>
        </div>
      </section>

      {loading ? (
        <div className="text-center py-20 text-text-warm/40 italic">Loading map data...</div>
      ) : mapUrl ? (
        /* Map available */
        <div className="text-center space-y-8">
          <div className="bg-neutral-900/60 border-2 border-sp-bronze rounded-2xl p-10 steampunk-glass space-y-6 max-w-2xl mx-auto">
            {/* Decorative map icon */}
            <div className="w-24 h-24 mx-auto rounded-full bg-emerald-900/30 border-2 border-emerald-500/40 flex items-center justify-center">
              <Map className="w-12 h-12 text-emerald-400" />
            </div>
            <div className="space-y-2">
              <h2 className="font-serif text-2xl text-sp-gold font-bold uppercase tracking-wide">
                Iekcorbar Interactive Map
              </h2>
              <p className="text-text-warm/60 text-sm">
                Click below to open the live world map in a new window. The map shows real-time player positions, territories, and points of interest.
              </p>
            </div>
            <a
              href={mapUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-3 px-8 py-4 bg-emerald-700 hover:bg-emerald-500 border-2 border-emerald-500/60 hover:border-emerald-400 text-white font-serif font-bold uppercase tracking-wider rounded-xl transition-all shadow-lg shadow-emerald-900/30 hover:shadow-emerald-700/40 text-sm"
            >
              <ExternalLink className="w-5 h-5" />
              Open Live Map
            </a>
            <p className="text-text-warm/30 text-[10px] uppercase font-mono tracking-widest">{mapUrl}</p>
          </div>

          {/* Info cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-3xl mx-auto">
            {[
              { icon: '🗺️', label: 'Full World View', desc: 'See the entire Iekcorbar map rendered in real-time' },
              { icon: '👥', label: 'Player Tracking', desc: 'View active player positions across all dimensions' },
              { icon: '🏯', label: 'Land Claims', desc: 'See all claimed territories and guild borders' },
            ].map(c => (
              <div key={c.label} className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-4 text-center space-y-2">
                <span className="text-2xl">{c.icon}</span>
                <p className="text-xs font-bold text-sp-gold uppercase tracking-wide">{c.label}</p>
                <p className="text-[11px] text-text-warm/50">{c.desc}</p>
              </div>
            ))}
          </div>
        </div>
      ) : (
        /* No map configured */
        <div className="text-center py-20 space-y-6">
          <div className="w-24 h-24 mx-auto rounded-full bg-neutral-900/60 border-2 border-sp-bronze/30 flex items-center justify-center">
            <Cog className="w-12 h-12 text-sp-bronze/40 animate-spin-gear-cw" />
          </div>
          <div className="space-y-2">
            <h2 className="font-serif text-xl text-text-warm/50 uppercase tracking-wide">Map Not Yet Configured</h2>
            <p className="text-sm text-text-warm/30">
              The live map hasn't been set up yet. Staff can configure the map URL in the admin panel.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
