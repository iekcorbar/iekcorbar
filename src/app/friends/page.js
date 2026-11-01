'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useUser, useSiteContent } from '../providers';
import PlayerAvatar from '@/components/PlayerAvatar';
import {
  Users,
  UserPlus,
  Check,
  X,
  Trash2,
  AlertTriangle,
  Send,
  Clock
} from 'lucide-react';

export default function FriendsPage() {
  const { user, setIsLoginOpen } = useUser();
  const siteContent = useSiteContent();
  const [friends, setFriends] = useState([]);
  const [incoming, setIncoming] = useState([]);
  const [outgoing, setOutgoing] = useState([]);
  const [loading, setLoading] = useState(true);
  const [addUsername, setAddUsername] = useState('');
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [directory, setDirectory] = useState([]);

  useEffect(() => {
    if (user) fetchFriends();
    // Member directory, fetched only to look up each friend/request's
    // equipped custom skin (Steampunk Wardrobe) for their avatar here —
    // /api/friends only ever returns usernames, not full user records.
    fetch('/api/members')
      .then(res => res.json())
      .then(data => setDirectory(data.users || []))
      .catch(() => {});
  }, [user]);

  const getCustomSkinUrl = (username) => {
    const match = directory.find(u => u.mcUsername && u.mcUsername.toLowerCase() === (username || '').toLowerCase());
    return match?.customSkinUrl || null;
  };

  async function fetchFriends() {
    try {
      const res = await fetch(`/api/friends?email=${encodeURIComponent(user.email)}`);
      const data = await res.json();
      if (res.ok) {
        setFriends(data.friends || []);
        setIncoming(data.incoming || []);
        setOutgoing(data.outgoing || []);
      }
    } catch (err) {
      console.error("Failed to load friends:", err);
    } finally {
      setLoading(false);
    }
  }

  async function handleAction(body) {
    setError('');
    try {
      const res = await fetch('/api/friends', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: user.email, ...body })
      });
      const data = await res.json();
      if (res.ok) {
        setFriends(data.friends || []);
        setIncoming(data.incoming || []);
        setOutgoing(data.outgoing || []);
        return true;
      } else {
        setError(data.error || "Something went wrong");
        return false;
      }
    } catch (err) {
      setError("Network error");
      return false;
    }
  }

  async function handleAddFriend(e) {
    e.preventDefault();
    if (!addUsername.trim()) return;
    setAdding(true);
    setSuccess('');
    const ok = await handleAction({ action: 'request', targetUsername: addUsername.trim() });
    if (ok) {
      setSuccess(`Request sent to ${addUsername.trim()}!`);
      setAddUsername('');
      setTimeout(() => setSuccess(''), 4000);
    }
    setAdding(false);
  }

  if (!user) {
    return (
      <div className="max-w-md mx-auto my-12 bg-sp-dark/95 border-2 border-crimson p-8 rounded-xl text-center steampunk-border shadow-2xl space-y-6">
        <div className="w-16 h-16 bg-crimson/15 rounded-full flex items-center justify-center border border-crimson text-rose-400 mx-auto">
          <AlertTriangle className="w-8 h-8" />
        </div>
        <h2 className="font-serif text-2xl font-bold text-rose-300 uppercase tracking-wider">Access Restrained</h2>
        <p className="text-sm text-text-warm/85 leading-relaxed">
          You must align your transmitter and authenticate to view your friends list.
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

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
          <Users className="w-8 h-8 text-sp-copper" /> {siteContent.title_friends_page}
        </h1>
        <p className="text-xs text-text-warm/60 mt-1">Add other players as friends to share logbook pages and see their journal.</p>
      </div>

      {/* Add Friend */}
      <div className="bg-sp-dark/60 border border-sp-bronze/40 rounded-xl p-5 steampunk-glass">
        <h2 className="font-serif text-sm text-sp-gold uppercase tracking-wide flex items-center gap-2 mb-3">
          <UserPlus className="w-4 h-4 text-sp-copper" /> Add a {siteContent.role_label_player}
        </h2>
        <form onSubmit={handleAddFriend} className="flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            value={addUsername}
            onChange={e => setAddUsername(e.target.value)}
            placeholder="Minecraft username"
            className="flex-1 bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
          />
          <button
            type="submit"
            disabled={adding || !addUsername.trim()}
            className="px-4 py-2.5 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase tracking-wider rounded-lg flex items-center justify-center gap-2 transition-all cursor-pointer"
          >
            <Send className="w-3.5 h-3.5" /> Send Request
          </button>
        </form>
        {error && <p className="text-xs text-rose-400 mt-2">{error}</p>}
        {success && <p className="text-xs text-emerald-400 mt-2">{success}</p>}
      </div>

      {loading ? (
        <div className="text-xs text-text-warm/40 italic text-center py-10">Loading friends...</div>
      ) : (
        <>
          {/* Incoming Requests */}
          {incoming.length > 0 && (
            <div className="bg-sp-dark/60 border border-sp-bronze/40 rounded-xl p-5 steampunk-glass">
              <h2 className="font-serif text-sm text-sp-gold uppercase tracking-wide mb-3">Incoming Requests ({incoming.length})</h2>
              <div className="space-y-2">
                {incoming.map(req => (
                  <div key={req.id} className="flex items-center justify-between bg-neutral-950/40 border border-sp-bronze/20 rounded-lg px-3 py-2">
                    <div className="flex items-center gap-2.5">
                      <PlayerAvatar mcUsername={req.username} customSkinUrl={getCustomSkinUrl(req.username)} size={28} className="rounded-sm" />
                      <span className="text-sm font-bold text-white">{req.username}</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => handleAction({ action: 'accept', requestId: req.id })}
                        className="p-1.5 bg-emerald-600/20 hover:bg-emerald-600/40 border border-emerald-500/40 rounded text-emerald-400 cursor-pointer transition-colors"
                        title="Accept"
                      >
                        <Check className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => handleAction({ action: 'decline', requestId: req.id })}
                        className="p-1.5 bg-crimson/20 hover:bg-crimson/40 border border-crimson/40 rounded text-rose-400 cursor-pointer transition-colors"
                        title="Decline"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Outgoing Requests */}
          {outgoing.length > 0 && (
            <div className="bg-sp-dark/60 border border-sp-bronze/40 rounded-xl p-5 steampunk-glass">
              <h2 className="font-serif text-sm text-sp-gold uppercase tracking-wide mb-3">Pending (Sent) ({outgoing.length})</h2>
              <div className="space-y-2">
                {outgoing.map(req => (
                  <div key={req.id} className="flex items-center justify-between bg-neutral-950/40 border border-sp-bronze/20 rounded-lg px-3 py-2">
                    <div className="flex items-center gap-2.5">
                      <PlayerAvatar mcUsername={req.username} customSkinUrl={getCustomSkinUrl(req.username)} size={28} className="rounded-sm" />
                      <span className="text-sm font-bold text-white">{req.username}</span>
                      <span className="text-[9px] uppercase font-mono text-text-warm/40 flex items-center gap-1"><Clock className="w-3 h-3" /> Awaiting response</span>
                    </div>
                    <button
                      onClick={() => handleAction({ action: 'cancel', requestId: req.id })}
                      className="p-1.5 border border-sp-bronze/30 hover:border-crimson rounded text-text-warm/50 hover:text-rose-400 cursor-pointer transition-colors"
                      title="Cancel Request"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Friends List */}
          <div className="bg-sp-dark/60 border border-sp-bronze/40 rounded-xl p-5 steampunk-glass">
            <h2 className="font-serif text-sm text-sp-gold uppercase tracking-wide mb-3">Your Friends ({friends.length})</h2>
            {friends.length === 0 ? (
              <div className="text-center py-10 border border-dashed border-sp-bronze/30 rounded-xl text-text-warm/40 text-xs italic">
                No friends yet. Add another player above to get started.
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {friends.map(f => (
                  <div key={f.id} className="flex items-center justify-between bg-neutral-950/40 border border-sp-bronze/20 rounded-lg px-3 py-2">
                    <Link href={`/members/${f.username}`} className="flex items-center gap-2.5 hover:opacity-80 transition-opacity">
                      <PlayerAvatar mcUsername={f.username} customSkinUrl={getCustomSkinUrl(f.username)} size={28} className="rounded-sm" />
                      <span className="text-sm font-bold text-white">{f.username}</span>
                    </Link>
                    <button
                      onClick={() => { if (confirm(`Remove ${f.username} from your friends?`)) handleAction({ action: 'remove', friendshipId: f.id }); }}
                      className="p-1.5 border border-sp-bronze/30 hover:border-crimson rounded text-text-warm/50 hover:text-rose-400 cursor-pointer transition-colors"
                      title="Remove Friend"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
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
