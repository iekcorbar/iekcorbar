import path from 'node:path';
import { fileURLToPath } from 'node:url';

// StaffHub lives inside the iekcorbar-final repo, which is zélf ook een Next.js
// site. Zonder deze pin ziet Next het root-lockfile hierboven, raadt de parent
// als workspace-root en bouwt meónster de instrumentation/reminders/MySQL-code
// van de site mee in StaffHub (.next/server/chunks/src_lib_reminders_*).
const root = path.dirname(fileURLToPath(import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  // WebDAV and the app share one port via server.js; nothing to export here.
  turbopack: { root },
  outputFileTracingRoot: root,
};

export default nextConfig;
