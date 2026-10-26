'use client';

import React, { useState, useEffect } from 'react';
import { Newspaper, Sparkles, Calendar, User, Bell, BellOff, Rocket, LogIn, ExternalLink, Share2, Check } from 'lucide-react';
import { useUser } from '../providers';
import { linkifyText, lineVideoEmbed, VideoEmbed } from '@/lib/linkify';

// Public News & Upcoming feed — Minecraft news and planned server/website
// features, posted by staff (Staff Panel > News & Upcoming) and grouped by
// topic. Logged-in players can follow a topic here to get a bell
// notification whenever a new post lands under it (see
// api/news/follow/route.js and db.saveNewsPost's
// broadcastTopicFollowersNotification) — distinct from the /updates
// changelog, which is a flat everyone-sees-everything feed.
export default function NewsClient() {
  const { user, setIsLoginOpen } = useUser();
  const [posts, setPosts] = useState([]);
  const [topics, setTopics] = useState(['all']);
  const [followedTopics, setFollowedTopics] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedTopic, setSelectedTopic] = useState('all');
  const [selectedType, setSelectedType] = useState('all');
  const [followBusy, setFollowBusy] = useState(null);
  // Which post's share link was just copied (brief "Copied!" state) and
  // which post a deep link (?post=<id>) should be scrolled to + highlighted
  // once its card actually renders.
  const [copiedId, setCopiedId] = useState(null);
  const [highlightedId, setHighlightedId] = useState(null);

  useEffect(() => {
    async function fetchAll() {
      try {
        const [resPosts, resTopics] = await Promise.all([
          fetch('/api/news'),
          fetch('/api/news/topics')
        ]);
        const dataPosts = await resPosts.json();
        const dataTopics = await resTopics.json();

        if (resPosts.ok) {
          const sorted = (dataPosts.posts || []).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
          setPosts(sorted);

          // Deep-link support (?post=<id>), same idea as the wiki's
          // ?page=<slug> — jump straight to a shared post instead of
          // leaving the visitor to scroll through the whole feed.
          const params = new URLSearchParams(window.location.search);
          const targetId = params.get('post');
          if (targetId && sorted.some(p => String(p.id) === targetId)) {
            setSelectedTopic('all');
            setSelectedType('all');
            setHighlightedId(targetId);
          }
        }
        if (resTopics.ok && dataTopics.topics) {
          setTopics(['all', ...dataTopics.topics]);
        }
      } catch (err) {
        console.error("Failed to fetch news or topics", err);
      } finally {
        setLoading(false);
      }
    }
    fetchAll();
  }, []);

  useEffect(() => {
    if (!highlightedId) return;
    const el = document.getElementById(`news-post-${highlightedId}`);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const timer = setTimeout(() => setHighlightedId(null), 3000);
    return () => clearTimeout(timer);
  }, [highlightedId, posts]);

  const copyShareLink = async (post, e) => {
    e?.stopPropagation();
    const url = `${window.location.origin}/news?post=${post.id}`;
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = url;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); } catch { /* nothing more we can do */ }
      document.body.removeChild(ta);
    }
    setCopiedId(post.id);
    setTimeout(() => setCopiedId(prev => (prev === post.id ? null : prev)), 1500);
  };

  useEffect(() => {
    if (!user) { setFollowedTopics([]); return; }
    fetch('/api/news/follow')
      .then(res => res.json())
      .then(data => { if (Array.isArray(data.topics)) setFollowedTopics(data.topics); })
      .catch(() => {});
  }, [user]);

  const toggleFollow = async (topic) => {
    if (!user) { setIsLoginOpen(true); return; }
    const isFollowing = followedTopics.includes(topic);
    setFollowBusy(topic);
    try {
      const res = await fetch(`/api/news/follow${isFollowing ? `?topic=${encodeURIComponent(topic)}` : ''}`, {
        method: isFollowing ? 'DELETE' : 'POST',
        headers: isFollowing ? undefined : { 'Content-Type': 'application/json' },
        body: isFollowing ? undefined : JSON.stringify({ topic })
      });
      const data = await res.json();
      if (res.ok && Array.isArray(data.topics)) setFollowedTopics(data.topics);
    } catch (err) {
      console.error("Failed to toggle topic follow", err);
    } finally {
      setFollowBusy(null);
    }
  };

  const filteredPosts = posts.filter(p =>
    (selectedTopic === 'all' || p.topic === selectedTopic) &&
    (selectedType === 'all' || p.type === selectedType)
  );

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

  return (
    <div className="space-y-8">

      {/* HEADER */}
      <div className="border-b border-sp-bronze pb-5 space-y-3">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <Newspaper className="w-8 h-8 text-sp-copper" /> News &amp; Upcoming
          </h1>
          <p className="text-xs text-text-warm/60 mt-1 max-w-2xl">
            Minecraft news and planned server or website features, posted by staff. Follow a topic below to get a bell
            notification the moment something new lands under it — no need to keep checking back.
          </p>
        </div>

        {/* Type filter */}
        <div className="flex flex-wrap gap-1.5">
          {[['all', 'Everything'], ['news', 'News'], ['upcoming', 'Upcoming']].map(([val, label]) => (
            <button
              key={val}
              onClick={() => setSelectedType(val)}
              className={`px-3 py-1 rounded text-[10px] font-bold uppercase transition-all cursor-pointer flex items-center gap-1 ${
                selectedType === val
                  ? 'bg-sp-copper text-black font-black'
                  : 'bg-neutral-900/40 text-text-warm/60 border border-sp-bronze/25 hover:text-white'
              }`}
            >
              {val === 'upcoming' && <Rocket className="w-3 h-3" />} {label}
            </button>
          ))}
        </div>

        {/* Topic filter + follow toggles */}
        <div className="flex flex-wrap gap-1.5">
          {topics.map(topic => {
            const isFollowing = topic !== 'all' && followedTopics.includes(topic);
            return (
              <div
                key={topic}
                className={`flex items-center gap-1 rounded-md border text-[10px] font-bold uppercase overflow-hidden ${
                  selectedTopic === topic ? 'border-sp-gold' : 'border-sp-bronze/25'
                }`}
              >
                <button
                  onClick={() => setSelectedTopic(topic)}
                  className={`px-2.5 py-1 transition-all cursor-pointer ${
                    selectedTopic === topic ? 'bg-sp-gold text-black' : 'bg-neutral-900/40 text-text-warm/60 hover:text-white'
                  }`}
                >
                  {topic === 'all' ? 'All Topics' : topic}
                </button>
                {topic !== 'all' && (
                  <button
                    onClick={() => toggleFollow(topic)}
                    disabled={followBusy === topic}
                    title={user ? (isFollowing ? `Unfollow ${topic}` : `Follow ${topic} for bell notifications`) : 'Log in to follow topics'}
                    className={`px-1.5 py-1 border-l transition-all cursor-pointer disabled:opacity-40 ${
                      selectedTopic === topic ? 'border-sp-gold/40' : 'border-sp-bronze/25'
                    } ${isFollowing ? 'bg-sp-gold/20 text-sp-gold' : 'bg-neutral-950/40 text-text-warm/40 hover:text-sp-gold'}`}
                  >
                    {isFollowing ? <Bell className="w-3 h-3" /> : <BellOff className="w-3 h-3" />}
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {!user && (
          <button
            onClick={() => setIsLoginOpen(true)}
            className="flex items-center gap-1.5 text-[10px] text-sp-gold/70 hover:text-sp-gold underline underline-offset-2"
          >
            <LogIn className="w-3 h-3" /> Log in to follow topics and get notified
          </button>
        )}
      </div>

      {loading ? (
        <div className="py-20 text-center text-xs text-text-warm/40 italic animate-pulse">
          Retrieving news signals...
        </div>
      ) : filteredPosts.length === 0 ? (
        <div className="bg-neutral-900/10 border border-sp-bronze/20 rounded-xl p-12 text-center text-text-warm/30 italic">
          No posts found matching your filters.
        </div>
      ) : (
        <div className="relative border-l-2 border-sp-bronze/35 ml-4 md:ml-6 pl-6 md:pl-8 space-y-12">

          {filteredPosts.map((post) => {
            const formattedDate = new Date(post.createdAt).toLocaleDateString(undefined, {
              year: 'numeric', month: 'long', day: 'numeric'
            });
            const isUpcoming = post.type === 'upcoming';

            return (
              <div key={post.id} id={`news-post-${post.id}`} className="relative text-left group">

                <div className={`absolute -left-[35px] md:-left-[43px] top-1.5 w-6 h-6 rounded-full bg-neutral-950 border-2 flex items-center justify-center shadow-md transition-colors z-10 ${
                  isUpcoming ? 'border-sp-copper group-hover:border-sp-gold' : 'border-sp-gold group-hover:border-sp-copper'
                }`}>
                  <div className={`w-1.5 h-1.5 rounded-full ${isUpcoming ? 'bg-sp-copper' : 'bg-sp-gold'}`} />
                </div>

                <div className={`bg-sp-dark border p-5 rounded-xl transition-all shadow-md space-y-3 relative overflow-hidden steampunk-border ${
                  highlightedId === String(post.id) ? 'border-sp-gold ring-2 ring-sp-gold/50' : 'border-sp-bronze/45 hover:border-sp-gold/80'
                }`}>

                  <div className="absolute right-4 top-4 flex items-center gap-1.5">
                    <span className={`text-[9px] px-2 py-0.5 border font-bold uppercase tracking-wider rounded-md flex items-center gap-1 ${
                      isUpcoming
                        ? 'bg-sp-copper/10 border-sp-copper/40 text-sp-copper'
                        : 'bg-sp-gold/10 border-sp-gold/40 text-sp-gold'
                    }`}>
                      {isUpcoming ? <><Rocket className="w-2.5 h-2.5" /> Upcoming</> : <><Sparkles className="w-2.5 h-2.5" /> News</>}
                    </span>
                    <span className="text-[9px] px-2 py-0.5 border border-sp-bronze/25 bg-neutral-950/40 text-text-warm/60 font-bold uppercase tracking-wider rounded-md">
                      {post.topic}
                    </span>
                    <button
                      onClick={(e) => copyShareLink(post, e)}
                      title="Copy share link"
                      className={`p-1 rounded-sm transition-colors cursor-pointer ${copiedId === post.id ? 'text-emerald-400' : 'text-text-warm/30 hover:text-sp-gold'}`}
                    >
                      {copiedId === post.id ? <Check className="w-3.5 h-3.5" /> : <Share2 className="w-3.5 h-3.5" />}
                    </button>
                  </div>

                  <div className="space-y-1 max-w-[75%]">
                    <h2 className="font-serif text-lg md:text-xl font-bold text-white uppercase tracking-wide group-hover:text-sp-gold transition-colors">
                      {post.title}
                    </h2>

                    <div className="flex flex-wrap items-center gap-4 text-[10px] text-text-warm/40 font-mono">
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-sp-copper" /> {formattedDate}
                      </span>
                      <span className="flex items-center gap-1">
                        <User className="w-3.5 h-3.5 text-sp-copper" /> Posted by {post.author}
                      </span>
                    </div>
                  </div>

                  <hr className="border-sp-bronze/10" />

                  <div className="text-xs text-text-warm/85 space-y-1">
                    {renderContent(post.content)}
                  </div>

                  {post.link && (
                    <a
                      href={post.link}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider px-3 py-1.5 rounded-lg border border-sp-gold/50 bg-sp-gold/10 hover:bg-sp-gold hover:text-black text-sp-gold transition-all"
                    >
                      <ExternalLink className="w-3 h-3" /> Open Link
                    </a>
                  )}

                </div>
              </div>
            );
          })}

        </div>
      )}

    </div>
  );
}
