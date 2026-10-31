// Bouwt van een Blockbench .bbmodel (project-JSON) een echte three.js-preview.
// Inclusief animaties: per element een mesh, gegroepeerd volgens de outliner,
// en AnimationClips die de juiste bone/element animeren.
const ASSEN = { x: [1, 0, 0], y: [0, 1, 0], z: [0, 0, 1] };

function vlakken(x0, y0, z0, x1, y1, z1) {
  return {
    north: [[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0]],
    south: [[x1, y0, z1], [x0, y0, z1], [x0, y1, z1], [x1, y1, z1]],
    east:  [[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1]],
    west:  [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]],
    up:    [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]],
    down:  [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]],
  };
}

function textureVan(THREE, entry, base64map) {
  const src = entry?.source || (entry?.id != null ? base64map?.[entry.id] : null) || null;
  if (!src) return { tex: null, src: null };
  const img = new Image();
  img.crossOrigin = 'anonymous';
  const tex = new THREE.Texture(img);
  img.onload = () => { tex.needsUpdate = true; };
  img.src = src;
  return { tex, src };
}

function deg(v) { return (Number(v) || 0) * Math.PI / 180; }

function bouwElement(THREE, el, res, textures, b64) {
  const f = el.from, t = el.to;
  if (!f || !t || !el.faces) return null;
  const x0 = Math.min(f[0], t[0]) / res, x1 = Math.max(f[0], t[0]) / res;
  const y0 = Math.min(f[1], t[1]) / res, y1 = Math.max(f[1], t[1]) / res;
  const z0 = Math.min(f[2], t[2]) / res, z1 = Math.max(f[2], t[2]) / res;
  const corners = vlakken(x0, y0, z0, x1, y1, z1);

  // rotation
  const rot = el.rotation || [0, 0, 0];
  const origin = el.origin ? el.origin.map((v) => v / res) : [(x0+x1)/2, (y0+y1)/2, (z0+z1)/2];
  const axis = el.rotation ? (el.rotation_axis || 'y') : null;
  const angle = axis ? deg(rot[axis === 'x' ? 0 : axis === 'y' ? 1 : 2]) : 0;

  const groep = new THREE.Group();
  groep.name = el.name || el.uuid;
  groep.position.set(origin[0], origin[1], origin[2]);
  if (axis) {
    const a = ASSEN[axis.toLowerCase()] || ASSEN.y;
    groep.rotation.set(
      axis === 'x' ? angle : 0,
      axis === 'y' ? angle : 0,
      axis === 'z' ? angle : 0
    );
  }
  // offset back by origin (element position is from/to, origin is pivot)
  // Actually, Blockbench applies rotation around origin, position is absolute
  // So we need to: position at origin, rotate, then offset by (center - origin)
  // Simplified: just set position to center, rotation around origin handled by group hierarchy

  // Build mesh per texture
  const pos = [], uv = [];
  let idx = -1;
  for (const [richting, face] of Object.entries(el.faces)) {
    const cs = corners[richting];
    if (!cs || !face || !Array.isArray(face.uv) || face.uv.length !== 4) continue;
    idx = Number(face.texture ?? -1);
    const entry = textures.find((tx) => Number(tx.id) === idx) || textures[idx];
    const breed = Number(entry?.width) || Number(entry?.uv_width) || 16;
    const hoog = Number(entry?.height) || Number(entry?.uv_height) || 16;
    const [u1, v1, u2, v2] = face.uv;
    const uvs = [[u1 / breed, 1 - v1 / hoog], [u2 / breed, 1 - v1 / hoog], [u2 / breed, 1 - v2 / hoog], [u1 / breed, 1 - v2 / hoog]];
    const volgorde = [0, 1, 2, 0, 2, 3];
    for (const i of volgorde) {
      // apply rotation around origin
      const p = cs[i];
      const dx = p[0] - origin[0], dy = p[1] - origin[1], dz = p[2] - origin[2];
      // simple: no rotation applied to vertices, group handles it
      pos.push(p[0], p[1], p[2]);
      uv.push(...uvs[i]);
    }
  }

  if (!pos.length) return null;

  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  const entry = textures.find((tx) => Number(tx.id) === idx) || textures[idx];
  const { tex: map, src } = textureVan(THREE, entry, b64);
  const mat = new THREE.MeshBasicMaterial({
    map: map || undefined,
    color: map ? 0xffffff : 0x9aa4b2,
    transparent: true,
    alphaTest: 0.5,
    side: THREE.DoubleSide,
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.userData.uuid = el.uuid;
  mesh.userData.name = el.name;
  mesh.userData.tex = idx;
  // position mesh at origin (rotation handled by parent group)
  mesh.position.set(-origin[0], -origin[1], -origin[2]);
  groep.add(mesh);
  groep.userData.uuid = el.uuid;
  groep.userData.name = el.name;

  return { group: groep, uuid: el.uuid, name: el.name };
}

export function bouwBbmodel(THREE, data) {
  const res = Number(data?.resolution) || 16;
  const textures = Array.isArray(data?.textures) ? data.textures : [];
  const b64 = data?.textures_base64 || {};
  const root = new THREE.Group();
  const uuidMap = new Map(); // uuid -> THREE.Object3D

  // Build elements
  const elements = data?.elements || [];
  const elMap = new Map();
  for (const el of elements) {
    if ((el.type || 'cube') !== 'cube') continue;
    const result = bouwElement(THREE, el, res, textures, b64);
    if (result) {
      elMap.set(el.uuid, result);
      uuidMap.set(el.uuid, result.group);
    }
  }

  // Build outliner hierarchy
  function buildNode(node) {
    if (typeof node === 'string') {
      // element uuid
      const el = elMap.get(node);
      return el ? el.group : null;
    }
    if (typeof node === 'object' && node !== null) {
      const g = new THREE.Group();
      g.name = node.name || 'group';
      g.userData.uuid = node.uuid;
      g.userData.name = node.name;
      if (node.origin) g.position.set(node.origin[0]/res, node.origin[1]/res, node.origin[2]/res);
      if (node.rotation) {
        g.rotation.set(deg(node.rotation[0] || 0), deg(node.rotation[1] || 0), deg(node.rotation[2] || 0));
      }
      uuidMap.set(node.uuid, g);
      for (const child of node.children || []) {
        const c = buildNode(child);
        if (c) g.add(c);
      }
      return g;
    }
    return null;
  }

  for (const node of data?.outliner || []) {
    const g = buildNode(node);
    if (g) root.add(g);
  }

  // If no outliner, add all elements directly
  if (!data?.outliner?.length) {
    for (const [uuid, el] of elMap) {
      root.add(el.group);
    }
  }

  // Parse animations
  const anims = data?.animations || [];
  const clips = [];
  for (const anim of anims) {
    const tracks = [];
    for (const [animUuid, animator] of Object.entries(anim.animators || {})) {
      const target = uuidMap.get(animator.name);
      if (!target) continue;
      for (const kf of animator.keyframes || []) {
        const channel = kf.channel || 'rotation';
        const times = [Number(kf.time) || 0];
        const values = [];
        const dp = kf.data_points?.[0] || {};
        if (channel === 'rotation') {
          values.push(deg(dp.x || 0), deg(dp.y || 0), deg(dp.z || 0));
        } else if (channel === 'position') {
          values.push(Number(dp.x || 0)/res, Number(dp.y || 0)/res, Number(dp.z || 0)/res);
        } else if (channel === 'scale') {
          values.push(Number(dp.x || 1), Number(dp.y || 1), Number(dp.z || 1));
        }
        // Need multiple keyframes for a track - collect all
      }
      // Build tracks from all keyframes of this animator
      const channels = ['rotation', 'position', 'scale'];
      for (const ch of channels) {
        const kfs = (animator.keyframes || []).filter((k) => (k.channel || 'rotation') === ch);
        if (kfs.length === 0) continue;
        const times = kfs.map((k) => Number(k.time) || 0);
        const values = [];
        for (const kf of kfs) {
          const dp = kf.data_points?.[0] || {};
          if (ch === 'rotation') {
            values.push(deg(dp.x || 0), deg(dp.y || 0), deg(dp.z || 0));
          } else if (ch === 'position') {
            values.push(Number(dp.x || 0)/res, Number(dp.y || 0)/res, Number(dp.z || 0)/res);
          } else if (ch === 'scale') {
            values.push(Number(dp.x || 1), Number(dp.y || 1), Number(dp.z || 1));
          }
        }
        const TrackClass = ch === 'rotation' ? THREE.QuaternionKeyframeTrack : THREE.VectorKeyframeTrack;
        // Use VectorKeyframeTrack for all (simpler)
        const trackName = target.name + '.' + (ch === 'rotation' ? 'rotation' : ch === 'position' ? 'position' : 'scale');
        const track = new THREE.VectorKeyframeTrack(trackName, times, values);
        tracks.push(track);
      }
    }
    if (tracks.length > 0) {
      const clip = new THREE.AnimationClip(anim.name || 'animation', Number(anim.length) || -1, tracks);
      clips.push({ name: anim.name || 'animation', clip, loop: anim.loop !== false });
    }
  }

  // Center and scale
  const bb = new THREE.Box3().setFromObject(root);
  const om = bb.getSize(new THREE.Vector3());
  const f = 2 / Math.max(om.x, om.y, om.z, 1e-6);
  root.scale.setScalar(f);
  bb.setFromObject(root);
  root.position.sub(bb.getCenter(new THREE.Vector3()));
  bb.setFromObject(root);
  root.position.y -= bb.min.y;

  const texturen = [];
  // collect unique textures
  const seen = new Set();
  for (const el of elements) {
    for (const face of Object.values(el.faces || {})) {
      const idx = Number(face.texture ?? -1);
      if (seen.has(idx)) continue;
      seen.add(idx);
      const entry = textures.find((tx) => Number(tx.id) === idx) || textures[idx];
      if (entry) {
        const { src } = textureVan(THREE, entry, b64);
        texturen.push({ idx, naam: String(entry?.name || entry?.path || 'texture ' + idx).split(/[\\/]/).pop(), src, vlakken: 0 });
      }
    }
  }

  root.userData.bb = { res, elementen: elements.length, texturen, animations: clips.map((c) => c.name) };
  root.userData.animations = clips;
  return root;
}