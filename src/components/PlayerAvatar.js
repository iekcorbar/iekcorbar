'use client';

import React, { useEffect, useRef, useState } from 'react';

// Shared head-icon avatar. Every avatar on the site used to hit
// mc-heads.net keyed only by Minecraft username, which has no idea a
// player equipped a custom skin via the profile "Steampunk Wardrobe"
// (users.custom_skin_url) — that skin never appeared anywhere on the site
// itself, even on the player's own profile header, which is what made the
// wardrobe feature look completely broken. This component fixes that: when
// a customSkinUrl is passed, it crops the head region (base layer 8,8 +
// hat-layer overlay 40,8, both 8x8) straight out of the raw skin texture
// onto a canvas — the same approach the profile page's own live preview
// already used, just not reused anywhere else. Falls back to mc-heads.net
// by username (matching every other avatar in the app) when no custom skin
// is set, or if the custom skin image fails to load.
export default function PlayerAvatar({ mcUsername, customSkinUrl, size = 32, className = '', title }) {
  const canvasRef = useRef(null);
  const [customFailed, setCustomFailed] = useState(false);

  useEffect(() => {
    setCustomFailed(false);
  }, [customSkinUrl]);

  useEffect(() => {
    if (!customSkinUrl || customFailed || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(img, 8, 8, 8, 8, 0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 40, 8, 8, 8, 0, 0, canvas.width, canvas.height);
    };
    img.onerror = () => setCustomFailed(true);
    img.src = customSkinUrl;
  }, [customSkinUrl, customFailed]);

  if (customSkinUrl && !customFailed) {
    return (
      <canvas
        ref={canvasRef}
        width={size}
        height={size}
        title={title}
        className={className}
        style={{ width: size, height: size, imageRendering: 'pixelated' }}
      />
    );
  }

  return (
    <img
      src={`https://mc-heads.net/avatar/${mcUsername || 'Steve'}/${size}`}
      alt={title || mcUsername || ''}
      title={title}
      className={className}
      style={{ width: size, height: size }}
      onError={(e) => { e.target.onerror = null; e.target.src = 'https://mc-heads.net/avatar/Steve'; }}
    />
  );
}
