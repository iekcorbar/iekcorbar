import React from 'react';
import { linkifyText, lineVideoEmbed, VideoEmbed } from '@/lib/linkify';

// Rough markdown-to-plaintext, e.g. for card previews or Open Graph
// `description` tags where real markup would just show up as literal
// asterisks/hashes — doesn't need to be perfect, just readable. Shared by
// wiki/news/projects' index-card excerpts and their generateMetadata()
// share-link previews (see each route's page.js) so both use the same
// stripping logic instead of two slightly-different regexes drifting apart.
export function markdownToPlainText(text = '', maxLen = 160) {
  const plain = (text || '')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')       // images
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')    // links -> label text
    .replace(/[#>*_`|-]/g, ' ')                 // markdown punctuation
    .replace(/\s+/g, ' ')
    .trim();
  return plain.length > maxLen ? plain.slice(0, maxLen).trim() + '…' : plain;
}

// Custom lightweight markdown formatter with extended tag support
export function renderMarkdown(text = '') {
  const lines = text.split('\n');
  const renderedElements = [];
  let inList = false;
  let listItems = [];
  let inTable = false;
  let tableRows = [];

  const flushList = (key) => {
    if (listItems.length > 0) {
      renderedElements.push(
        <ul key={key} className="list-disc ml-5 space-y-1 my-2">
          {listItems}
        </ul>
      );
      listItems = [];
    }
  };

  const flushTable = (key) => {
    if (tableRows.length > 0) {
      let hasHeader = false;
      if (tableRows.length > 1) {
        const secondRow = tableRows[1].trim();
        if (secondRow.startsWith('|') && secondRow.includes('-')) {
          hasHeader = true;
        }
      }

      const rows = [];
      tableRows.forEach((rowStr, rowIdx) => {
        if (rowIdx === 1 && hasHeader) return;

        const cells = rowStr
          .split('|')
          .slice(1, -1)
          .map(cell => cell.trim());

        if (rowIdx === 0 && hasHeader) {
          rows.push(
            <tr key={rowIdx} className="border-b border-sp-bronze bg-neutral-900/60 font-bold">
              {cells.map((cell, cellIdx) => (
                <th key={cellIdx} className="px-4 py-2 text-sp-gold text-left">{parseInline(cell)}</th>
              ))}
            </tr>
          );
        } else {
          rows.push(
            <tr key={rowIdx} className="border-b border-sp-bronze/10 hover:bg-neutral-950/20">
              {cells.map((cell, cellIdx) => (
                <td key={cellIdx} className="px-4 py-2 text-text-warm/85">{parseInline(cell)}</td>
              ))}
            </tr>
          );
        }
      });

      renderedElements.push(
        <div key={key} className="overflow-x-auto my-4 border border-sp-bronze/30 rounded-lg bg-neutral-950/20">
          <table className="w-full text-xs text-left border-collapse">
            <tbody>{rows}</tbody>
          </table>
        </div>
      );
      tableRows = [];
    }
  };

  const parseInline = (str) => {
    const elements = [];
    let keyIdx = 0;
    let index = 0;
    let remaining = str;

    while (index < remaining.length) {
      if (remaining.startsWith('**', index)) {
        const end = remaining.indexOf('**', index + 2);
        if (end !== -1) {
          elements.push(<strong key={keyIdx++} className="text-sp-gold">{remaining.substring(index + 2, end)}</strong>);
          index = end + 2;
          continue;
        }
      }
      if (remaining.startsWith('[', index)) {
        const endLabel = remaining.indexOf(']', index);
        if (endLabel !== -1 && remaining.startsWith('(', endLabel + 1)) {
          const endUrl = remaining.indexOf(')', endLabel + 2);
          if (endUrl !== -1) {
            const label = remaining.substring(index + 1, endLabel);
            const url = remaining.substring(endLabel + 2, endUrl);
            elements.push(
              <a key={keyIdx++} href={url} target="_blank" rel="noreferrer" className="text-sp-gold hover:text-white underline font-semibold">
                {label}
              </a>
            );
            index = endUrl + 1;
            continue;
          }
        }
      }
      // Reaching here means whatever's AT `index` didn't turn out to be a
      // real **bold** or [link](url) after all (e.g. a bare "[area]" with no
      // "(url)" after it — real content, command-syntax docs like
      // "/lands untrust <player> [area]" use exactly this). Searching for
      // the next marker starting at `index + 1` (not `index`) is the fix for
      // a real production bug: searching from `index` re-found that same
      // unmatched `[`/`**` every pass, so `nextSpecial === index` forever —
      // substring(index, index) is '' and `index` never advances, hanging
      // the tab in an infinite loop the moment any wiki page used "[...]"
      // without it being a link. Starting one character later guarantees
      // this line's un-matched marker gets emitted as plain text and the
      // scan moves on.
      const searchFrom = index + 1;
      const nextSpecial = Math.min(
        remaining.indexOf('**', searchFrom) === -1 ? Infinity : remaining.indexOf('**', searchFrom),
        remaining.indexOf('[', searchFrom) === -1 ? Infinity : remaining.indexOf('[', searchFrom)
      );
      // Plain text between markers: bare URLs become clickable links.
      if (nextSpecial === Infinity) {
        elements.push(linkifyText(remaining.substring(index), { keyPrefix: `u${keyIdx++}` }));
        break;
      } else {
        elements.push(linkifyText(remaining.substring(index, nextSpecial), { keyPrefix: `u${keyIdx++}` }));
        index = nextSpecial;
      }
    }
    return elements.length > 0 ? elements : str;
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    if (inTable && !trimmed.startsWith('|')) {
      inTable = false;
      flushTable(`table-${i}`);
    }

    if (inList && !trimmed.startsWith('* ') && !trimmed.startsWith('- ')) {
      inList = false;
      flushList(`list-${i}`);
    }

    // A YouTube/Vimeo link alone on its own line -> embedded player.
    const embed = lineVideoEmbed(trimmed);
    if (embed) {
      renderedElements.push(<VideoEmbed key={`video-${i}`} embed={embed} />);
      continue;
    }

    if (trimmed.startsWith('![') && trimmed.includes('](') && trimmed.endsWith(')')) {
      const alt = trimmed.slice(trimmed.indexOf('![') + 2, trimmed.indexOf(']('));
      const src = trimmed.slice(trimmed.indexOf('](') + 2, trimmed.lastIndexOf(')'));
      renderedElements.push(
        <div key={`img-${i}`} className="my-4 rounded-xl overflow-hidden border border-sp-bronze/40 max-w-full max-h-[400px] flex justify-center bg-black/20">
          <img src={src} alt={alt} className="max-w-full h-auto object-contain" />
        </div>
      );
      continue;
    }

    if (trimmed.startsWith('|')) {
      inTable = true;
      tableRows.push(trimmed);
      continue;
    }

    if (trimmed.startsWith('## ')) {
      renderedElements.push(
        <h2 key={`h2-${i}`} className="font-serif text-xl font-black text-sp-gold border-b border-sp-bronze/30 pb-1 mt-6 mb-3 uppercase tracking-wide">
          {trimmed.replace('## ', '')}
        </h2>
      );
      continue;
    }
    if (trimmed.startsWith('### ')) {
      renderedElements.push(
        <h3 key={`h3-${i}`} className="font-serif text-lg font-bold text-sp-gold mt-5 mb-2">
          {trimmed.replace('### ', '')}
        </h3>
      );
      continue;
    }
    if (trimmed.startsWith('#### ')) {
      renderedElements.push(
        <h4 key={`h4-${i}`} className="font-serif text-base font-bold text-sp-copper mt-4 mb-1">
          {trimmed.replace('#### ', '')}
        </h4>
      );
      continue;
    }

    if (trimmed === '---') {
      renderedElements.push(<hr key={`hr-${i}`} className="border-t border-sp-bronze/30 my-6" />);
      continue;
    }

    if (trimmed.startsWith('> ')) {
      renderedElements.push(
        <blockquote key={`bq-${i}`} className="border-l-4 border-sp-gold bg-neutral-900/40 px-4 py-2 my-4 italic text-text-warm/80 rounded-r-md">
          {parseInline(trimmed.replace('> ', ''))}
        </blockquote>
      );
      continue;
    }

    if (trimmed.startsWith('* ') || trimmed.startsWith('- ')) {
      inList = true;
      const text = trimmed.startsWith('* ') ? trimmed.replace('* ', '') : trimmed.replace('- ', '');
      listItems.push(
        <li key={`li-${i}-${listItems.length}`} className="text-sm text-text-warm/85 my-0.5">
          {parseInline(text)}
        </li>
      );
      continue;
    }

    if (trimmed === '') {
      renderedElements.push(<div key={`empty-${i}`} className="h-2" />);
      continue;
    }

    renderedElements.push(
      <p key={`p-${i}`} className="text-sm leading-relaxed text-text-warm/95 my-2">
        {parseInline(line)}
      </p>
    );
  }

  if (inList) flushList('list-end');
  if (inTable) flushTable('table-end');

  return renderedElements;
}
