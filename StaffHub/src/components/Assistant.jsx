'use client';

// Het AI-boord. Twee modi met elkaars eigen belofte:
//   Zoeken   /api/assistant  - keyword vindplaatsen in eigen content, gratis, direct
//   Boord-AI /api/ai          - model-dat-alleen-de-hub-leest, met klikbare bronnen
//
// Wat hier bewust in zit, omdat het board zonder deze dingen "net niks" voelde:
//  - een statuslampje: je ZIET of de Boord-AI aan staat en zo nee waarom, in
//    plaats van dat pas na een vraag te ontdekken;
//  - een oplopende teller bij het denken: een model van acht seconden is geen
//    hangende pagina, maar dat moet je wel zien;
//  - vervolgvragen als chips: het model mag terugvragen, en doorvragen mag geen
//    typwerk kosten;
//  - bronnen als chips: een antwoord zonder aanwijsbare gids is hier niets waard;
//  - geschiedenis in localStorage: een herladen wisste het hele gesprek.
import { useCallback, useEffect, useRef, useState } from 'react';
import { Bot, Search, Sparkles, FileText, Folder, File as FileIcon, RefreshCw } from 'lucide-react';
import { useLang, trServer } from '@/lib/i18n';
import { mdNaarJSX } from '@/lib/mdLite';

const ICONS = { folder: Folder, page: FileText, file: FileIcon };
const SLEUTEL = 'staffhub_board';

