'use client';

import React from 'react';
import { getBanner, safeBannerImage } from '@/lib/profile-theme';
import './profile.css';

// Decorative brass gear for the banner (teeth generated, no asset needed).
function gearPath(teeth, rOut, rIn, hole) {
  const step = (Math.PI * 2) / teeth;
  const o = [-0.3, -0.17, 0.17, 0.3];
  const pts = [];
  for (let i = 0; i < teeth; i++) {
    const a = i * step;
    pts.push([rIn, a + o[0] * step], [rOut, a + o[1] * step], [rOut, a + o[2] * step], [rIn, a + o[3] * step]);
  }
  const outline = pts.map(([r, t], i) => `${i ? 'L' : 'M'}${(50 + r * Math.cos(t)).toFixed(2)} ${(50 + r * Math.sin(t)).toFixed(2)}`).join('') + 'Z';
  const bore = `M${50 + hole} 50A${hole} ${hole} 0 1 0 ${50 - hole} 50A${hole} ${hole} 0 1 0 ${50 + hole} 50Z`;
  return outline + bore;
}

function BannerGear({ size, teeth, className, style }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} className={`ph-gear ${className}`} style={style} aria-hidden="true">
      <path d={gearPath(teeth, 48, 39, 12)} fill="currentColor" fillRule="evenodd" />
    </svg>
  );
}

// Shared profile header for /members/[username] and /profile: banner theme,
// big skin render, name + role, bio, quick chips, and a slot for actions.
// `chips` is [{ icon: <Icon/>, label, value }].
export default function ProfileHero({ username, banner, bannerImage = null, bannerPos = 50, onReportBanner = null, roleLabel, roleClass, bio, chips = [], actions = null, emptyBio = null, bioExtra = null }) {
  const theme = getBanner(banner);
  // A player-uploaded banner covers the preset theme (which stays underneath as
  // a fallback while the image loads). Only our own upload URLs are ever used.
  // (or a blob: URL the browser itself made for the not-yet-uploaded preview).
  const image = safeBannerImage(bannerImage) || (typeof bannerImage === 'string' && /^blob:[A-Za-z0-9:/._-]+$/.test(bannerImage) ? bannerImage : null);
  const bannerStyle = image
    ? { backgroundImage: `url("${image}"), ${theme.css}`, backgroundSize: 'cover, cover', backgroundPosition: `center ${Number.isFinite(Number(bannerPos)) ? Number(bannerPos) : 50}%, center`, backgroundRepeat: 'no-repeat' }
    : { background: theme.css };

  return (
    <section className={`ph ${image ? 'ph-has-image' : ''}`} style={{ '--accent': theme.accent }}>
      <div className="ph-banner" style={bannerStyle}>
        <BannerGear size={210} teeth={16} className="ph-gear-a" style={{ top: -60, right: 40 }} />
        <BannerGear size={120} teeth={10} className="ph-gear-b" style={{ bottom: -34, right: 220 }} />
        <BannerGear size={90} teeth={9} className="ph-gear-c" style={{ top: 18, left: '38%' }} />
        <div className="ph-shade" />
        {image && onReportBanner && (
          <button type="button" className="ph-report-banner" onClick={onReportBanner}>Report banner</button>
        )}
      </div>

      <div className="ph-body">
        <div className="ph-skin">
          <img
            src={`https://mc-heads.net/body/${encodeURIComponent(username)}`}
            alt={`${username}'s skin`}
            draggable={false}
            onError={(e) => { e.target.onerror = null; e.target.src = 'https://mc-heads.net/body/Steve'; }}
          />
        </div>

        <div className="ph-main">
          <div className="ph-name">
            <h1>{username}</h1>
            {roleLabel && <span className={`ph-role ${roleClass || ''}`}>{roleLabel}</span>}
          </div>

          {bio ? <p className="ph-bio">{bio}</p> : emptyBio}
          {bio && bioExtra}

          {chips.length > 0 && (
            <ul className="ph-chips">
              {chips.map((c, i) => (
                <li key={i}>
                  {c.icon}
                  <span className="ph-chip-label">{c.label}</span>
                  <strong>{c.value}</strong>
                </li>
              ))}
            </ul>
          )}
        </div>

        {actions && <div className="ph-actions">{actions}</div>}
      </div>
    </section>
  );
}

// Simple tab strip shared by both profile pages. `tabs`: [{ key, label, icon, badge }]
export function ProfileTabs({ tabs, active, onChange }) {
  return (
    <div className="pt-strip" role="tablist">
      {tabs.map(t => (
        <button
          key={t.key}
          type="button"
          role="tab"
          aria-selected={active === t.key}
          onClick={() => onChange(t.key)}
          className={`pt-tab ${active === t.key ? 'is-on' : ''}`}
        >
          {t.icon}
          <span>{t.label}</span>
          {t.badge != null && t.badge !== '' && <em>{t.badge}</em>}
        </button>
      ))}
    </div>
  );
}
