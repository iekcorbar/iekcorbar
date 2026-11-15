import dgram from 'dgram';
import { promisify } from 'util';
import dns from 'dns';

// Minecraft Query client for the protocol Paper actually runs — verified
// byte-by-byte on 2026-11-15 by disassembling the live paper-26.2.jar
// (net.minecraft.server.rcon.thread.QueryThreadGs4). NOTE: this is NOT the
// pre-1.18 plain-GS4 format most libraries implement. The live exchange:
//
//   1. handshake: client -> 0xFE 0xFD 0x09 <session: 4 ASCII-digit bytes>
//      server  -> 0x09 <session> <challenge as DECIMAL digits> 0x00
//      (the session bytes round-trip through the server as a UTF-8 string,
//       so they must stay ASCII; Paper echoes them back untouched)
//   2. full status: client -> 0xFE 0xFD 0x00 <session> <challenge as 4-byte
//      BIG-ENDIAN int> <4 padding bytes>   — exactly 15 bytes total; that
//      length is what selects the full response. 11 bytes would only get the
//      short header-only variant without player names.
//      server -> 0x00 <session> then classic GS4 cstring sections:
//      "splitnum" 0x80 0x00, key\0value\0 pairs (hostname, gametype, game_id,
//      version, plugins, map, numplayers, maxplayers, hostport, hostip), a
//      \0\0 terminator, then the player section (player_0\0<name>\0 ...).
//
// Why this exists: it gives the website live status AND player names
// directly from our own server — UDP-only, unauthenticated, and it never
// touches the server console. That is what let RCON be switched off
// (2026-11-15, console-spam was the last RCON consumer together with the
// DB command queue in src/lib/rcon.js).

const resolveSrv = promisify(dns.resolveSrv);

// Which UDP port to query `host` on: explicit argument (a ":port" in the
// staff-entered server address) > MINECRAFT_QUERY_PORT > the host's
// _minecraft._tcp SRV record (servers behind a non-default port, like ours,
// publish that record; the vanilla client follows it the same way) > 25565.
export async function resolveQueryTarget(host, explicitPort) {
  const explicit = parseInt(explicitPort, 10);
  if (Number.isFinite(explicit) && explicit > 0) return explicit;
  const envPort = parseInt(process.env.MINECRAFT_QUERY_PORT, 10);
  if (Number.isFinite(envPort) && envPort > 0) return envPort;
  try {
    const recs = await resolveSrv(`_minecraft._tcp.${host}`);
    if (recs && recs.length) return recs[0].port;
  } catch { /* no SRV record - plain port it is */ }
  return 25565;
}

// Reads null-terminated cstrings starting at `offset`, as key/value pairs,
// until an empty key (the section terminator). Returns a flat map.
function readKeyValues(buf, offset) {
  const out = {};
  let pos = offset;
  while (pos < buf.length - 1) {
    const end = buf.indexOf(0x00, pos);
    if (end === -1) break;
    const key = buf.toString('utf8', pos, end);
    pos = end + 1;
    if (key === '') break;
    const vEnd = buf.indexOf(0x00, pos);
    if (vEnd === -1) break;
    out[key] = buf.toString('utf8', pos, vEnd);
    pos = vEnd + 1;
  }
  return out;
}

// Sends one request and collects the reply. The extra quiet window after
// the first packet picks up any split tail.
function ask(socket, host, port, packet, timeoutMs, quietMs) {
  return new Promise((resolve, reject) => {
    const packets = [];
    let firstTimer = null;
    let quietTimer = null;
    const finish = () => {
      clearTimeout(firstTimer);
      clearTimeout(quietTimer);
      socket.removeListener('message', onMessage);
      resolve(packets);
    };
    const onMessage = (msg) => {
      packets.push(msg);
      clearTimeout(quietTimer);
      quietTimer = setTimeout(finish, quietMs);
    };
    firstTimer = setTimeout(() => {
      socket.removeListener('message', onMessage);
      reject(new Error('Minecraft query timed out'));
    }, timeoutMs);
    socket.on('message', onMessage);
    socket.send(packet, port, host, (err) => {
      if (!err) return;
      clearTimeout(firstTimer);
      socket.removeListener('message', onMessage);
      reject(err);
    });
  });
}

/**
 * Queries a Minecraft server over its query protocol.
 * Resolves null when the server doesn't answer (query disabled, firewall,
 * offline) so callers can fall back gracefully.
 */
export async function queryServerStatus(host, queryPort) {
  queryPort = await resolveQueryTarget(host, queryPort);
  // Four ASCII digits: the session id survives a Java String round-trip.
  const session = Buffer.from(String(1000 + Math.floor(Math.random() * 9000)), 'ascii');
  const socket = dgram.createSocket('udp4');
  try {
    // 1. handshake -> 0x09 <session> <challenge digits> 0x00
    const hello = await ask(socket, host, queryPort,
      Buffer.concat([Buffer.from([0xfe, 0xfd, 0x09]), session]), 3000, 200);
    const reply = hello[0];
    const tail = reply.subarray(1 + session.length);
    const nul = tail.indexOf(0x00);
    const digits = tail.toString('ascii', 0, nul === -1 ? tail.length : nul);
    if (!/^\d+$/.test(digits)) throw new Error('Minecraft query handshake returned no challenge');

    // 2. full status: header + session + BE-challenge + 4 padding (15 bytes)
    const challengeBuf = Buffer.alloc(4);
    challengeBuf.writeUInt32BE(parseInt(digits, 10), 0);
    const stats = await ask(socket, host, queryPort, Buffer.concat([
      Buffer.from([0xfe, 0xfd, 0x00]),
      session,
      challengeBuf,
      Buffer.alloc(4),
    ]), 3000, 800);

    const kv = {};
    let players = [];
    for (const pkt of stats) {
      const start = pkt.indexOf('hostname');
      if (start !== -1) Object.assign(kv, readKeyValues(pkt, start));
      // The player section is behind the "\0\0" section terminator; scan it
      // by pattern instead of relying on the exact header length.
      let m;
      const re = /player_(\d+)\x00([^\x00]+)\x00/g;
      const buf = pkt.toString('latin1');
      while ((m = re.exec(buf)) !== null) players[parseInt(m[1], 10)] = Buffer.from(m[2], 'latin1').toString('utf8');
    }
    players = players.filter(Boolean);
    if (kv.hostname === undefined && kv.numplayers === undefined) return null;
    return {
      online: true,
      hostname: (kv.hostname || '').replace(/§./g, ''),
      version: kv.version || '',
      software: (kv.plugins || '').split(':')[0].replace(/§./g, '').trim(),
      playersOnline: parseInt(kv.numplayers, 10) || 0,
      playersMax: parseInt(kv.maxplayers, 10) || 0,
      playersList: players,
    };
  } catch {
    return null;
  } finally {
    try { socket.close(); } catch { /* already closed */ }
  }
}
