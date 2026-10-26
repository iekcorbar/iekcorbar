import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

export async function GET() {
  let online = false;
  let playersOnline = 0;
  let playersMax = 100;
  let playersList = [];
  let version = "Paper 26.1.2";

  // Staff-editable (Staff Panel > Settings > General Settings) — see
  // db.getSettings()'s comment on why this isn't hardcoded per-callsite
  // anymore.
  const { server_address: serverAddress } = await db.getSettings();
  const [serverHost, serverPortStr] = serverAddress.split(':');
  const serverPort = parseInt(serverPortStr, 10) || 25565;

  try {
    const res = await fetch(`https://api.mcsrvstat.us/3/${serverAddress}`, {
      signal: AbortSignal.timeout(2500),
      cache: 'no-store'
    });
    const data = await res.json();
    if (data && data.online) {
      online = true;
      playersOnline = data.players?.online || 0;
      playersMax = data.players?.max || 100;
      version = data.version || "Paper 26.1.2";
      if (data.players?.list) {
        playersList = data.players.list.map(p => p.name);
      }
    }
  } catch (err) {
    console.error("Failed to query live Minecraft server status:", err.message);
  }

  // Real-time Discord server invite data query
  let discordOnline = true;
  let discordMembers = 142;
  let discordPresence = 28;
  let discordGuildId = null;
  try {
    const dcRes = await fetch("https://discord.com/api/v9/invites/G9P2khU7F4?with_counts=true", {
      signal: AbortSignal.timeout(2500),
      cache: 'no-store'
    });
    const dcData = await dcRes.json();
    if (dcData && dcData.guild) {
      discordMembers = dcData.approximate_member_count || 142;
      discordPresence = dcData.approximate_presence_count || 28;
      discordGuildId = dcData.guild.id || null;
    }
  } catch (err) {
    console.error("Failed to query live Discord invite stats:", err.message);
  }

  // Optional richer widget data (a few online members' avatars + active
  // voice channels) — Discord's real "Server Widget" API, a *separate*
  // opt-in toggle from the invite link above (Discord Server Settings ->
  // Widget -> Enable Server Widget). Silently omitted if it's off or the
  // request fails for any reason; the counts above already work without
  // it, this only adds the small avatar row when available.
  let discordWidget = null;
  if (discordGuildId) {
    try {
      const widgetRes = await fetch(`https://discord.com/api/guilds/${discordGuildId}/widget.json`, {
        signal: AbortSignal.timeout(2500),
        cache: 'no-store'
      });
      if (widgetRes.ok) {
        const widgetData = await widgetRes.json();
        discordWidget = {
          members: (widgetData.members || []).slice(0, 12).map(m => ({ username: m.username, avatarUrl: m.avatar_url, status: m.status })),
          voiceChannels: (widgetData.channels || []).filter(c => (c.members || []).length > 0).map(c => ({ name: c.name, memberCount: c.members.length }))
        };
      }
    } catch (err) {
      console.error("Discord server widget not available (likely disabled in Server Settings):", err.message);
    }
  }

  // Fallback values for beautiful UI if server is offline during testing
  const finalOnline = online;
  const finalPlayersOnline = online ? playersOnline : 0;
  const finalPlayersMax = online ? playersMax : 100;
  const finalPlayersList = playersList;
  const finalVersion = version;

  // Live Broadcast widget stats — all real data (used to be fabricated
  // formulas): total claimed land and active jobs come from player_stats
  // (synced by the IekcorbarSiteSync plugin).
  const [totalClaims, activeJobsCount] = await Promise.all([
    db.getTotalLandsClaimed(),
    db.getTotalActiveJobs()
  ]);

  // Record actual live peak player count for today if the server is online
  if (online) {
    await db.recordDailyPlayerPeak(playersOnline);
  }

  const weeklyTrend = await db.getPlayerHistory();

  return NextResponse.json({
    online: finalOnline,
    ip: serverHost,
    port: serverPort,
    // Exactly as staff entered it — a bare hostname (no ":port") relies on
    // the _minecraft._tcp SRV record, so it must be shown without a port.
    address: serverAddress,
    version: finalVersion,
    players: {
      online: finalPlayersOnline,
      max: finalPlayersMax,
      list: finalPlayersList
    },
    discord: {
      online: discordOnline,
      membersCount: discordMembers,
      onlineCount: discordPresence,
      inviteLink: "https://discord.gg/G9P2khU7F4",
      widget: discordWidget
    },
    stats: {
      totalClaims,
      activeJobs: activeJobsCount
    },
    weeklyTrend: weeklyTrend
  });
}
