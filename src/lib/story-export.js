// Story Room downloads: turns chapter / lore bodies (Tiptap HTML) into Word
// (.docx), Markdown, plain text or a standalone HTML page, and bundles a
// folder or the whole Story Room into a ZIP. Server-only (uses zip.js).
//
// Bodies are first parsed into a small block list (headings, paragraphs,
// quotes, list items, scene breaks — the editor's whole vocabulary, see
// StoryEditor.js) and every format is rendered from that, so the output is
// always rebuilt from escaped text and never passes stored HTML through.
import { createZip } from './zip';
import { STORY_STATUSES, STORY_ENTITY_KINDS } from './story';
import { isCompressedStoryFile } from './story-files';

export const STORY_EXPORT_FORMATS = {
  docx: { ext: 'docx', label: 'Word', mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  md: { ext: 'md', label: 'Markdown', mime: 'text/markdown; charset=utf-8' },
  txt: { ext: 'txt', label: 'Plain text', mime: 'text/plain; charset=utf-8' },
  html: { ext: 'html', label: 'Web page', mime: 'text/html; charset=utf-8' }
};

/* ------------------------------ parsing ------------------------------ */

function decodeEntities(s) {
  return s
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&amp;/g, '&');
}

function attr(attrs, name) {
  const m = new RegExp(`\\b${name}\\s*=\\s*("([^"]*)"|'([^']*)')`, 'i').exec(attrs || '');
  return m ? decodeEntities(m[2] ?? m[3] ?? '') : null;
}

