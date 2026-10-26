'use client';

import React, { useState } from 'react';
import { Share2, Check } from 'lucide-react';

// Share a link: the phone's own share sheet (WhatsApp, Discord, ...) where
// available, otherwise copies the link. The link preview (title, text,
// image) comes from the target page's Open Graph metadata.
export default function ShareButton({ url, title, text, label = 'Share', className = '' }) {
  const [copied, setCopied] = useState(false);

  const share = async () => {
    const fullUrl = url.startsWith('http') ? url : `${window.location.origin}${url}`;
    try {
      if (navigator.share) {
        await navigator.share({ title, text, url: fullUrl });
        return;
      }
    } catch (err) {
      if (err?.name === 'AbortError') return; // user closed the share sheet
    }
    try {
      await navigator.clipboard.writeText(fullUrl);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = fullUrl;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); } catch { /* nothing more we can do */ }
      document.body.removeChild(ta);
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <button
      type="button"
      onClick={share}
      className={className || 'flex items-center justify-center gap-2 px-4 py-2 rounded-lg border border-sp-bronze/60 text-text-warm/85 hover:border-sp-gold hover:text-sp-gold text-xs font-bold uppercase tracking-wider transition-all'}
    >
      {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Share2 className="w-4 h-4" />}
      {copied ? 'Link copied!' : label}
    </button>
  );
}
