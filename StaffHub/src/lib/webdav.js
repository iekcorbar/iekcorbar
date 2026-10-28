// WebDAV-laag: /dav/<pad> zodat Verkenner, Finder en iOS-bestanden StaffHub als
// netwerkschijf zien. Draait in server.js vóór Next (route handlers kennen geen
// PROPFIND/MKCOL/MOVE). Rechten: dezelfde mappen-ACL als de web-UI.
import fs from 'node:fs';
import { getDb, blobPath, clean, uniqueName, findChild, storeBlob, now, deleteNodeTree, gcBlobs, latestVersion } from './db.js';
import { userFromBasic } from './secure.js';
import { canSee, canWrite } from './acl.js';

const ROOT = '/dav';
const MAX = 512 * 1024 * 1024;
const ALLOW = 'OPTIONS, PROPFIND, PROPPATCH, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY';

const enc = (s) => s.split('/').map(encodeURIComponent).join('/');
const xmlEsc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const rdate = (ms) => new Date(ms).toUTCString();

function segments(pathname) {
  let p;
  try { p = decodeURIComponent(pathname || ''); } catch { return null; }
  if (!p.startsWith(ROOT)) return null;
  p = p.slice(ROOT.length).replace(/^\/+|\/+$/g, '');
  return p ? p.split('/').filter(Boolean) : [];
}

/** Segment kan "x.html" zijn voor een pagina genaamd "x". */
function child(parentId, seg) {
  const hit = findChild(parentId, seg);
  if (hit) return hit;
  if (seg.endsWith('.html')) {
    const p = findChild(parentId, seg.slice(0, -5));
    if (p && p.type === 'page') return p;
  }
  return null;
}

/** Node bij pad; { root:true } als het root zelf is; null als onbestaand/onzichtbaar. */
function nodeAt(user, pathname) {
  const parts = segments(pathname);
  if (parts === null) return null;
  if (!parts.length) return { root: true };
  let parentId = null;
  let node = null;
  for (let i = 0; i < parts.length; i++) {
    node = child(parentId, parts[i]);
    if (!node || !canSee(user, node)) return null;
    if (i < parts.length - 1 && node.type !== 'folder') return null; // ouder moet een map zijn
    parentId = node.id;
  }
  return { node };
}

/** Ouder + naam voor een pad waarbij het laatste segment mag ontbreken. */
function locationOf(user, pathname) {
  const parts = segments(pathname);
  if (parts === null) return { denied: true };
  if (!parts.length) return { denied: true };
  let parentId = null;
  const d = getDb();
  for (const seg of parts.slice(0, -1)) {
    const dir = child(parentId, seg);
    if (!dir || dir.type !== 'folder' || !canSee(user, dir)) return { denied: true };
    parentId = dir.id;
  }
  return { parentId, name: parts[parts.length - 1] };
}

function propXml(node, hrefBase) {
  const d = getDb();
  const isPage = node.type === 'page';
  const displayName = isPage ? node.name + '.html' : node.name;
  const href = hrefBase + '/' + encodeURIComponent(displayName) + (node.type === 'folder' ? '/' : '');
  const v = node.type === 'file' ? latestVersion(node.id) : null;
  return '<d:response><d:href>' + xmlEsc(href) + '</d:href><d:propstat><d:prop>'
    + '<d:displayname>' + xmlEsc(node.name) + '</d:displayname>'
    + (node.type === 'folder' ? '<d:resourcetype><d:collection/></d:resourcetype>' : '<d:resourcetype/>')
    + (node.type === 'file' ? '<d:getcontentlength>' + (v ? v.size : 0) + '</d:getcontentlength><d:getcontenttype>' + xmlEsc((v && v.mime) || 'application/octet-stream') + '</d:getcontenttype>' : '')
    + (isPage ? '<d:getcontenttype>text/html</d:getcontenttype>' : '')
    + '<d:getlastmodified>' + rdate(node.updated) + '</d:getlastmodified>'
    + '<d:creationdate>' + new Date(node.created).toISOString() + '</d:creationdate>'
    + '<d:getetag>"' + node.id + '-' + node.updated + '"</d:getetag>'
    + '</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>';
}

function folderXml(user, node, hrefBase) {
  const kids = getDb().prepare('SELECT * FROM nodes WHERE parent IS ? ORDER BY type = \'folder\' DESC, name COLLATE NOCASE').all(node ? node.id : null).filter((n) => canSee(user, n));
  return kids.map((k) => propXml(k, hrefBase)).join('');
}

