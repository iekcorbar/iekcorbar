package com.iekcorbar.sitesync;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Closes a real audit gap: the website's own moderation log
// (`moderation_logs` table, see api/moderation/route.js) only ever gets a
// row when staff act *through the website* — the RCON command it dispatches
// from there. Staff typing the exact same CMI commands directly in-game (or
// at the server console) bypass that entirely, so the site's moderation
// history silently missed them.
//
// Only matches the same four `cmi <subcommand>` forms the website itself
// uses — never vanilla /kick or /ban, which CMI does not override (see the
// website's own comment on this in api/moderation/route.js). This is a
// best-effort mirror, not a guarantee: it logs the moment the command is
// *typed*, before CMI has actually resolved/executed it, so a typo'd
// player name still produces a log entry even though nothing really
// happened in-game — the website's own RCON-response handling has the same
// class of limitation (see its "reply text alone isn't trustworthy"
// comments elsewhere), so this isn't a new weakness, just an inherited one.
public class ModerationAuditListener implements Listener {

    private static final Pattern CMI_MOD_COMMAND = Pattern.compile(
            "^/cmi\\s+(kick|ban|tempban|unban)\\s+(\\S+)(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE);

    private final IekcorbarSiteSync plugin;

    public ModerationAuditListener(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        record(event.getMessage(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        // Typed at the console without a leading slash — normalize so one
        // pattern covers both sources.
        String command = event.getCommand();
        if (!command.startsWith("/")) command = "/" + command;
        record(command, "Console");
    }

    private void record(String rawCommand, String staffMember) {
        if (!plugin.getConfig().getBoolean("moderation-audit.enabled", true)) return;

        Matcher m = CMI_MOD_COMMAND.matcher(rawCommand.trim());
        if (!m.matches()) return;

        String type = m.group(1).toLowerCase();
        String target = m.group(2);
        String reason = (m.group(3) != null && !m.group(3).isBlank()) ? m.group(3).trim() : "(no reason given)";

        if (plugin.getMysqlSync() != null) {
            plugin.getMysqlSync().logModerationAction(target, type, reason, staffMember);
        }
    }
}
