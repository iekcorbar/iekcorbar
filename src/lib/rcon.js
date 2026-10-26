import net from 'net';

// Source RCON packet: int32 LE size (of everything after this field) + int32 LE
// id + int32 LE type + null-terminated body + an extra empty null-terminated
// string. `size` therefore equals 4 (id) + 4 (type) + body.length + 1 (body's
// own terminator) + 1 (the empty trailing string) = 4 + 4 + body.length + 2.
function encodePacket(id, type, payload) {
  const payloadBuffer = Buffer.from(payload, 'utf-8');
  const size = 4 + 4 + payloadBuffer.length + 2;
  const buffer = Buffer.alloc(4 + size);
  buffer.writeInt32LE(size, 0);
  buffer.writeInt32LE(id, 4);
  buffer.writeInt32LE(type, 8);
  payloadBuffer.copy(buffer, 12);
  buffer.writeUInt8(0, 12 + payloadBuffer.length);
  buffer.writeUInt8(0, 12 + payloadBuffer.length + 1);
  return buffer;
}

// Pulls exactly one complete packet off the front of `buffer` if one is fully
// present yet, returning { id, type, body, rest } — or null if more bytes are
// still needed. A single TCP `data` event can contain zero, one, or several
// packets, and a single packet can also arrive split across several `data`
// events, so callers must keep looping/re-buffering rather than assuming one
// event == one packet (the previous version of this file assumed exactly
// that, which is what made large/multi-packet responses unreliable).
function tryExtractPacket(buffer) {
  if (buffer.length < 4) return null;
  const size = buffer.readInt32LE(0);
  const totalLen = 4 + size;
  if (size < 10 || buffer.length < totalLen) return null;
  const id = buffer.readInt32LE(4);
  const type = buffer.readInt32LE(8);
  // Body runs from byte 12 up to the two trailing null bytes.
  const body = buffer.toString('utf-8', 12, totalLen - 2);
  return { id, type, body, rest: buffer.subarray(totalLen) };
}

class RconClient {
  constructor(host, port, password) {
    this.host = host;
    this.port = port;
    this.password = password;
    this.socket = null;
    this.requestId = 1;
    this.authenticated = false;
    this._recvBuffer = Buffer.alloc(0);
    this._authPendingId = null;
    this._authEntry = null;
    // Command execution uses two maps to reliably reassemble responses that
    // the server split across multiple packets: every packet sharing a
    // command's own id is a body chunk, and the arrival of the *dummy*
    // command's response (sent right after, see execute()) signals "nothing
    // more for that id is coming" — the standard technique real RCON
    // clients (e.g. mcrcon) use, since the protocol has no explicit
    // response-length-exceeded marker of its own.
    this._commands = new Map();   // targetId -> { chunks, resolve, reject }
    this._dummyToTarget = new Map(); // dummyId -> targetId
  }

  connect() {
    return new Promise((resolve, reject) => {
      let resolved = false;

      const timeout = setTimeout(() => {
        if (!resolved) {
          resolved = true;
          this.close();
          reject(new Error("Connection timeout to Minecraft RCON server"));
        }
      }, 5000);

      this.socket = net.connect({ host: this.host, port: this.port }, () => {
        this.socket.on('data', (chunk) => this._onData(chunk));

        this._authenticate()
          .then((ok) => {
            clearTimeout(timeout);
            if (resolved) return;
            resolved = true;
            if (!ok) {
              this.close();
              reject(new Error("RCON Authentication failed"));
            } else {
              this.authenticated = true;
              resolve();
            }
          })
          .catch((err) => {
            clearTimeout(timeout);
            if (resolved) return;
            resolved = true;
            this.close();
            reject(err);
          });
      });

      this.socket.on('error', (err) => {
        clearTimeout(timeout);
        this._rejectAllPending(err);
        if (!resolved) {
          resolved = true;
          this.close();
          reject(err);
        }
      });

      this.socket.on('close', () => {
        this._rejectAllPending(new Error("RCON connection closed"));
      });
    });
  }

  _onData(chunk) {
    this._recvBuffer = this._recvBuffer.length ? Buffer.concat([this._recvBuffer, chunk]) : chunk;
    let packet;
    while ((packet = tryExtractPacket(this._recvBuffer))) {
      this._recvBuffer = packet.rest;
      this._handlePacket(packet);
    }
  }

