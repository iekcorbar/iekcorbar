// Builds src/lib/recipes-data.json from the plugin configs and sources (no server needed).
// Run: node scripts/build-recipes-data.js
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const root = path.join(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

const pretty = (s) => s.toLowerCase().replace(/[_]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

function name(token) {
  token = token.trim();
  if (token === 'planks' || token === 'Tag.PLANKS') return 'Any Planks';
  if (token === 'Tag.LOGS') return 'Any Log';
  const m = token.match(/^(?:\w+)\.(\w+)$/);
  if (m) return pretty(m[1]);
  return pretty(token);
}

// "kitchen:rice", "sw:flour", "#logs", "WHEAT|sw:flour", "COPPER_INGOT 3", "kitchen:tofu x2 50%"
function spec(s) {
  s = String(s).trim();
  let count = 1;
  let chance = null;
  const ch = s.match(/\s(\d+)%$/);
  if (ch) { chance = Number(ch[1]); s = s.slice(0, ch.index).trim(); }
  const x = s.match(/\s*x(\d+)$/);
  if (x) { count = Number(x[1]); s = s.slice(0, x.index).trim(); }
  else {
    const n = s.match(/\s(\d+)$/);
    if (n) { count = Number(n[1]); s = s.slice(0, n.index).trim(); }
  }
  const label = s.split('|').map((p) => {
    p = p.trim();
    if (p.startsWith('#')) return 'Any ' + pretty(p.slice(1));
    if (p.startsWith('tag:')) return 'Any ' + pretty(p.slice(4));
    return pretty(p.replace(/^(kitchen|sw|sakagura|mc):/, ''));
  }).join(' or ');
  return { name: label, count, chance };
}

const out = { generated: new Date().toISOString().slice(0, 10), plugins: [] };

// ---------------------------------------------------------------- java crafting
function javaCrafting(file, category) {
  const src = read(file).split(/\r?\n/).map((l) => l.replace(/^\s*\/\/.*$/, '')).join('\n');
  const stmts = src.split(/;\s*\n/);
  const list = [];
  for (let st of stmts) {
    st = st.replace(/\s+/g, ' ');
    let m;
    // Steamworks: r.put(PartType.X, new Object[]{n, new String[]{..}, 'C', ...})
    if ((m = st.match(/r\.put\((\w+)\.(\w+), new Object\[\]\{(\d+), new String\[\]\{([^}]*)\}(.*)\}\)/))) {
      list.push(shaped(m[2], Number(m[3]), m[4], m[5]));
    } else if ((m = st.match(/addRecipe\("[^"]+", \w+\((\w+)\.(\w+), (\d+)\), new Object\[\]\{(\d+), new String\[\]\{([^}]*)\}(.*)\}\)/))) {
      list.push(shaped(m[2], Number(m[4]), m[5], m[6]));
    } else if ((m = st.match(/^\s*shaped\((?:"[^"]+", create\(|)KItem\.(\w+),? ?(\d+)?\)?,? ?(?:(\d+), )?new String\[\]\{([^}]*)\},? ?(.*)\)$/))) {
      const cnt = Number(m[2] || m[3] || 1);
      const rest = m[5].replace(/^Map\.of\(/, '').replace(/\)$/, '');
      list.push(shaped(m[1], cnt, m[4], ',' + rest));
    } else if ((m = st.match(/shapeless\((?:"[^"]+", create\()?KItem\.(\w+),? ?(\d+)?\)?,? ?(?:(\d+), )?(.*)\)$/))) {
      const cnt = Number(m[2] || m[3] || 1);
      const items = m[4].split(/,(?![^(]*\))/).map((t) => t.trim()).filter(Boolean).map((t) => ({ name: name(t.replace(/^\w+\.(?=\w+$)/, (x) => x)), count: 1 }));
      list.push({ result: pretty(m[1]), count: cnt, type: 'shapeless', category, ingredients: collapse(items) });
    }
  }
  return list.filter(Boolean).map((r) => ({ ...r, category }));
}

function collapse(items) {
  const map = new Map();
  for (const i of items) map.set(i.name, (map.get(i.name) || 0) + i.count);
  return [...map].map(([n, c]) => ({ name: n, count: c }));
}

function shaped(resultEnum, count, shapeStr, ingStr) {
  const shape = [...shapeStr.matchAll(/"([^"]*)"/g)].map((x) => x[1]);
  const map = {};
  for (const x of ingStr.matchAll(/'(.)',\s*([\w.]+)/g)) map[x[1]] = name(x[2]);
  const grid = shape.map((row) => [...row.padEnd(3, ' ')].map((c) => (c === ' ' ? null : map[c] || '?')));
  return { result: pretty(resultEnum), count, type: 'shaped', grid, ingredients: collapse(grid.flat().filter(Boolean).map((n) => ({ name: n, count: 1 }))) };
}

// ---------------------------------------------------------------- Steamworks
const sw = yaml.load(read('Steamworks/src/main/resources/machines.yml'));
const machineTitles = {
  millstone: 'Millstone', steam_hammer: 'Steam Hammer', fan_washing: 'Bellows Fan: Washing',
  fan_haunting: 'Bellows Fan: Haunting', mixer: 'Kettle and Mixer', crusher: 'Crushing Wheels',
};
const swRecipes = javaCrafting('Steamworks/src/main/java/com/iekcorbar/steamworks/PartItems.java', 'Workbench');
for (const [key, title] of Object.entries(machineTitles)) {
  for (const r of sw[key] || []) {
    const inputs = (r.inputs || [r.input]).map(spec);
    swRecipes.push({
      category: title,
      type: 'machine',
      ingredients: inputs,
      outputs: (r.output || []).map(spec),
      time: r.time ? Math.round((r.time / 20) * 10) / 10 : null,
      water: r.water || 0,
      heat: !!r.heat,
    });
  }
}
out.plugins.push({ id: 'steamworks', name: 'Steamworks', blurb: 'Kinetic machines, ores, oil and trains.', recipes: swRecipes });

// ---------------------------------------------------------------- Kitchen
const kit = yaml.load(read('Kitchen/src/main/resources/kitchen.yml'));
const kitRecipes = javaCrafting('Kitchen/src/main/java/com/iekcorbar/kitchen/Items.java', 'Workbench');
for (const r of Object.values(kit.cutting || {})) {
  kitRecipes.push({ category: 'Cutting Board', type: 'machine', ingredients: [spec(r.input)], outputs: (r.output || []).map(spec), time: null, water: 0, heat: false, note: 'Right-click the board with a knife.' });
}
for (const r of Object.values(kit.cooking || {})) {
  kitRecipes.push({ category: 'Cooking Pot', type: 'machine', ingredients: collapse((r.ingredients || []).map(spec)), outputs: [spec(r.result)], time: r.seconds || null, water: 0, heat: true });
}
out.plugins.push({ id: 'kitchen', name: 'Kitchen', blurb: 'Farming and Japanese cooking.', recipes: kitRecipes });

// ---------------------------------------------------------------- Kura
out.plugins.push({ id: 'kura', name: 'Kura', blurb: 'Storage networks.', recipes: javaCrafting('Kura/src/main/java/com/iekcorbar/kura/Items.java', 'Workbench') });

// The java parser cannot read "new RecipeChoice.MaterialChoice(Tag.X)" and a few other forms: name them here.
const WHO = { Station: 'Any Sign', Windmill: 'Any Wool' };
function tidy(n, r) {
  if (n === 'New') return WHO[r.result] || 'Any Item';
  if (n === 'Stripped') return 'Stripped Log';
  if (n.startsWith('Ore:')) return n.slice(4);
  if (n.startsWith('Of(')) return 'Any Sashimi or Daikon Slice';
  return n;
}
for (const pl of out.plugins) {
  for (const r of pl.recipes) {
    if (r.grid) r.grid = r.grid.map((row) => row.map((n) => (n ? tidy(n, r) : n)));
    for (const k of ['ingredients', 'outputs']) if (r[k]) for (const i of r[k]) i.name = tidy(i.name, r);
  }
}
fs.writeFileSync(path.join(root, 'src/lib/recipes-data.json'), JSON.stringify(out));
for (const p of out.plugins) {
  const bad = p.recipes.filter((r) => r.grid && r.grid.flat().includes('?')).length;
  console.log(p.id, p.recipes.length, 'recipes', bad ? `(${bad} with unresolved ingredients)` : '');
}
