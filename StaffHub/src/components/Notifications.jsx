'use client';

// Bel in de topbalk: ongelezen-teller, uitklapper met de laatste meldingen,
// klik = spring naar het document. De eigenaar kan hier ook een mededeling
// sturen. Pollt elke 30 s (en meteen als het tabblad weer zichtbaar wordt).
import { useCallback, useEffect, useRef, useState } from 'react';
import { Bell, Check, Trash2, Send, MessageSquare, AtSign, Pencil, ShieldCheck, Megaphone } from 'lucide-react';
import { useLang, useDt } from '@/lib/i18n';

const ICOON = { comment: MessageSquare, mention: AtSign, edit: Pencil, perms: ShieldCheck, announce: Megaphone };

export default function Notifications({ me, onOpen }) {
  const { t } = useLang();
  const dt = useDt();
  const [data, setData] = useState({ items: [], unread: 0 });
  const [open, setOpen] = useState(false);
  const [tekst, setTekst] = useState('');
  const [naar, setNaar] = useState('all');
  const [doel, setDoel] = useState('');
  const [klaar, setKlaar] = useState('');
  const doos = useRef(null);
  const titel0 = useRef(null);

  const haal = useCallback(async () => {
    try {
      const r = await fetch('/api/notifications', { cache: 'no-store' });
      if (r.ok) setData(await r.json());
    } catch { /* offline: volgende ronde */ }
  }, []);

  useEffect(() => {
    haal();
    const iv = setInterval(() => { if (!document.hidden) haal(); }, 30000);
    const zicht = () => { if (!document.hidden) haal(); };
    document.addEventListener('visibilitychange', zicht);
    return () => { clearInterval(iv); document.removeEventListener('visibilitychange', zicht); };
  }, [haal]);

  // "(3) StaffHub" in het tabblad, zodat je het ook ziet als de hub op de achtergrond staat.
  useEffect(() => {
    if (titel0.current == null) titel0.current = document.title.replace(/^\(\d+\)\s*/, '');
    document.title = (data.unread ? `(${data.unread}) ` : '') + titel0.current;
  }, [data.unread]);

  useEffect(() => {
    if (!open) return;
    const buiten = (e) => { if (doos.current && !doos.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', buiten);
    return () => document.removeEventListener('mousedown', buiten);
  }, [open]);

  async function actie(body) {
    const r = await fetch('/api/notifications', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    if (r.ok) { const j = await r.json(); if (j.items) setData(j); return j; }
    return null;
  }

  async function klik(n) {
    if (!n.read) await actie({ action: 'read', ids: [n.id] });
    if (n.node) { setOpen(false); onOpen?.(n.node); }
  }

  async function stuur() {
    if (!tekst.trim()) return;
    const audience = naar === 'all' ? { type: 'all' } : { type: naar, value: doel.trim() };
    const j = await actie({ action: 'send', text: tekst, audience });
    if (j?.sent != null) { setTekst(''); setKlaar(t('nt.verzonden').replace('{n}', j.sent)); setTimeout(() => setKlaar(''), 4000); }
  }

  return (
    <div ref={doos} className="relative">
      <button title={t('nt.titel')} onClick={() => { setOpen(!open); if (!open) haal(); }} className="relative p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold">
        <Bell size={18} />
        {data.unread > 0 && <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-red-600 text-white text-[10px] leading-4 text-center font-bold">{data.unread > 99 ? '99+' : data.unread}</span>}
      </button>
      {open && (
        <div className="absolute right-0 top-9 z-50 w-80 max-h-[70vh] flex flex-col rounded-lg border border-bronze/40 bg-panel shadow-2xl text-left">
          <div className="flex items-center gap-1 px-3 py-2 border-b border-bronze/30">
            <span className="font-semibold text-parch text-sm flex-1">{t('nt.titel')}</span>
            <button title={t('nt.gelezen')} onClick={() => actie({ action: 'read', all: true })} className="p-1 rounded hover:bg-white/10"><Check className="size-4" /></button>
            <button title={t('nt.opruimen')} onClick={() => actie({ action: 'clear' })} className="p-1 rounded hover:bg-white/10"><Trash2 className="size-4" /></button>
          </div>
          <div className="overflow-y-auto flex-1">
            {data.items.length === 0 && <p className="px-3 py-6 text-center text-xs text-parch/50">{t('nt.leeg')}</p>}
            {data.items.map((n) => {
              const I = ICOON[n.kind] || Bell;
              return (
                <button key={n.id} onClick={() => klik(n)} className={`w-full flex gap-2 px-3 py-2 text-left border-b border-bronze/10 hover:bg-white/5 ${n.read ? 'opacity-60' : ''}`}>
                  <I className={`size-4 mt-0.5 shrink-0 ${n.read ? 'text-parch/50' : 'text-gold'}`} />
                  <span className="min-w-0">
                    <span className="block text-xs text-parch break-words">{n.text}</span>
                    <span className="block text-[10px] text-parch/40 mt-0.5">{dt(n.created)}</span>
                  </span>
                  {!n.read && <span className="ml-auto mt-1 size-2 shrink-0 rounded-full bg-gold" />}
                </button>
              );
            })}
          </div>
          {me?.role === 'owner' && (
            <div className="p-2 border-t border-bronze/30 space-y-1.5">
              <div className="flex gap-1 text-[11px]">
                {[['all', 'nt.iedereen'], ['team', 'nt.team'], ['user', 'nt.lid']].map(([k, l]) => (
                  <button key={k} onClick={() => setNaar(k)} className={`px-2 py-0.5 rounded-full border ${naar === k ? 'border-gold text-gold' : 'border-bronze/30 text-parch/60'}`}>{t(l)}</button>
                ))}
              </div>
              {naar !== 'all' && <input value={doel} onChange={(e) => setDoel(e.target.value)} placeholder={t('nt.naar')} className="w-full rounded bg-black/30 border border-bronze/30 px-2 py-1 text-xs text-parch" />}
              <div className="flex gap-1">
                <input value={tekst} onChange={(e) => setTekst(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') stuur(); }} maxLength={300} placeholder={t('nt.ph')} className="flex-1 rounded bg-black/30 border border-bronze/30 px-2 py-1 text-xs text-parch" />
                <button title={t('nt.stuur')} onClick={stuur} className="p-1.5 rounded bg-gold/20 hover:bg-gold/30 text-gold"><Send className="size-4" /></button>
              </div>
              {klaar && <p className="text-[11px] text-green-400">{klaar}</p>}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