  _handlePacket(packet) {
    // Auth response: SERVERDATA_AUTH_RESPONSE echoes the auth request's id
    // on success, but sends id -1 on failure — which never matches anything
    // registered, so it needs its own check before the normal lookups.
    if (packet.id === -1 && this._authPendingId !== null) {
      const entry = this._authEntry;
      this._authPendingId = null;
      this._authEntry = null;
      if (entry) entry.resolve(packet);
      return;
    }
    if (packet.id === this._authPendingId) {
      const entry = this._authEntry;
      this._authPendingId = null;
      this._authEntry = null;
      if (entry) entry.resolve(packet);
      return;
    }

    // Command response chunk — every packet sharing a target id is part of
    // that command's body, regardless of whether it's the only one.
    const cmd = this._commands.get(packet.id);
    if (cmd) {
      cmd.chunks.push(packet.body);
      return;
    }
    // Dummy response arriving means its preceding command's response is
    // now fully accumulated.
    const targetId = this._dummyToTarget.get(packet.id);
    if (targetId !== undefined) {
      this._dummyToTarget.delete(packet.id);
      const target = this._commands.get(targetId);
      this._commands.delete(targetId);
      if (target) target.resolve(target.chunks.join(''));
      return;
    }
    // Unroutable packet (e.g. something unprompted) — ignore.
  }

  _rejectAllPending(err) {
    if (this._authEntry) {
      const entry = this._authEntry;
      this._authEntry = null;
      this._authPendingId = null;
      entry.reject(err);
    }
    for (const [id, entry] of this._commands) {
      this._commands.delete(id);
      entry.reject(err);
    }
    this._dummyToTarget.clear();
  }

  _authenticate() {
    return new Promise((resolve, reject) => {
      const id = this.requestId++;
      this._authPendingId = id;
      this._authEntry = { resolve: (packet) => resolve(packet.id !== -1), reject };
      this.socket.write(encodePacket(id, 3, this.password));
    });
  }

  // Executes a command and reliably collects its full response even if the
  // server split it across multiple packets.
  execute(command) {
    return new Promise((resolve, reject) => {
      if (!this.socket) {
        return reject(new Error("Not connected to RCON"));
      }
      const targetId = this.requestId++;
      const dummyId = this.requestId++;
      this._commands.set(targetId, {
        chunks: [],
        resolve: (body) => resolve(body.trim()),
        reject
      });
      this._dummyToTarget.set(dummyId, targetId);
      this.socket.write(encodePacket(targetId, 2, command));
      this.socket.write(encodePacket(dummyId, 2, ''));
    });
  }

  close() {
    if (this.socket) {
      this.socket.destroy();
      this.socket = null;
    }
    this.authenticated = false;
    this._recvBuffer = Buffer.alloc(0);
  }
}

/**
 * Persistent singleton RCON connection.
 * Keeps a single connection alive so the Minecraft server console
 * doesn't get spammed with connect/disconnect messages.
 */
let _persistentClient = null;
let _connectPromise = null;

function _getConfig() {
  return {
    host: process.env.MINECRAFT_RCON_HOST || '85.202.160.151',
    port: parseInt(process.env.MINECRAFT_RCON_PORT) || 25575,
    password: process.env.MINECRAFT_RCON_PASSWORD,
  };
}

async function _getClient() {
  // If we already have a connected, authenticated client, reuse it
  if (_persistentClient && _persistentClient.socket && _persistentClient.authenticated) {
    return _persistentClient;
  }

  // If a connect attempt is already in progress, wait for it
  if (_connectPromise) {
    return _connectPromise;
  }

  const { host, port, password } = _getConfig();
  const client = new RconClient(host, port, password);

  _connectPromise = client.connect().then(() => {
    _persistentClient = client;
    _connectPromise = null;

    // Listen for unexpected disconnects and clean up silently
    client.socket.on('close', () => {
      _persistentClient = null;
    });
    client.socket.on('error', () => {
      _persistentClient = null;
    });

    return client;
  }).catch((err) => {
    _connectPromise = null;
    _persistentClient = null;
    client.close();
    throw err;
  });

  return _connectPromise;
}

/**
 * Executes a single command on the remote Minecraft server via RCON.
 * Uses a persistent connection to avoid spamming the server console.
 */
export async function sendRconCommand(command) {
  const { password } = _getConfig();

  if (!password) {
    console.warn("RCON not configured (MINECRAFT_RCON_PASSWORD missing). Skipping in-game verification command.");
    return "BYPASS";
  }

  try {
    const client = await _getClient();
    return await client.execute(command);
  } catch (firstErr) {
    // Connection might have gone stale — try one reconnect
    _persistentClient = null;
    try {
      const client = await _getClient();
      return await client.execute(command);
    } catch (retryErr) {
      throw retryErr;
    }
  }
}
