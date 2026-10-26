import { db } from './db';

// Records one staff action in the audit log. `user` is the session user a
// route already has from requirePermission()/requireRole(). Never throws and
// never blocks the caller on failure — an audit write must not undo or fail
// the change it describes.
//
//   area    which part of the Staff Panel (usually a STAFF_TABS key such as
//           'wiki' or 'settings_rules'; 'roles', 'newsletter', 'files',
//           'members', 'gdpr' for the admin-only tools)
//   action  short verb: create | update | delete | reorder | send | ...
//   target  what it was done to (a page title, a username, ...)
//   detail  optional extra context (kept short)
//
// `throttleMs`: skip the entry if the same actor already produced the same
// area+action within that window (in-memory, per server process). Used for
// failed sign-in attempts so a guessing run cannot flood the table.
const recent = new Map();

export async function logAudit(user, { area, action, target, detail }, { throttleMs = 0 } = {}) {
  try {
    if (throttleMs > 0) {
      const key = `${user?.email || user?.mcUsername || 'unknown'}|${area}|${action}`;
      const now = Date.now();
      if (now - (recent.get(key) || 0) < throttleMs) return;
      recent.set(key, now);
      if (recent.size > 500) for (const [k, t] of recent) if (now - t > throttleMs) recent.delete(k);
    }
    await db.addAuditLog({
      actor: user?.mcUsername || user?.email || 'unknown',
      actorEmail: user?.email || null,
      actorRole: user?.role || null,
      area,
      action,
      target: target != null ? String(target).slice(0, 250) : null,
      detail: detail != null ? String(detail).slice(0, 1000) : null
    });
  } catch (err) {
    console.error('[audit] could not record entry:', err);
  }
}
