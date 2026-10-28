'use client';

// Mediaspeler voor de hub: audio en video mét playlist vanuit de eigen map
// (vorige/volgende/shuffle/herhalen), plus een 3D-kijker voor .glb/.gltf.
//
// Why een eigen balk en niet gewoon <audio controls>? Omdat "shuffle" niet
// bestaat zonder lijst: de speler hoort de bestanden om het huidige bestand
// heen te kennen en na het einde automatisch door te klikken. Video houdt wel
// de native controls (spoelen, volumeschuif, volledig scherm - dat doet de
// browser beter dan wij ooit bouwen); daarbovenop komt dezelfde playlistknoppen.
//
// three.js wordt LAAD-end opgehaald (import()): een hub-bezoeker die nooit een
// model opent, sleepdt nooit 600 KB mee.

import { useEffect, useRef, useState } from 'react';
import { Play, Pause, Square, SkipBack, SkipForward, Shuffle, Repeat, Move3d } from 'lucide-react';
import { useLang } from '@/lib/i18n';

export const AUDIO_EXT = ['mp3', 'ogg', 'oga', 'opus', 'wav', 'm4a', 'aac', 'flac'];
export const VIDEO_EXT = ['mp4', 'm4v', 'webm', 'mov', 'ogv'];
export const MODEL_EXT = ['glb', 'gltf', 'bbmodel'];

export function mediaKind(naam) {
  const ext = String(naam || '').split('.').pop()?.toLowerCase();
  if (AUDIO_EXT.includes(ext)) return 'audio';
  if (VIDEO_EXT.includes(ext)) return 'video';
  if (MODEL_EXT.includes(ext)) return '3d';
  return null;
}

const BNA = 'p-1.5 rounded hover:bg-white/10 text-parch/70 disabled:opacity-30';

