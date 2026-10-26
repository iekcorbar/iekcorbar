// Shared Story Room constants + helpers (Staff Panel → Story Room). Plain
// data/functions only, safe to import from both the client component
// (src/components/StoryRoomTab.js) and the API routes (src/app/api/story/**).

export const STORY_STATUSES = [
  { key: 'idea', label: 'Idea', className: 'border-text-warm/30 text-text-warm/70 bg-neutral-900/40' },
  { key: 'draft', label: 'Draft', className: 'border-sky-500/50 text-sky-300 bg-sky-500/10' },
  { key: 'review', label: 'Review', className: 'border-amber-500/50 text-amber-300 bg-amber-500/10' },
  { key: 'canon', label: 'Canon', className: 'border-emerald-500/50 text-emerald-300 bg-emerald-500/10' }
];
export const STORY_STATUS_KEYS = STORY_STATUSES.map(s => s.key);

export const STORY_ENTITY_KINDS = [
  { key: 'character', label: 'Character' },
  { key: 'place', label: 'Place' },
  { key: 'faction', label: 'Faction' },
  { key: 'item', label: 'Item' },
  { key: 'other', label: 'Other' }
];
export const STORY_ENTITY_KIND_KEYS = STORY_ENTITY_KINDS.map(k => k.key);

// The name shown for a Story Room action (lock holder, "last edited by",
// comment author) — same convention as the wiki's updatedBy.
export function storyActor(user) {
  return user?.mcUsername || user?.email || 'Staff';
}

// Lore links are written as [[Name]] inside a chapter. Returns the unique
// names in order of first appearance.
export function extractLoreLinks(text = '') {
  const names = [];
  const re = /\[\[([^\[\]]{1,150})\]\]/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const name = decodeHtmlEntities(m[1]).trim();
    if (name && !names.includes(name)) names.push(name);
  }
  return names;
}

function decodeHtmlEntities(s = '') {
  return s
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&');
}

// Converts a chapter body (Tiptap HTML — a small, known tag set) into the
// wiki's own markdown dialect (see src/lib/markdown.js: ##/###/#### headings,
// **bold**, [text](url), "- " lists, "> " quotes) for "Publish to wiki".
// Italic/strike have no wiki equivalent and are kept as plain text; lore
// links [[Name]] become bold names.
export function storyHtmlToWikiMarkdown(html = '') {
  let s = String(html || '');

  const inline = (t) => decodeHtmlEntities(
    t
      .replace(/<a [^>]*href="([^"]*)"[^>]*>([\s\S]*?)<\/a>/gi, (_, href, label) => `[${label.replace(/<[^>]+>/g, '')}](${href})`)
      .replace(/<(strong|b)>([\s\S]*?)<\/\1>/gi, '**$2**')
      .replace(/<br\s*\/?>/gi, ' ')
      .replace(/<[^>]+>/g, '')
  ).replace(/\[\[([^\[\]]+)\]\]/g, '**$1**').trim();

  const out = [];
  const blockRe = /<(h[1-6]|p|blockquote|ul|ol|hr)(\s[^>]*)?>([\s\S]*?)<\/\1>|<hr\s*\/?>/gi;
  let m;
  while ((m = blockRe.exec(s)) !== null) {
    // The wiki renderer has no horizontal rule, so a scene break becomes a
    // centred-looking ornament line.
    if (!m[1] || m[1].toLowerCase() === 'hr') { out.push('⁂'); continue; }
    const tag = m[1].toLowerCase();
    const body = m[3] || '';
    if (/^h[1-6]$/.test(tag)) {
      const level = Math.min(4, Math.max(2, Number(tag[1])));
      out.push(`${'#'.repeat(level)} ${inline(body)}`);
    } else if (tag === 'p') {
      const text = inline(body);
      if (text) out.push(text);
    } else if (tag === 'blockquote') {
      const lines = body.split(/<\/p>\s*<p[^>]*>/i).map(inline).filter(Boolean);
      if (lines.length) out.push(lines.map(line => `> ${line}`).join('\n'));
    } else if (tag === 'ul' || tag === 'ol') {
      const items = body.match(/<li[^>]*>([\s\S]*?)<\/li>/gi) || [];
      const lines = items.map(li => `- ${inline(li.replace(/^<li[^>]*>|<\/li>$/gi, ''))}`);
      out.push(lines.join('\n'));
    }
  }
  return out.join('\n\n').trim();
}
