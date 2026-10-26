const path = require('path');
const fs = require('fs');
const http = require('http');
const next = require('next');

const rootDir = path.resolve(__dirname);

process.env.PWD = rootDir;
process.env.APP_ROOT = rootDir;
process.env.NODE_ENV = 'production';
process.chdir(rootDir);

const port = process.env.PORT || 3000;

// Create Next.js app instance
const app = next({ dev: false, dir: rootDir });
const handle = app.getRequestHandler();

let isPrepared = false;
let prepareError = null;

// Start preparing Next.js in background
const preparePromise = app.prepare()
  .then(() => {
    isPrepared = true;
    console.log('> Next.js app prepared successfully');
  })
  .catch((err) => {
    prepareError = err;
    console.error('> Next.js prepare error:', err);
  });

// --- Runtime-uploaded file serving (public/uploads/**) ---
// IMPORTANT: Next.js production builds only know about files that existed in
// public/ at `next build` time (baked into the build output). Anything written
// there afterwards — chat images, uploaded skins, etc. — 404s through Next's own
// handler even after a process restart; it only picks them up on the next
// rebuild. Uploads need to be servable immediately, so we intercept /uploads/*
// here and stream it straight from disk, bypassing Next's router entirely.
const UPLOADS_PREFIX = '/uploads/';
const UPLOADS_ROOT = path.join(rootDir, 'public', 'uploads');
const UPLOAD_MIME_TYPES = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml'
};

function serveUpload(req, res) {
  const urlPath = decodeURIComponent(req.url.split('?')[0]);
  const relative = urlPath.slice(UPLOADS_PREFIX.length);
  const resolved = path.normalize(path.join(UPLOADS_ROOT, relative));

  // Guard against path traversal (../..) escaping the uploads directory.
  if (resolved !== UPLOADS_ROOT && !resolved.startsWith(UPLOADS_ROOT + path.sep)) {
    res.statusCode = 400;
    return res.end('Bad request');
  }

  fs.stat(resolved, (err, stat) => {
    if (err || !stat.isFile()) {
      res.statusCode = 404;
      return res.end('Not found');
    }
    const ext = path.extname(resolved).toLowerCase();
    res.setHeader('Content-Type', UPLOAD_MIME_TYPES[ext] || 'application/octet-stream');
    res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
    fs.createReadStream(resolved).pipe(res);
  });
}

// Start HTTP server IMMEDIATELY so Passenger (Plesk) binds port instantly!
const server = http.createServer(async (req, res) => {
  try {
    if (req.url.startsWith(UPLOADS_PREFIX)) {
      return serveUpload(req, res);
    }

    if (prepareError) {
      res.statusCode = 500;
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      return res.end(`<h2>Next.js Startup Error</h2><pre>${prepareError.stack || prepareError}</pre>`);
    }

    if (!isPrepared) {
      await preparePromise;
    }

    await handle(req, res);
  } catch (err) {
    console.error('Request error:', err);
    if (!res.headersSent) {
      res.statusCode = 500;
      res.end('Internal Server Error');
    }
  }
});

server.listen(port, (err) => {
  if (err) throw err;
  console.log(`> Server ready on ${port}`);
});
