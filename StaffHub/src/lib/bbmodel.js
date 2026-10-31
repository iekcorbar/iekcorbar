// Bouwt van een Blockbench .bbmodel (project-JSON) een three.js-preview, inclusief animaties.
// Conventies (zoals Blockbench/FMM): bone-rotatie M = Rz·Ry·Rx om de eigen origin (three-order 'ZYX'),
// een animatie-keyframe VERVANGT de basisrotatie, positie-keyframes worden erbij opgeteld.
// Elke node (bone of element) krijgt als three-naam zijn uuid, zodat AnimationClip-tracks
// ("<uuid>.quaternion") er ondubbelzinnig aan hangen.

const FACE_HOEKEN = (x0, y0, z0, x1, y1, z1) => ({
  north: [[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0]],
  south: [[x1, y0, z1], [x0, y0, z1], [x0, y1, z1], [x1, y1, z1]],
  east:  [[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1]],
  west:  [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]],
  up:    [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]],
  down:  [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]],
});

const rad = (v) => (Number(v) || 0) * Math.PI / 180;
const getal = (v, d = 0) => { const n = Number(v); return Number.isFinite(n) ? n : d; };

function textureVan(THREE, entry, base64map) {
  const src = entry?.source || (entry?.id != null ? base64map?.[entry.id] : null) || null;
  if (!src) return { tex: null, src: null };
  const img = new Image();
  img.crossOrigin = 'anonymous';
  const tex = new THREE.Texture(img);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.generateMipmaps = false;
  tex.colorSpace = THREE.SRGBColorSpace;
  img.onload = () => { tex.needsUpdate = true; };
  img.src = src;
  return { tex, src };
}

// Een element → pivot-Group (op de element-origin, relatief aan de ouder) met één mesh per textuur.
function bouwElement(THREE, el, res, ouderOrigin, zoekTex, materiaal, tellers) {
  const f = el.from, t = el.to;
  if (!f || !t || !el.faces) return null;
  const origin = el.origin || [(f[0] + t[0]) / 2, (f[1] + t[1]) / 2, (f[2] + t[2]) / 2];
  const hoeken = FACE_HOEKEN(
    (Math.min(f[0], t[0]) - origin[0]) / res, (Math.min(f[1], t[1]) - origin[1]) / res, (Math.min(f[2], t[2]) - origin[2]) / res,
    (Math.max(f[0], t[0]) - origin[0]) / res, (Math.max(f[1], t[1]) - origin[1]) / res, (Math.max(f[2], t[2]) - origin[2]) / res,
  );

  const perTex = new Map(); // textuur-index → { pos, uv }
  for (const [richting, face] of Object.entries(el.faces)) {
    const cs = hoeken[richting];
    if (!cs || !face || !Array.isArray(face.uv) || face.uv.length !== 4) continue;
    if (face.texture === null || face.texture === undefined) continue;
    const idx = Number(face.texture);
    const { entry } = zoekTex(idx);
    const breed = getal(entry?.uv_width, getal(entry?.width, 16)) || 16;
    const hoog = getal(entry?.uv_height, getal(entry?.height, 16)) || 16;
    const [u1, v1, u2, v2] = face.uv;
    const uvs = [[u1 / breed, 1 - v1 / hoog], [u2 / breed, 1 - v1 / hoog], [u2 / breed, 1 - v2 / hoog], [u1 / breed, 1 - v2 / hoog]];
    let b = perTex.get(idx);
    if (!b) perTex.set(idx, (b = { pos: [], uv: [] }));
    for (const i of [0, 1, 2, 0, 2, 3]) { b.pos.push(...cs[i]); b.uv.push(...uvs[i]); }
    tellers.set(idx, (tellers.get(idx) || 0) + 1);
  }
  if (!perTex.size) return null;

  const pivot = new THREE.Group();
  pivot.name = el.uuid;
  pivot.userData = { uuid: el.uuid, name: el.name };
  pivot.position.set((origin[0] - ouderOrigin[0]) / res, (origin[1] - ouderOrigin[1]) / res, (origin[2] - ouderOrigin[2]) / res);
  pivot.rotation.order = 'ZYX';
  const rot = el.rotation || [0, 0, 0];
  const as = el.rotation_axis ? String(el.rotation_axis).toLowerCase() : null;
  pivot.rotation.set(
    as && as !== 'x' ? 0 : rad(rot[0]),
    as && as !== 'y' ? 0 : rad(rot[1]),
    as && as !== 'z' ? 0 : rad(rot[2]),
  );
  pivot.userData.basisRot = [pivot.rotation.x, pivot.rotation.y, pivot.rotation.z];

  for (const [idx, b] of perTex) {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(b.pos, 3));
    geo.setAttribute('uv', new THREE.Float32BufferAttribute(b.uv, 2));
    const mat = materiaal(idx);
    const mesh = new THREE.Mesh(geo, mat);
    mesh.userData = { uuid: el.uuid, name: el.name, tex: idx };
    pivot.add(mesh);
  }
  return pivot;
}

