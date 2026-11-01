import crypto from 'node:crypto';
import { db } from '@/lib/db';

// A short hash of the current rules text: stored next to each acceptance, so staff can
// later see who agreed to which version of the rules.
export async function currentRulesVersion() {
  const rules = await db.getRules();
  const text = (rules || []).map(r => `${r.title}\n${r.content}`).join('\n--\n');
  return crypto.createHash('sha1').update(text).digest('hex').slice(0, 12);
}
