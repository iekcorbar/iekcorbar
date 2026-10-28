'use client';

// Mini markdown-naar-JSX voor AI-antwoorden. Met opzet GEEN innerHTML: een
// modeltekst is niet vertrouwd, dus er wordt niets rauws ge-renderd. Wel de
// opmaak die een antwoord leesbaar maakt: kopjes, lijstjes, vet, code en links.
//
// Ondersteund: # tot ######, - / * / 1. lijsten, ``` codeblok, > citaat,
// **vet**, *schuing*, `code`, [tekst](http://…) enLosse regel met ---.

function inline(tekst, sloter) {
  const delen = [];
  const regex = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)\s]+\)|\*[^*\n]+\*)/g;
  let laatste = 0, m;
  while ((m = regex.exec(tekst))) {
    if (m.index > laatste) delen.push(tekst.slice(laatste, m.index));
    const t = m[0];
    if (t.startsWith('**')) delen.push(<strong key={sloter()}>{t.slice(2, -2)}</strong>);
    else if (t.startsWith('`')) delen.push(<code key={sloter()} className="bg-black/50 border border-bronze/25 rounded px-1 font-mono">{t.slice(1, -1)}</code>);
    else if (t.startsWith('[')) {
      const txt = t.slice(1, t.indexOf(']'));
      const href = t.slice(t.indexOf('](') + 2, -1);
      const ok = /^(https?:|mailto:|\/)/i.test(href);
      delen.push(ok
        ? <a key={sloter()} href={href} target="_blank" rel="noopener noreferrer" className="text-gold underline decoration-gold/40">{txt}</a>
        : txt);
    } else delen.push(<em key={sloter()}>{t.slice(1, -1)}</em>);
    laatste = m.index + t.length;
  }
  if (laatste < tekst.length) delen.push(tekst.slice(laatste));
  return delen;
}

/** Markdown-achtige tekst naar een lijst React-elementen. */
export function mdNaarJSX(bron) {
  const out = [];
  let k = 0;
  const id = () => 'x' + (k++);
  const regels = String(bron || '').replace(/\r\n/g, '\n').split('\n');
  let i = 0, list = null, code = null, quote = null;

  const sluit = () => {
    if (list) { out.push(list.tag === 'ul'
      ? <ul key={id()} className="list-disc pl-4 space-y-0.5">{list.items}</ul>
      : <ol key={id()} className="list-decimal pl-4 space-y-0.5">{list.items}</ol>); list = null; }
    if (quote) { out.push(<blockquote key={id()} className="border-l-2 border-gold/40 pl-2 text-parch/70 italic">{quote}</blockquote>); quote = null; }
  };

  while (i < regels.length) {
    const lijn = regels[i];

    if (code !== null) {                       // binnen een ```-blok: letterlijk
      if (/^\s*```/.test(lijn)) { out.push(<pre key={id()} className="bg-black/50 border border-bronze/25 rounded p-2 overflow-auto text-[11px] font-mono whitespace-pre">{code.join('\n')}</pre>); code = null; }
      else code.push(lijn);
      i++; continue;
    }
    if (/^\s*```/.test(lijn)) { sluit(); code = []; i++; continue; }

    const kop = lijn.match(/^(#{1,6})\s+(.*)$/);
    if (kop) {
      sluit();
      const lvl = kop[1].length;
      const klas = lvl <= 2 ? 'font-bold text-gold text-[13px] mt-1' : 'font-bold text-parch/90 text-xs mt-1';
      out.push(<p key={id()} className={klas}>{inline(kop[2], id)}</p>);
      i++; continue;
    }

    if (/^\s*(-{3,}|\*{3,})\s*$/.test(lijn)) { sluit(); out.push(<hr key={id()} className="border-bronze/20 my-1" />); i++; continue; }

    const cit = lijn.match(/^>\s?(.*)$/);
    if (cit) { if (!quote) { sluit(); quote = []; } quote.push(...inline(cit[1], id)); quote.push(<br key={id()} />); i++; continue; }

    const li = lijn.match(/^\s*[-*+]\s+(.*)$/) || lijn.match(/^\s*\d+[.)]\s+(.*)$/);
    if (li) {
      const ol = /^\s*\d+[.)]/.test(lijn);
      if (!list || list.tag !== (ol ? 'ol' : 'ul')) { sluit(); list = { tag: ol ? 'ol' : 'ul', items: [] }; }
      list.items.push(<li key={id()}>{inline(li[1], id)}</li>);
      i++; continue;
    }

    sluit();
    if (lijn.trim()) out.push(<p key={id()} className="leading-relaxed">{inline(lijn.trim(), id)}</p>);
    i++;
  }
  if (code !== null) out.push(<pre key={id()} className="bg-black/50 border border-bronze/25 rounded p-2 overflow-auto text-[11px] font-mono whitespace-pre">{code.join('\n')}</pre>);
  sluit();
  return out;
}
