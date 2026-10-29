// Bouwt van een Blockbench .bbmodel (project-JSON) een echte three.js-preview.
//
// Een bbmodel is GEEN vorm maar een bouwplan: cubes (from/to in pixels met
// "resolution" = pixels-per-blok, meestal 16), per vlak een UV-vierhoek en een
// textuur-id; de texturen zitten als base64-PNG in het bestand zelf
// (entry.source 'data:image/png;base64,...', soms in textures_base64).
//
// Deze bouwer maakt per textuur Eén samengevoegde mesh (twee driehoeken per
// vlak, niet-geïndexeerd), draait hoeken weg vanuit 1 in de texture (de
// blockbench-v-as loopt naar beneden, three's flipY naar boven) en past de
// per-element rotatie (origin + as + graden) toe. Groepen en animaties worden
// genegeerd: statische pose zoals opgeslagen, voldoende voor een staff-rit.
//
// Wordt LAAD-end geïmporteerd door Model3D (zie Media.jsx), net als three zelf.

const ASSEN = { x: [1, 0, 0], y: [0, 1, 0], z: [0, 0, 1] };

// vier hoekpunten per vlak, rechtsom gezien van buiten; UV-volgorde tl,tr,br,bl
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
  let src = entry?.source || '';
  if (!src && entry?.path && base64map?.[entry.path]) {
    const b64 = base64map[entry.path];
    src = b64.startsWith('data:') ? b64 : 'data:image/png;base64,' + b64;
  }
  if (!src || !src.startsWith('data:')) return null;
  try {
    const tex = new THREE.TextureLoader().load(src);
    tex.magFilter = THREE.NearestFilter;         // blok-textuur: scherp, geen blur
    tex.minFilter = THREE.NearestFilter;
    tex.generateMipmaps = false;
    if (THREE.SRGBColorSpace) tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  } catch { return null; }                       // outside the browser (smoke test): gray substitute
}

/**
 * @param THREE  de dynamisch geladen three-module
 * @param data   geparsede .bbmodel JSON
 * @returns THREE.Group met één mesh per gebruikte textuur
 */
export function bouwBbmodel(THREE, data) {
  const res = Number(data?.resolution) || 16;
  const textures = Array.isArray(data?.textures) ? data.textures : [];
  const b64 = data?.textures_base64 || {};
  const groep = new THREE.Group();
  const bakken = new Map();                    // textuur-index -> {pos[], uv[], entry}

  for (const el of data?.elements || []) {
    if ((el.type || 'cube') !== 'cube') continue;              // cilinders/meshes: overslaan
    if (!el.from || !el.to || !el.faces) continue;
    const f = el.from, t = el.to;
    const x0 = Math.min(f[0], t[0]) / res, x1 = Math.max(f[0], t[0]) / res;
    const y0 = Math.min(f[1], t[1]) / res, y1 = Math.max(f[1], t[1]) / res;
    const z0 = Math.min(f[2], t[2]) / res, z1 = Math.max(f[2], t[2]) / res;
    const hoeken = vlakken(x0, y0, z0, x1, y1, z1);

    // rotatie van het element: om 'origin' langs één as (graden)
    const draai = el.rotation && ASSEN[String(el.rotation.axis || '').toLowerCase()];
    const draaiHoek = draai ? Number(el.rotation.angle || 0) * Math.PI / 180 : 0;
    const o = el.rotation?.origin ? el.rotation.origin.map((v) => v / res) : null;
    const as = draai ? new THREE.Vector3(...ASSEN[String(el.rotation.axis).toLowerCase()]) : null;
    const v3 = new THREE.Vector3();
    const draip = (p) => {
      if (!draaiHoek || !o) return p;
      v3.set(p[0] - o[0], p[1] - o[1], p[2] - o[2]).applyAxisAngle(as, draaiHoek);
      return [v3.x + o[0], v3.y + o[1], v3.z + o[2]];
    };

    for (const [richting, face] of Object.entries(el.faces)) {
      const corners = hoeken[richting];
      if (!corners || !face || !Array.isArray(face.uv) || face.uv.length !== 4) continue;
      const idx = Number(face.texture ?? -1);
      const entry = textures.find((tx) => Number(tx.id) === idx) || textures[idx];
      const breed = Number(entry?.width) || Number(entry?.uv_width) || 16;
      const hoog = Number(entry?.height) || Number(entry?.uv_height) || 16;
      let bak = bakken.get(idx);
      if (!bak) { bak = { pos: [], uv: [], entry }; bakken.set(idx, bak); }
      const [u1, v1, u2, v2] = face.uv;
        // tl, tr, br, bl → twee driehoeken; v omlaag (bb) → omhoog (three)
      const uv = [[u1 / breed, 1 - v1 / hoog], [u2 / breed, 1 - v1 / hoog], [u2 / breed, 1 - v2 / hoog], [u1 / breed, 1 - v2 / hoog]];
      const volgorde = [0, 1, 2, 0, 2, 3];
      for (const i of volgorde) {
        bak.pos.push(...draip(corners[i]));
        bak.uv.push(...uv[i]);
      }
    }
  }

  for (const { pos, uv, entry } of bakken.values()) {
    if (!pos.length) continue;
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    const map = textureVan(THREE, entry, b64);
    const mat = new THREE.MeshBasicMaterial({
      map: map || undefined,
      color: map ? 0xffffff : 0x9aa4b2,          // geen textuur: staalgris
      transparent: true,
      alphaTest: 0.5,
      side: THREE.DoubleSide,
    });
    groep.add(new THREE.Mesh(geo, mat));
  }
  return groep;
}
