import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { may } from '@/lib/acl';

export const dynamic = 'force-dynamic';

// Keyword-to-tag mapping for auto-suggestion
const TAG_KEYWORDS = {
  kitchen: ['kitchen', 'kook', 'recept', 'recipe', 'crop', 'ferment', 'smoke', 'drying', 'noodle', 'miso', 'sushi', 'ramen', 'tofu', 'soy', 'rice', 'ginger', 'wasabi'],
  steamworks: ['steamworks', 'steam', 'stoom', 'shaft', 'cog', 'gear', 'belt', 'millstone', 'mixer', 'press', 'turbine', 'flywheel', 'sieve', 'deployer', 'crusher', 'fan'],
  sakagura: ['sakagura', 'brew', 'brouw', 'sake', 'cauldron', 'distill', 'cask', 'aging', 'fruit', 'orchard', 'drunk', 'sommelier'],
  wildlife: ['wildlife', 'dier', 'animal', 'species', 'hunt', 'rescue', 'breeding', 'dragon', 'journal', 'zoo', 'advancement', 'achievement'],
  relics: ['relic', 'relics', 'prop', 'decor', 'collection'],
  sitesync: ['sitesync', 'shop', 'payment', 'delivery', 'money', 'vault'],
  staffhub: ['staffhub', 'document', 'pagina', 'page', 'tag', 'favoriet', 'favorite'],
  deploy: ['deploy', 'jar', 'plugin', 'versie', 'version', 'build', 'server'],
  quest: ['quest', 'npc', 'betonquest', 'daily', 'mission', 'opdracht'],
};

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { text, name } = await req.json().catch(() => ({}));
  const d = getDb();
  d.prepare(`CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY, name TEXT UNIQUE, color TEXT DEFAULT '#gold')`).run();

  const content = (String(name || '') + ' ' + String(text || '')).toLowerCase();
  const suggested = new Set();

  // Match keywords
  for (const [tag, keywords] of Object.entries(TAG_KEYWORDS)) {
    for (const kw of keywords) {
      if (content.includes(kw)) {
        suggested.add(tag);
        break;
      }
    }
  }

  // Ensure tags exist in DB
  const tagIds = [];
  for (const tagName of suggested) {
    d.prepare('INSERT OR IGNORE INTO tags (name, color) VALUES (?, ?)').run(tagName, '#gold');
    const row = d.prepare('SELECT id FROM tags WHERE name = ?').get(tagName);
    if (row) tagIds.push({ id: row.id, name: tagName });
  }

  // Also get all existing tags
  const allTags = d.prepare('SELECT * FROM tags ORDER BY name').all();

  return Response.json({ suggested: tagIds, allTags });
}