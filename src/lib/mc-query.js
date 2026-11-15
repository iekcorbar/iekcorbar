import dgram from 'dgram';

// Minimal Minecraft GS4 "Query" client (the UDP protocol behind
// server.properties' enable-query / query.port), implemented directly here
// since 2026-11-15. The site used to ask the third-party mcsrvstat.us API for
// live server status — that works, but it's an extra external hop. The query
// protocol answers straight from our own server (no RCON involved, and
// nothing ever touches the server console) and additionally returns the
// player-name list.
//
// Protocol (GameSpy4, as vanilla/Paper implement it):
//   1. handshake:  0xFE 0x00 <sessionID int32 BE>              -> "HELLO \0<challenge>\0"
//   2. stats:      0xFE 0x01 <sessionID> <challenge cstring> <int32 0>
//      -> single packet: 0xFE 0x00 <sessionID echoed> then null-terminated
//         key/value pairs ("hostname", "numplayers", "player_0", ...) ending
//         at "\0\0". Large answers are split over several UDP packets with a
//         0xFE 0x00 <requestID 4 LE> <packetNumber 4 BE> frame instead; the
//         key/value text then simply continues across those packets.

const QUERY_MAGIC = 0xfe;
const SESSION_ID = 0x01020304; // arbitrary fixed int32, echoed back in single-packet replies

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

// Waits for the first matching UDP reply (rejects on timeout), then keeps
// collecting for `quietMs` of silence so multi-packet tails are not lost.
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
      if (msg.length < 6 || msg[0] !== QUERY_MAGIC || msg[1] !== 0x00) return;
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

// Pulls the merged key/value map out of one-or-more stats packets. Rather
// than hardcoding every framing offset (vanilla and Paper differ by a
// padding field or two), each packet's payload is scanned for where its
// cstring section actually begins.
function parseStatsPackets(packets) {
  const kv = {};
  for (const p of packets) {
    let start = p.indexOf('hostname');
    if (start === -1) start = p.indexOf('splitnum');
    if (start === -1) start = p.indexOf('player_');
    if (start === -1) continue;
    Object.assign(kv, readKeyValues(p, start));
  }
  return kv;
}

/**
 * Queries a Minecraft server over the UDP query protocol.
 * Resolves null when the server doesn't answer (query disabled, firewall,
 * offline) so callers can fall back gracefully.
 */
export async function queryServerStatus(host, queryPort) {
  const socket = dgram.createSocket('udp4');
  try {
    const hello = await ask(socket, host, queryPort, Buffer.from([
      QUERY_MAGIC, 0x00,
      (SESSION_ID >>> 24) & 0xff, (SESSION_ID >>> 16) & 0xff, (SESSION_ID >>> 8) & 0xff, SESSION_ID & 0xff,
    ]), 3000, 300);
    const challenge = hello[0].toString('latin1', 6).split('\0')[1];
    if (!challenge) throw new Error('Minecraft query handshake returned no challenge');

    const sid = Buffer.alloc(4);
    sid.writeInt32BE(SESSION_ID, 0);
    const statsPacket = Buffer.concat([
      Buffer.from([QUERY_MAGIC, 0x01]),
      sid,
      Buffer.from(challenge + '\0', 'latin1'),
      Buffer.alloc(4),
    ]);
    const replies = await ask(socket, host, queryPort, statsPacket, 3000, 800);
    const kv = parseStatsPackets(replies);
    if (kv.hostname === undefined && kv.numplayers === undefined) return null;
    const players = [];
    for (let i = 0; kv[`player_${i}`] !== undefined; i++) players.push(kv[`player_${i}`]);
    return {
      online: true,
      hostname: kv.hostname || '',
      version: kv.version || kv.software || '',
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
