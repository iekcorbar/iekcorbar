'use client';

import React, { useState, useEffect } from 'react';
import { Sparkles, Calendar, User, Tag, ArrowRight, Lock, LogIn } from 'lucide-react';
import { useUser } from '../providers';
import { linkifyText, lineVideoEmbed, VideoEmbed } from '@/lib/linkify';

export default function UpdatesPage() {
  const { user, setIsLoginOpen } = useUser();
  const [updates, setUpdates] = useState([]);
  const [categories, setCategories] = useState(['all']);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState('all');
  // Real /api/permissions/me check (not a hardcoded role !== 'admin' &&
  // role !== 'moderator' guess) so a custom staff role gets in too — see
  // CLAUDE.md's auth-model note about client-trusted role checks. null
  // means "still checking", to avoid a flash of the restricted notice for
  // staff while /api/permissions/me is in flight.
  const [isStaff, setIsStaff] = useState(null);

  useEffect(() => {
    fetch('/api/permissions/me')
      .then(res => res.json())
      .then(data => setIsStaff(!!data.isStaff))
      .catch(() => setIsStaff(false));
  }, [user]);

  useEffect(() => {
    if (!isStaff) { setLoading(false); return; }

    async function fetchUpdatesAndCats() {
      try {
        const [resUpdates, resCats] = await Promise.all([
          fetch('/api/updates'),
          fetch('/api/updates/categories')
        ]);
        const dataUpdates = await resUpdates.json();
        const dataCats = await resCats.json();

        if (resUpdates.ok) {
          const sorted = (dataUpdates.updates || []).sort((a, b) => new Date(b.date) - new Date(a.date));
          setUpdates(sorted);
        }
        if (resCats.ok && dataCats.categories) {
          setCategories(['all', ...dataCats.categories]);
        }
      } catch (err) {
        console.error("Failed to fetch updates or categories", err);
      } finally {
        setLoading(false);
      }
    }
    fetchUpdatesAndCats();
  }, [isStaff]);

  const filteredUpdates = selectedCategory === 'all' 
    ? updates 
    : updates.filter(u => u.category === selectedCategory);

  const getCategoryColor = (cat) => {
    switch (cat.toLowerCase()) {
      case 'general': return 'bg-sp-gold/10 border-sp-gold/40 text-sp-gold';
      case 'fixes': return 'bg-rose-500/10 border-rose-500/40 text-rose-400';
      case 'survival': return 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400';
      case 'skyblock': return 'bg-violet-500/10 border-violet-500/40 text-violet-400';
      default: return 'bg-sp-copper/10 border-sp-copper/40 text-sp-copper';
    }
  };

  const renderContent = (text) => {
    if (!text) return null;
    return text.split('\n').map((line, idx) => {
      // A YouTube/Vimeo link alone on its own line -> embedded player.
      const embed = lineVideoEmbed(line);
      if (embed) return <VideoEmbed key={idx} embed={embed} />;
      if (line.startsWith('### ')) {
        return <h3 key={idx} className="font-serif text-sm text-sp-gold font-bold mt-4 mb-2 uppercase tracking-wide">{line.replace('### ', '')}</h3>;
      }
      if (line.startsWith('* ')) {
        const boldMatch = line.match(/\*\*([^*]+)\*\*/);
        if (boldMatch) {
          const boldText = boldMatch[1];
          const remainingText = line.replace('* ', '').replace(/\*\*([^*]+)\*\*/, '');
          return (
            <li key={idx} className="list-disc ml-5 text-xs text-text-warm/85 my-1.5 leading-relaxed">
              <strong className="text-white">{boldText}</strong>{linkifyText(remainingText)}
            </li>
          );
        }
        return <li key={idx} className="list-disc ml-5 text-xs text-text-warm/85 my-1.5 leading-relaxed">{linkifyText(line.replace('* ', ''))}</li>;
      }
      if (line.trim() === '') return <div key={idx} className="h-2" />;
      return <p key={idx} className="text-xs text-text-warm/85 my-2 leading-relaxed text-left">{linkifyText(line)}</p>;
    });
  };

  if (isStaff === null) {
    return (
      <div className="py-20 text-center text-xs text-text-warm/40 italic animate-pulse">
        Verifying clearance...
      </div>
    );
  }

  if (!isStaff) {
    return (
      <div className="max-w-lg mx-auto py-20 text-center space-y-4">
        <Lock className="w-10 h-10 text-sp-copper/60 mx-auto" />
        <h1 className="font-serif text-2xl font-bold text-sp-gold uppercase tracking-wide">Staff Only</h1>
        <p className="text-xs text-text-warm/60 leading-relaxed">
          The Transmitter Logs are an internal changelog reserved for staff. If you believe this is a mistake, contact an administrator.
        </p>
        {!user && (
          <button
            onClick={() => setIsLoginOpen(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2 border border-sp-gold text-sp-gold hover:bg-sp-gold hover:text-black rounded-lg text-xs font-bold uppercase tracking-wider transition-all cursor-pointer"
          >
            <LogIn className="w-3.5 h-3.5" /> Log In
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-8">

      {/* HEADER */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-sp-bronze pb-5">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <Sparkles className="w-8 h-8 text-sp-copper" /> Transmitter Logs // Updates
          </h1>
          <p className="text-xs text-text-warm/60 mt-1">
            Chronological documentation of patches, expansions, and mechanical adjustments in Iekcorbar.
          </p>
        </div>

        {/* Filter Category Selector */}
        <div className="flex flex-wrap gap-1.5">
          {categories.map(cat => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`px-3 py-1 rounded text-[10px] font-bold uppercase transition-all cursor-pointer ${
                selectedCategory === cat
                  ? 'bg-sp-gold text-black font-black'
                  : 'bg-neutral-900/40 text-text-warm/60 border border-sp-bronze/25 hover:text-white'
              }`}
            >
              {cat === 'all' ? 'All Logs' : cat}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="py-20 text-center text-xs text-text-warm/40 italic animate-pulse">
          Retrieving transmitter signals...
        </div>
      ) : filteredUpdates.length === 0 ? (
        <div className="bg-neutral-900/10 border border-sp-bronze/20 rounded-xl p-12 text-center text-text-warm/30 italic">
          No logs found matching selected category signals.
        </div>
      ) : (
        <div className="relative border-l-2 border-sp-bronze/35 ml-4 md:ml-6 pl-6 md:pl-8 space-y-12">
          
          {filteredUpdates.map((update, idx) => {
            const formattedDate = new Date(update.date).toLocaleDateString(undefined, {
              year: 'numeric',
              month: 'long',
              day: 'numeric'
            });

            return (
              <div key={update.id} className="relative text-left group">
                
                {/* Timeline node circle */}
                <div className="absolute -left-[35px] md:-left-[43px] top-1.5 w-6 h-6 rounded-full bg-neutral-950 border-2 border-sp-gold flex items-center justify-center shadow-md group-hover:border-sp-copper transition-colors z-10">
                  <div className="w-1.5 h-1.5 rounded-full bg-sp-gold group-hover:bg-sp-copper transition-colors" />
                </div>

                <div className="bg-sp-dark border border-sp-bronze/45 hover:border-sp-gold/80 p-5 rounded-xl transition-all shadow-md space-y-3 relative overflow-hidden steampunk-border">
                  
                  {/* Category Pill Tag */}
                  <div className="absolute right-4 top-4">
                    <span className={`text-[9px] px-2 py-0.5 border font-bold uppercase tracking-wider rounded-md ${getCategoryColor(update.category)}`}>
                      {update.category}
                    </span>
                  </div>

                  <div className="space-y-1 max-w-[80%]">
                    <h2 className="font-serif text-lg md:text-xl font-bold text-white uppercase tracking-wide group-hover:text-sp-gold transition-colors">
                      {update.title}
                    </h2>
                    
                    <div className="flex flex-wrap items-center gap-4 text-[10px] text-text-warm/40 font-mono">
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-sp-copper" /> {formattedDate}
                      </span>
                      <span className="flex items-center gap-1">
                        <User className="w-3.5 h-3.5 text-sp-copper" /> Posted by {update.author}
                      </span>
                    </div>
                  </div>

                  <hr className="border-sp-bronze/10" />

                  {/* Body Content */}
                  <div className="text-xs text-text-warm/85 space-y-1">
                    {renderContent(update.content)}
                  </div>

                </div>
              </div>
            );
          })}

        </div>
      )}

    </div>
  );
}