/** Knoppenbalk: vorige · speel/pauzeer · stop · volgende · shuffle · herhaal. */
function Balk({ kind, playing, shuffle, repeat, onPlay, onPause, onStop, onNext, onPrev, onShuffle, onRepeat, tijd, duur, seek }) {
  const { t } = useLang();
  const mm = (s) => { s = Math.max(0, Math.floor(s || 0)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
  return (
    <div className="space-y-1">
      {kind === 'audio' && (
        <div className="flex items-center gap-2 text-[10px] text-parch/60">
          <span className="tabular-nums w-10 text-right">{mm(tijd)}</span>
          <input type="range" min="0" max={duur || 0} step="0.1" value={Math.min(tijd, duur || 0)} onChange={(e) => seek?.(Number(e.target.value))}
            className="flex-1 accent-gold h-1" />
          <span className="tabular-nums w-10">{mm(duur)}</span>
        </div>
      )}
      <div className="flex items-center gap-0.5">
        <button className={BNA} title={t('med.prev')} onClick={onPrev}><SkipBack className="size-4" /></button>
        <button className={BNA} title={playing ? t('med.pauze') : t('med.play')} onClick={playing ? onPause : onPlay}>
          {playing ? <Pause className="size-4.5" /> : <Play className="size-4.5" />}
        </button>
        <button className={BNA} title={t('med.stop')} onClick={onStop}><Square className="size-4" /></button>
        <button className={BNA} title={t('med.next')} onClick={onNext}><SkipForward className="size-4" /></button>
        <span className="w-2" />
        <button className={`${BNA} ${shuffle ? 'text-gold bg-gold/10' : ''}`} title={t('med.shuffle')} onClick={onShuffle}><Shuffle className="size-4" /></button>
        <button className={`${BNA} ${repeat ? 'text-gold bg-gold/10' : ''}`} title={t('med.repeat')} onClick={onRepeat}><Repeat className="size-4" /></button>
      </div>
    </div>
  );
}

/**
 * MediaPlayer: één <audio>/<video>-element + playlist van de sibling-bestanden.
 * lijst = [{id, name}] (alle media van dezelfde soort uit de map); de speler
 * onthoudt zijn positie/shuffle alleen binnen één geopend mapbezoek.
 */
export function MediaPlayer({ kind, id, naam, lijst }) {
  const { t } = useLang();
  const ref = useRef(null);
  const [playing, setPlaying] = useState(false);
  const [tijd, setTijd] = useState(0);
  const [duur, setDuur] = useState(0);
  const [shuffle, setShuffle] = useState(() => { try { return localStorage.getItem('staffhub_shuffle') === '1'; } catch { return false; } });
  const [repeat, setRepeat] = useState(false);
  const wisselRef = useRef(null);

  // speellijst: de map-siblings van dezelfde soort; het geopende bestand vooraan
  const bron = (lijst || []).length ? lijst : [{ id, name: naam }];
  let orde = bron.map((_, i) => i);
  if (shuffle) {
    orde = [...bron.map((_, i) => i)];
    for (let i = orde.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1));[orde[i], orde[j]] = [orde[j], orde[i]]; }
  }
  // De shuffle-herkanteling hierboven zou bij élke render een nieuwe volgorde
  // trekken; dat is niet de bedoeling - dus wordt de volgorde één keer per
  // (her-)open van de speler gezet en daarna vastgezet in deze ref.
  if (!wisselRef.current || wisselRef.current.bron !== bron) {
    wisselRef.current = { bron, orde, idx: Math.max(0, bron.findIndex((b) => b.id === id)) };
  }
  const cur = wisselRef.current;

  function stap(richting) {
    const volgorde = cur.orde;
    let pos = volgorde.indexOf(cur.idx);
    pos = (pos + richting + volgorde.length) % volgorde.length;
    cur.idx = volgorde[pos];
    setPlaying(false); setTijd(0);
    if (ref.current) { ref.current.src = '/api/file/' + bron[cur.idx].id; ref.current.load(); ref.current.play().then(() => setPlaying(true)).catch(() => setPlaying(false)); }
  }
  function klaar() {                       // einde van een nummer
    if (repeat && ref.current) { ref.current.currentTime = 0; ref.current.play().catch(() => setPlaying(false)); return; }
    if ((cur.orde || []).length > 1) stap(1); else setPlaying(false);
  }

  const url = '/api/file/' + bron[cur.idx]?.id;
  const El = kind === 'audio' ? 'audio' : 'video';
  return (
    <div className="space-y-2">
      <El ref={ref} src={url} controls={kind === 'video'} autoPlay={false} playsInline
        className={kind === 'video' ? 'w-full max-h-72 rounded-lg border border-bronze/30 bg-black' : 'hidden'}
        onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)}
        onTimeUpdate={(e) => setTijd(e.target.currentTime)} onLoadedMetadata={(e) => setDuur(e.target.duration || 0)}
        onEnded={klaar}
        onError={() => setPlaying(false)} />
      {kind === 'audio' && (
        <div className="rounded-lg border border-bronze/30 bg-black/40 p-2">
          <p className="text-[11px] text-parch/80 truncate mb-1" title={bron[cur.idx]?.name}>{bron[cur.idx]?.name}</p>
          {bron.length > 1 && <p className="text-[9px] text-parch/40 mb-1">{t('med.list', { n: cur.orde.indexOf(cur.idx) + 1, van: bron.length })}</p>}
          <Balk kind="audio" playing={playing} shuffle={shuffle} repeat={repeat} tijd={tijd} duur={duur}
            seek={(v) => { if (ref.current) { ref.current.currentTime = v; setTijd(v); } }}
            onPlay={() => ref.current?.play()} onPause={() => ref.current?.pause()}
            onStop={() => { ref.current?.pause(); if (ref.current) { ref.current.currentTime = 0; ref.current.src = url; } setPlaying(false); setTijd(0); }}
            onNext={() => stap(1)} onPrev={() => stap(-1)}
            onShuffle={() => {
              const aan = !shuffle; setShuffle(aan);
              try { localStorage.setItem('staffhub_shuffle', aan ? '1' : '0'); } catch { }
              const b = [...Array(bron.length).keys()];
              if (aan) for (let i = b.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1));[b[i], b[j]] = [b[j], b[i]]; }
              cur.orde = b;
            }}
            onRepeat={() => setRepeat(!repeat)} />
        </div>
      )}
      {kind === 'video' && (
        <div className="rounded-lg border border-bronze/30 bg-black/40 p-2 flex items-center justify-between gap-2">
          <p className="text-[11px] text-parch/80 truncate flex-1" title={bron[cur.idx]?.name}>{bron[cur.idx]?.name}</p>
          <Balk kind="video" playing={playing} shuffle={shuffle} repeat={repeat}
            onPlay={() => ref.current?.play()} onPause={() => ref.current?.pause()}
            onStop={() => { ref.current?.pause(); if (ref.current) { ref.current.currentTime = 0; } setPlaying(false); }}
            onNext={() => stap(1)} onPrev={() => stap(-1)}
            onShuffle={() => { const aan = !shuffle; setShuffle(aan); try { localStorage.setItem('staffhub_shuffle', aan ? '1' : '0'); } catch { } const b = [...Array(bron.length).keys()]; if (aan) for (let i = b.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1));[b[i], b[j]] = [b[j], b[i]]; } cur.orde = b; }}
            onRepeat={() => setRepeat(!repeat)} />
        </div>
      )}
    </div>
  );
}

/**
 * 3D-kijker voor .glb/.gltf (three.js, lui geladen). Sleep draait, scroll zoomt,
 * animaties uit Blockbench/Blender spoelen vanzelf; knop voor automatisch
 * draaien en herframe. .bbmodel kan hier niet: dat is een Blockbench-PROJECT
 * (json), geen vorm - de hint zegt hoe je er wél naar kunt kijken.
 */
