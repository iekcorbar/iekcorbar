#!/usr/bin/env node
// Offline controle van IekQuests' quests.yml (geen server nodig):
//   node IekQuests/tools/validate-quests.js [pad-naar-quests.yml]
// Standaard: IekQuests/src/main/resources/quests.yml. Exit 1 bij fouten, 0 bij alleen waarschuwingen.
// Spiegelt de grammatica van IekQuestsPlugin (stage-types, actie-werkwoorden, voorwaarden).
const fs = require('fs');
const path = require('path');
const yaml = require(path.join(__dirname, '..', '..', 'node_modules', 'js-yaml'));

const file = process.argv[2] || path.join(__dirname, '..', 'src', 'main', 'resources', 'quests.yml');
const STAGE_TYPES = new Set(['talk', 'craft', 'deliver', 'walk', 'kill', 'deliverall', 'menu']);
// werkwoorden die run() zelf afhandelt; al het andere wordt als console-commando uitgevoerd
const VERBS = new Set(['money', 'pay', 'eco', 'title', 'sound', 'say', 'tag+', 'tag-', 'perm+', 'perm-', 'point',
  'effect', 'teleport', 'give', 'compass', 'shop', 'joinjob', 'leavejob', 'chat', 'sequence']);
// typefouten die op een werkwoord lijken maar er geen zijn (zouden stil als console-commando falen)
const SUSPECT = /^(tag\s*[+-]|tag[+-]{2,}|perm\s*[+-]|perm[+-]{2,}|monney|moeny|titel|sond|sequense|sequnce|compas|poin|teleprot)$/i;
const COND = /^(!?)(tag:|qtag:|point:|item:|money>=|money<|perm:|npc:|day$|night$)/;

const errors = [], warns = [];
const err = (m) => errors.push(m);
const warn = (m) => warns.push(m);

let doc;
try {
  doc = yaml.load(fs.readFileSync(file, 'utf8'));
} catch (e) {
  console.error('YAML-fout: ' + e.reason + ' (regel ' + (e.mark ? e.mark.line + 1 : '?') + ')');
  process.exit(1);
}
const quests = (doc && doc.quests) || {};
const seqs = (doc && doc.sequences) || {};
const ids = Object.keys(quests);
const tagsSet = new Set(), tagsUsed = new Map();
const npcOwner = new Map(), unplaced = [];

const arr = (v) => (Array.isArray(v) ? v : v == null ? [] : [v]);

function checkCond(where, raw) {
  if (!raw) return;
  for (const c of String(raw).split(',').map((s) => s.trim()).filter(Boolean)) {
    if (!COND.test(c)) { warn(`${where}: onbekende voorwaarde "${c}" (de engine laat die altijd door)`); continue; }
    const body = c.replace(/^!/, '');
    if (body.startsWith('tag:')) tagsUsed.set(body.slice(4), where);
    if (body.startsWith('money>=') || body.startsWith('money<')) {
      if (!Number.isFinite(Number(body.replace(/^money(>=|<)/, '')))) err(`${where}: ongeldig bedrag in "${c}"`);
    }
    if (body.startsWith('point:') && !body.includes('>=')) err(`${where}: point-voorwaarde mist ">=" in "${c}"`);
    if (body.startsWith('item:') && !body.includes('>=')) err(`${where}: item-voorwaarde mist ">=" in "${c}"`);
    if (body.startsWith('qtag:') && body.split(':').length < 3) err(`${where}: qtag moet qtag:<quest>:<tag> zijn ("${c}")`);
    if (body.startsWith('qtag:')) {
      const qid = body.split(':')[1];
      if (!quests[qid]) warn(`${where}: qtag verwijst naar onbekende quest "${qid}"`);
    }
  }
}

function checkAction(where, raw) {
  const a = String(raw).trim();
  if (!a) return;
  const sp = a.indexOf(' ');
  const key = sp < 0 ? a : a.slice(0, sp);
  const val = sp < 0 ? '' : a.slice(sp + 1);
  if (key === 'tag+') tagsSet.add(val.trim());
  if (!VERBS.has(key)) {
    if (SUSPECT.test(key)) err(`${where}: "${key}" lijkt een typefout van een actie; wordt nu als console-commando uitgevoerd`);
    return;
  }
  if (['money', 'pay', 'eco'].includes(key) && !Number.isFinite(Number(val))) err(`${where}: "${a}" heeft geen geldig getal`);
  if (key === 'sequence' && !seqs[val.trim()]) err(`${where}: sequence "${val}" bestaat niet`);
  if (key === 'point') {
    const m = val.trim().split(/\s+/);
    if (m.length < 2 || !/^[+-]\d+$/.test(m[1])) err(`${where}: point verwacht "<categorie> +N" ("${a}")`);
  }
  if (key === 'tag-' ) tagsUsed.set(val.trim(), where);
}

