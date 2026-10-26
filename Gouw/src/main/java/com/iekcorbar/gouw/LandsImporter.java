package com.iekcorbar.gouw;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reads an old Lands database (SQLite, version 8.x schema "database_v2.db") and turns every LAND into a
 * gouw in the ClaimStore. The mapping:
 * <ul>
 *   <li>lands_lands.name / .area.holder.trusted - the Owner-role player becomes our owner, the other
 *       trusted entries (Member/Tenant roles) become trusted members;</li>
 *   <li>lands_lands_claims.chunks - JSON [{"x":..,"z":..}] chunk coordinates, copied straight over;</li>
 *   <li>lands_worlds maps the Lands world ulid to the Bukkit world name (skip worlds not on this server).</li>
 * </ul>
 * Lands' own tables stay untouched; after this run Gouw owns the truth in claims.json. The driver is
 * not a compile dependency: plugin.yml "libraries:" downloads sqlite-jdbc on startup, so this class only
 * touches java.sql and loads the driver by name.
 */
final class LandsImporter {
    private LandsImporter() {
    }

    /** @return the number of gouwen imported (0 = nothing there or failed). */
    static int run(GouwPlugin plugin, ClaimStore store) {
        File db = new File(plugin.getConfig().getString("import.lands-database", "plugins/Lands/Data/database_v2.db"));
        if (!db.exists()) {
            plugin.getLogger().info("No Lands database at " + db.getPath() + " - nothing to import.");
            return 0;
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("SQLite driver not loaded yet (Paper fetches plugin.yml libraries at startup) - try /gouw admin import after the next restart.");
            return 0;
        }
        String server = plugin.getConfig().getString("import.server", "server-1").replace("'", "''");
        int lands = 0, chunkCount = 0;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath()); Statement st = c.createStatement()) {

            Map<String, String> worldByUlid = new HashMap<>();
            try (ResultSet rs = st.executeQuery("select ulid, name from lands_worlds where server = '" + server + "'")) {
                while (rs.next()) worldByUlid.put(rs.getString(1), rs.getString(2));
            }

            Map<String, List<int[]>> chunksByLand = new HashMap<>();
            try (ResultSet rs = st.executeQuery("select land, chunks from lands_lands_claims")) {
                while (rs.next()) {
                    String json = rs.getString(2);
                    if (json == null || json.isEmpty()) continue;
                    List<int[]> list = new ArrayList<>();
                    for (var el : JsonParser.parseString(json).getAsJsonArray()) {
                        JsonObject o = el.getAsJsonObject();
                        list.add(new int[]{o.get("x").getAsInt(), o.get("z").getAsInt()});
                    }
                    chunksByLand.put(rs.getString(1), list);
                }
            }

            try (ResultSet rs = st.executeQuery("select ulid, name, world, area from lands_lands where type = 'LAND'")) {
                while (rs.next()) {
                    String ulid = rs.getString(1), name = rs.getString(2);
                    String world = worldByUlid.get(rs.getString(3));
                    if (world == null || Bukkit.getWorld(world) == null) {
                        plugin.getLogger().warning("Skipped gouw '" + name + "' - its world (" + world + ") is not loaded here.");
                        continue;
                    }
                    String areaJson = rs.getString(4);
                    if (areaJson == null) continue;
                    JsonObject holder = JsonParser.parseString(areaJson).getAsJsonObject().getAsJsonObject("holder");
                    if (holder == null) continue;

                    Map<String, String> roleNames = new HashMap<>();
                    for (var r : holder.getAsJsonArray("roles")) {
                        JsonObject o = r.getAsJsonObject();
                        roleNames.put(o.get("ulid").getAsString(), o.get("name").getAsString());
                    }

                    UUID owner = null;
                    List<UUID> trusted = new ArrayList<>();
                    for (var t : holder.getAsJsonArray("trusted")) {
                        String s = t.getAsString();
                        int i = s.indexOf(':');
                        UUID u = UUID.fromString(s.substring(0, i));
                        String role = roleNames.getOrDefault(s.substring(i + 1), "");
                        if (owner == null && role.toLowerCase(Locale.ROOT).contains("owner")) owner = u;
                        else trusted.add(u);
                    }
                    if (owner == null) {
                        plugin.getLogger().warning("Skipped gouw '" + name + "' - no Owner role found in its trusted list.");
                        continue;
                    }
                    trusted.remove(owner);

                    Land l = store.add(name, owner, world);
                    for (int[] xy : chunksByLand.getOrDefault(ulid, List.of())) {
                        if (store.addChunk(l, xy[0], xy[1])) chunkCount++;
                    }
                    l.trusted.addAll(trusted);
                    lands++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Lands import failed: " + e);
            return 0;
        }
        if (lands > 0) {
            store.save();
            plugin.getLogger().info("Imported " + lands + " Lands gouwen (" + chunkCount + " chunks) into claims.json.");
        }
        return lands;
    }
}
