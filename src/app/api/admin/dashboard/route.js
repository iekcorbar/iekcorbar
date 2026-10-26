import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { getRoleTabs } from '@/lib/permissions';

export const dynamic = 'force-dynamic';

const DAY = 24 * 60 * 60 * 1000;

// Areas whose history this feed already builds from their own tables (and
// which therefore must not be added a second time from the audit log).
const COVERED_BY_SOURCES = ['moderation', 'console', 'reports', 'bugs', 'applications', 'wiki', 'updates'];
// Audit areas that map onto a Staff Panel tab the activity row can open.
const AREA_TAB = { events: 'events', polls: 'polls', news: 'news', members: 'moderation' };

const ts = (v) => {
  const t = v ? new Date(v).getTime() : NaN;
  return Number.isNaN(t) ? 0 : t;
};

// Staff Panel overview: "what needs attention" counters plus a recent-activity
// feed. Everything is filtered by the caller's own Staff Panel permissions
// (getRoleTabs) so a role only ever sees counters/events for tabs it can open
// — e.g. a Helper with just Reports never learns about RCON console commands.
// The feed is assembled from tables that already record who/when (moderation
// log, console log, reports, bug reports, staff applications, wiki, updates);
// it is a read-only view, nothing here writes.
export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });

  const tabs = await getRoleTabs(user.role);
  if (tabs.length === 0) return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
  const can = (k) => tabs.includes(k);

  // A failing source must not blank the whole overview — each one degrades to [].
  const safe = (promise) => promise.catch(() => []);
  const isAdmin = user.role === 'admin';
  // Admin sees every audit area; other roles only the areas they hold a tab for.
  const auditAreas = isAdmin ? null : tabs;
  const [reports, bugs, applications, modLogs, consoleLog, wikiPages, updates, users, audit] = await Promise.all([
    can('reports') ? safe(db.getReports()) : [],
    can('bugs') ? safe(db.getBugReports()) : [],
    can('applications') ? safe(db.getStaffApplications()) : [],
    can('moderation') ? safe(db.getModerationLogs()) : [],
    can('console') ? safe(db.getRconConsoleLog()) : [],
    can('wiki') ? safe(db.getWikiPages()) : [],
    can('updates') ? safe(db.getUpdates()) : [],
    safe(db.getUsers()),
    db.getAuditLog({ limit: 40, areas: auditAreas, excludeAreas: COVERED_BY_SOURCES }).then(r => r.entries).catch(() => [])
  ]);

  const weekAgo = Date.now() - 7 * DAY;
  const stats = [];
  if (can('reports')) {
    stats.push({ key: 'reports', tab: 'reports', label: 'Open reports', value: reports.filter(r => r.status === 'open').length, urgent: true });
  }
  if (can('bugs')) {
    stats.push({ key: 'bugs', tab: 'bugs', label: 'Open bug reports', value: bugs.filter(b => b.status === 'open').length, urgent: true });
  }
  if (can('applications')) {
    stats.push({ key: 'applications', tab: 'applications', label: 'Pending applications', value: applications.filter(a => a.status === 'pending').length, urgent: true });
  }
  if (can('moderation')) {
    stats.push({ key: 'sanctions', tab: 'moderation', label: 'Sanctions this week', value: modLogs.filter(l => l.type !== 'note' && ts(l.date) >= weekAgo).length });
  }
  stats.push({ key: 'members', tab: null, label: 'Registered members', value: users.length });

  const activity = [];
  modLogs.forEach(l => activity.push({
    id: `mod-${l.id}`, kind: 'moderation', tab: 'moderation', at: ts(l.date), actor: l.staffMember,
    text: l.type === 'note' ? `added a staff note on ${l.username}` : `issued a ${l.type} on ${l.username}`,
    detail: l.reason
  }));
  consoleLog.forEach(l => activity.push({
    id: `con-${l.id}`, kind: 'console', tab: 'console', at: ts(l.date), actor: l.staffMember,
    text: 'ran a console command', detail: l.command
  }));
  reports.forEach(r => activity.push({
    id: `rep-${r.id}`, kind: 'reports', tab: 'reports', at: ts(r.submittedAt), actor: null,
    text: `New report against ${r.targetUsername}`, detail: r.category
  }));
  bugs.forEach(b => {
    activity.push({ id: `bug-${b.id}`, kind: 'bugs', tab: 'bugs', at: ts(b.submittedAt), actor: b.reporterUsername || null, text: 'reported a bug', detail: b.subject });
    if (b.resolvedAt) {
      activity.push({ id: `bugr-${b.id}`, kind: 'bugs', tab: 'bugs', at: ts(b.resolvedAt), actor: b.resolvedBy || null, text: `marked a bug "${String(b.status).replace('_', ' ')}"`, detail: b.subject });
    }
  });
  applications.forEach(a => {
    activity.push({ id: `app-${a.id}`, kind: 'applications', tab: 'applications', at: ts(a.createdAt), actor: a.applicantUsername || null, text: 'applied for staff', detail: null });
    if (a.reviewedAt) {
      activity.push({ id: `appr-${a.id}`, kind: 'applications', tab: 'applications', at: ts(a.reviewedAt), actor: a.reviewedBy || null, text: `${a.status} the staff application of ${a.applicantUsername || a.applicantEmail}`, detail: null });
    }
  });
  wikiPages.forEach(p => activity.push({
    id: `wiki-${p.id || p.slug}`, kind: 'wiki', tab: 'wiki', at: ts(p.updatedAt), actor: p.updatedBy || null,
    text: 'edited a wiki page', detail: p.title
  }));
  updates.forEach(u => activity.push({
    id: `upd-${u.id}`, kind: 'updates', tab: 'updates', at: ts(u.date), actor: u.author || null,
    text: 'posted an update', detail: u.title
  }));

  audit.forEach(a => {
    const settingsArea = String(a.area).startsWith('settings_');
    activity.push({
      id: `aud-${a.id}`,
      kind: settingsArea ? 'settings' : a.area,
      tab: settingsArea ? 'settings' : (AREA_TAB[a.area] || null),
      at: ts(a.createdAt),
      actor: a.actor,
      text: `${a.action}${a.target ? ` — ${a.target}` : ''}`,
      detail: a.detail || null
    });
  });

  const feed = activity
    .filter(a => a.at > 0)
    .sort((a, b) => b.at - a.at)
    .slice(0, 60)
    .map(a => ({ ...a, at: new Date(a.at).toISOString() }));

  return NextResponse.json({ stats, activity: feed });
}
