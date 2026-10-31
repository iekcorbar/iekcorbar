'use client';
// Animatie-editor voor de 3D-viewer (.bbmodel): kies/maak een animatie, kies een bone,
// voeg keyframes toe, bewerk of verwijder ze. Werkt direct op de bron-JSON (api.current.data);
// na elke wijziging roept hij herbouw() aan, waarna de viewer de clips opnieuw opbouwt.
import { useState } from 'react';

const KANALEN = { rotation: [0, 0, 0], position: [0, 0, 0], scale: [1, 1, 1] };

function alleBones(data) {
  const uit = [];
  const loop = (lijst) => {
    for (const n of lijst || []) {
      if (n && typeof n === 'object') { uit.push({ uuid: n.uuid, name: n.name || n.uuid }); loop(n.children); }
    }
  };
  loop(data?.outliner);
  return uit;
}

const KNOP = 'px-2 py-1 rounded text-[10px] border border-bronze/40 text-parch/70 hover:border-gold/50';
const VELD = 'px-1 py-0.5 rounded border border-bronze/30 bg-black/40 text-parch/80';
const KEUZE = 'px-2 py-1 rounded text-[10px] border border-bronze/30 bg-black/40 text-parch/70';

export default function AnimEditor({ api, tijd, boneKey, setBoneKey, zet, herbouw, download, slaOp, opslag, wijzig }) {
  const data = api.current?.data;
  const [animSel, setAnimSel] = useState(0);
  const [kanaal, setKanaal] = useState('rotation');
  const [nieuwTijd, setNieuwTijd] = useState(null);
  if (!data) return null;

  const anims = data.animations || (data.animations = []);
  const bron = anims[Math.min(animSel, anims.length - 1)];
  const bones = alleBones(data);
  const bone = bones.find((b) => b.uuid === boneKey) || bones[0];

  const nieuweAnimatie = async () => {
    anims.push({ uuid: crypto.randomUUID(), name: `animatie ${anims.length + 1}`, loop: 'loop', override: false, length: 1, snapping: 24, animators: {} });
    setAnimSel(anims.length - 1);
    await herbouw();
  };
  const voegToe = async () => {
    if (!bron || !bone) return;
    const a = (bron.animators[bone.uuid] ||= { name: bone.name, type: 'bone', keyframes: [] });
    const t = Number(nieuwTijd ?? tijd) || 0;
    const [x, y, z] = KANALEN[kanaal];
    a.keyframes.push({ channel: kanaal, data_points: [{ x: String(x), y: String(y), z: String(z) }], uuid: crypto.randomUUID(), time: Math.round(t * 1000) / 1000, color: -1, interpolation: 'linear' });
    if (t > (Number(bron.length) || 0)) bron.length = Math.round(t * 100) / 100;
    await herbouw();
  };
  const verwijder = async (a, kf) => {
    a.keyframes = a.keyframes.filter((k) => k !== kf);
    const key = Object.keys(bron.animators).find((k) => bron.animators[k] === a);
    if (!a.keyframes.length && key) delete bron.animators[key];
    await herbouw();
  };
  const zetLengte = async (v) => { if (bron && v > 0) { bron.length = v; await herbouw(); } };
  const zetTijd = async (kf, v) => { if (Number.isFinite(v) && v >= 0) { kf.time = v; await herbouw(); } };

  const a = bron && bone ? bron.animators[bone.uuid] : null;
  const kfs = a ? [...a.keyframes].sort((p, q) => (Number(p.time) || 0) - (Number(q.time) || 0)) : [];

  return (
    <div className="rounded-lg border border-bronze/30 bg-black/30 p-2 space-y-2" data-w={wijzig}>
      <div className="flex items-center gap-1.5 flex-wrap">
        {anims.length > 0 && (
          <select value={Math.min(animSel, anims.length - 1)} onChange={(e) => setAnimSel(Number(e.target.value))} className={KEUZE}>
            {anims.map((x, i) => <option key={x.uuid || i} value={i}>{x.name || 'animatie ' + (i + 1)}</option>)}
          </select>
        )}
        {bron && (
          <label className="flex items-center gap-1 text-[10px] text-parch/60">lengte
            <input type="number" step="0.05" min="0.05" defaultValue={Number(bron.length) || 1} key={`l${animSel}-${wijzig}`}
              onBlur={(e) => zetLengte(Number(e.target.value))} className={`w-16 ${VELD}`} />s
          </label>
        )}
        <button className={KNOP} onClick={nieuweAnimatie}>+ nieuwe animatie</button>
        {opslag && (
          <span className={`ml-auto text-[10px] ${opslag.startsWith('ok') ? 'text-emerald-300' : opslag === 'bezig' ? 'text-parch/50' : 'text-red-300'}`}>
            {opslag === 'ok' ? 'Opgeslagen in de hub' : opslag}
          </span>
        )}
        {slaOp && (
          <button className={`px-2 py-1 rounded text-[10px] border border-gold bg-gold/15 text-gold hover:bg-gold/25 ${opslag ? '' : 'ml-auto'}`} onClick={slaOp}>Opslaan in hub</button>
        )}
        <button className={KNOP + (slaOp || opslag ? '' : ' ml-auto')} onClick={download}>Download</button>
      </div>

      {!bron && <p className="text-[10px] text-parch/50">Maak een animatie aan om keyframes toe te voegen.</p>}
      {bron && (
        <>
          <div className="flex items-center gap-1.5 flex-wrap">
            <select value={bone?.uuid || ''} onChange={(e) => setBoneKey(e.target.value)} className={`${KEUZE} max-w-[11rem]`}>
              {bones.map((b) => <option key={b.uuid} value={b.uuid}>{b.name}</option>)}
            </select>
            <select value={kanaal} onChange={(e) => setKanaal(e.target.value)} className={KEUZE}>
              <option value="rotation">rotatie</option>
              <option value="position">positie</option>
              <option value="scale">schaal</option>
            </select>
            <label className="flex items-center gap-1 text-[10px] text-parch/60">op
              <input type="number" step="0.05" min="0" value={nieuwTijd ?? tijd.toFixed(2)} onChange={(e) => setNieuwTijd(e.target.value)} className={`w-16 ${VELD}`} />s
            </label>
            <button className="px-2 py-1 rounded text-[10px] border border-gold/50 text-gold hover:bg-gold/10" onClick={voegToe}>+ keyframe</button>
          </div>

          <div className="max-h-48 overflow-auto space-y-0.5">
            {kfs.length === 0 && <p className="text-[10px] text-parch/45">Geen keyframes voor deze bone in deze animatie.</p>}
            {kfs.map((kf) => (
              <div key={kf.uuid} className="flex items-center gap-1.5 text-[10px] text-parch/70">
                <span className="w-12 shrink-0">{kf.channel === 'rotation' ? 'rot' : kf.channel === 'position' ? 'pos' : 'schaal'}</span>
                <input type="number" step="0.05" min="0" defaultValue={Number(kf.time) || 0} key={`t${kf.uuid}-${wijzig}`}
                  onBlur={(e) => zetTijd(kf, Number(e.target.value))} className={`w-14 ${VELD}`} />
                {['x', 'y', 'z'].map((as) => (
                  <label key={as} className="flex items-center gap-0.5">{as}
                    <input type="number" step="0.5" defaultValue={Number(kf.data_points?.[0]?.[as]) || 0} key={`${kf.uuid}${as}-${wijzig}`}
                      onBlur={(e) => { const v = Number(e.target.value); if (Number.isFinite(v) && v !== Number(kf.data_points[0][as])) zet(kf, as, v); }}
                      className={`w-14 ${VELD}`} />
                  </label>
                ))}
                <button className="ml-auto px-1.5 text-red-300/80 hover:text-red-300" title="Keyframe verwijderen" onClick={() => verwijder(a, kf)}>✕</button>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