function bouwClips(THREE, data, res, nodes) {
  const clips = [];
  for (const anim of data?.animations || []) {
    const tracks = [];
    for (const [sleutel, animator] of Object.entries(anim.animators || {})) {
      const node = nodes.get(sleutel);
      if (!node) continue;
      const perKanaal = {};
      for (const kf of animator.keyframes || []) (perKanaal[kf.channel || 'rotation'] ||= []).push(kf);
      for (const [kanaal, lijst] of Object.entries(perKanaal)) {
        lijst.sort((a, b) => getal(a.time) - getal(b.time));
        const tijden = lijst.map((k) => getal(k.time));
        if (kanaal === 'rotation') {
          const waarden = [];
          const q = new THREE.Quaternion();
          const e = new THREE.Euler(0, 0, 0, 'ZYX');
          for (const k of lijst) {
            const dp = k.data_points?.[0] || {};
            e.set(rad(dp.x), rad(dp.y), rad(dp.z), 'ZYX');
            q.setFromEuler(e);
            waarden.push(q.x, q.y, q.z, q.w);
          }
          tracks.push(new THREE.QuaternionKeyframeTrack(`${node.name}.quaternion`, tijden, waarden));
        } else if (kanaal === 'position') {
          const basis = node.position;
          const waarden = [];
          for (const k of lijst) {
            const dp = k.data_points?.[0] || {};
            waarden.push(basis.x + getal(dp.x) / res, basis.y + getal(dp.y) / res, basis.z + getal(dp.z) / res);
          }
          tracks.push(new THREE.VectorKeyframeTrack(`${node.name}.position`, tijden, waarden));
        } else if (kanaal === 'scale') {
          const waarden = [];
          for (const k of lijst) {
            const dp = k.data_points?.[0] || {};
            waarden.push(getal(dp.x, 1), getal(dp.y, 1), getal(dp.z, 1));
          }
          tracks.push(new THREE.VectorKeyframeTrack(`${node.name}.scale`, tijden, waarden));
        }
      }
    }
    if (!tracks.length) continue;
    const lengte = getal(anim.length, 0) || Math.max(...tracks.map((t) => t.times[t.times.length - 1]), 0.001);
    clips.push({
      name: anim.name || 'animation',
      clip: new THREE.AnimationClip(anim.name || 'animation', lengte, tracks),
      loop: anim.loop !== 'once' && anim.loop !== 'hold',
      length: lengte,
    });
  }
  return clips;
}

export function bouwBbmodel(THREE, data) {
  const res = getal(data?.resolution, 16) || 16;
  const textures = Array.isArray(data?.textures) ? data.textures : [];
  const b64 = data?.textures_base64 || {};
  const root = new THREE.Group();
  const nodes = new Map();      // uuid → Object3D (bones én elementen)
  const matCache = new Map();
  const tellers = new Map();    // textuur-index → aantal vlakken

  const zoekTex = (idx) => {
    const entry = textures.find((tx) => Number(tx.id) === idx) || textures[idx];
    return { entry };
  };
  const materiaal = (idx) => {
    let m = matCache.get(idx);
    if (!m) {
      const { entry } = zoekTex(idx);
      const { tex } = textureVan(THREE, entry, b64);
      m = new THREE.MeshBasicMaterial({ map: tex || undefined, color: tex ? 0xffffff : 0x9aa4b2, transparent: true, alphaTest: 0.5, side: THREE.DoubleSide });
      matCache.set(idx, m);
    }
    return m;
  };

  const elementen = (data?.elements || []).filter((el) => (el.type || 'cube') === 'cube');
  const elMap = new Map(elementen.map((el) => [el.uuid, el]));
  const geplaatst = new Set();

  function bouwNode(node, ouderOrigin) {
    if (typeof node === 'string') {
      const el = elMap.get(node);
      if (!el) return null;
      const g = bouwElement(THREE, el, res, ouderOrigin, zoekTex, materiaal, tellers);
      if (g) { nodes.set(el.uuid, g); geplaatst.add(el.uuid); }
      return g;
    }
    if (node && typeof node === 'object') {
      const origin = node.origin || [0, 0, 0];
      const g = new THREE.Group();
      g.name = node.uuid;
      g.userData = { uuid: node.uuid, name: node.name };
      g.position.set((origin[0] - ouderOrigin[0]) / res, (origin[1] - ouderOrigin[1]) / res, (origin[2] - ouderOrigin[2]) / res);
      g.rotation.order = 'ZYX';
      const r = node.rotation || [0, 0, 0];
      g.rotation.set(rad(r[0]), rad(r[1]), rad(r[2]));
      nodes.set(node.uuid, g);
      for (const kind of node.children || []) {
        const c = bouwNode(kind, origin);
        if (c) g.add(c);
      }
      return g;
    }
    return null;
  }

  for (const n of data?.outliner || []) {
    const g = bouwNode(n, [0, 0, 0]);
    if (g) root.add(g);
  }
  // elementen die niet in de outliner staan (of geen outliner): los toevoegen
  for (const el of elementen) {
    if (geplaatst.has(el.uuid)) continue;
    const g = bouwElement(THREE, el, res, [0, 0, 0], zoekTex, materiaal, tellers);
    if (g) { nodes.set(el.uuid, g); root.add(g); }
  }

  const clips = bouwClips(THREE, data, res, nodes);

  const texturen = [];
  for (const [idx, vlakken] of tellers) {
    const { entry } = zoekTex(idx);
    if (!entry) continue;
    const { src } = textureVan(THREE, entry, b64);
    texturen.push({ idx, naam: String(entry?.name || entry?.path || 'texture ' + idx).split(/[\\/]/).pop(), src, vlakken });
  }
  texturen.sort((a, b) => a.idx - b.idx);

  root.userData.bb = { res, elementen: elementen.length, texturen, animations: clips.map((c) => c.name) };
  root.userData.animations = clips;
  return root;
}
