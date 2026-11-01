'use client';

// Document vertalen vanuit het AI-bord. De motor (lib/vertaal) was er al - dit
// is de knop die ontbrak.
//
// Belangrijk ontwerpbesluit: een complete handleiding gaat NIET als HTML door de
// model heen (dat verbrukt de opmaak), maar als markdown via lib/docconv - en de
// vertaling komt er ook weer doorheen. De motor meet na afloop de structuur
// (kopjes, lijstjes, plaatjes) en waarschuwt als die niet overeenkomt.
//
// Het HTML dat hier getoond wordt, is door onszelf opgebouwd uit geescapte
// markdown (mdToHtml escaapt álle raw HTML vóórdat het eigen tags toevoegt), dus
// er kan geen modeltekst als markup doorglippen.
import { useEffect, useState } from 'react';
import { Languages, Download, FileText, Printer } from 'lucide-react';
import { useLang, trServer } from '@/lib/i18n';

const TAALNAAM = { nl: 'NL', en: 'EN', it: 'IT', fi: 'FI' };

/**
 * Een handleiding-HTML zoals die in de hub staat, puur lezen. Zelfde opmaak voor
 * het bord én de editor-tabbladen, dus één plek.
 *
 * Het HTML is door onszelf opgebouwd (mdToHtml escaapt álle rauwe HTML vóórdat
 * het eigen tags toevoegt), dus er kan geen modeltekst als markup doorglippen.
 */
export function GidsWeergave({ html, klas = '' }) {
  return (
    <div className={`overflow-auto rounded-lg border border-bronze/25 bg-black/40 p-2 text-[11px] text-parch/90 space-y-1 ${klas}`}
      dangerouslySetInnerHTML={{ __html: String(html || '') }} />
  );
}

// Bestanden die we als tekst kunnen vertalen (de route toetst dit nogmaals)
export const VERTAAL_EXT = ['md', 'txt', 'html', 'htm', 'log', 'pdf'];
export const kanVertalen = (n) => n?.type === 'page' || (n?.type === 'file' && VERTAAL_EXT.includes(String(n.name || '').split('.').pop().toLowerCase()));