for (const id of ids) {
  const q = quests[id] || {};
  const at = `quest ${id}`;
  if (!q.title) warn(`${at}: geen title`);
  if (typeof q['npc-id'] !== 'number') warn(`${at}: npc-id ontbreekt of is geen getal`);
  else if (q['npc-id'] < 0) unplaced.push(id);
  else {
    npcOwner.set(q['npc-id'], id);
  }
  checkCond(`${at} start`, q.start);
  arr(q.rewards).forEach((r) => checkAction(`${at} rewards`, r));
  const stages = q.stages || {};
  const keys = Object.keys(stages);
  if (!keys.length) warn(`${at}: geen stages`);
  for (const k of keys) {
    const s = stages[k] || {};
    const w = `${at}/${k}`;
    const type = s.type || 'talk';
    if (!STAGE_TYPES.has(type)) err(`${w}: onbekend stage-type "${type}"`);
    if ((type === 'craft' || type === 'deliver') && !s.material) warn(`${w}: ${type} zonder material (standaard PAPER)`);
    if (type === 'walk' && !s.location) err(`${w}: walk-stage zonder location`);
    if (type === 'kill' && !s.mob) err(`${w}: kill-stage zonder mob`);
    if (type === 'deliverall' && !arr(s.items).length) err(`${w}: deliverall zonder items`);
    if (s.location && String(s.location).split(';').length < 4) err(`${w}: location "${s.location}" moet x;y;z;wereld zijn`);
    for (const t of ['next', 'goto']) if (s[t] && !stages[s[t]]) err(`${w}: ${t} "${s[t]}" bestaat niet`);
    checkCond(`${w} cond`, s.cond);
    arr(s.rewards).forEach((r) => checkAction(`${w} rewards`, r));
    const ch = Array.isArray(s.choices) ? s.choices : s.choices ? Object.values(s.choices) : [];
    ch.forEach((c, i) => {
      const cw = `${w} choice#${i + 1}`;
      if (!c || !c.label) warn(`${cw}: geen label`);
      if (c && c.goto && !stages[c.goto]) err(`${cw}: goto "${c.goto}" bestaat niet`);
      checkCond(`${cw} cond`, c && c.cond);
      arr(c && c.do).forEach((d) => checkAction(cw, d));
    });
  }
  for (const [rk, r] of Object.entries(q.repeat || {})) {
    const w = `${at}/repeat/${rk}`;
    if (!r || (!r.material && !arr(r.items).length && !r.location)) warn(`${w}: geen material, items of location`);
    if (r && r.cooldown != null && !(Number(r.cooldown) >= 0)) err(`${w}: ongeldige cooldown`);
    arr(r && r.rewards).forEach((x) => checkAction(w, x));
  }
}

for (const [sid, seq] of Object.entries(seqs)) {
  arr(seq).forEach((st, i) => {
    const m = String(st).match(/^(\d+(?:\.\d+)?)\|(.*)$/);
    if (!m) { err(`sequence ${sid}#${i + 1}: verwacht "vertraging|actie" maar kreeg "${st}"`); return; }
    checkAction(`sequence ${sid}#${i + 1}`, m[2]);
  });
}

for (const [t, where] of tagsUsed) {
  if (!tagsSet.has(t) && !ids.some((i) => (quests[i].stages || {})[t])) {
    warn(`${where}: tag "${t}" wordt nergens met "tag+" gezet (alleen automatische tags zoals menu:/choice: kunnen dit zijn)`);
  }
}

console.log(`${ids.length} quests, ${Object.keys(seqs).length} sequences gecontroleerd in ${path.basename(file)}`);
if (unplaced.length) console.log(`Nog zonder NPC (npc-id < 0): ${unplaced.join(', ')}`);
warns.forEach((m) => console.log('  waarschuwing: ' + m));
errors.forEach((m) => console.log('  FOUT: ' + m));
console.log(`${errors.length} fout(en), ${warns.length} waarschuwing(en)`);
process.exit(errors.length ? 1 : 0);
