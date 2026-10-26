'use client';

import React, { useState, useEffect } from 'react';
import { useUser } from '../providers';
import {
  Hammer,
  Search,
  Users,
  Clock,
  CheckCircle,
  AlertCircle,
  ChevronDown,
  ChevronUp,
  UserPlus,
  UserMinus,
  Tag,
  Target,
  Cog,
  Sparkles,
  Filter,
  Share2,
  Check
} from 'lucide-react';
import { renderMarkdown } from '@/lib/markdown';
import PlayerAvatar from '@/components/PlayerAvatar';

export default function ProjectsClient() {
  const { user, setIsLoginOpen } = useUser();
  const [projects, setProjects] = useState([]);
  const [signups, setSignups] = useState([]);
  const [directory, setDirectory] = useState([]);
  const [loading, setLoading] = useState(true);

  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('All');
  const [statusFilter, setStatusFilter] = useState('All');
  const [expandedProject, setExpandedProject] = useState(null);
  const [signingUp, setSigningUp] = useState(null);

  // Share-link support: which project's link was just copied, and which
  // project a ?project=<id> deep link should expand + scroll to once its
  // card actually renders — mirrors the wiki's ?page=<slug> deep link.
  const [copiedId, setCopiedId] = useState(null);
  const [highlightedProjectId, setHighlightedProjectId] = useState(null);
  // Set by a "Join project" push-notification button (?project=<id>&signup=1).
  const [signupIntent, setSignupIntent] = useState(null);

  useEffect(() => {
    fetchProjects();
    // Only fetched to look up each signed-up participant's equipped custom
    // skin (Steampunk Wardrobe) for their avatar below — /api/projects only
    // ever returns usernames, not full user records.
    fetch('/api/members')
      .then(res => res.json())
      .then(data => setDirectory(data.users || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!highlightedProjectId) return;
    const el = document.getElementById(`project-${highlightedProjectId}`);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const timer = setTimeout(() => setHighlightedProjectId(null), 3000);
    return () => clearTimeout(timer);
  }, [highlightedProjectId, projects]);

  const copyShareLink = async (project, e) => {
    e?.stopPropagation();
    const url = `${window.location.origin}/projects?project=${project.id}`;
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
    setCopiedId(project.id);
    setTimeout(() => setCopiedId(prev => (prev === project.id ? null : prev)), 1500);
  };

  const getCustomSkinUrl = (username) => {
    const match = directory.find(u => u.mcUsername && u.mcUsername.toLowerCase() === (username || '').toLowerCase());
    return match?.customSkinUrl || null;
  };

  async function fetchProjects() {
    try {
      const res = await fetch('/api/projects');
      const data = await res.json();
      const fetchedProjects = data.projects || [];
      setProjects(fetchedProjects);
      setSignups(data.signups || []);

      const params = new URLSearchParams(window.location.search);
      const targetId = params.get('project');
      if (targetId && fetchedProjects.some(p => String(p.id) === targetId)) {
        setExpandedProject(targetId);
        setHighlightedProjectId(targetId);
        if (params.get('signup') === '1') setSignupIntent(targetId);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }

  const handleSignUp = async (projectId) => {
    if (!user) {
      setIsLoginOpen(true);
      return;
    }
    setSigningUp(projectId);
    try {
      const res = await fetch('/api/projects/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ projectId, username: user.mcUsername })
      });
      if (res.ok) {
        await fetchProjects();
      }
    } catch (err) {
      console.error(err);
    } finally {
      setSigningUp(null);
    }
  };

  const handleLeave = async (projectId) => {
    if (!user) return;
    setSigningUp(projectId);
    try {
      const res = await fetch(`/api/projects/signup?projectId=${projectId}&username=${user.mcUsername}`, {
        method: 'DELETE'
      });
      if (res.ok) {
        await fetchProjects();
      }
    } catch (err) {
      console.error(err);
    } finally {
      setSigningUp(null);
    }
  };

  const projectsList = Array.isArray(projects) ? projects : [];
  const categories = ['All', ...Array.from(new Set(projectsList.map(p => p.category)))];
  const statuses = ['All', 'open', 'in-progress', 'completed'];

  const filteredProjects = projectsList.filter(p => {
    const matchesCategory = categoryFilter === 'All' || p.category === categoryFilter;
    const matchesStatus = statusFilter === 'All' || p.status === statusFilter;
    const matchesSearch = (p.title || '').toLowerCase().includes(searchQuery.toLowerCase()) || 
                          (p.description || '').toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesStatus && matchesSearch;
  });

  const getStatusBadge = (status) => {
    switch (status) {
      case 'open':
        return (
          <span className="flex items-center gap-1 text-[10px] font-bold uppercase px-2.5 py-1 rounded-full bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
            <AlertCircle className="w-3 h-3" /> Open
          </span>
        );
      case 'in-progress':
        return (
          <span className="flex items-center gap-1 text-[10px] font-bold uppercase px-2.5 py-1 rounded-full bg-amber-500/15 text-amber-400 border border-amber-500/30">
            <Cog className="w-3 h-3 animate-spin" style={{ animationDuration: '3s' }} /> In Progress
          </span>
        );
      case 'completed':
        return (
          <span className="flex items-center gap-1 text-[10px] font-bold uppercase px-2.5 py-1 rounded-full bg-blue-500/15 text-blue-400 border border-blue-500/30">
            <CheckCircle className="w-3 h-3" /> Completed
          </span>
        );
      default:
        return null;
    }
  };

  const getProjectSignups = (projectId) => signups.filter(s => s.projectId === projectId);
  const isSignedUp = (projectId) => user && signups.some(s => s.projectId === projectId && s.username === user.mcUsername);

  useEffect(() => {
    if (!signupIntent || loading) return;
    if (!user) { setIsLoginOpen(true); return; } // re-runs after logging in
    const projectId = signupIntent;
    setSignupIntent(null);
    window.history.replaceState(null, '', `/projects?project=${encodeURIComponent(projectId)}`);
    const project = projects.find(p => String(p.id) === String(projectId));
    const count = signups.filter(s => s.projectId === projectId).length;
    const full = project?.maxParticipants && count >= project.maxParticipants;
    if (project && project.status === 'open' && !full && !isSignedUp(projectId)) handleSignUp(projectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signupIntent, loading, user]);

  if (loading) {
    return (
      <main className="flex-1 flex items-center justify-center bg-bg-dark text-text-warm font-sans p-8">
        <div className="text-center space-y-3">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-sp-gold mx-auto"></div>
          <p className="text-xs uppercase tracking-wider text-sp-gold/80 font-mono">Loading blueprints...</p>
        </div>
      </main>
    );
  }

  return (
    <main className="flex-1 bg-bg-dark text-text-warm font-sans">
      {/* Hero Header */}
      <div className="relative overflow-hidden border-b border-sp-bronze/30">
        <div className="absolute inset-0 bg-gradient-to-br from-sp-gold/5 via-transparent to-sp-copper/5"></div>
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_50%,rgba(205,127,50,0.08),transparent_50%)]"></div>
        <div className="relative max-w-6xl mx-auto px-4 py-12 md:py-16">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-lg bg-sp-gold/10 border border-sp-gold/30 flex items-center justify-center">
              <Hammer className="w-5 h-5 text-sp-gold" />
            </div>
            <div>
              <h1 className="font-serif text-2xl md:text-3xl font-bold text-white uppercase tracking-wider">
                Community Projects
              </h1>
              <p className="text-xs text-text-warm/50 font-mono uppercase tracking-wider mt-0.5">
                Collaborative blueprints & construction orders
              </p>
            </div>
          </div>
          <p className="text-sm text-text-warm/70 max-w-xl leading-relaxed">
            Join forces with fellow engineers to build, expand, and shape the world of iekcorbar. 
            Sign up for active projects and contribute your skills.
          </p>
        </div>
      </div>

      {/* Filters Bar */}
      <div className="max-w-6xl mx-auto px-4 py-5">
        <div className="flex flex-col md:flex-row gap-3 items-stretch md:items-center">
          {/* Search */}
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-warm/40" />
            <input
              type="text"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="Search blueprints..."
              className="w-full pl-10 pr-4 py-2.5 bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg focus:outline-hidden focus:border-sp-gold focus:ring-1 focus:ring-sp-gold/20 placeholder-text-warm/35 transition-all"
            />
          </div>

          {/* Category Filter */}
          <div className="flex items-center gap-2">
            <Filter className="w-3.5 h-3.5 text-text-warm/40 shrink-0" />
            <select
              value={categoryFilter}
              onChange={e => setCategoryFilter(e.target.value)}
              className="bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
            >
              {categories.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>

          {/* Status Filter */}
          <div className="flex items-center gap-2">
            <Target className="w-3.5 h-3.5 text-text-warm/40 shrink-0" />
            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              className="bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
            >
              {statuses.map(s => <option key={s} value={s}>{s === 'All' ? 'All Statuses' : s.replace('-', ' ').replace(/\b\w/g, l => l.toUpperCase())}</option>)}
            </select>
          </div>
        </div>
      </div>

      {/* Projects Grid */}
      <div className="max-w-6xl mx-auto px-4 pb-16">
        {filteredProjects.length === 0 ? (
          <div className="text-center py-16 text-text-warm/40 space-y-3">
            <Hammer className="w-12 h-12 mx-auto opacity-20" />
            <p className="text-sm">No blueprints found matching your criteria.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {filteredProjects.map(project => {
              const projectSignups = getProjectSignups(project.id);
              const isMember = isSignedUp(project.id);
              const isExpanded = expandedProject === project.id;
              const isFull = project.maxParticipants && projectSignups.length >= project.maxParticipants;
              const fillPercent = project.maxParticipants ? Math.min((projectSignups.length / project.maxParticipants) * 100, 100) : 0;

              return (
                <div
                  key={project.id}
                  id={`project-${project.id}`}
                  className={`bg-sp-dark/80 border rounded-xl overflow-hidden shadow-lg transition-all duration-300 hover:shadow-sp-gold/5 ${
                    highlightedProjectId === project.id
                      ? 'border-sp-gold ring-2 ring-sp-gold/50'
                      : isMember ? 'border-sp-gold/50 ring-1 ring-sp-gold/10' : 'border-sp-bronze/40'
                  }`}
                >
                  {/* Card Header */}
                  <div className="p-5 pb-3">
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap mb-1.5">
                          {getStatusBadge(project.status)}
                          <span className="text-[10px] font-mono text-sp-copper/80 bg-sp-copper/10 border border-sp-copper/20 px-2 py-0.5 rounded flex items-center gap-1">
                            <Tag className="w-2.5 h-2.5" /> {project.category}
                          </span>
                        </div>
                        <h3 className="font-serif text-base font-bold text-white uppercase tracking-wide leading-tight">
                          {project.title}
                        </h3>
                      </div>
                      <button
                        onClick={(e) => copyShareLink(project, e)}
                        title="Copy share link"
                        className={`p-1 rounded-sm transition-colors cursor-pointer shrink-0 ${copiedId === project.id ? 'text-emerald-400' : 'text-text-warm/30 hover:text-sp-gold'}`}
                      >
                        {copiedId === project.id ? <Check className="w-3.5 h-3.5" /> : <Share2 className="w-3.5 h-3.5" />}
                      </button>
                    </div>

                    {/* Participant Bar */}
                    <div className="flex items-center gap-3 mb-3">
                      <div className="flex items-center gap-1.5 text-[10px] font-mono text-text-warm/60">
                        <Users className="w-3.5 h-3.5" />
                        <span className="font-bold text-white">{projectSignups.length}</span>
                        {project.maxParticipants && <span>/ {project.maxParticipants}</span>}
                        <span className="text-text-warm/40">engineers</span>
                      </div>
                      {project.maxParticipants && (
                        <div className="flex-1 h-1.5 bg-neutral-800 rounded-full overflow-hidden">
                          <div 
                            className={`h-full rounded-full transition-all duration-500 ${
                              isFull ? 'bg-crimson' : fillPercent > 70 ? 'bg-amber-500' : 'bg-sp-gold'
                            }`}
                            style={{ width: `${fillPercent}%` }}
                          />
                        </div>
                      )}
                    </div>

                    {/* Participant Avatars */}
                    {projectSignups.length > 0 && (
                      <div className="flex items-center gap-1 mb-3">
                        {projectSignups.slice(0, 8).map(s => (
                          <PlayerAvatar
                            key={s.id}
                            mcUsername={s.username}
                            customSkinUrl={getCustomSkinUrl(s.username)}
                            size={24}
                            title={s.username}
                            className="rounded border border-sp-bronze/40 hover:border-sp-gold transition-colors"
                          />
                        ))}
                        {projectSignups.length > 8 && (
                          <span className="text-[9px] font-mono text-text-warm/40 ml-1">+{projectSignups.length - 8} more</span>
                        )}
                      </div>
                    )}

                    {/* Meta Info */}
                    <div className="flex items-center gap-3 text-[9px] font-mono text-text-warm/40">
                      <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        Created {new Date(project.createdAt).toLocaleDateString()}
                      </span>
                      <span>by <span className="text-sp-copper">{project.createdBy}</span></span>
                    </div>
                  </div>

                  {/* Expandable Description */}
                  <div className="border-t border-sp-bronze/20">
                    <button
                      onClick={() => setExpandedProject(isExpanded ? null : project.id)}
                      className="w-full flex items-center justify-between px-5 py-2.5 text-[10px] uppercase font-bold tracking-wider text-sp-gold/70 hover:text-sp-gold hover:bg-sp-gold/5 transition-all cursor-pointer"
                    >
                      <span className="flex items-center gap-1.5">
                        <Sparkles className="w-3 h-3" />
                        {isExpanded ? 'Hide Blueprint Details' : 'View Blueprint Details'}
                      </span>
                      {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                    </button>

                    {isExpanded && (
                      <div className="px-5 pb-5 animate-fade-in">
                        <div className="prose prose-invert prose-sm max-w-none text-text-warm/80 leading-relaxed text-xs">
                          {renderMarkdown(project.description)}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Action Bar */}
                  <div className="border-t border-sp-bronze/20 px-5 py-3 bg-neutral-950/40 flex items-center justify-between">
                    {project.status === 'completed' ? (
                      <span className="text-[10px] font-mono text-blue-400/70 uppercase font-bold flex items-center gap-1.5">
                        <CheckCircle className="w-3.5 h-3.5" /> Project Completed
                      </span>
                    ) : isMember ? (
                      <button
                        onClick={() => handleLeave(project.id)}
                        disabled={signingUp === project.id}
                        className="flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-bold uppercase tracking-wider border-2 border-crimson/40 bg-crimson/10 hover:bg-crimson/25 text-rose-300 hover:text-rose-200 transition-all cursor-pointer disabled:opacity-50"
                      >
                        <UserMinus className="w-3.5 h-3.5" />
                        {signingUp === project.id ? 'Leaving...' : 'Leave Project'}
                      </button>
                    ) : (
                      <button
                        onClick={() => handleSignUp(project.id)}
                        disabled={signingUp === project.id || isFull}
                        className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-bold uppercase tracking-wider border-2 transition-all cursor-pointer disabled:opacity-50 ${
                          isFull
                            ? 'border-neutral-600 bg-neutral-800 text-neutral-400 cursor-not-allowed'
                            : 'border-sp-gold/50 bg-sp-gold/10 hover:bg-sp-gold hover:text-black text-sp-gold shadow-md shadow-sp-gold/5'
                        }`}
                      >
                        <UserPlus className="w-3.5 h-3.5" />
                        {signingUp === project.id ? 'Joining...' : isFull ? 'Roster Full' : 'Join Project'}
                      </button>
                    )}

                    {isMember && (
                      <span className="text-[9px] font-mono text-sp-gold/60 flex items-center gap-1">
                        <CheckCircle className="w-3 h-3" /> You are enlisted
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </main>
  );
}
