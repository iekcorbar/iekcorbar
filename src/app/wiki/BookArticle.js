'use client';

import React, { useState } from 'react';
import { Clock, User, FileText, History, Eye, Paperclip, Download, Share2, Check, ChevronRight, Layers, X } from 'lucide-react';
import { renderMarkdown } from '@/lib/markdown';

// A wiki article as printed on the right-hand page of the open book (see
// OpenBook in Bookcase.js). Parchment styling lives in bookcase.css under
// `.bka`, which also re-colours the dark-theme classes renderMarkdown emits.
// View tab / revision preview are local state: the parent keys this component
// by page id, so opening another article always starts on its current text.
export default function BookArticle({ page, pages, onSelect, onCopy, copiedId }) {
  const [view, setView] = useState('article'); // 'article' | 'history'
  const [preview, setPreview] = useState(null);

  const chain = [];
  let cur = page;
  for (let guard = 0; cur && guard < 10; guard++) {
    chain.unshift(cur);
    cur = pages.find((p) => p.id === cur.parentId) || null;
  }
  const children = pages.filter((p) => p.parentId === page.id);
  const revisions = page.revisions || [];

  return (
    <article className="bka">
      <div className="bka-crumbs">
        <span>{page.category}</span>
        {chain.map((p, i) => (
          <React.Fragment key={p.id}>
            <ChevronRight className="w-3 h-3" />
            {i === chain.length - 1 ? (
              <strong>{p.title}</strong>
            ) : (
              <button type="button" onClick={() => onSelect(p)}>{p.title}</button>
            )}
          </React.Fragment>
        ))}
      </div>

      <header className="bka-head">
        <h2>{page.title}</h2>
        <div className="bka-actions">
          <div className="bka-tabs">
            <button type="button" className={view === 'article' && !preview ? 'is-on' : ''} onClick={() => { setView('article'); setPreview(null); }}>
              <FileText className="w-3.5 h-3.5" /> Article
            </button>
            <button type="button" className={view === 'history' ? 'is-on' : ''} onClick={() => setView('history')}>
              <History className="w-3.5 h-3.5" /> Revisions ({revisions.length})
            </button>
          </div>
          <button type="button" className={`bka-share ${copiedId === page.id ? 'is-copied' : ''}`} onClick={(e) => onCopy(page, e)}>
            {copiedId === page.id ? <><Check className="w-3.5 h-3.5" /> Copied!</> : <><Share2 className="w-3.5 h-3.5" /> Share</>}
          </button>
        </div>
      </header>

      {view === 'history' ? (
        <div className="bka-history">
          <ul>
            <li className="is-current">
              <strong>v{revisions.length + 1} (current)</strong>
              <span>{page.updatedBy} · {new Date(page.updatedAt).toLocaleString()}</span>
            </li>
            {revisions.length === 0 && <li className="bka-none">No earlier versions.</li>}
            {revisions.map((rev, idx) => (
              <li key={idx}>
                <strong>v{revisions.length - idx}</strong>
                <span>{rev.updatedBy} · {new Date(rev.updatedAt).toLocaleString()}</span>
                <button type="button" onClick={() => { setPreview(rev.content); setView('article'); }}>
                  <Eye className="w-3 h-3" /> Preview
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <>
          {preview && (
            <div className="bka-banner">
              <span>Viewing an earlier version of this page.</span>
              <button type="button" onClick={() => setPreview(null)}><X className="w-3 h-3" /> Back to current</button>
            </div>
          )}

          <div className="bka-body">{renderMarkdown(preview || page.content)}</div>

          {!preview && page.attachments?.length > 0 && (
            <section className="bka-section">
              <h3><Paperclip className="w-3.5 h-3.5" /> Attachments</h3>
              <div className="bka-attachments">
                {page.attachments.map((att) => (
                  /\.(png|jpe?g|gif|webp|svg)(\?|$)/i.test(att.url) ? (
                    <a key={att.url} href={att.url} target="_blank" rel="noopener noreferrer">
                      <img src={att.url} alt={att.name} />
                    </a>
                  ) : (
                    <a key={att.url} href={att.url} target="_blank" rel="noopener noreferrer" className="bka-file">
                      <Download className="w-3.5 h-3.5" /> {att.name}
                    </a>
                  )
                ))}
              </div>
            </section>
          )}

          {!preview && children.length > 0 && (
            <section className="bka-section">
              <h3><Layers className="w-3.5 h-3.5" /> Subtopics</h3>
              <ul className="bka-subtopics">
                {children.map((c) => (
                  <li key={c.id}>
                    <button type="button" onClick={() => onSelect(c)}>{c.title} <ChevronRight className="w-3.5 h-3.5" /></button>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      <footer className="bka-foot">
        <span><User className="w-3.5 h-3.5" /> Last edited by <strong>{page.updatedBy}</strong></span>
        <span><Clock className="w-3.5 h-3.5" /> {new Date(page.updatedAt).toLocaleString()}</span>
      </footer>
    </article>
  );
}
