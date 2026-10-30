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
import { Play, Pause, Square, SkipBack, SkipForward, Shuffle, Repeat, Move3d, Maximize2 } from 'lucide-react';
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
 * 3D-kijker voor .glb/.gltf én .bbmodel (three.js, lui geladen). Sleep draait,
 * scroll zoomt, animaties uit Blockbench/Blender spoelen vanzelf.
 *
 * Sinds 0.5.0 is dit méér dan een raampje: de kijker is dé plek waar een
 * staff-lid een model BEOORDEELT, dus hij heeft
 *   - drie weergaven: textuur (hoe het eruitziet), klei (hoe het GEBOUWD is,
 *     zonder ruis van textuur) en draadmodel (kaal skelet, voor overlap);
 *   - bij .bbmodel een textuurpaneel: elke atlas als thumbnail, klikbaar om
 *     die textuur te verbergen - zo zie je in één draai welke cube bij welke
 *     plaat hoort en of een UV-verkeerd zit;
 *   - een fullscreen-knop (de popup eromheen is al groot, maar een model op
 *     de volle hoogte van het scherm beoordelen is nét even prettiger);
 *   - een inforegeltje: elementen, texturen en de resolution van het
 *     project, want "12 elementen, 1 textuur" verraadt een luie textuurbeurt.
 *
 * .bbmodel (Blockbench-PROJECT, json met cubes + base64-texturen) wordt door
 * eigen builder (lib/bbmodel) naar meshes omgezet: statische pose, per textuur
 * één mesh.
 *
 * @param groot  groter canvas (62% van het scherm) - de popup-setting
 */
