import React from 'react';

// Shared by every content renderer (markdown.js for wiki/events/projects,
// news, updates, rules, chat): turns bare URLs in plain text into clickable
// links, and a video URL that sits alone on its own line into an embedded
// player. Links open in a new tab (in the Android app: the phone's browser).

const URL_RE = /((?:https?:\/\/|www\.)[^\s<>"'`]+)/gi;
const TRAILING_PUNCT = /[.,!?;:)\]}'"]+$/;

const DEFAULT_LINK_CLASS = 'text-sp-gold hover:text-white underline font-semibold break-words';

// Strips sentence punctuation after a URL ("see https://x.com." / "(https://x.com)"),
// but gives back closing ")" that belong to the URL itself, e.g.
// https://en.wikipedia.org/wiki/Steam_(disambiguation).
function trimUrl(raw) {
  const trail = raw.match(TRAILING_PUNCT);
  if (!trail) return raw;
  let url = raw.slice(0, -trail[0].length);
  let rest = trail[0];
  const count = (s, c) => s.split(c).length - 1;
  while (rest.startsWith(')') && count(url, '(') > count(url, ')')) {
    url += ')';
    rest = rest.slice(1);
  }
  return url;
}

function hrefFor(url) {
  return url.toLowerCase().startsWith('www.') ? `https://${url}` : url;
}

// Plain text -> array of strings and <a> elements (or the original string if
// there's no URL in it, so callers can use it anywhere text is allowed).
export function linkifyText(text, { className = DEFAULT_LINK_CLASS, keyPrefix = 'lnk' } = {}) {
  if (typeof text !== 'string' || !/(https?:\/\/|www\.)/i.test(text)) return text;
  const out = [];
  let last = 0;
  let k = 0;
  text.replace(URL_RE, (match, _g, offset) => {
    const url = trimUrl(match);
    if (offset > last) out.push(text.slice(last, offset));
    out.push(
      <a key={`${keyPrefix}-${k++}`} href={hrefFor(url)} target="_blank" rel="noopener noreferrer" className={className}>
        {url}
      </a>
    );
    last = offset + url.length;
    return match;
  });
  if (last < text.length) out.push(text.slice(last));
  return out;
}

// Returns { src, title } for a supported video URL, else null.
export function videoEmbedFor(rawUrl) {
  if (!rawUrl) return null;
  let url;
  try {
    url = new URL(hrefFor(rawUrl.trim()));
  } catch {
    return null;
  }
  const host = url.hostname.replace(/^www\.|^m\./, '');
  let id = null;
  if (host === 'youtu.be') {
    id = url.pathname.slice(1).split('/')[0];
  } else if (host === 'youtube.com' || host === 'music.youtube.com' || host === 'youtube-nocookie.com') {
    if (url.pathname === '/watch') id = url.searchParams.get('v');
    else {
      const m = url.pathname.match(/^\/(?:shorts|embed|live|v)\/([^/?#]+)/);
      if (m) id = m[1];
    }
  }
  if (id && /^[\w-]{6,20}$/.test(id)) {
    const t = url.searchParams.get('t') || url.searchParams.get('start');
    const start = t ? parseInt(String(t).replace(/s$/, ''), 10) : 0;
    return {
      src: `https://www.youtube-nocookie.com/embed/${id}${start > 0 ? `?start=${start}` : ''}`,
      title: 'YouTube video'
    };
  }
  if (host === 'vimeo.com') {
    const m = url.pathname.match(/^\/(\d+)/);
    if (m) return { src: `https://player.vimeo.com/video/${m[1]}?dnt=1`, title: 'Vimeo video' };
  }
  return null;
}

// A line that is *only* a video URL (optionally as a markdown link or inside
// <>), e.g. "https://youtu.be/abc123" -> embed info, else null.
export function lineVideoEmbed(line) {
  if (typeof line !== 'string') return null;
  let t = line.trim();
  const md = t.match(/^\[[^\]]*\]\(([^)\s]+)\)$/);
  if (md) t = md[1];
  t = t.replace(/^<|>$/g, '');
  if (!t || /\s/.test(t) || !/^(https?:\/\/|www\.)/i.test(t)) return null;
  return videoEmbedFor(t);
}

export function VideoEmbed({ embed }) {
  return (
    <div className="my-4 w-full max-w-2xl aspect-video rounded-xl overflow-hidden border border-sp-bronze/40 bg-black">
      <iframe
        src={embed.src}
        title={embed.title}
        className="w-full h-full"
        loading="lazy"
        referrerPolicy="strict-origin-when-cross-origin"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
        allowFullScreen
      />
    </div>
  );
}

// HTML-string variant for renderers that build HTML (rules page): wraps bare
// URLs that aren't already inside a tag attribute or an <a>…</a>.
export function linkifyHtml(html) {
  if (typeof html !== 'string' || !/(https?:\/\/|www\.)/i.test(html)) return html;
  // Split around existing anchors so their contents/attributes are left alone.
  return html.split(/(<a\b[^>]*>[\s\S]*?<\/a>)/gi).map(part => {
    if (/^<a\b/i.test(part)) return part;
    return part.replace(/(^|[\s(>])((?:https?:\/\/|www\.)[^\s<>"']+)/gi, (m, pre, url) => {
      const clean = trimUrl(url);
      const rest = url.slice(clean.length);
      return `${pre}<a href="${hrefFor(clean)}" target="_blank" rel="noopener noreferrer" class="${DEFAULT_LINK_CLASS}">${clean}</a>${rest}`;
    });
  }).join('');
}
