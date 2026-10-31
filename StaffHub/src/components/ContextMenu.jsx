'use client';

// Rechtermuisklik-menu zoals in een besturingssysteem: opent op de plek van de
// klik, blijft binnen het scherm, sluit met Esc, buiten klikken, scrollen of
// een andere rechtermuisklik, en is met de pijltjestoetsen + Enter te bedienen.
// items: [{ label, icon, onClick, danger, disabled, hint }] of { sep: true }.
import { useEffect, useLayoutEffect, useRef, useState } from 'react';

export default function ContextMenu({ x, y, items, onClose }) {
  const ref = useRef(null);
  const [pos, setPos] = useState({ left: x, top: y });
  const [actief, setActief] = useState(-1);

  // Binnen het scherm houden (na het meten van de echte afmetingen).
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    setPos({
      left: Math.max(4, Math.min(x, window.innerWidth - r.width - 4)),
      top: Math.max(4, Math.min(y, window.innerHeight - r.height - 4)),
    });
  }, [x, y, items]);

  useEffect(() => {
    const dicht = () => onClose();
    const buiten = (e) => { if (ref.current && !ref.current.contains(e.target)) onClose(); };
    const toets = (e) => {
      const ok = items.map((it, i) => (it.sep || it.disabled ? -1 : i)).filter((i) => i >= 0);
      if (e.key === 'Escape') { e.preventDefault(); onClose(); }
      else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!ok.length) return;
        const p = ok.indexOf(actief);
        const n = e.key === 'ArrowDown' ? (p + 1) % ok.length : (p <= 0 ? ok.length - 1 : p - 1);
        setActief(ok[n]);
      } else if (e.key === 'Enter' && actief >= 0) {
        e.preventDefault();
        const it = items[actief];
        onClose();
        it.onClick?.();
      }
    };
    document.addEventListener('mousedown', buiten);
    document.addEventListener('contextmenu', buiten);
    window.addEventListener('scroll', dicht, true);
    window.addEventListener('resize', dicht);
    window.addEventListener('blur', dicht);
    document.addEventListener('keydown', toets);
    return () => {
      document.removeEventListener('mousedown', buiten);
      document.removeEventListener('contextmenu', buiten);
      window.removeEventListener('scroll', dicht, true);
      window.removeEventListener('resize', dicht);
      window.removeEventListener('blur', dicht);
      document.removeEventListener('keydown', toets);
    };
  }, [items, actief, onClose]);

  return (
    <div
      ref={ref}
      role="menu"
      style={{ left: pos.left, top: pos.top }}
      onContextMenu={(e) => e.preventDefault()}
      className="fixed z-[60] min-w-48 max-w-72 rounded-lg border border-bronze/40 bg-panel py-1 shadow-2xl text-[13px] text-parch select-none"
    >
      {items.map((it, i) => it.sep
        ? <div key={i} className="my-1 border-t border-bronze/25" />
        : (
          <button
            key={i}
            role="menuitem"
            disabled={it.disabled}
            onMouseEnter={() => setActief(i)}
            onClick={() => { onClose(); it.onClick?.(); }}
            className={`w-full flex items-center gap-2.5 px-3 py-1.5 text-left disabled:opacity-35 disabled:cursor-default ${actief === i ? 'bg-white/10' : ''} ${it.danger ? 'text-red-300' : ''}`}
          >
            <span className="w-4 shrink-0 flex justify-center">{it.icon}</span>
            <span className="flex-1 truncate">{it.label}</span>
            {it.hint && <span className="text-[10px] text-parch/40">{it.hint}</span>}
          </button>
        ))}
    </div>
  );
}