export function Model3D({ url, naam, groot = false }) {
  const { t } = useLang();
  const doosRef = useRef(null);
  const [klaar, setKlaar] = useState(false);
  const [fout, setFout] = useState('');
  const apiRef = useRef(null);
  const [spin, setSpin] = useState(true);
  const [modus, setModus] = useState('textuur');       // textuur | klei | draad
  const [texturen, setTexturen] = useState([]);         // bbmodel-only: [{idx,naam,src}]
  const [meta, setMeta] = useState(null);               // bbmodel-only: {elementen, texturen, res}
  const [verberg, setVerberg] = useState(() => new Set());

  useEffect(() => {
    let weg = false;
    const doos = doosRef.current;
    if (!doos) return undefined;
    (async () => {
      try {
        const THREE = await import('three');
        const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js');
        if (weg) return;
        const breed = () => doos.clientWidth || 380;
        const hoog = () => Math.max(groot ? 360 : 240, Math.min(window.innerHeight * (groot ? 0.62 : 0.45), groot ? 1400 : 420));
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
        // bron: glb/gltf via de loader; .bbmodel via eigen builder (Blockbench-project)
        let object;
        if (String(naam || '').toLowerCase().endsWith('.bbmodel')) {
          const { bouwBbmodel } = await import('@/lib/bbmodel');
          object = bouwBbmodel(THREE, JSON.parse(await (await fetch(url)).text()));
        } else {
          const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js');
          const g = await new Promise((res, rej) => new GLTFLoader().load(url, res, undefined, rej));
          object = g.scene || g.scenes?.[0];
          if (g.animations?.length) { mixer.h = new THREE.AnimationMixer(object); for (const a of g.animations) mixer.h.clipAction(a).play(); }
        }
        if (weg) { renderer.dispose(); return; }
        if (object) {
          // centreer en schaalt naar een kast van ~2 eenheden - élke modelgrootte vult het scherm
          const bb = new THREE.Box3().setFromObject(object);
          const om = bb.getSize(new THREE.Vector3());
          const f = 2 / Math.max(om.x, om.y, om.z, 1e-6);
          object.scale.setScalar(f);
          bb.setFromObject(object);
          object.position.sub(bb.getCenter(new THREE.Vector3()));   // in het midden
          bb.setFromObject(object);
          object.position.y -= bb.min.y;                            // op het raster zetten
          scene.add(object);
        }
        // kleimateriaal: één gedeeld, goedkoop, lichtgevoelig (standaard Lambert)
        const klei = new THREE.MeshLambertMaterial({ color: 0xd8dee9 });
        const meshList = [];
        if (object) object.traverse((m) => { if (m.isMesh) { m.userData.orgMat = m.material; meshList.push(m); } });
        const hor = () => {
          if (weg) return;
          requestAnimationFrame(hor);
          controls.update();
          if (apiRef.current?.spin) scene.rotation.y += 0.005;
          if (mixer.h) mixer.h.update(1 / 60);
          renderer.render(scene, camera);
        };
        requestAnimationFrame(hor);
        const herframe = () => {
          // canvas-grootte volgt de doos: ook na fullscreen-wissel en ruit-schaal
          const b = breed(), h = hoog();
          renderer.setSize(b, h);
          camera.aspect = b / h; camera.updateProjectionMatrix();
        };
        window.addEventListener('resize', herframe);
        const ws = () => { if (!document.fullscreenElement) window.removeEventListener('resize', herframe); };
        document.addEventListener('fullscreenchange', herframe);
        document.addEventListener('fullscreenchange', ws);
        apiRef.current = {
          spin, herstel: () => { camera.position.set(2.2, 1.8, 2.6); controls.target.set(0, 0, 0); controls.update(); },
          meshes: meshList, klei,
          full: () => { const d = doosRef.current; if (d?.requestFullscreen) d.requestFullscreen().then(herframe).catch(() => { }); },
        };
        setTexturen(object?.userData?.bb?.texturen || []);
        setMeta(object?.userData?.bb ? { elementen: object.userData.bb.elementen, texturen: object.userData.bb.texturen.length, res: object.userData.bb.res } : null);
        setKlaar(true);
      } catch (e) { if (!weg) setFout(String(e?.message || e)); }
    })();
    return () => {
      weg = true;
      // renderer + canvas opruimen: bij elk heropenen draait een verse scène
      while (doos && doos.firstChild) { const c = doos.firstChild; if (c.getContext) c.forceContextLoss?.(); doos.removeChild(c); }
    };
  }, [url, naam]); // eslint-disable-line

  // weergave-modus doorzetten naar de levende meshes (de scène leeft in apiRef)
  useEffect(() => {
    const api = apiRef.current; if (!api?.meshes) return;
    for (const m of api.meshes) {
      const org = m.userData.orgMat;
      if (modus === 'klei') m.material = api.klei;
      else { m.material = org; if (org) org.wireframe = modus === 'draad'; }
    }
  }, [modus, klaar]);

  // textuur-aan/uit (bbmodel): de mesh van dát atlas wegtoveren, rest blijft
  useEffect(() => {
    const api = apiRef.current; if (!api?.meshes) return;
    for (const m of api.meshes) if (m.userData.tex !== undefined) m.visible = !verberg.has(m.userData.tex);
  }, [verberg, klaar]);

  if (fout === 'bbmodel') return <p className="text-[11px] text-parch/60 bg-black/30 border border-bronze/25 rounded-lg p-2">{t('med.bbmodel')}</p>;
  const MOD = [['textuur', t('med.mod_tex')], ['klei', t('med.mod_clay')], ['draad', t('med.mod_wire')]];
  return (
    <div className="flex gap-2 items-start">
      <div className="flex-1 min-w-0 space-y-1">
        <div ref={doosRef} className={`relative w-full rounded-lg border border-bronze/30 bg-black/50 overflow-hidden ${groot ? 'min-h-[360px]' : ''}`}>
          {!klaar && !fout && <div className="absolute inset-0 grid place-items-center text-[11px] text-parch/50">…</div>}
          {fout && <div className="absolute inset-0 grid place-items-center text-[11px] text-red-300/80 p-2 text-center">{fout}</div>}
        </div>
        <div className="flex items-center gap-1 flex-wrap">
          {MOD.map(([k, lab]) => (
            <button key={k} className={`px-2 py-1 rounded text-[10px] border ${modus === k ? 'bg-gold/15 border-gold/60 text-gold' : 'border-bronze/30 text-parch/60 hover:border-gold/50'}`} onClick={() => setModus(k)} title={lab}>{lab}</button>
          ))}
          <span className="w-1" />
          <button className={`${BNA} ${spin ? 'text-gold bg-gold/10' : ''}`} title={t('med.draaien')} onClick={() => { const aan = !spin; setSpin(aan); if (apiRef.current) apiRef.current.spin = aan; }}><Move3d className="size-4" /></button>
          <button className={BNA} title={t('med.herframe')} onClick={() => apiRef.current?.herstel()}>
            <span className="text-sm leading-none">⟲</span>
          </button>
          <button className={BNA} title={t('med.full')} onClick={() => apiRef.current?.full()}><Maximize2 className="size-4" /></button>
          <span className="text-[10px] text-parch/45 ml-1">{meta ? `${meta.elementen} ${t('med.el')} · ${meta.texturen} ${t('med.tx')} · ${meta.res} px` : t('med.draai_hint')}</span>
        </div>
      </div>
      {/* textuurpaneel: alleen bij bbmodels die texturen meekregen */}
      {klaar && texturen.length > 0 && (
        <div className="w-40 shrink-0 space-y-1">
          <p className="text-[9px] uppercase tracking-wider text-parch/45">{t('med.texturen')}</p>
          <div className="max-h-[46vh] overflow-auto space-y-1 pr-0.5">
            {texturen.map((tx) => (
              <button key={tx.idx}
                onClick={() => setVerberg((v) => { const s = new Set(v); if (s.has(tx.idx)) s.delete(tx.idx); else s.add(tx.idx); return s; })}
                title={verberg.has(tx.idx) ? t('med.tex_show') : t('med.tex_hide')}
                className={`w-full rounded-lg border p-1 text-left transition-colors ${verberg.has(tx.idx) ? 'border-bronze/20 bg-black/20 opacity-45' : 'border-gold/40 bg-black/40'}`}>
                {tx.src
                  ? <img src={tx.src} alt="" className="w-full h-14 object-contain rounded bg-black/60 [image-rendering:pixelated]" />
                  : <div className="w-full h-14 rounded bg-black/60 grid place-items-center text-[9px] text-parch/40">{t('fp.unreadable')}</div>}
                <p className="text-[9px] text-parch/70 truncate mt-0.5">{tx.naam}</p>
                <p className="text-[8px] text-parch/40 tabular-nums">{tx.vlakken} × {t('med.vlakken')}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

