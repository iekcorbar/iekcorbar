const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

console.log('Cleaning up old build cache if present...');
// .next/cache is next build's own cache. .next/dev is Turbopack's dev-server
// scratch directory (npm run dev) — it's not part of a production build at
// all, but it lingers in .next/ if `next dev` was ever run against this same
// checkout (e.g. for a local visual check) before packaging. Neither is
// needed to run the app; leaving .next/dev in used to balloon this zip from
// ~12MB to 100MB+ without anyone noticing until the zip size looked wrong.
for (const sub of ['cache', 'dev']) {
  const subPath = path.join(__dirname, '.next', sub);
  if (fs.existsSync(subPath)) {
    try {
      fs.rmSync(subPath, { recursive: true, force: true });
      console.log(`Removed .next/${sub} to reduce package size.`);
    } catch (err) {
      console.warn(`Could not remove .next/${sub}:`, err.message);
    }
  }
}

// NOTE: node_modules is intentionally NOT included. ZIP archives don't reliably
// preserve Unix executable permissions, so binaries like node_modules/.bin/next
// lose their +x bit when extracted on the Linux Plesk host, causing
// "sh: 1: next: Permission denied" on `npm run build` / `npm run start`.
// Instead, run `npm install` (or use Plesk's "NPM install" button) on the
// server itself after uploading/extracting this package.

// De plugin-jar-vracht is per 30-10 uit deze zip verdwenen (zie de items-
// comment hieronder): site-zip = website, SiteSync = MC-server-deploy.

const items = [
  '.next', 'src', 'public', 'app.js', 'server.js', 'package.json',
  'package-lock.json', 'next.config.mjs', 'postcss.config.mjs', 'jsconfig.json',
  '.env.local', '.node-version'
];
// IekcorbarSiteSync (plugin-jar + resourcepack) REIST NIET MEER MEE (eigenaar
// 30-10): deze zip is uitsluitend het iekcorbar.com-buildpakket voor Plesk.
// De site-code verwijst alleen in commentaren naar de plugin en leest niets
// uit de map; het jar + resourcepack reizen naar de MC-server toe via hun
// eigen TE-regels. Scheelt ~12 MB upload - precies wat de Plesk-filemanager
// de hele middag niet lukte.

const zipName = process.env.ZIP_NAME || 'iekcorbar_iekje_barend.zip';
const zipPath = path.join(__dirname, zipName);
if (fs.existsSync(zipPath)) {
  fs.rmSync(zipPath, { force: true });
}

console.log(`Creating ${zipName} using Python's zipfile (spec-correct forward-slash paths)...`);
// IMPORTANT: neither Windows zip tool is safe for this package:
//   - `tar -a -c -f x.zip ...` (bsdtar zip-emulation) produced a malformed,
//     dual-stream archive because one input (IekcorbarSiteSync-1.0.0.jar) is
//     itself already a ZIP-format file — Linux `unzip` on Plesk only
//     recovered the jar's own internal contents and reported
//     "N extra bytes at beginning or within zipfile" for everything else.
//   - PowerShell's Compress-Archive writes entry names with backslash path
//     separators on Windows, which the ZIP spec forbids and Linux `unzip`
//     does not reliably turn into subdirectories.
// Python's zipfile module always normalizes to forward slashes, so it's
// used instead via scripts/build-zip.py.
const scriptPath = path.join(__dirname, 'scripts', 'build-zip.py');
const cmd = `python "${scriptPath}" "${zipName}" ${items.map(i => `"${i}"`).join(' ')}`;

try {
  execSync(cmd, { cwd: __dirname, stdio: 'inherit' });
  console.log(`SUCCESS: ${zipName} created successfully!`);
  console.log('NOTE: node_modules was excluded on purpose. After uploading and');
  console.log('extracting on Plesk, run "npm install" (or click "NPM install" in');
  console.log('the Plesk Node.js app panel) before starting/building the app.');
} catch (err) {
  console.error('Error creating zip:', err.message);
}
