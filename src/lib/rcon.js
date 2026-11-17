import { db } from '@/lib/db';

// Site → Minecraft command transport, since 2026-11-15: a database command
// queue instead of RCON. RCON is switched OFF in the server's server.properties
// — every site-side attempt echoed into the server console, which had become
// unreadable spam. The IekcorbarSiteSync plugin now polls the
// `site_command_queue` table in the plugin database, runs each command as
// console on the main thread (capturing its output with its own CommandSender,
// so nothing is echoed to the console at all), and writes the reply back into
// the row. Zero console noise, zero open TCP port, same request/response
// semantics for callers.
//
// `sendRconCommand()` keeps its old name and signature on purpose: ~15 routes
// (registration/whitelisting, moderation kicks/bans, shop, vote rewards, the
// Staff Console tab) call it and all of them treat the return value as the
// server's exact reply — the admin rcon route even logs that reply verbatim
// into rcon_console_log. Only the transport changed.
//
// Return contract (unchanged): the server's output string on success, and
// 'BYPASS' when there is no database to queue through at all — local dev
// without PLUGIN_DB_* in .env.local. Any real failure (queue table missing
// because the plugin jar predates this change, queue disabled in the plugin's
// config, plugin not consuming, command timeout) THROWS, and the old RCON
// callers' catch-blocks already treat a throw as "server unreachable".

function envInt(name, fallback) {
  const n = parseInt(process.env[name], 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

async function wait(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

// The plugin bumps site_queue_status.heartbeat once per poll tick. Two ticks
// of silence means the plugin isn't consuming the queue (DB down on its side,
// or it crashed) — queueing a command would just burn the full timeout, so
// fail fast instead. Deliberately generous: a single missed tick (GC pause,
// slow query) must not flip the site into error mode.
async function probeQueue() {
  const row = await db.getSiteQueueHeartbeat(); // throws if table/plugin DB missing
  if (!row) {
    throw new Error('The Minecraft server command queue is not set up yet — update IekcorbarSiteSync on the server.');
  }
  if (!row.enabled) {
    throw new Error('The website command queue is disabled on the Minecraft server (command-queue.enabled: false in IekcorbarSiteSync config.yml).');
  }
  const ageMs = Date.now() - new Date(row.heartbeat).getTime();
  if (!Number.isFinite(ageMs) || ageMs > 15_000) {
    throw new Error('Could not reach the Minecraft server: the IekcorbarSiteSync command queue is not running.');
  }
}

export async function sendRconCommand(command) {
  const trimmed = String(command ?? '').trim();
  if (!trimmed) return '';

  if (!process.env.PLUGIN_DB_HOST) {
    console.warn('Minecraft command queue not configured (PLUGIN_DB_HOST missing). Skipping in-game command.');
    return 'BYPASS';
  }

  let enqueueId;
  try {
    await probeQueue();
    enqueueId = await db.enqueueSiteCommand(trimmed);
  } catch (err) {
    if (err && /PLUGIN_DB_HOST not configured/i.test(err.message)) return 'BYPASS';
    throw err;
  }

  const timeoutMs = envInt('SITE_COMMAND_TIMEOUT_MS', 10_000);
  const deadline = Date.now() + timeoutMs;
  const waitStart = Date.now();
  let recheckDone = false;
  let pollMs = envInt('SITE_COMMAND_POLL_MS', 200);

  while (Date.now() < deadline) {
    await wait(pollMs);
    pollMs = Math.min(Math.round(pollMs * 1.4), 1_000); // back off for long-running commands
    const row = await db.getSiteCommandResult(enqueueId);
    if (!row) {
      // Row vanished (DB hiccup / manual purge) — assume failure rather than
      // making the user wait out the rest of the timeout blind.
      throw new Error('The Minecraft server lost track of this command.');
    }
    if (row.status === 'done') return row.response ?? '';
    if (row.status === 'failed') {
      throw new Error((row.response || '').trim() || 'The Minecraft server refused or failed this command.');
    }
    // queued/running: keep waiting. If the plugin dies mid-flight the site
    // would otherwise wait the full timeout for a row nobody will ever
    // update — once the command has had a reasonable head start, re-check
    // the heartbeat once and give up with a clear message if it went quiet.
    if (!recheckDone && Date.now() - waitStart > 5_000) {
      recheckDone = true;
      try {
        await probeQueue();
      } catch {
        throw new Error('Lost contact with the Minecraft server while running this command.');
      }
    }
  }

  await db.abandonSiteCommand(enqueueId).catch(() => {});
  throw new Error(`The Minecraft server did not answer within ${Math.round(timeoutMs / 1000)}s.`);
}
