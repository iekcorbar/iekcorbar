import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireRole } from '@/lib/session';

export const dynamic = 'force-dynamic';

const DATE = /^\d{4}-\d{2}-\d{2}$/;

// A spreadsheet treats a cell starting with = + - @ as a formula; prefix such
// cells with an apostrophe so logged text (which players can influence, e.g.
// a bio or a note) can never execute when an admin opens the export.
function csvCell(value) {
  let v = value == null ? '' : String(value);
  if (/^[=+\-@\t\r]/.test(v)) v = `'${v}`;
  return `"${v.replace(/"/g, '""')}"`;
}

// Staff audit log viewer + export. Admin-only: it spans every area, including
// admin-only tools (roles, newsletter, account deletion) that no delegated
// Staff Panel permission covers. Read-only — nothing can edit or delete
// entries through the API.
export async function GET(request) {
  const auth = await requireRole(request, ['admin']);
  if (auth.error) return auth.error;

  const { searchParams } = new URL(request.url);
  const csv = searchParams.get('format') === 'csv';
  const from = searchParams.get('from');
  const to = searchParams.get('to');

  const result = await db.getAuditLog({
    limit: csv ? 5000 : Math.min(parseInt(searchParams.get('limit'), 10) || 50, 200),
    offset: csv ? 0 : searchParams.get('offset') || 0,
    area: searchParams.get('area') || null,
    actor: searchParams.get('actor') || null,
    q: searchParams.get('q') || null,
    from: from && DATE.test(from) ? from : null,
    to: to && DATE.test(to) ? to : null
  });

  if (!csv) return NextResponse.json(result);

  const header = ['When', 'Who', 'Role', 'Area', 'Action', 'Target', 'Detail'];
  const lines = [header.map(csvCell).join(',')];
  for (const e of result.entries) {
    lines.push([e.createdAt ? new Date(e.createdAt).toISOString() : '', e.actor, e.actorRole, e.area, e.action, e.target, e.detail].map(csvCell).join(','));
  }
  return new NextResponse('\uFEFF' + lines.join('\r\n'), {
    headers: {
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': `attachment; filename="audit-log-${new Date().toISOString().slice(0, 10)}.csv"`
    }
  });
}