// Vertaling printen of als PDF bewaren: eigen venster met alleen de tekst, het
// browser-afdrukvenster doet de rest (Opslaan als PDF). Het HTML is door
// onszelf opgebouwd uit geescapte markdown, zie boven.
function afdrukken(html, naam, taal) {
  const w = window.open('', '_blank');
  if (!w) return;
  const titel = String(naam || '').replace(/[<>&"]/g, '') + ' (' + (TAALNAAM[taal] || taal) + ')';
  w.document.write('<!doctype html><meta charset="utf-8"><title>' + titel + '</title>' +
    '<style>body{font:13px/1.5 Georgia,serif;max-width:760px;margin:24px auto;color:#111}h1,h2,h3{color:#6b4a12}code{background:#eee;padding:0 3px}li{margin-left:18px}</style>' + html);
  w.document.close();
  w.focus();
  setTimeout(() => w.print(), 300);
}

export default function Vertaal({ node }) {
  const { t, tr } = useLang();
  const [bezig, setBezig] = useState('');        // taal die loopt
  const [resultaat, setResultaat] = useState(null); // { taal, html, controle, stukken, cached }
  const [melding, setMelding] = useState('');

  useEffect(() => { setResultaat(null); setMelding(''); setBezig(''); }, [node?.id]);

  async function starten(lang, opnieuw) {
    if (!node || bezig) return;
    setBezig(lang); setMelding('');
    try {
      const r = await fetch((node.type === 'file' ? '/api/file/' : '/api/page/') + node.id + '/vertaal', {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ lang, opnieuw: Boolean(opnieuw) }),
      });
      const j = await r.json().catch(() => ({}));
      if (!r.ok || j.error) { setMelding(trServer(j.error || 'mislukt') + (j.stuk ? ' (stuk ' + j.stuk + ')' : '')); return; }
      setResultaat({ taal: lang, html: j.html, controle: j.controle, stukken: j.stukken, cached: j.cached });
      // Seintje naar de handleiding: er is een taal-tab bij gekomen. Zonder dit
      // moet de staff-lid eerst een andere map in en uit om de nieuwe tab te zien.
      try { window.dispatchEvent(new Event('staffhub:vertaling')); } catch { }
    } catch (e) {
      setMelding(String(e?.message || e));
    } finally {
      setBezig('');
    }
  }

  if (!kanVertalen(node)) return null;

  return (
    <div className="border-t border-bronze/20 px-3 py-2 text-xs bg-black/10">
      <div className="flex items-center gap-2 flex-wrap">
        <Languages className="size-3.5 text-gold shrink-0" />
        <span className="text-parch/70 truncate max-w-full" title={node.name}>{t('tr.titel')}</span>
        <span className="ml-auto flex gap-1">
          {Object.keys(TAALNAAM).map((lang) => (
            <button key={lang} onClick={() => starten(lang)} disabled={Boolean(bezig)}
              className={`rounded-lg px-2 py-0.5 border text-[11px] ${resultaat?.taal === lang ? 'bg-gold/20 border-gold/50 text-gold' : 'bg-white/5 border-bronze/30 text-parch/70 hover:border-gold/60'} disabled:opacity-40`}>
              {bezig === lang ? t('tr.bezig') : (resultaat?.taal === lang && !resultaat.cached ? TAALNAAM[lang] + ' ✓' : TAALNAAM[lang])}
            </button>
          ))}
          {!!resultaat && (
            <button onClick={() => starten(resultaat.taal, true)} disabled={Boolean(bezig)} title={t('tr.opnieuw')}
              className="rounded-lg px-1.5 py-0.5 border border-bronze/30 bg-white/5 text-parch/60 text-[11px] hover:border-gold/60 disabled:opacity-40">↻</button>
          )}
        </span>
      </div>

      {bezig && <p className="text-[10px] text-gold/80 mt-1 animate-pulse">{t('tr.bezig')} {TAALNAAM[bezig]}…</p>}
      {melding && <p className="text-[10px] text-red-300 mt-1">{melding}</p>}

      {!!resultaat && (
        <div className="mt-1.5">
          <p className="text-[10px] text-parch/45">
            {TAALNAAM[resultaat.taal]} · {resultaat.stukken || 1} {resultaat.stukken > 1 ? 'stukken' : 'stuk'}
            {resultaat.cached ? ' · ' + t('tr.klaar') : ''}
            {resultaat.controle?.twijfel ? ' · ⚠ ' + t('tr.twijfel') : ''}
          </p>
          <GidsWeergave html={resultaat.html} klas="mt-1 max-h-52 [&_h1]:text-[13px] [&_h1]:font-bold [&_h1]:text-gold [&_h2]:font-bold [&_h2]:text-gold/90 [&_li]:ml-4 [&_li]:list-disc [&_code]:bg-black/60 [&_code]:px-1 [&_pre]:bg-black/60 [&_pre]:p-2 [&_pre]:overflow-auto" />
          <button onClick={() => afdrukken(resultaat.html, node.name, resultaat.taal)} className="mt-1 mr-3 inline-flex items-center gap-1 text-[10px] text-parch/60 hover:text-gold">
            <Printer className="size-3" /> print / PDF
          </button>
          {node.type === 'page' && <a href={'/api/page/' + node.id + '?fmt=md'} download className="mt-1 inline-flex items-center gap-1 text-[10px] text-parch/60 hover:text-gold">
            <Download className="size-3" /> markdown van het origineel
          </a>}
          <p className="text-[9px] text-parch/35 mt-0.5 flex items-center gap-1"><FileText className="size-2.5" /> de vertaling is nog niet opgeslagen als eigen taal-tab in de editor - dat is de volgende stap</p>
        </div>
      )}
    </div>
  );
}
