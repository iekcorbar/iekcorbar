// Custom server: vang /dav/* af voor WebDAV (Next-route-handlers kunnen die
// HTTP-methoden niet), de rest gaat gewoon naar Next.
import http from 'node:http';
import { parse } from 'node:url';
import next from 'next';

const dev = process.env.NODE_ENV !== 'production';
const port = Number(process.env.PORT || 3120);
const hostname = process.env.HOST || '0.0.0.0';

const { handleWebDav } = await import('./src/lib/webdav.js');

const app = next({ dev, hostname, port });
const handle = app.getRequestHandler();

await app.prepare();

const server = http.createServer((req, res) => {
  const path = (req.url || '').split('?')[0];
  if (path === '/dav' || path.startsWith('/dav/')) {
    handleWebDav(req, res).catch((e) => {
      console.error('webdav', e);
      if (!res.headersSent) res.writeHead(500, { 'Content-Length': '0' });
      res.end();
    });
    return;
  }
  const parsed = parse(req.url || '/', true);
  handle(req, res, parsed);
});

// WebDAV-clients (Verkenner) zetten soms een timeout; groot houden helpt.
server.keepAliveTimeout = 65_000;
server.headersTimeout = 70_000;

server.listen(port, hostname, () => {
  console.log(`StaffHub ${dev ? '(dev)' : ''} op http://${hostname === '0.0.0.0' ? 'localhost' : hostname}:${port} - WebDAV onder /dav`);
});
