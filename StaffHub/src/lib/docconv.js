// Document-omzetter voor handleidingen: de hub bewaart ze als TipTap-HTML, maar
// staff wil ze ook als .md meenemen (of een bestaand .md/-txt/-html-document
// just in de hub zetten). Geen dependency, want het werkveld is klein en
// bekend: de HTML komt uit onszelf (TipTap StarterKit + Image + Link) en de
// upload is platte tekst.
//
// Veiligheid voorop: mdToHtml ESCAAPT alle HTML vóór het opmaken, dus een
// geüpload bestand kan nooit script meenemen dat in de editor renderen. De
// editor-zelf schrijft zijn eigen HTML en gaat daar langs heen (PUT /api/page).

const esc = (s) => String(s == null ? '' : s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// ---------------------------------------------------------------- html → markdown

export function htmlToMd(html) {
  let s = String(html || '');
  s = s.replace(/<img[^>]*>/gi, (tag) => {
    const src = (tag.match(/src="([^"]*)"/i) || [])[1] || '';
    const alt = (tag.match(/alt="([^"]*)"/i) || [])[1] || 'afbeelding';
    return src ? `\n![${alt}](${src})\n` : '';
  });
  s = s.replace(/<a\s[^>]*href="([^"]*)"[^>]*>([\s\S]*?)<\/a>/gi, (_, href, txt) => `[${txt.replace(/<[^>]+>/g, '').trim()}](${href})`);
  s = s.replace(/<(strong|b)>([\s\S]*?)<\/\1>/gi, '**$2**');
  s = s.replace(/<(em|i)>([\s\S]*?)<\/\1>/gi, '*$2*');
  s = s.replace(/<(s|strike|del)>([\s\S]*?)<\/\1>/gi, '~~$2~~');
  s = s.replace(/<code>([\s\S]*?)<\/code>/gi, '`$1`');
  s = s.replace(/<pre[^>]*>([\s\S]*?)<\/pre>/gi, (_, c) => '\n```\n' + c.replace(/<[^>]+>/g, '') + '\n```\n');
  s = s.replace(/<blockquote>([\s\S]*?)<\/blockquote>/gi, (_, c) => c.replace(/<\/?p>/gi, '').split('\n').map((l) => '> ' + l.trim()).join('\n') + '\n');
  s = s.replace(/<h([1-6])[^>]*>([\s\S]*?)<\/h\1>/gi, (_, n, c) => '\n' + '#'.repeat(Number(n)) + ' ' + inline(c) + '\n');
  s = s.replace(/<ul[^>]*>([\s\S]*?)<\/ul>/gi, (_, c) => '\n' + c.replace(/<li[^>]*>([\s\S]*?)<\/li>/gi, (m, i) => '- ' + inline(i).replace(/\n+/g, ' ') + '\n') + '\n');
  let n = 0;
  s = s.replace(/<ol[^>]*>([\s\S]*?)<\/ol>/gi, (_, c) => {
    n = 0;
    return '\n' + c.replace(/<li[^>]*>([\s\S]*?)<\/li>/gi, (m, i) => `${++n}. ` + inline(i).replace(/\n+/g, ' ') + '\n') + '\n';
  });
  s = s.replace(/<hr[^>]*>/gi, '\n---\n');
  s = s.replace(/<br\s*\/?>/gi, '\n');
  s = s.replace(/<\/(p|div|tr)>/gi, '\n\n').replace(/<(p|div)[^>]*>/gi, '');
  s = s.replace(/<[^>]+>/g, '');                       // wat overblijft: losse tags
  s = s.replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'");
  return s.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim() + '\n';
}
const inline = (c) => String(c || '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();

// ---------------------------------------------------------------- markdown → html

/** Eén regel platte tekst naar inline-HTML (al ge-escapet door de aanroeper). */
function inlineMd(t) {
  let s = esc(t);
  s = s.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, '<img src="$2" alt="$1">');
  s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, txt, href) => {
    const ok = /^(https?:|mailto:|\/)/i.test(href);            // geen javascript:...
    return ok ? `<a href="${esc(href)}">${txt}</a>` : txt;
  });
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>');
  s = s.replace(/~~([^~]+)~~/g, '<s>$1</s>');
  return s;
}

/**
 * Markdown (of platte tekst) naar het HTML-dat TipTap snapt.
 * Eerlijk: een regel-generator voor de gangbare opbouw, geen volledige
 * CommonMark-implementatie - tabellen en geneste lijsten blijven plat tekst.
 */
export function mdToHtml(md) {
  const lines = String(md || '').replace(/\r\n/g, '\n').split('\n');
  const out = [];
  let para = [], list = null, code = null, quote = [];

  const flushPara = () => { if (para.length) { out.push('<p>' + inlineMd(para.join(' ')) + '</p>'); para = []; } };
  const flushList = () => { if (list) { out.push(`<${list.tag}>` + list.items.map((i) => '<li>' + inlineMd(i) + '</li>').join('') + `</${list.tag}>`); list = null; } };
  const flushQuote = () => { if (quote.length) { out.push('<blockquote>' + quote.map((q) => '<p>' + inlineMd(q) + '</p>').join('') + '</blockquote>'); quote = []; } };
  const flushAll = () => { flushPara(); flushList(); flushQuote(); };

  for (const raw of lines) {
    const line = raw.trimEnd();
    if (code !== null) {
      if (/^```/.test(line.trim())) { out.push('<pre><code>' + esc(code.join('\n')) + '</code></pre>'); code = null; }
      else code.push(raw);
      continue;
    }
    if (/^```/.test(line.trim())) { flushAll(); code = []; continue; }
    if (!line.trim()) { flushAll(); continue; }

    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) { flushAll(); out.push(`<h${h[1].length}>${inlineMd(h[2])}</h${h[1].length}>`); continue; }
    if (/^(---|\*\*\*|___)\s*$/.test(line)) { flushAll(); out.push('<hr>'); continue; }

    const q = line.match(/^>\s?(.*)$/);
    if (q) { flushPara(); flushList(); quote.push(q[1]); continue; }

    const ul = line.match(/^[-*+]\s+(.*)$/);
    const ol = line.match(/^\d+[.)]\s+(.*)$/);
    if (ul || ol) {
      flushPara(); flushQuote();
      const tag = ul ? 'ul' : 'ol';
      if (!list || list.tag !== tag) { flushList(); list = { tag, items: [] }; }
      list.items.push((ul || ol)[1]);
      continue;
    }
    flushList(); flushQuote();
    para.push(line.trim());
  }
  if (code !== null) out.push('<pre><code>' + esc(code.join('\n')) + '</code></pre>');  // niet gesloten blok
  flushAll();
  return out.join('');
}
