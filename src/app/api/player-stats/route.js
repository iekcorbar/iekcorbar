import { NextResponse } from 'next/server';
import { sendRconCommand } from '@/lib/rcon';
import { db } from '@/lib/db';
import { getCmiPlayerData } from '@/lib/cmi-db';

/**
 * In-memory cache for player stats.
 * Prevents hammering the server with RCON queries for the same player.
 * Each entry expires after 60 seconds.
 */
const statsCache = new Map();
const CACHE_TTL_MS = 60_000; // 60 seconds

function getCached(username) {
  const key = username.toLowerCase();
  const entry = statsCache.get(key);
  if (entry && (Date.now() - entry.timestamp) < CACHE_TTL_MS) {
    return entry.data;
  }
  statsCache.delete(key);
  return null;
}

function setCache(username, data) {
  statsCache.set(username.toLowerCase(), {
    data,
    timestamp: Date.now()
  });
}

/**
 * GET /api/player-stats?username=<name>
 *
 * 1. Tries RCON command "webstats <username>" (IekcorbarSiteSync plugin).
 * 2. Fallbacks to MySQL database (player_stats table) if RCON is offline.
 * 3. Either way, non-fatally folds in real CMI-only fields (rank, homes,
 *    a more precise total playtime) — see src/lib/cmi-db.js. If that DB is
 *    unreachable, these fields are just omitted rather than failing the
 *    whole request; nothing here depends on CMI being up.
 */
export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const username = searchParams.get('username');

  if (!username || username.trim().length === 0) {
    return NextResponse.json({ error: "username parameter is required" }, { status: 400 });
  }

  const cleanUsername = username.trim();

  // Check cache first
  const cached = getCached(cleanUsername);
  if (cached) {
    return NextResponse.json(cached);
  }

  async function withCmiData(stats) {
    const [cmi, eliteCoins, landsClaimed] = await Promise.all([
      getCmiPlayerData(cleanUsername),
      db.getEliteCoins(cleanUsername),
      db.getLandsClaimedByPlayer(cleanUsername),
    ]);
    const merged = { ...stats };
    if (eliteCoins != null) merged.eliteCoins = eliteCoins;
    if (landsClaimed != null) merged.landsClaimed = landsClaimed;
    return cmi ? { ...merged, cmi } : merged;
  }

  try {
    const rconResponse = await sendRconCommand(`webstats ${cleanUsername}`);

    if (rconResponse && rconResponse !== "BYPASS") {
      let stats;
      try {
        stats = JSON.parse(rconResponse);
        if (stats && !stats.error) {
          const merged = await withCmiData(stats);
          setCache(cleanUsername, merged);
          return NextResponse.json(merged);
        }
      } catch {
        /* invalid JSON, fall through to MySQL fallback */
      }
    }
  } catch {
    /* RCON error, fall through to MySQL fallback */
  }

  // Fallback to MySQL database (player_stats table populated by plugin)
  try {
    const dbStats = await db.getPlayerStats(cleanUsername);
    if (dbStats) {
      const merged = await withCmiData(dbStats);
      setCache(cleanUsername, merged);
      return NextResponse.json(merged);
    }
  } catch (dbErr) {
    console.error('MySQL stats query error:', dbErr.message);
  }

  return NextResponse.json({
    error: "Stats unavailable. Make sure IekcorbarSiteSync plugin is connected.",
    username: cleanUsername,
    offline: true
  }, { status: 503 });
}