export function Model3D({ url, naam }) {
  const { t } = useLang();
  const doosRef = useRef(null);
  const [klaar, setKlaar] = useState(false);
  const [fout, setFout] = useState('');
  const apiRef = useRef(null);
  const [spin, setSpin] = useState(true);

  useEffect(() => {
    if (String(naam || '').toLowerCase().endsWith('.bbmodel')) { setFout('bbmodel'); return; }
    let weg = false;
    const doos = doosRef.current;
    if (!doos) return undefined;
    (async () => {
      try {
        const THREE = await import('three');
        const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js');
        const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js');
        if (weg) return;
        const breed = () => doos.clientWidth || 380;
        const hoog = () => Math.max(240, Math.min(420, window.innerHeight * 0.45));
        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(45, breed() / hoog(), 0.01, 2000);
        camera.position.set(2.2, 1.8, 2.6);
        const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        renderer.setSize(breed(), hoog());
        renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
        doos.appendChild(renderer.domElement);
        scene.add(new THREE.AmbientLight(0xcfd8e6, 1.1));
        const zon = new THREE.DirectionalLight(0xffffff, 2.2); zon.position.set(4, 6, 3); scene.add(zon);
        const tegen = new THREE.DirectionalLight(0x88aaff, 0.7); tegen.position.set(-4, -2, -3); scene.add(tegen);
        const raster = new THREE.GridHelper(4, 16, 0x33414f, 0x1c2634); scene.add(raster);
        const controls = new OrbitControls(camera, renderer.domElement);
        controls.enableDamping = true;
        const mixer = { h: null };
        const loader = new GLTFLoader();
        await new Promise((res, rej) => loader.load(url, (g) => {
          if (weg) return res();
          const o = g.scene || g.scenes?.[0];
          // centreer en schaalt naar een kast van ~2 eenheden - élke modelgrootte vult het scherm
          const bb = new THREE.Box3().setFromObject(o);
          const om = bb.getSize(new THREE.Vector3());
          const f = 2 / Math.max(om.x, om.y, om.z, 1e-6);
          o.scale.setScalar(f);
          bb.setFromObject(o);
          o.position.sub(bb.getCenter(new THREE.Vector3()));   // in het midden
          bb.setFromObject(o);
          o.position.y -= bb.min.y;                            // op het raster zetten
          scene.add(o);
          if (g.animations?.length) { mixer.h = new THREE.AnimationMixer(o); for (const a of g.animations) mixer.h.clipAction(a).play(); }
          res();
        }, undefined, rej));
        if (weg) { renderer.dispose(); return; }
        const hor = () => {
          if (weg) return;
          requestAnimationFrame(hor);
          controls.update();
          if (apiRef.current?.spin) scene.rotation.y += 0.005;
          if (mixer.h) mixer.h.update(1 / 60);
          renderer.render(scene, camera);
        };
        requestAnimationFrame(hor);
        apiRef.current = { spin, herstel: () => { camera.position.set(2.2, 1.8, 2.6); controls.target.set(0, 0, 0); controls.update(); } };
        setKlaar(true);
      } catch (e) { if (!weg) setFout(String(e?.message || e)); }
    })();
    return () => {
      weg = true;
      // renderer + canvas opruimen: bij elk heropenen draait een verse scène
      while (doos && doos.firstChild) { const c = doos.firstChild; if (c.getContext) c.forceContextLoss?.(); doos.removeChild(c); }
    };
  }, [url, naam]); // eslint-disable-line

  if (fout === 'bbmodel') return <p className="text-[11px] text-parch/60 bg-black/30 border border-bronze/25 rounded-lg p-2">{t('med.bbmodel')}</p>;
  return (
    <div className="space-y-1">
      <div ref={doosRef} className="relative w-full rounded-lg border border-bronze/30 bg-black/50 overflow-hidden">
        {!klaar && !fout && <div className="absolute inset-0 grid place-items-center text-[11px] text-parch/50">…</div>}
        {fout && <div className="absolute inset-0 grid place-items-center text-[11px] text-red-300/80 p-2 text-center">{fout}</div>}
      </div>
      <div className="flex items-center gap-1">
        <button className={`${BNA} ${spin ? 'text-gold bg-gold/10' : ''}`} title={t('med.draaien')} onClick={() => { const aan = !spin; setSpin(aan); if (apiRef.current) apiRef.current.spin = aan; }}><Move3d className="size-4" /></button>
        <button className={BNA} title={t('med.herframe')} onClick={() => apiRef.current?.herstel()}>
          <span className="text-sm leading-none">⟲</span>
        </button>
        <span className="text-[10px] text-parch/45 ml-1">{t('med.draai_hint')}</span>
      </div>
    </div>
  );
}
