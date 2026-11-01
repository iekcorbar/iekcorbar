'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { Users, Search, Shield, Award, Cpu, Star, UserPlus } from 'lucide-react';
import { useSiteContent, useUser } from '../providers';
import PlayerAvatar from '@/components/PlayerAvatar';
import { roleBadgeClass } from '@/lib/staff-tabs';

export default function MembersPage() {
  const siteContent = useSiteContent();
  const { user, setIsLoginOpen, setIsRegisterMode } = useUser();
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    async function fetchMembers() {
      try {
        const res = await fetch('/api/members');
        const data = await res.json();
        if (res.ok) {
          setMembers(data.users || []);
        }
      } catch (err) {
        console.error("Failed to fetch members", err);
      } finally {
        setLoading(false);
      }
    }
    fetchMembers();
  }, []);

  const membersList = Array.isArray(members) ? members : [];
  const filteredMembers = membersList.filter(m => 
    m.mcUsername && m.mcUsername.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Custom Role Manager roles (e.g. "helper") count as staff if they have
  // any Staff Panel tab — roleIsStaff/roleLabel come from /api/members.
  const isStaffMember = m => m.role === 'admin' || m.role === 'moderator' || !!m.roleIsStaff;
  const roleName = m => m.role === 'admin' ? siteContent.role_label_admin
    : m.role === 'moderator' ? siteContent.role_label_moderator
    : (m.roleLabel || siteContent.role_label_player);
  // Same role hierarchy as the Staff Panel (see roleRank in staff-tabs.js):
  // highest role first, then alphabetical by IGN within a role.
  const byRoleThenName = (a, b) => ((a.roleRank ?? 0) - (b.roleRank ?? 0)) ||
    (a.mcUsername || '').localeCompare(b.mcUsername || '', undefined, { sensitivity: 'base' });
  const staff = filteredMembers.filter(isStaffMember).sort(byRoleThenName);
  const crew = filteredMembers.filter(m => !isStaffMember(m)).sort(byRoleThenName);

  const totalMembersCount = membersList.length;
  const staffCount = membersList.filter(isStaffMember).length;
  const crewCount = totalMembersCount - staffCount;

  return (
    <div className="space-y-8">
      
      {/* HEADER SECTION */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-sp-bronze pb-5">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <Users className="w-8 h-8 text-sp-copper" /> {siteContent.title_members_page}
          </h1>
          <p className="text-xs text-text-warm/60 mt-1">
            Registered players and staff of the Iekcorbar server.
          </p>
        </div>

        {/* Real-time search */}
        <div className="relative max-w-sm w-full">
          <input
            type="text"
            placeholder="Search player by IGN..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg pl-9 pr-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
          />
          <Search className="w-4 h-4 text-text-warm/40 absolute left-3 top-3.5" />
        </div>
      </div>

      {/* Role legend — the steampunk role names aren't self-explanatory for new visitors */}
      <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-[10px] text-text-warm/40 -mt-4">
        <span className="uppercase tracking-wider font-bold text-text-warm/30">Roles:</span>
        <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-rose-400" /> {siteContent.role_label_admin} = Server admin (in-game group Admin)</span>
        <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-amber-400" /> {siteContent.role_label_moderator} = Engineer (in-game group Engineer)</span>
        <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-sp-gold" /> {siteContent.role_label_player} = Regular player</span>
        <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-emerald-400" /> Other staff roles (e.g. Helper)</span>
      </div>

      {/* METRICS CARDS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-neutral-900/40 border border-sp-bronze/35 p-4 rounded-xl flex items-center justify-between backdrop-blur-xs">
          <div>
            <span className="block text-[9px] uppercase font-bold text-text-warm/40">Total Members</span>
            <span className="font-serif text-2xl font-black text-white">{totalMembersCount}</span>
          </div>
          <Cpu className="w-8 h-8 text-sp-gold/40" />
        </div>
        <div className="bg-neutral-900/40 border border-sp-bronze/35 p-4 rounded-xl flex items-center justify-between backdrop-blur-xs">
          <div>
            <span className="block text-[9px] uppercase font-bold text-text-warm/40">Staff</span>
            <span className="font-serif text-2xl font-black text-rose-300">{staffCount}</span>
          </div>
          <Shield className="w-8 h-8 text-rose-300/40" />
        </div>
        <div className="bg-neutral-900/40 border border-sp-bronze/35 p-4 rounded-xl flex items-center justify-between backdrop-blur-xs">
          <div>
            <span className="block text-[9px] uppercase font-bold text-text-warm/40">Players</span>
            <span className="font-serif text-2xl font-black text-sp-copper">{crewCount}</span>
          </div>
          <Award className="w-8 h-8 text-sp-copper/40" />
        </div>
      </div>

      {loading ? (
        <div className="py-20 text-center text-text-warm/40 italic animate-pulse">
          Retrieving transmitter registry lists...
        </div>
      ) : (
        <div className="space-y-10">
          
          {/* STAFF COMMAND SECTION */}
          {staff.length > 0 && (
            <div className="space-y-4 text-left">
              <h2 className="font-serif text-xl font-bold text-rose-300 uppercase tracking-wider flex items-center gap-2 border-b border-sp-bronze/25 pb-2">
                <Shield className="w-5 h-5 text-crimson" /> Staff
              </h2>
              
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                {staff.map((member) => (
                  <Link 
                    href={`/members/${member.mcUsername}`}
                    key={member.mcUsername} 
                    className="bg-sp-dark border-2 border-sp-gold p-5 rounded-xl flex items-center gap-5 relative overflow-hidden steampunk-border shadow-md hover:border-sp-copper transition-colors cursor-pointer text-left block"
                  >
                    {/* Watermark badge */}
                    <div className="absolute right-2 top-2 opacity-[0.08] pointer-events-none">
                      <Star className="w-20 h-20 text-sp-gold" />
                    </div>

                    {/* Skin Render Column */}
                    <div className="w-[70px] h-[110px] bg-neutral-950/40 rounded-lg border border-sp-bronze/30 shadow-inner flex items-center justify-center overflow-hidden relative group shrink-0">
                      <img 
                        src={`https://mc-heads.net/body/${member.mcUsername}`} 
                        alt={`${member.mcUsername} skin`} 
                        className="h-[95px] w-auto object-contain transition-transform duration-300 group-hover:scale-110"
                        onError={(e) => {
                          e.target.onerror = null;
                          e.target.src = 'https://mc-heads.net/body/Steve';
                        }}
                      />
                    </div>

                    {/* Meta info Column */}
                    <div className="flex-1 min-w-0 space-y-1.5">
                      <h3 className="font-serif text-lg font-black text-white truncate uppercase tracking-wide">
                        {member.mcUsername}
                      </h3>
                      <div className="flex items-center gap-1.5">
                        <span className={`text-[9px] px-2 py-0.5 rounded-md font-bold uppercase tracking-wider ${roleBadgeClass(member.role, member.roleColor)}`}>
                          {roleName(member)}
                        </span>
                        {member.gameRank && <span className="text-[9px] px-2 py-0.5 rounded-md font-bold uppercase tracking-wider bg-white/5 text-text-warm/60">{member.gameRank}</span>}
                      </div>
                      <p className="text-[10px] text-text-warm/40 italic">
                        {member.role === 'admin' ? siteContent.role_description_admin : member.role === 'moderator' ? siteContent.role_description_moderator : 'Part of the staff team'}
                      </p>
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          )}

          {/* CREW REGISTRY SECTION */}
          <div className="space-y-4 text-left">
            <h2 className="font-serif text-xl font-bold text-sp-gold uppercase tracking-wider flex items-center gap-2 border-b border-sp-bronze/25 pb-2">
              <Users className="w-5 h-5 text-sp-copper" /> Players
            </h2>

            {crew.length === 0 ? (
              <div className="bg-neutral-900/10 border border-sp-bronze/20 rounded-xl p-8 text-center text-text-warm/30 italic">
                No players matching your search.
              </div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-4">
                {crew.map((member) => (
                  <Link 
                    href={`/members/${member.mcUsername}`}
                    key={member.mcUsername} 
                    className="bg-neutral-900/30 border border-sp-bronze/40 p-3 rounded-lg flex flex-col items-center text-center space-y-2.5 relative group hover:border-sp-gold transition-colors cursor-pointer block"
                  >
                    {/* Avatar Render */}
                    <div className="w-12 h-12 rounded-md border border-sp-bronze/35 overflow-hidden bg-neutral-950/40 shrink-0">
                      <PlayerAvatar
                        mcUsername={member.mcUsername}
                        customSkinUrl={member.customSkinUrl}
                        size={48}
                        title={`${member.mcUsername} avatar`}
                        className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
                      />
                    </div>

                    <div className="w-full min-w-0">
                      <span className="block font-bold text-white text-xs truncate">{member.mcUsername}</span>
                      <span className="block text-[8px] uppercase tracking-widest text-text-warm/40 font-bold mt-0.5">{member.roleLabel ? <span className={`inline-block px-1.5 py-0.5 rounded ${roleBadgeClass(member.role, member.roleColor)}`}>{roleName(member)}</span> : (member.gameRank || roleName(member))}</span>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </div>

        </div>
      )}

      {/* Register CTA — this directory is exactly the place a visitor asks
          "could I be on here too?", so it gets its own closing nudge. */}
      {!user && (
        <div className="bg-sp-dark/70 border border-sp-bronze/40 rounded-xl p-6 text-center space-y-3">
          <p className="text-sm text-text-warm/70">
            Don&apos;t see your name yet? Create an account to join the member list.
          </p>
          <button
            onClick={() => { setIsLoginOpen(true); setIsRegisterMode(true); }}
            className="px-5 py-2.5 rounded-lg bg-sp-gold hover:bg-white border border-transparent text-black font-bold uppercase text-xs tracking-wider transition-all cursor-pointer shadow-md shadow-sp-gold/20 inline-flex items-center gap-2"
          >
            <UserPlus className="w-4 h-4" /> Create Your Account
          </button>
        </div>
      )}

    </div>
  );
}
