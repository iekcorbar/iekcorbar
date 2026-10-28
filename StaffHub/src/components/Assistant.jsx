'use client';

// Chat-paneel voor de boord-assistent: vragen lopen tegen /api/assistant aan
// (zoek-service op eigen content), antwoorden tonen klikbare bronnen.
import { useRef, useState } from 'react';
import { Bot, ChevronDown, ChevronRight, Search, FileText, Folder, File as FileIcon } from 'lucide-react';
import { useLang } from '@/lib/i18n';

const ICONS = { folder: Folder, page: FileText, file: FileIcon };

export default function Assistant({ onOpen }) {
  const { t, tr } = useLang();
  const [open, setOpen] = useState(true);
  const [q, setQ] = useState('');
  const [log, setLog] = useState([]); // { ask, hits?, error? }
  const [busy, setBusy] = useState(false);
  const endRef = useRef();

  async function ask(e) {
    e?.preventDefault();
    const query = q.trim();
    if (!query || busy) return;
    setBusy(true); setQ('');
    try {
      const j = await (await fetch('/api/assistant?q=' + encodeURIComponent(query))).json();
      if (j.error) setLog((l) => [...l, { ask: query, error: tr(j.error) }]);
      else setLog((l) => [...l, { ask: query, hits: j.hits || [] }]);
    } catch (err) {
      setLog((l) => [...l, { ask: query, error: String(err?.message || err) }]);
    } finally {
      setBusy(false);
      setTimeout(() => endRef.current?.scrollIntoView({ block: 'nearest' }), 30);
    }
  }

  return (
    <div className="border-b border-bronze/20 bg-black/10">
      <button onClick={() => setOpen((o) => !o)} className="w-full flex items-center gap-2 px-3 py-2 text-xs hover:bg-white/5">
        <Bot className="size-4 text-gold" />
        <b className="text-gold">{t('ai.title')}</b>
        <span className="text-parch/40 truncate">{t('ai.hint')}</span>
        {open ? <ChevronDown className="size-3.5 ml-auto text-parch/50 shrink-0" /> : <ChevronRight className="size-3.5 ml-auto text-parch/50 shrink-0" />}
      </button>

      {open && (
        <div className="px-3 pb-3">
          {log.length > 0 && (
            <div className="space-y-2 max-h-64 overflow-auto mb-2">
              {log.map((m, i) => (
                <div key={i}>
                  <div className="bg-gold/10 border border-gold/25 rounded-lg px-2.5 py-1.5 text-xs text-parch/90">{m.ask}</div>
                  {m.error ? (
                    <p className="text-red-300 text-[11px] mt-1 px-1">{m.error}</p>
                  ) : (m.hits || []).length === 0 ? (
                    <p className="text-parch/50 text-[11px] mt-1 px-1">{t('ai.answer_none')}</p>
                  ) : (
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
                </div>
              ))}
              <div ref={endRef} />
            </div>
          )}
          <form onSubmit={ask} className="flex gap-1">
            <div className="relative flex-1">
              <Search className="absolute left-2 top-2 size-3.5 text-parch/40" />
              <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('ai.ph')}
                className="w-full bg-black/40 border border-bronze/30 rounded-lg pl-7 pr-2 py-1.5 text-xs outline-none focus:border-gold" />
            </div>
            <button disabled={busy || !q.trim()} className="flex items-center gap-1 bg-gold/90 text-ink rounded-lg px-2.5 text-xs font-bold hover:bg-gold disabled:opacity-40">
              {busy ? '…' : t('ai.ask')}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