export default function Assistant({ onOpen }) {
  const { t, lang } = useLang();
  const [mode, setMode] = useState('zoek');
  const [q, setQ] = useState('');
  const [log, setLog] = useState([]);
  const [busy, setBusy] = useState(false);
  const [sec, setSec] = useState(0);
  const [st, setSt] = useState(null);            // { on, model, reden }
  const sid = useRef('');
  const endRef = useRef();
  const tik = useRef();

  // Gesprek per toestel terugzetten (nooit tijdens server-render: localStorage).
  useEffect(() => {
    try {
      const oud = JSON.parse(localStorage.getItem(SLEUTEL) || 'null');
      // zoekgeschiedenis staat uit: alleen Boord-AI-gesprekken komen terug
      const ai = Array.isArray(oud) ? oud.filter((m) => m.mode === 'ai') : [];
      if (ai.length) setLog(ai);
      sid.current = String(localStorage.getItem(SLEUTEL + ':sid') || '');
    } catch { /* prullenbak van een vorige versie mag het never breken */ }
    fetch('/api/ai').then((r) => r.json()).then(setSt).catch(() => setSt({ on: false, reden: 'time-out' }));
  }, []);
  useEffect(() => {
    try {
      localStorage.setItem(SLEUTEL, JSON.stringify(log.filter((m) => m.mode === 'ai').slice(-24)));
      localStorage.setItem(SLEUTEL + ':sid', sid.current);
    } catch { }
  }, [log]);

  const vraag = useCallback(async (tekst) => {
    const vraagTekst = String(tekst ?? q ?? '').trim();
    if (!vraagTekst || busy) return;
    const m = mode;
    setBusy(true); setSec(0); setQ('');
    tik.current = setInterval(() => setSec((s) => s + 1), 1000);
    try {
      if (m === 'ai') {
        const r = await fetch('/api/ai', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ question: vraagTekst, session: sid.current, lang }) });
        const j = await r.json().catch(() => ({}));
        if (!r.ok || j.error) {
          if (String(j.error || '') === 'ai-uit') setSt({ on: false, reden: 'sandbox-bereikt-niet' });
          setLog((l) => [...l, {
            ask: vraagTekst, mode: m,
            error: String(j.error || '') === 'ai-uit' ? t('ai.off') : trServer(j.error || 'mislukt'),
            next: j.volgende || [],
          }]);
        } else {
          sid.current = j.session || sid.current;
          setLog((l) => [...l, { ask: vraagTekst, mode: m, answer: j.answer || '', bronnen: j.bronnen || [], next: j.volgende || [] }]);
        }
      } else {
        const j = await (await fetch('/api/assistant?q=' + encodeURIComponent(vraagTekst))).json().catch(() => ({}));
        if (j.error) setLog((l) => [...l, { ask: vraagTekst, mode: m, error: trServer(j.error) }]);
        else setLog((l) => [...l, { ask: vraagTekst, mode: m, hits: j.hits || [] }]);
      }
    } catch (e) {
      setLog((l) => [...l, { ask: vraagTekst, mode: m, error: String(e?.message || e) }]);
    } finally {
      clearInterval(tik.current);
      setBusy(false);
      setTimeout(() => endRef.current?.scrollIntoView({ block: 'nearest' }), 30);
    }
  }, [q, busy, mode, t]);

  function wissen() {
    setLog([]); sid.current = '';
    try { localStorage.removeItem(SLEUTEL); localStorage.removeItem(SLEUTEL + ':sid'); } catch { }
  }

  const lamp = !st ? 'bg-parch/30' : st.on ? 'bg-emerald-400' : 'bg-red-400';
  const lampTekst = !st ? t('ai.check') : st.on ? `${t('ai.aan')} · ${st.model || ''}` : `${t('ai.uit')} · ${trServer(st.reden || '')}`;

  // Sinds 0.5.0 vult de assistent zijn eigen kolom: geen inklapbare doos meer
  // boven een voorbeeld-ruite, maar het gesprek als hoofdonderwerp - de hele
  // dag door, met de invoer onderaan plakt en het log erboven scrollbar-end.
  return (
    <div className="flex-1 flex flex-col min-h-0 bg-black/10">
      {/* kop */}
      <div className="flex items-center gap-2 px-3 py-2.5 text-xs border-b border-bronze/20 shrink-0">
        <Bot className="size-4 text-gold shrink-0" />
        <b className="text-gold shrink-0 text-sm">{t('ai.title')}</b>
        <span className={`size-2 rounded-full shrink-0 ${lamp}`} title={lampTekst} />
        <span className="text-parch/40 truncate">{lampTekst}</span>
        {log.length > 0 && (
          <button onClick={wissen} title={t('ai.nieuw')} className="ml-auto shrink-0 p-1 rounded hover:bg-white/10"><RefreshCw className="size-3.5" /></button>
        )}
      </div>

      <div className="flex-1 flex flex-col min-h-0 px-3 pb-3 pt-2">
          {/* modus */}
          <div className="flex gap-1 mb-2">
            <button onClick={() => setMode('zoek')} title={t('ai.hint')} className={`flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] border ${mode === 'zoek' ? 'bg-gold/20 border-gold/50 text-gold' : 'bg-white/5 border-bronze/30 text-parch/60'}`}>
              <Search className="size-3" /> {t('ai.mode_zoek')}
            </button>
            <button onClick={() => setMode('ai')} title={t('ai.deep_hint')} className={`flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] border ${mode === 'ai' ? 'bg-gold/20 border-gold/50 text-gold' : 'bg-white/5 border-bronze/30 text-parch/60'}`}>
              <Sparkles className="size-3" /> {t('ai.mode_ai')}
            </button>
          </div>
          {mode === 'ai' && st && !st.on && (
            <p className="text-[10px] text-amber-300/80 border border-amber-500/30 bg-amber-900/20 rounded px-2 py-1 mb-2">
              {t('ai.uit')} · {trServer(st.reden || '')} — {t('ai.gebruik_zoek')}
            </p>
          )}

          {/* gesprek */}
          {log.length === 0 ? (
            <div className="mb-2">
              <p className="text-[10px] text-parch/40 mb-1">{t(mode === 'ai' ? 'ai.leeg_ai' : 'ai.leeg_zoek')}</p>
              <div className="flex flex-wrap gap-1">
                {['ai.v1', 'ai.v2', 'ai.v3'].map((k) => (
                  <button key={k} onClick={() => vraag(t(k))} className="text-[10px] bg-white/5 border border-bronze/30 rounded-full px-2 py-0.5 hover:border-gold/60 hover:text-gold">{t(k)}</button>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-2 flex-1 min-h-0 overflow-auto mb-2 pr-1">
              {log.map((m, i) => (
                <div key={i}>
                  <div className="bg-gold/10 border border-gold/25 rounded-lg px-2.5 py-1.5 text-xs text-parch/90">{m.ask}</div>

                  {m.error && <p className="text-red-300 text-[11px] mt-1 px-1 whitespace-pre-wrap">{m.error}</p>}

                  {m.answer && (
                    <div className="mt-1 px-1 text-[11px] text-parch/85 space-y-1 [&_p]:leading-relaxed">{mdNaarJSX(m.answer)}</div>
                  )}
                  {m.answer && <p className="text-[9px] text-parch/40 mt-0.5 px-1">{t('ai.disclaimer')}</p>}

                  {!!m.bronnen?.length && (
                    <div className="mt-1 px-1">
                      <p className="text-[9px] text-parch/45 mb-0.5">{t('ai.bronnen')}</p>
                      <div className="flex flex-wrap gap-1">
                        {m.bronnen.map((b) => {
                          const I = ICONS.page;
                          return (
                            <button key={b.id} onClick={() => onOpen({ id: b.id, name: b.name, type: 'page' })}
                              title={b.trail} className="flex items-center gap-1 text-[10px] bg-black/30 border border-bronze/25 rounded-full px-2 py-0.5 hover:border-gold/60">
                              <I className="size-3 text-gold/70" /><span className="truncate max-w-32">{b.name}</span>
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {!!m.hits?.length && (
                    <div className="mt-1 space-y-1">
                      <p className="text-[10px] text-parch/50 px-1">{t('ai.answer_found', { n: m.hits.length })}</p>
                      {m.hits.map((h) => {
                        const I = ICONS[h.type] || FileIcon;
                        return (
                          <button key={h.id} onClick={() => onOpen({ id: h.id, name: h.name, type: h.type })}
                            className="w-full text-left bg-black/30 border border-bronze/20 rounded-lg px-2.5 py-1.5 hover:border-gold/50">
                            <span className="flex items-center gap-1.5 text-xs">
                              <I className="size-3.5 text-gold/70 shrink-0" />
                              <span className="truncate font-bold text-parch/90">{h.name}</span>
                              <span className="text-parch/40 ml-auto shrink-0 text-[10px]">{t('why.' + h.why)}</span>
                            </span>
                            {!!h.snippet && <p className="text-[11px] text-parch/60 mt-0.5 line-clamp-2">{h.snippet}</p>}
                          </button>
                        );
                      })}
                    </div>
                  )}
                  {m.hits?.length === 0 && <p className="text-parch/50 text-[11px] mt-1 px-1">{t('ai.answer_none')}</p>}

                  {!!m.next?.length && (
                    <div className="mt-1 px-1">
                      <p className="text-[9px] text-parch/45 mb-0.5">{t('ai.verder')}</p>
                      <div className="flex flex-col gap-1">
                        {m.next.map((v, n) => (
                          <button key={n} onClick={() => vraag(v)} className="text-left text-[10px] border border-bronze/30 bg-white/5 rounded-lg px-2 py-1 hover:border-gold/60 hover:text-gold">→ {v}</button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ))}
              {busy && <p className="text-[11px] text-gold/80 px-1 animate-pulse">{t('ai.denkt')} {sec}s…</p>}
              <div ref={endRef} />
            </div>
          )}

          <form onSubmit={(e) => { e.preventDefault(); vraag(); }} className="flex gap-1 shrink-0">
            <div className="relative flex-1">
              <Search className="absolute left-2 top-2 size-3.5 text-parch/40" />
              <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={mode === 'ai' ? t('ai.ph_ai') : t('ai.ph')}
                className="w-full bg-black/40 border border-bronze/30 rounded-lg pl-7 pr-2 py-1.5 text-xs outline-none focus:border-gold" />
            </div>
            <button disabled={busy || !q.trim()} className="flex items-center gap-1 bg-gold/90 text-ink rounded-lg px-2.5 text-xs font-bold hover:bg-gold disabled:opacity-40">
              {busy ? sec + 's' : t(mode === 'ai' ? 'ai.think' : 'ai.ask')}
            </button>
          </form>
        </div>
    </div>
  );
}