function selfXml(user, node, pathname) {
  const href = ROOT + '/' + (segments(pathname) || []).map((s) => encodeURIComponent(s)).join('/');
  const isPage = node.type === 'page';
  const v = node.type === 'file' ? latestVersion(node.id) : null;
  return '<d:response><d:href>' + xmlEsc(href + (isPage ? '.html' : '') + (node.type === 'folder' ? '/' : '')) + '</d:href><d:propstat><d:prop>'
    + '<d:displayname>' + xmlEsc(node.name) + '</d:displayname>'
    + (node.type === 'folder' ? '<d:resourcetype><d:collection/></d:resourcetype>' : '<d:resourcetype/>')
    + (node.type === 'file' ? '<d:getcontentlength>' + (v ? v.size : 0) + '</d:getcontentlength><d:getcontenttype>' + xmlEsc((v && v.mime) || 'application/octet-stream') + '</d:getcontenttype>' : '')
    + '<d:getlastmodified>' + rdate(node.updated) + '</d:getlastmodified>'
    + '<d:getetag>"' + node.id + '-' + node.updated + '"</d:getetag>'
    + '</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>';
}

function send(res, code, body = '', type = 'text/plain; charset=utf-8', extra = {}) {
  const buf = Buffer.from(body);
  res.writeHead(code, { 'Content-Type': type, 'Content-Length': String(buf.length), ...extra });
  res.end(buf);
}

function multistatus(res, body) {
  send(res, 207, '<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:">' + body + '</d:multistatus>', 'application/xml; charset=utf-8');
}