// -> [{ kind: 'h'|'p'|'quote'|'li'|'hr', level?, ordered?, index?, depth?, runs: [{ text, bold, italic, strike, code, href }] }]
export function parseStoryHtml(html) {
  const blocks = [];
  let cur = null;
  const marks = { bold: 0, italic: 0, strike: 0, code: 0 };
  let href = null;
  const lists = [];
  let quoteDepth = 0;
  let liDepth = 0;

  const start = (kind, extra = {}) => { cur = { kind, runs: [], ...extra }; blocks.push(cur); };
  const text = (t) => {
    if (!t) return;
    if (!cur) start(quoteDepth ? 'quote' : 'p');
    cur.runs.push({ text: t, bold: marks.bold > 0, italic: marks.italic > 0, strike: marks.strike > 0, code: marks.code > 0, href });
  };

  const re = /<!--[\s\S]*?-->|<(\/?)([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>|([^<]+)/g;
  // The editor never produces these, but a body saved straight through the
  // API could contain them — drop their contents rather than export them.
  const source = String(html || '').replace(/<(script|style)\b[\s\S]*?<\/\1\s*>/gi, '');
  let m;
  while ((m = re.exec(source)) !== null) {
    if (m[4] !== undefined) { text(decodeEntities(m[4]).replace(/[\r\n\t]+/g, ' ')); continue; }
    if (!m[2]) continue;
    const closing = m[1] === '/';
    const tag = m[2].toLowerCase();
    const attrs = m[3];

    if (!closing) {
      if (/^h[1-6]$/.test(tag)) start('h', { level: Number(tag[1]) });
      else if (tag === 'p' || tag === 'pre' || tag === 'div') {
        if (liDepth > 0 && cur && cur.kind === 'li') { if (cur.runs.length) text('\n'); }
        else start(quoteDepth ? 'quote' : 'p');
      }
      else if (tag === 'blockquote') { quoteDepth++; cur = null; }
      else if (tag === 'ul' || tag === 'ol') { lists.push({ ordered: tag === 'ol', index: Math.max(0, (parseInt(attr(attrs, 'start'), 10) || 1) - 1) }); cur = null; }
      else if (tag === 'li') {
        liDepth++;
        const top = lists[lists.length - 1] || { ordered: false, index: 0 };
        top.index++;
        start('li', { ordered: top.ordered, index: top.index, depth: Math.max(0, lists.length - 1) });
      }
      else if (tag === 'hr') { blocks.push({ kind: 'hr', runs: [] }); cur = null; }
      else if (tag === 'br') text('\n');
      else if (tag === 'strong' || tag === 'b') marks.bold++;
      else if (tag === 'em' || tag === 'i') marks.italic++;
      else if (tag === 's' || tag === 'strike' || tag === 'del') marks.strike++;
      else if (tag === 'code') marks.code++;
      else if (tag === 'a') href = attr(attrs, 'href');
    } else {
      if (/^h[1-6]$/.test(tag)) cur = null;
      else if (tag === 'p' || tag === 'pre' || tag === 'div') { if (!(liDepth > 0 && cur && cur.kind === 'li')) cur = null; }
      else if (tag === 'blockquote') { quoteDepth = Math.max(0, quoteDepth - 1); cur = null; }
      else if (tag === 'ul' || tag === 'ol') { lists.pop(); cur = null; }
      else if (tag === 'li') { liDepth = Math.max(0, liDepth - 1); cur = null; }
      else if (tag === 'strong' || tag === 'b') marks.bold = Math.max(0, marks.bold - 1);
      else if (tag === 'em' || tag === 'i') marks.italic = Math.max(0, marks.italic - 1);
      else if (tag === 's' || tag === 'strike' || tag === 'del') marks.strike = Math.max(0, marks.strike - 1);
      else if (tag === 'code') marks.code = Math.max(0, marks.code - 1);
      else if (tag === 'a') href = null;
    }
  }

  return blocks.filter(b => b.kind === 'hr' || b.runs.some(r => r.text.trim()));
}

// Plain-text paragraphs (e.g. a timeline description) as blocks.
function textToBlocks(t) {
  return String(t || '').split(/\n{2,}/).map(p => p.trim()).filter(Boolean)
    .map(p => ({ kind: 'p', runs: [{ text: p }] }));
}

const runsText = (runs) => runs.map(r => r.text).join('');

/* ------------------------------ helpers ------------------------------ */

export function safeFileName(name, fallback = 'Untitled') {
  const s = String(name || '').replace(/[\\/:*?"<>|\x00-\x1f]+/g, ' ').replace(/\s+/g, ' ').replace(/[. ]+$/, '').trim().slice(0, 80);
  return s || fallback;
}

const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }) : '');
const statusLabel = (key) => STORY_STATUSES.find(s => s.key === key)?.label || key || '';
const kindLabel = (key) => STORY_ENTITY_KINDS.find(k => k.key === key)?.label || key || 'Other';

function chapterMeta(node) {
  return [
    node.status ? `Status: ${statusLabel(node.status)}` : null,
    node.updatedBy ? `Last edited by ${node.updatedBy}${node.updatedAt ? `, ${fmtDate(node.updatedAt)}` : ''}` : null
  ].filter(Boolean).join(' · ');
}

function absoluteHref(href, origin) {
  if (!href) return null;
  if (/^(https?:|mailto:)/i.test(href)) return href;
  if (href.startsWith('/') && origin) return origin.replace(/\/$/, '') + href;
  return null;
}

/* ------------------------------ Markdown ------------------------------ */

function mdRuns(runs) {
  return runs.map(r => {
    if (r.text === '\n') return '  \n';
    const m = /^(\s*)([\s\S]*?)(\s*)$/.exec(r.text);
    let t = m[2];
    if (!t) return r.text;
    if (r.code) t = '`' + t + '`';
    if (r.bold) t = `**${t}**`;
    if (r.italic) t = `*${t}*`;
    if (r.strike) t = `~~${t}~~`;
    if (r.href) t = `[${t}](${r.href})`;
    return m[1] + t + m[3];
  }).join('');
}

function blocksToMarkdown(blocks) {
  const out = [];
  let prevLi = false;
  for (const b of blocks) {
    let line;
    if (b.kind === 'h') line = `${'#'.repeat(Math.min(6, Math.max(2, b.level)))} ${mdRuns(b.runs).trim()}`;
    else if (b.kind === 'quote') line = mdRuns(b.runs).trim().split('\n').map(l => `> ${l}`).join('\n');
    else if (b.kind === 'li') line = `${'   '.repeat(b.depth || 0)}${b.ordered ? `${b.index}.` : '-'} ${mdRuns(b.runs).trim().replace(/\n/g, '\n   ')}`;
    else if (b.kind === 'hr') line = '---';
    else line = mdRuns(b.runs).trim();
    if (prevLi && b.kind === 'li') out[out.length - 1] += '\n' + line;
    else out.push(line);
    prevLi = b.kind === 'li';
  }
  return out.join('\n\n');
}

export function toMarkdown(title, blocks, meta) {
  return [`# ${title}`, meta ? `*${meta}*` : null, blocksToMarkdown(blocks)].filter(Boolean).join('\n\n') + '\n';
}

/* ------------------------------ Plain text ------------------------------ */

export function toText(title, blocks, meta) {
  const out = [title, '='.repeat(Math.min(80, Math.max(3, title.length)))];
  if (meta) out.push(meta);
  for (const b of blocks) {
    const t = runsText(b.runs).trim();
    if (b.kind === 'h') out.push(`${t}\n${'-'.repeat(Math.min(80, Math.max(3, t.length)))}`);
    else if (b.kind === 'quote') out.push(t.split('\n').map(l => `    ${l}`).join('\n'));
    else if (b.kind === 'li') out.push(`${'   '.repeat(b.depth || 0)}${b.ordered ? `${b.index}.` : '•'} ${t}`);
    else if (b.kind === 'hr') out.push('* * *');
    else out.push(t);
  }
  return out.join('\n\n').replace(/\r?\n/g, '\r\n') + '\r\n';
}

/* ------------------------------ HTML ------------------------------ */

const escHtml = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function htmlRuns(runs, origin) {
  return runs.map(r => {
    if (r.text === '\n') return '<br>';
    let t = escHtml(r.text);
    if (r.code) t = `<code>${t}</code>`;
    if (r.bold) t = `<strong>${t}</strong>`;
    if (r.italic) t = `<em>${t}</em>`;
    if (r.strike) t = `<s>${t}</s>`;
    const href = absoluteHref(r.href, origin);
    if (href) t = `<a href="${escHtml(href)}">${t}</a>`;
    return t;
  }).join('');
}

export function toHtml(title, blocks, meta, { origin } = {}) {
  const body = [];
  let openList = null;
  const closeList = () => { if (openList) { body.push(`</${openList}>`); openList = null; } };
  for (const b of blocks) {
    if (b.kind === 'li') {
      const tag = b.ordered ? 'ol' : 'ul';
      if (openList !== tag) { closeList(); body.push(`<${tag}>`); openList = tag; }
      body.push(`<li${b.depth ? ` style="margin-left:${b.depth * 1.5}em"` : ''}>${htmlRuns(b.runs, origin)}</li>`);
      continue;
    }
    closeList();
    if (b.kind === 'h') { const l = Math.min(4, Math.max(2, b.level)); body.push(`<h${l}>${htmlRuns(b.runs, origin)}</h${l}>`); }
    else if (b.kind === 'quote') body.push(`<blockquote><p>${htmlRuns(b.runs, origin)}</p></blockquote>`);
    else if (b.kind === 'hr') body.push('<hr>');
    else body.push(`<p>${htmlRuns(b.runs, origin)}</p>`);
  }
  closeList();
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escHtml(title)}</title>
<style>
  body { font-family: Georgia, 'Times New Roman', serif; line-height: 1.65; color: #222; background: #fdfbf7; max-width: 42em; margin: 3em auto; padding: 0 1.25em; }
  h1 { font-size: 2em; margin-bottom: .2em; } .meta { color: #777; font-style: italic; margin-top: 0; }
  blockquote { border-left: 3px solid #b87333; margin: 1em 0; padding: .1em 1em; color: #555; font-style: italic; }
  hr { border: 0; text-align: center; margin: 2em 0; } hr::after { content: '* * *'; color: #999; letter-spacing: .5em; }
  a { color: #8a4b12; } code { font-family: Consolas, monospace; background: #f0ebe2; padding: 0 .2em; }
</style>
</head>
<body>
<h1>${escHtml(title)}</h1>
${meta ? `<p class="meta">${escHtml(meta)}</p>\n` : ''}${body.join('\n')}
</body>
</html>
`;
}

/* ------------------------------ Word (.docx) ------------------------------ */

const escXml = (s) => String(s)
  .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const W_NS = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"';

const DOCX_STYLES = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles ${W_NS}>
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Georgia" w:hAnsi="Georgia" w:eastAsia="Georgia" w:cs="Georgia"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:val="en-GB"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="300" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="80"/></w:pPr><w:rPr><w:b/><w:sz w:val="48"/><w:szCs w:val="48"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="320"/></w:pPr><w:rPr><w:i/><w:color w:val="777777"/><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="120"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="34"/><w:szCs w:val="34"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="280" w:after="100"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="2"/></w:pPr><w:rPr><w:b/><w:i/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:ind w:left="720" w:right="720"/></w:pPr><w:rPr><w:i/><w:color w:val="555555"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="60"/></w:pPr></w:style>
<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:rPr><w:color w:val="8A4B12"/><w:u w:val="single"/></w:rPr></w:style>
</w:styles>`;

function docxRuns(runs, links, origin) {
  return runs.map(r => {
    const props = [
      r.href ? '<w:rStyle w:val="Hyperlink"/>' : '',
      r.code ? '<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/>' : '',
      r.bold ? '<w:b/>' : '',
      r.italic ? '<w:i/>' : '',
      r.strike ? '<w:strike/>' : ''
    ].join('');
    const rPr = props ? `<w:rPr>${props}</w:rPr>` : '';
    const parts = r.text.split('\n');
    const inner = parts.map((p, i) => `${i > 0 ? '<w:br/>' : ''}${p ? `<w:t xml:space="preserve">${escXml(p)}</w:t>` : ''}`).join('');
    const run = `<w:r>${rPr}${inner}</w:r>`;
    const href = absoluteHref(r.href, origin);
    if (!href) return run;
    let id = links.get(href);
    if (!id) { id = `rIdL${links.size + 1}`; links.set(href, id); }
    return `<w:hyperlink r:id="${id}" w:history="1">${run}</w:hyperlink>`;
  }).join('');
}

const para = (style, inner, extraPPr = '') => `<w:p><w:pPr>${style ? `<w:pStyle w:val="${style}"/>` : ''}${extraPPr}</w:pPr>${inner}</w:p>`;
const plainRun = (t) => `<w:r><w:t xml:space="preserve">${escXml(t)}</w:t></w:r>`;

export function toDocx(title, blocks, meta, { origin } = {}) {
  const links = new Map();
  const body = [para('Title', plainRun(title))];
  if (meta) body.push(para('Subtitle', plainRun(meta)));
  for (const b of blocks) {
    if (b.kind === 'h') {
      const style = `Heading${Math.min(3, Math.max(1, b.level - 1))}`;
      body.push(para(style, docxRuns(b.runs, links, origin)));
    } else if (b.kind === 'quote') {
      body.push(para('Quote', docxRuns(b.runs, links, origin)));
    } else if (b.kind === 'li') {
      const left = 720 + (b.depth || 0) * 360;
      const bullet = b.ordered ? `${b.index}.` : '•';
      body.push(para('ListParagraph', `${plainRun(bullet)}<w:r><w:tab/></w:r>${docxRuns(b.runs, links, origin)}`, `<w:ind w:left="${left}" w:hanging="360"/>`));
    } else if (b.kind === 'hr') {
      body.push(para(null, `<w:r><w:rPr><w:color w:val="999999"/></w:rPr><w:t xml:space="preserve">*   *   *</w:t></w:r>`, '<w:spacing w:before="240" w:after="240"/><w:jc w:val="center"/>'));
    } else {
      body.push(para(null, docxRuns(b.runs, links, origin)));
    }
  }

  const document = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document ${W_NS}><w:body>${body.join('')}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>`;

  const docRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>${
  [...links.entries()].map(([href, id]) => `<Relationship Id="${id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${escXml(href)}" TargetMode="External"/>`).join('')
}</Relationships>`;

  const now = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
  const core = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>${escXml(title)}</dc:title><dc:creator>iekcorbar Story Room</dc:creator><dcterms:created xsi:type="dcterms:W3CDTF">${now}</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">${now}</dcterms:modified></cp:coreProperties>`;

  return createZip([
    { name: '[Content_Types].xml', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>` },
    { name: '_rels/.rels', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>` },
    { name: 'docProps/core.xml', data: core },
    { name: 'word/document.xml', data: document },
    { name: 'word/styles.xml', data: DOCX_STYLES },
    { name: 'word/_rels/document.xml.rels', data: docRels }
  ]);
}

/* ------------------------------ one document ------------------------------ */

function render(format, title, blocks, meta, opts) {
  if (format === 'docx') return toDocx(title, blocks, meta, opts);
  if (format === 'md') return toMarkdown(title, blocks, meta);
  if (format === 'txt') return toText(title, blocks, meta);
  return toHtml(title, blocks, meta, opts);
}

export function renderChapter(node, format, opts) {
  return render(format, node.title, parseStoryHtml(node.content), chapterMeta(node), opts);
}

function renderEntity(entity, format, opts) {
  const blocks = parseStoryHtml(entity.content);
  const meta = [kindLabel(entity.kind), entity.summary].filter(Boolean).join(' — ');
  return render(format, entity.name, blocks, meta, opts);
}

function renderTimeline(events, format, opts) {
  const blocks = [];
  for (const ev of events) {
    blocks.push({ kind: 'h', level: 3, runs: [{ text: `${ev.eraLabel || `#${ev.sortKey}`} — ${ev.title}` }] });
    blocks.push(...textToBlocks(ev.description));
    if (ev.nodeTitle) blocks.push({ kind: 'p', runs: [{ text: `Chapter: ${ev.nodeTitle}`, italic: true }] });
  }
  return render(format, 'Timeline', blocks, `${events.length} event${events.length === 1 ? '' : 's'}`, opts);
}

/* ------------------------------ packages ------------------------------ */

function childrenOf(nodes, parentId) {
  return nodes
    .filter(n => (n.parentId || null) === (parentId || null))
    .sort((a, b) => (a.sortOrder - b.sortOrder) || a.title.localeCompare(b.title));
}

// Adds every chapter under `parentId` to `files`, mirroring the folder tree
// with "01 - " order prefixes so the story order survives in a file browser.
function addChapterTree(files, nodes, parentId, basePath, format, opts) {
  const kids = childrenOf(nodes, parentId);
  const pad = String(kids.length).length < 2 ? 2 : String(kids.length).length;
  const used = new Set();
  kids.forEach((n, i) => {
    let name = `${String(i + 1).padStart(pad, '0')} - ${safeFileName(n.title)}`;
    while (used.has(name.toLowerCase())) name += '_';
    used.add(name.toLowerCase());
    if (n.type === 'folder') addChapterTree(files, nodes, n.id, `${basePath}${name}/`, format, opts);
    else files.push({ name: `${basePath}${name}.${STORY_EXPORT_FORMATS[format].ext}`, data: renderChapter(n, format, opts) });
  });
}

// Uploaded files go under "Files/", grouped by what they're attached to.
// `uploads` are rows from db.listStoryFiles() with a `data` Buffer added by
// the route (rows it couldn't or didn't load have no data and are skipped).
function addUploads(files, uploads, basePath) {
  const used = new Set();
  for (const u of uploads) {
    if (!u.data) continue;
    const group = u.nodeTitle ? `Chapters/${safeFileName(u.nodeTitle)}`
      : u.entityName ? `Lore/${safeFileName(u.entityName)}`
      : 'General';
    const dot = u.originalName.lastIndexOf('.');
    const stem = safeFileName(dot > 0 ? u.originalName.slice(0, dot) : u.originalName, 'file');
    const ext = dot > 0 ? u.originalName.slice(dot) : '';
    let name = `${basePath}${group}/${stem}${ext}`;
    for (let i = 2; used.has(name.toLowerCase()); i++) name = `${basePath}${group}/${stem} (${i})${ext}`;
    used.add(name.toLowerCase());
    files.push({ name, data: u.data, store: isCompressedStoryFile(u.originalName) });
  }
}

const uploadMeta = (uploads) => uploads.map(({ data, storedName, ...u }) => u);

function readme({ scopeLabel, format, exportedBy, uploads = [], skipped = [] }) {
  const f = STORY_EXPORT_FORMATS[format];
  const included = uploads.filter(u => u.data).length;
  return [
    'iekcorbar Story Room download',
    '=============================',
    '',
    `Contents: ${scopeLabel}`,
    `Downloaded: ${new Date().toLocaleString('en-GB')}${exportedBy ? ` by ${exportedBy}` : ''}`,
    `Format: ${f.label} (.${f.ext})`,
    '',
    'Chapters are numbered in story order ("01 - ...") and keep the same folder',
    'structure as the Story Room.',
    '',
    'This is a copy. Changes you make to these files do not appear in the Story',
    'Room by themselves - send your edited files to the staff, or ask them for a',
    'guest link so you can work in the Story Room editor directly.',
    '',
    'story-backup.json (full download only) is a complete machine-readable backup',
    'of every chapter, lore entry and timeline event.',
    '',
    ...(included ? [`Uploaded files (${included}) are in the "Files" folder, grouped by the chapter`, 'or lore entry they belong to ("General" = not attached to anything).', ''] : []),
    ...(skipped.length ? [
      `${skipped.length} uploaded file(s) were too large to fit in this download and were left out.`,
      'Download them one by one from the Files tab in the Story Room:',
      ...skipped.map(n => `  - ${n}`),
      ''
    ] : [])
  ].join('\r\n');
}

export function buildFolderZip({ nodes, folder, format, exportedBy, opts, uploads = [], skipped = [] }) {
  const root = `${safeFileName(folder.title, 'Folder')}/`;
  const files = [{ name: `${root}README.txt`, data: readme({ scopeLabel: `the folder "${folder.title}"`, format, exportedBy, uploads, skipped }) }];
  addChapterTree(files, nodes, folder.id, root, format, opts);
  addUploads(files, uploads, `${root}Files/`);
  return createZip(files);
}

export function buildFullZip({ nodes, entities, timeline, format, exportedBy, opts, uploads = [], skipped = [] }) {
  const date = new Date().toISOString().slice(0, 10);
  const root = `iekcorbar-story-${date}/`;
  const ext = STORY_EXPORT_FORMATS[format].ext;
  const files = [{ name: `${root}README.txt`, data: readme({ scopeLabel: 'the complete Story Room (chapters, lore library, timeline, uploaded files)', format, exportedBy, uploads, skipped }) }];

  addChapterTree(files, nodes, null, `${root}Chapters/`, format, opts);

  const usedLore = new Set();
  for (const e of [...entities].sort((a, b) => a.name.localeCompare(b.name))) {
    const folder = `${kindLabel(e.kind)}s`.replace(/ys$/, 'ies');
    let name = `${root}Lore Library/${folder}/${safeFileName(e.name)}`;
    while (usedLore.has(name.toLowerCase())) name += '_';
    usedLore.add(name.toLowerCase());
    files.push({ name: `${name}.${ext}`, data: renderEntity(e, format, opts) });
  }

  if (timeline.length) files.push({ name: `${root}Timeline.${ext}`, data: renderTimeline(timeline, format, opts) });

  addUploads(files, uploads, `${root}Files/`);

  files.push({
    name: `${root}story-backup.json`,
    data: JSON.stringify({ exportedAt: new Date().toISOString(), exportedBy: exportedBy || null, nodes, entities, timeline, files: uploadMeta(uploads) }, null, 2)
  });
  return createZip(files);
}

// Content-Disposition with a UTF-8 file name (RFC 6266) plus an ASCII fallback.
export function attachmentHeader(filename) {
  const ascii = filename.replace(/[^\x20-\x7e]/g, '_').replace(/["\\]/g, '_');
  return `attachment; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(filename)}`;
}
