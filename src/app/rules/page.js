'use client';
import React, { useState, useEffect } from 'react';
import { Shield, ChevronRight, AlertTriangle, Search, Info } from 'lucide-react';
import { linkifyHtml, lineVideoEmbed, VideoEmbed } from '@/lib/linkify';

function extractEmoji(title) {
  // Find unicode emojis or symbols at the start of the string
  const match = title.match(/^([^\w\s\d(),.:;'"!&|-]+)/);
  if (match) {
    const emoji = match[1].trim();
    const cleanTitle = title.substring(match[0].length).trim();
    return { emoji, title: cleanTitle };
  }
  return { emoji: '⚙️', title };
}

function renderContent(text) {
  if (!text) return null;
  return text.split('\n').map((line, i) => {
    let trimmed = line.trim();

    // A YouTube/Vimeo link alone on its own line -> embedded player.
    const embed = lineVideoEmbed(trimmed);
    if (embed) return <VideoEmbed key={i} embed={embed} />;
    
    // Check if line is a bullet
    let isBullet = false;
    if (trimmed.startsWith('•')) {
      isBullet = true;
      trimmed = trimmed.substring(1).trim();
    } else if (trimmed.startsWith('-')) {
      isBullet = true;
      trimmed = trimmed.substring(1).trim();
    }
    
    let processed = trimmed
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" class="text-sp-gold hover:text-white underline font-bold transition-colors">$1</a>');
    processed = linkifyHtml(processed); // bare URLs -> clickable links

    if (isBullet) {
      return (
        <div key={i} className="flex items-start gap-2 pl-4 py-1 select-none text-left">
          <span className="text-sp-gold select-none mt-1.5 text-[8px] animate-pulse">⚙️</span>
          <span className="text-sm text-text-warm/90 leading-relaxed" dangerouslySetInnerHTML={{ __html: processed }} />
        </div>
      );
    }

    if (trimmed.toLowerCase().startsWith('allowed:') || trimmed.toLowerCase() === 'allowed:') {
      return (
        <p key={i} className="text-xs font-bold text-green-400 uppercase tracking-widest mt-4 mb-1.5 flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" />
          {processed}
        </p>
      );
    }

    if (trimmed.toLowerCase().startsWith('not allowed:') || trimmed.toLowerCase() === 'not allowed:') {
      return (
        <p key={i} className="text-xs font-bold text-rose-400 uppercase tracking-widest mt-4 mb-1.5 flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-rose-400 animate-pulse" />
          {processed}
        </p>
      );
    }

    if (!trimmed) {
      return <div key={i} className="h-2" />;
    }

    return <p key={i} className="text-sm text-text-warm/80 leading-relaxed my-1 text-left" dangerouslySetInnerHTML={{ __html: processed }} />;
  });
}

export default function RulesPage() {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    fetch('/api/rules')
      .then(r => r.json())
      .then(d => { if (d.rules) setRules(d.rules); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const filteredRules = rules.filter(rule => {
    const query = searchQuery.toLowerCase();
    return (
      rule.title.toLowerCase().includes(query) ||
      rule.content.toLowerCase().includes(query)
    );
  });

  return (
    <div className="space-y-10 pb-12">
      <title>Rules — Iekcorbar</title>

      {/* Page Header */}
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0">
          <div className="absolute inset-0 bg-gradient-to-br from-crimson/10 via-black to-sp-charcoal/40" />
        </div>
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-crimson/40 bg-crimson/10 text-rose-300 text-xs font-semibold uppercase tracking-wider">
            <Shield className="w-3.5 h-3.5" /> Server Rules
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-widest uppercase steampunk-glow">
            Code of Conduct
          </h1>
          <p className="text-text-warm/70 max-w-xl mx-auto text-sm">
            These rules govern all interactions on the Iekcorbar server and Discord. Violations will be handled by staff with appropriate sanctions.
          </p>
        </div>
      </section>

      {/* Search & Warning Bar */}
      <div className="space-y-4">
        {/* Warning Banner */}
        <div className="flex items-start gap-3 bg-amber-900/20 border border-amber-600/40 rounded-xl p-4 text-left">
          <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <p className="text-sm text-amber-300/90">
            <strong className="text-amber-400">Important:</strong> Ignorance of the rules is not a valid defence. By joining our server you agree to abide by these rules at all times.
          </p>
        </div>

        {/* Search Bar */}
        <div className="relative max-w-md mx-auto">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search rules (e.g. AFK, hacking, mods)..."
            className="w-full bg-neutral-900/80 border-2 border-sp-bronze hover:border-sp-gold/60 focus:border-sp-gold focus:outline-hidden text-white placeholder-text-warm/40 text-sm rounded-xl py-3 pl-11 pr-4 transition-all steampunk-glass font-sans"
          />
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-sp-gold/60" />
        </div>
      </div>

      {/* Rules List */}
      {loading ? (
        <div className="text-center py-16 text-text-warm/40 italic">Loading Code of Conduct...</div>
      ) : (
        <div className="space-y-4">
          {filteredRules.map((rule, idx) => {
            const { emoji, title: cleanTitle } = extractEmoji(rule.title);
            return (
              <div key={rule.id} className="group bg-neutral-900/50 border border-sp-bronze/40 hover:border-sp-gold/60 rounded-xl p-6 transition-all steampunk-glass">
                <div className="flex items-start gap-5">
                  {/* Decorative Badge with Emoji */}
                  <div className="shrink-0 w-12 h-12 rounded-xl bg-sp-gold/5 border border-sp-bronze/65 group-hover:border-sp-gold flex items-center justify-center text-2xl transition-all shadow-inner relative overflow-hidden bg-gradient-to-br from-neutral-950 to-neutral-900">
                    <span className="relative z-10">{emoji}</span>
                    <div className="absolute -bottom-0.5 -right-0.5 text-[8px] font-mono font-bold text-sp-gold/45 group-hover:text-sp-gold/80 px-1 py-0.5 bg-black/80 border-t border-l border-sp-bronze/20 rounded-tl">
                      #{idx + 1}
                    </div>
                  </div>
                  <div className="flex-1 space-y-3">
                    <div className="flex items-center gap-2 border-b border-sp-bronze/10 pb-1.5 group-hover:border-sp-gold/25 transition-all text-left">
                      <h2 className="font-serif text-base text-white font-black tracking-wide uppercase">{cleanTitle}</h2>
                    </div>
                    <div className="pl-2 space-y-1">
                      {renderContent(rule.content)}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}

          {filteredRules.length === 0 && (
            <div className="text-center py-16 text-text-warm/40 border border-dashed border-sp-bronze/30 rounded-xl">
              No rules match your search query.
            </div>
          )}
        </div>
      )}

      {/* Footer Note */}
      <div className="text-center text-xs text-text-warm/30 italic">
        Rules may be updated at any time. Check back regularly. Last resort: contact staff via Discord.
      </div>
    </div>
  );
}