function readBody(req) {
  return new Promise((resolveP, reject) => {
    const chunks = [];
    let len = 0;
    req.on('data', (c) => {
      len += c.length;
      if (len > MAX) { reject(new Error('te groot')); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => resolveP(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function copyTree(user, node, parentId, name) {
  const d = getDb();
  const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
    .run(parentId, name, node.type, user.id, now(), now()).lastInsertRowid);
  for (const v of d.prepare('SELECT * FROM versions WHERE node = ?').all(node.id))
    d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)').run(id, v.sha, v.size, v.mime, v.who, v.created);
  const p = d.prepare('SELECT * FROM pages WHERE node = ?').get(node.id);
  if (p) d.prepare('INSERT INTO pages(node,html,who,updated) VALUES(?,?,?,?)').run(id, p.html, p.who, p.updated);
  for (const k of d.prepare('SELECT * FROM nodes WHERE parent = ?').all(node.id)) copyTree(user, k, id, k.name);
  return id;
}

function destPath(req) {
  const raw = String(req.headers.destination || '');
  let path;
  try { path = raw.startsWith('http') ? new URL(raw).pathname : raw; } catch { return null; }
  return segments(path);
}

export async function handleWebDav(req, res) {
  const user = userFromBasic(req.headers.authorization);
  if (!user) {
    res.writeHead(401, { 'WWW-Authenticate': 'Basic realm="StaffHub", charset="UTF-8"', 'Content-Length': '0' });
    return res.end();
  }
  const method = (req.method || '').toUpperCase();
  const parts = segments(req.url.split('?')[0]) || [];
  const d = getDb();

  if (method === 'OPTIONS') {
    res.writeHead(200, { DAV: '1, 2', Allow: ALLOW, MS: 'webdav_enabled', 'Content-Length': '0' });
    return res.end();
  }

  if (method === 'PROPFIND') {
    const depth = String(req.headers.depth || '1');
    const here = nodeAt(user, req.url.split('?')[0]);
    if (!here) return send(res, 404, 'not found');
    if (here.root) {
      const rootSelf = '<d:response><d:href>' + ROOT + '/</d:href><d:propstat><d:prop><d:displayname>StaffHub</d:displayname><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>' + rdate(Date.now()) + '</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>';
      return multistatus(res, rootSelf + (depth === '0' ? '' : folderXml(user, null, ROOT)));
    }
    const n = here.node;
    const base = ROOT + '/' + parts.map((s) => encodeURIComponent(s)).join('/');
    if (n.type === 'folder') {
      const self = '<d:response><d:href>' + xmlEsc(base + '/') + '</d:href><d:propstat><d:prop><d:displayname>' + xmlEsc(n.name) + '</d:displayname><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>' + rdate(n.updated) + '</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>';
      return multistatus(res, self + (depth === '0' ? '' : folderXml(user, n, base)));
    }
    return multistatus(res, selfXml(user, n, req.url.split('?')[0]));
  }

  if (method === 'PROPPATCH') {
    const here = nodeAt(user, req.url.split('?')[0]);
    if (!here || !here.node) return send(res, 404, 'not found');
    return multistatus(res, selfXml(user, here.node, req.url.split('?')[0]));
  }

  if (method === 'GET' || method === 'HEAD') {
    const here = nodeAt(user, req.url.split('?')[0]);
    if (!here || here.root) return send(res, 404, 'not found');
    const n = here.node;
    if (n.type === 'folder') return send(res, 404, 'map');
    if (n.type === 'page') {
      const p = d.prepare('SELECT html FROM pages WHERE node = ?').get(n.id);
      const buf = Buffer.from('<!doctype html><meta charset="utf-8"><title>' + xmlEsc(n.name) + '</title>' + (p ? p.html : ''), 'utf8');
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Content-Length': String(buf.length) });
      return res.end(method === 'HEAD' ? undefined : buf);
    }
    const v = latestVersion(n.id);
    if (!v || !fs.existsSync(blobPath(v.sha))) return send(res, 404, 'not found');
    const st = fs.statSync(blobPath(v.sha));
    res.writeHead(200, { 'Content-Type': v.mime || 'application/octet-stream', 'Content-Length': String(st.size), 'ETag': '"' + n.id + '-' + v.id + '"' });
    if (method === 'HEAD') return res.end();
    return fs.createReadStream(blobPath(v.sha)).pipe(res);
  }

  if (method === 'PUT') {
    const loc = locationOf(user, req.url.split('?')[0]);
    if (loc.denied || !loc.name) return send(res, 409, 'ouder niet ok');
    const parent = loc.parentId ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(loc.parentId) : null;
    if (parent && !canWrite(user, parent)) return send(res, 403, 'verboden');
    const name = clean(loc.name.replace(/\.html$/i, '')) || (loc.name.endsWith('.html') ? clean(loc.name.slice(0, -5)) : '') || 'naamloos';
    let buf;
    try { buf = await readBody(req); } catch { return send(res, 413, 'te groot'); }
    const sha = storeBlob(buf);
    let node = findChild(loc.parentId, name);
    if (node && node.type === 'folder') return send(res, 405, 'map bestaat al');
    if (node && node.type === 'page') return send(res, 405, 'pagina met die naam');
    if (node) {
      const last = latestVersion(node.id);
      if (!last || last.sha !== sha)
        d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)')
          .run(node.id, sha, buf.length, String(req.headers['content-type'] || 'application/octet-stream'), user.id, now());
      d.prepare('UPDATE nodes SET updated = ? WHERE id = ?').run(now(), node.id);
      return send(res, 204);
    }
    const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
      .run(loc.parentId, uniqueName(loc.parentId, name), 'file', user.id, now(), now()).lastInsertRowid);
    d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)')
      .run(id, sha, buf.length, String(req.headers['content-type'] || 'application/octet-stream'), user.id, now());
    return send(res, 201);
  }

  if (method === 'MKCOL') {
    const loc = locationOf(user, req.url.split('?')[0]);
    if (loc.denied || !loc.name) return send(res, 409, 'ouder niet ok');
    const parent = loc.parentId ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(loc.parentId) : null;
    if (parent && !canWrite(user, parent)) return send(res, 403, 'verboden');
    const name = clean(loc.name);
    if (!name) return send(res, 400);
    if (findChild(loc.parentId, name)) return send(res, 405, 'bestaat al');
    d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
      .run(loc.parentId, name, 'folder', user.id, now(), now());
    return send(res, 201);
  }

  if (method === 'DELETE') {
    const here = nodeAt(user, req.url.split('?')[0]);
    if (!here || here.root) return send(res, 404, 'not found');
    if (!canWrite(user, here.node)) return send(res, 403, 'verboden');
    deleteNodeTree(here.node.id);
    gcBlobs();
    return send(res, 204);
  }

  if (method === 'MOVE' || method === 'COPY') {
    const src = nodeAt(user, req.url.split('?')[0]);
    if (!src || src.root) return send(res, 404, 'not found');
    if (!canWrite(user, src.node)) return send(res, 403, 'verboden');
    const dp = destPath(req);
    if (!dp || !dp.length) return send(res, 400, 'destination?');
    const over = String(req.headers.overwrite || 'T') === 'F';
    const destParent = (() => {
      let parentId = null;
      for (const seg of dp.slice(0, -1)) {
        const dir = child(parentId, seg);
        if (!dir || dir.type !== 'folder' || !canSee(user, dir)) return null;
        parentId = dir.id;
      }
      return { parentId, name: clean(dp[dp.length - 1]) };
    })();
    if (!destParent || !destParent.name) return send(res, 409, 'bestemming niet ok');
    if (destParent.parentId === src.node.id) return send(res, 400, 'in eigen kind');
    const conflict = findChild(destParent.parentId, destParent.name);
    if (conflict && conflict.id === src.node.id && method === 'MOVE') {
      d.prepare('UPDATE nodes SET parent = ?, updated = ? WHERE id = ?').run(destParent.parentId, now(), src.node.id);
      return send(res, 204);
    }
    if (conflict && over) return send(res, 412, 'bestaat al');
    if (method === 'MOVE') {
      if (conflict) deleteNodeTree(conflict.id);
      d.prepare('UPDATE nodes SET parent = ?, name = ?, updated = ? WHERE id = ?')
        .run(destParent.parentId, uniqueName(destParent.parentId, destParent.name), now(), src.node.id);
      gcBlobs();
      return send(res, conflict ? 204 : 201);
    }
    copyTree(user, src.node, destParent.parentId, uniqueName(destParent.parentId, destParent.name));
    return send(res, conflict ? 204 : 201);
  }

  if (method === 'LOCK' || method === 'UNLOCK') {
    res.writeHead(501, { Allow: ALLOW, 'Content-Length': '0' });
    return res.end();
  }
  return send(res, 405, '', 'text/plain', { Allow: ALLOW });
}
