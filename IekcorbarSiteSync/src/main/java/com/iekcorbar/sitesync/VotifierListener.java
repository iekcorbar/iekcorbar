package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

// Listens for the classic com.vexsoftware.votifier.model.VotifierEvent.
// NuVotifier deliberately kept this exact class/package for backward
// compatibility with the whole vote-plugin ecosystem, and VoteSpeed's
// "built-in NuVotifier receiver" (which is what this server actually runs —
// no separate Votifier plugin needed) fires the same event for the same
// reason: that's the entire point of claiming Votifier-receiver
// compatibility, so existing "listen for votes" plugins keep working
// regardless of which plugin actually receives the vote.
//
// Registered via Bukkit's low-level registerEvent(Class, ...) overload with
// the event class resolved BY NAME at runtime (no compile-time Maven
// dependency on Votifier/NuVotifier/VoteSpeed at all, matching this
// project's existing reflection-based pattern for other optional plugin
// integrations, e.g. the Jobs Reborn block in MySQLSync.java) — if the class
// isn't present (no compatible receiver installed, or a future version
// renamed it), this silently never fires instead of failing to load.
//
// Every vote this receives — from any registered service (TopG,
// PlanetMinecraft, our own direct "Iekcorbar" service, ...) — is real and
// already verified by whichever vote site or our own Votifier client sent
// it, unlike the website's older vote_log click-based honor system. Logged
// into a separate `votifier_events` table (not vote_log) precisely so this
// stays purely additive and doesn't change the existing reward flow's
// behavior.
public class VotifierListener implements Listener {

    public static void register(IekcorbarSiteSync plugin) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
            Method getVote = eventClass.getMethod("getVote");

            EventExecutor executor = (listener, event) -> {
                try {
                    Object vote = getVote.invoke(event);
                    Class<?> voteClass = vote.getClass();
                    String username = (String) voteClass.getMethod("getUsername").invoke(vote);
                    String serviceName = (String) voteClass.getMethod("getServiceName").invoke(vote);
                    String address = (String) voteClass.getMethod("getAddress").invoke(vote);
                    String timeStamp = String.valueOf(voteClass.getMethod("getTimeStamp").invoke(vote));
                    if (plugin.getMysqlSync() != null) {
                        plugin.getMysqlSync().recordVotifierEvent(username, serviceName, address, timeStamp);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to process VotifierEvent: " + ex.getMessage());
                }
            };

            Bukkit.getPluginManager().registerEvent(eventClass, new VotifierListener(), EventPriority.MONITOR, executor, plugin, false);
            plugin.getLogger().info("VotifierEvent listener registered — real vote verification active.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("VotifierEvent class not found (no Votifier-compatible receiver detected) — vote verification sync disabled, website will fall back to its own click-based vote log.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register VotifierEvent listener: " + e.getMessage());
        }
    }
}
