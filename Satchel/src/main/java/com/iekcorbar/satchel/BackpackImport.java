package com.iekcorbar.satchel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * One-time bridge from the (paid) BackpackPlus plugin to our own Satchel backpacks.
 * /satchel importbackpacks [path] reads BackpackPlus' SQLite database, rebuilds every non-empty
 * backpack as a Satchel backpack of the same size (contents included), hands it to the owner -
 * or, when they are offline, on their next join - and remembers the imported rows in
 * backpackplus-imported.txt so a second run never gives anything twice. The old database is
 * only read, never modified: rolling back is just putting BackpackPlus back. The SQLite driver
 * comes from the libraries/ folder the server already filled for BackpackPlus itself, so the
 * plugin shades nothing.
 */
final class BackpackImport implements Listener {
    private final SatchelPlugin plugin;

    BackpackImport(SatchelPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ command

    void run(CommandSender s, String[] args) {
        if (!s.hasPermission("satchel.admin")) {
            s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        File db = args.length > 1 ? new File(args[1]) : new File("plugins/BackpackPlus/backpack.db");
        if (!db.isFile()) {
            s.sendMessage(Component.text("No BackpackPlus database at " + db.getPath() + ".", NamedTextColor.RED));
            return;
        }
        try {
            importAll(s, db);
        } catch (Exception e) {
            s.sendMessage(Component.text("Import failed: " + e, NamedTextColor.RED));
            plugin.getLogger().warning("BackpackPlus import failed: " + e);
        }
    }

    private void importAll(CommandSender s, File db) throws Exception {
        Set<String> done = readDone();
        Map<UUID, List<ItemStack>> pending = readPending();
        int restored = 0, skipped = 0, loose = 0;
        List<String> finished = new ArrayList<>();
        try (Connection c = open(db); java.sql.Statement st = c.createStatement()) {
            try (java.sql.ResultSet rs = st.executeQuery("select id, backpackOwnerUUID, content from backpacks")) {
                while (rs.next()) {
                    String id = rs.getString(1), owner = rs.getString(2);
                    if (done.contains(id)) { skipped++; continue; }
                    List<ItemStack> items = new ArrayList<>();
                    int invSize = 9;
                    for (JsonElement pe : JsonParser.parseString(rs.getString(3)).getAsJsonObject().getAsJsonArray("pages")) {
                        JsonObject page = pe.getAsJsonObject();
                        invSize = Math.max(invSize, page.get("inventorySize").getAsInt());
                        for (JsonElement ce : page.getAsJsonArray("contents")) {
                            String b64 = ce.getAsJsonObject().get("content").getAsString();
                            if (b64.isBlank()) continue;
                            ItemStack it = decodeItem(b64);
                            if (it != null && !it.isEmpty()) items.add(it);
                        }
                    }
                    if (items.isEmpty()) { finished.add(id); skipped++; continue; }
                    ItemStack bag = toSatchel(items, invSize); // items keeps what no bag could hold
                    if (bag != null) restored++;
                    List<ItemStack> out = new ArrayList<>();
                    if (bag != null) out.add(bag);
                    out.addAll(items);
                    loose += items.size();
                    give(UUID.fromString(owner), out, pending);
                    finished.add(id);
                }
            }
        }
        if (!finished.isEmpty()) {
            doneFile().getParentFile().mkdirs();
            Files.write(doneFile().toPath(), finished, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        writePending(pending);
        String rep = "BackpackPlus import: " + restored + " backpack(s) restored, " + skipped + " empty/known skipped";
        if (loose > 0) rep += ", " + loose + " item(s) that fitted no bag handed loose";
        s.sendMessage(Component.text(rep + ".", NamedTextColor.GREEN));
    }

    // ------------------------------------------------------------ old bag -> Satchel bag

    /**
     * Fills the Satchel tier that matches the old inventory size (9 canvas, 27 copper, 36 iron,
     * 45 diamond) and grows towards netherite while things do not fit. The given list ends up
     * holding only what even the biggest bag could not take.
     */
    private ItemStack toSatchel(List<ItemStack> items, int oldSize) {
        SItem tier = switch (oldSize) {
            case 27 -> SItem.COPPER_BACKPACK;
            case 36 -> SItem.IRON_BACKPACK;
            case 45 -> SItem.DIAMOND_BACKPACK;
            default -> SItem.CANVAS_BACKPACK;
        };
        List<ItemStack> original = new ArrayList<>(items);
        ItemStack[] store = new ItemStack[tier.storage()];
        List<ItemStack> rest = new ArrayList<>();
        while (true) {
            store = new ItemStack[tier.storage()];
            rest = new ArrayList<>();
            for (ItemStack it : original) {
                ItemStack left = Items.insert(store, it);
                if (left != null && !left.isEmpty()) rest.add(left);
            }
            SItem next = next(tier);
            if (rest.isEmpty() || next == null) break;
            tier = next;
        }
        items.clear();
        items.addAll(rest);
        ItemStack bag = plugin.items().create(tier, 1);
        plugin.items().write(bag, store, new ItemStack[tier.upgradeSlots]);
        return bag;
    }

    private static SItem next(SItem t) {
        return switch (t) {
            case CANVAS_BACKPACK -> SItem.COPPER_BACKPACK;
            case COPPER_BACKPACK -> SItem.IRON_BACKPACK;
            case IRON_BACKPACK -> SItem.GOLD_BACKPACK;
            case GOLD_BACKPACK -> SItem.DIAMOND_BACKPACK;
            case DIAMOND_BACKPACK -> SItem.NETHERITE_BACKPACK;
            default -> null;
        };
    }

    // ------------------------------------------------------------ sqlite + item streams

    private Connection open(File db) throws Exception {
        File jar = findDriver();
        if (jar == null) throw new IllegalStateException("no sqlite-jdbc*.jar under libraries/ - run this while BackpackPlus is still installed");
        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()}, plugin.getClass().getClassLoader());
        java.sql.Driver d = (java.sql.Driver) Class.forName("org.sqlite.JDBC", true, cl).getDeclaredConstructor().newInstance();
        return d.connect("jdbc:sqlite:" + db.getAbsolutePath(), new Properties());
    }

    /** Newest sqlite driver the server (or BackpackPlus' own loader) already downloaded. */
    private static File findDriver() throws IOException {
        File best = null;
        for (File root : new File[]{new File("libraries"), new File("../libraries")}) {
            if (!root.isDirectory()) continue;
            try (var walk = Files.walk(root.toPath())) {
                for (var path : walk.toList()) {
                    File f = path.toFile();
                    if (f.isFile() && f.getName().startsWith("sqlite-jdbc") && f.getName().endsWith(".jar")) {
                        if (best == null || f.lastModified() > best.lastModified()) best = f;
                    }
                }
            }
        }
        return best;
    }

    private static ItemStack decodeItem(String b64) {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(b64)))) {
            Object o = in.readObject();
            return o instanceof ItemStack it ? it : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String encodeItems(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(items);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static ItemStack[] decodeItems(String b64) throws Exception {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(b64)))) {
            Object o = in.readObject();
            return o instanceof ItemStack[] arr ? arr : new ItemStack[0];
        }
    }

    // ------------------------------------------------------------ give now, or on next join

    private void give(UUID owner, List<ItemStack> out, Map<UUID, List<ItemStack>> pending) {
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            hand(p, out);
            p.sendMessage(Component.text("An old BackpackPlus backpack was refilled as a Satchel backpack.", NamedTextColor.GOLD));
        } else {
            pending.computeIfAbsent(owner, k -> new ArrayList<>()).addAll(out);
        }
    }

    private static void hand(Player p, List<ItemStack> out) {
        for (ItemStack it : out)
            for (ItemStack left : p.getInventory().addItem(it).values())
                p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Map<UUID, List<ItemStack>> pending;
        try {
            pending = readPending();
        } catch (Exception ex) {
            return;
        }
        List<ItemStack> out = pending.remove(e.getPlayer().getUniqueId());
        if (out == null || out.isEmpty()) return;
        hand(e.getPlayer(), out);
        e.getPlayer().sendMessage(Component.text("Your old BackpackPlus backpack(s) were moved into a Satchel backpack. Welcome back!", NamedTextColor.GOLD));
        try {
            writePending(pending);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not update the pending backpack list: " + ex);
        }
    }

    // ------------------------------------------------------------ marker + pending files

    private File pendingFile() {
        return new File(plugin.getDataFolder(), "pending-import.json");
    }

    private File doneFile() {
        return new File(plugin.getDataFolder(), "backpackplus-imported.txt");
    }

    private Set<String> readDone() throws IOException {
        Set<String> out = new HashSet<>();
        if (doneFile().isFile()) out.addAll(Files.readAllLines(doneFile().toPath()));
        return out;
    }

    private Map<UUID, List<ItemStack>> readPending() throws Exception {
        Map<UUID, List<ItemStack>> out = new LinkedHashMap<>();
        if (!pendingFile().isFile()) return out;
        JsonObject root = JsonParser.parseString(Files.readString(pendingFile().toPath())).getAsJsonObject();
        for (String uuid : root.keySet()) {
            List<ItemStack> bags = new ArrayList<>();
            for (JsonElement b : root.getAsJsonArray(uuid)) bags.addAll(List.of(decodeItems(b.getAsString())));
            out.put(UUID.fromString(uuid), bags);
        }
        return out;
    }

    private void writePending(Map<UUID, List<ItemStack>> pending) throws Exception {
        plugin.getDataFolder().mkdirs();
        JsonObject root = new JsonObject();
        for (var en : pending.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack bag : en.getValue()) arr.add(encodeItems(new ItemStack[]{bag}));
            root.add(en.getKey().toString(), arr);
        }
        Files.writeString(pendingFile().toPath(), root.toString());
    }
}
