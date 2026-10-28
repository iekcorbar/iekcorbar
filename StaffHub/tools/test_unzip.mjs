import { readFileSync } from 'node:fs';
import { plan, data, ZipFout } from '../src/lib/unzip.js';

let ok = 0, fail = 0;
function test(naam, fn) {
  try { fn(); console.log('  PASS', naam); ok++; }
  catch (e) { console.log('  FAIL', naam, '->', e.message); fail++; }
}
function assert(c, m) { if (!c) throw new Error(m || 'assert'); }

// 1) gewoon zipje met gedeelde top-map (Windows-stijl): moet 1 niveau strippen
const gewone = readFileSync(new URL('./fixtures/gewone.zip', import.meta.url));
test('gewone.zip plannen', () => {
  const p = plan(gewone);
  assert(Array.isArray(p) && p.length >= 2, 'items');
  assert(p.every((z) => z.pad.length >= 1), 'pad-den');
  console.log('     plan:', JSON.stringify(p.map((z) => z.pad.join('/'))));
});
test('gewone.zip data roundtrip', () => {
  const p = plan(gewone);
  const eerste = p.find((z) => z.naam.endsWith('.txt')) || p[0];
  const buf = data(gewone, eerste);
  assert(buf.length === eerste.grootte, `grootte ${buf.length} vs ${eerste.grootte}`);
  console.log('     eerste bestand:', eerste.pad.join('/'), '=', JSON.stringify(buf.toString('utf8').slice(0, 30)));
});

// 2) boos pad (../) - moet geweigerd worden vóór extractie
const boos = readFileSync(new URL('./fixtures/boos.zip', import.meta.url));
test('boos.zip wordt geweigerd (zip-slip)', () => {
  let gooide = false;
  try { plan(boos); } catch (e) { gooide = e instanceof ZipFout; console.log('     melding:', e.message); }
  assert(gooide, 'had een ZipFout moeten gooien');
});

// 3) macOS-rommel + dubbele wegen overgeslagen
const mac = readFileSync(new URL('./fixtures/mac.zip', import.meta.url));
test('mac.zip stipt __MACOSX en ._ weg', () => {
  const p = plan(mac);
  assert(!p.some((z) => z.pad[0] === '__MACOSX' || z.naam.startsWith('._')), 'geen rommel');
  console.log('     over:', JSON.stringify(p.map((z) => z.pad.join('/'))));
});

console.log(`\n  ${ok} geslaagd, ${fail} mislukt`);
process.exit(fail ? 1 : 0);
