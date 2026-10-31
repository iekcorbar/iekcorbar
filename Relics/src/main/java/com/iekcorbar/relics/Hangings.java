package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Notice Board (text the placer writes) and Wish Tree (tanabata: players hang a paper wish).
 * Both are drawn from vanilla block/text displays spawned here, so they need no resource pack. Their text lives in
 * plugins/Relics/hangings.yml (key = world,x,y,z).
 */
final class Hangings implements Listener {
    private static final int MAX_WISHES = 14, NOTICE_CHARS = 90, WISH_CHARS = 60;
    private static final int[] STRIP = {0xFFF4A6C0, 0xFFF2D86A, 0xFF8EC8F0, 0xFFA8E0A0};

    private final RelicsPlugin plugin;
    private final Relics relics;
    private final NamespacedKey tagKey;
    private final File file;
    private final YamlConfiguration data;
    private final Map<Relics.Relic, List<Entity>> parts = new HashMap<>();
    private final Map<UUID, Object[]> pending = new HashMap<>();
    private final NamespacedKey tanabataKey;
    private boolean lastDay; // player -> {Relic, "notice"|"wish"}

    Hangings(RelicsPlugin plugin, Relics relics, NamespacedKey tagKey) {
        this.plugin = plugin;
        this.relics = relics;
        this.tagKey = tagKey;
        this.file = new File(plugin.getDataFolder(), "hangings.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.tanabataKey = new NamespacedKey(plugin, "tanabata_year");
        this.lastDay = tanabata();
    }

    // ------------------------------------------------------------ tanabata (7 July): the strips glow, one small reward per year

    private java.time.LocalDate today() {
        java.time.ZoneId z;
        try {
            z = java.time.ZoneId.of(plugin.getConfig().getString("timezone", "Europe/Amsterdam"));
        } catch (Exception ex) {
            z = java.time.ZoneId.systemDefault();
        }
        return java.time.LocalDate.now(z);
    }

    private boolean tanabata() {
        java.time.LocalDate d = today();
        return d.getMonthValue() == 7 && d.getDayOfMonth() == 7;
    }

    /** Called a few times a second: when the date flips to or from 7 July every tree is redrawn (glowing or not). */
    void tick() {
        boolean now = tanabata();
        if (now == lastDay) return;
        lastDay = now;
        for (Relics.Relic r : relics.all()) if (handles(r) && r.world.isChunkLoaded(r.x >> 4, r.z >> 4)) spawn(r);
    }

    private void tanabataReward(Player p) {
        int year = today().getYear();
        Integer done = p.getPersistentDataContainer().get(tanabataKey, PersistentDataType.INTEGER);
        if (done != null && done == year) return;
        p.getPersistentDataContainer().set(tanabataKey, PersistentDataType.INTEGER, year);
        List<String> rewards = plugin.getConfig().getStringList("tanabata.reward");
        if (rewards.isEmpty()) rewards = List.of("EXPERIENCE_BOTTLE:3", "GOLD_NUGGET:2");
        for (String s : rewards) {
            String[] a = s.split(":");
            Material m = Material.matchMaterial(a[0]);
            if (m == null) continue;
            int n = a.length > 1 ? parseInt(a[1]) : 1;
            for (ItemStack left : p.getInventory().addItem(new ItemStack(m, Math.max(1, n))).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.sendMessage(Component.text("Tanabata! The stars heard you - a small gift for your wish.", NamedTextColor.GOLD));
    }


    static boolean handles(Relics.Relic r) {
        return r.type == RelicType.NOTICE_BOARD || r.type == RelicType.WISH_TREE;
    }

    private static String key(Relics.Relic r) {
        return r.world.getUID() + "," + r.x + "," + r.y + "," + r.z;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("hangings.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ drawing

    private static double[] rot(double x, double z, float yaw) {
        double a = Math.toRadians(yaw);
        return new double[]{x * Math.cos(a) - z * Math.sin(a), x * Math.sin(a) + z * Math.cos(a)};
    }

    /** One box in local block units (x right, y up, z towards the viewer), around the centre of the relic's block floor. */
    private void box(Relics.Relic r, List<Entity> out, float yaw, double x0, double y0, double z0, double x1, double y1, double z1, Material m) {
        double[] o = rot(x0, z0, yaw);
        Location at = new Location(r.world, r.x + 0.5 + o[0], r.y + y0, r.z + 0.5 + o[1], yaw, 0f);
        out.add(r.world.spawn(at, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            d.setBlock(m.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0)), new Quaternionf()));
        }));
    }

    private static float yaw(BlockFace f) {
        return switch (f) {
            case EAST -> -90;
            case SOUTH -> 0;
            case WEST -> 90;
            default -> 180;
        };
    }

    void spawn(Relics.Relic r) {
        remove(r);
        List<Entity> out = new ArrayList<>();
        parts.put(r, out);
        float yaw = yaw(r.facing);
        if (r.type == RelicType.NOTICE_BOARD) {
            Material wood = Material.DARK_OAK_PLANKS;
            box(r, out, yaw, -0.47, 0, -0.06, -0.37, 1.6, 0.06, wood);
            box(r, out, yaw, 0.37, 0, -0.06, 0.47, 1.6, 0.06, wood);
            box(r, out, yaw, -0.40, 0.52, -0.03, 0.40, 1.42, 0.05, Material.BIRCH_PLANKS);
            box(r, out, yaw, -0.44, 1.42, -0.07, 0.44, 1.50, 0.07, wood);
            box(r, out, yaw, -0.44, 0.46, -0.07, 0.44, 0.52, 0.07, wood);
            box(r, out, yaw, -0.58, 1.50, -0.16, 0.58, 1.58, 0.16, Material.SPRUCE_PLANKS);
            box(r, out, yaw, -0.50, 1.58, -0.10, 0.50, 1.64, 0.10, Material.SPRUCE_SLAB);
            double[] o = rot(0, 0.075, yaw);
            Location at = new Location(r.world, r.x + 0.5 + o[0], r.y + 0.98, r.z + 0.5 + o[1], yaw, 0f);
            out.add(r.world.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(false);
                d.setLineWidth(105);
                d.setViewRange(0.5f);
                d.text(noticeText(r));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.34f, 0.34f, 0.34f), new Quaternionf()));
            }));
        } else {
            box(r, out, yaw, -0.28, 0, -0.28, 0.28, 0.10, 0.28, Material.STONE_BRICKS);
            box(r, out, yaw, -0.07, 0.10, -0.07, 0.07, 0.95, 0.07, Material.CHERRY_LOG);
            box(r, out, yaw, 0.05, 0.55, -0.04, 0.30, 0.62, 0.04, Material.CHERRY_LOG);
            box(r, out, yaw, -0.30, 0.65, -0.04, -0.05, 0.72, 0.04, Material.CHERRY_LOG);
            box(r, out, yaw, -0.50, 0.85, -0.50, 0.50, 1.30, 0.50, Material.CHERRY_LEAVES);
            box(r, out, yaw, -0.38, 1.30, -0.38, 0.38, 1.65, 0.38, Material.CHERRY_LEAVES);
            box(r, out, yaw, -0.24, 1.65, -0.24, 0.24, 1.90, 0.24, Material.CHERRY_LEAVES);
            List<String> wishes = data.getStringList("wishes." + key(r));
            for (int i = 0; i < wishes.size() && i < MAX_WISHES; i++) {
                String[] w = wishes.get(i).split("\\|", 3);
                int col = Math.floorMod(parseInt(w[0]), STRIP.length);
                double ang = i * 2.399963;   // golden angle: the strips spread evenly over the canopy fringe
                double rad = 0.30 + 0.17 * ((i % 3) / 2.0);
                Location at = new Location(r.world, r.x + 0.5 + Math.cos(ang) * rad, r.y + 0.66 - 0.04 * (i % 2), r.z + 0.5 + Math.sin(ang) * rad);
                final int bg = STRIP[col];
                out.add(r.world.spawn(at, TextDisplay.class, d -> {
                    d.setPersistent(false);
                    d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setBackgroundColor(Color.fromARGB(bg >>> 24, (bg >> 16) & 255, (bg >> 8) & 255, bg & 255));
                    if (lastDay) d.setBrightness(new Display.Brightness(15, 15));
                    d.setShadowed(false);
                    d.setViewRange(0.4f);
                    d.text(Component.text("  \n  \n  "));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.35f, 0.35f, 0.35f), new Quaternionf()));
                }));
            }
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Component noticeText(Relics.Relic r) {
        String t = data.getString("notice." + key(r), "");
        if (t.isEmpty()) return Component.text("Nothing posted yet.", NamedTextColor.DARK_GRAY);
        Component c = Component.empty();
        String[] lines = t.split("\\|");
        for (int i = 0; i < lines.length; i++) c = c.append(Component.text((i > 0 ? "\n" : "") + lines[i].trim(), NamedTextColor.BLACK));
        return c;
    }

    void remove(Relics.Relic r) {
        List<Entity> l = parts.remove(r);
        if (l != null) for (Entity e : l) e.remove();
    }

    boolean lost(Relics.Relic r) {
        List<Entity> l = parts.get(r);
        if (l == null || l.isEmpty()) return true;
        for (Entity e : l) if (!e.isValid()) return true;
        return false;
    }

    /** The relic was really broken: its text goes with it. */
    void forget(Relics.Relic r) {
        remove(r);
        data.set("notice." + key(r), null);
        data.set("wishes." + key(r), null);
        save();
    }

    // ------------------------------------------------------------ interaction

    private boolean canEdit(Relics.Relic r, Player p) {
        return p.hasPermission("relics.build") || (r.owner != null && r.owner.equals(p.getUniqueId()));
    }

    void use(Relics.Relic r, Player p, ItemStack hand) {
        if (r.type == RelicType.NOTICE_BOARD) {
            if (p.isSneaking() && canEdit(r, p)) {
                pending.put(p.getUniqueId(), new Object[]{r, "notice"});
                p.sendMessage(Component.text("Type the new notice in chat (max " + NOTICE_CHARS + " characters, | starts a new line). "
                        + "Type clear to empty the board, cancel to keep it as it is.", NamedTextColor.GOLD));
                return;
            }
            String t = data.getString("notice." + key(r), "");
            p.sendMessage(Component.text(t.isEmpty() ? "Nothing is pinned to the board." : "Notice: " + t.replace("|", " / "), NamedTextColor.YELLOW));
            if (canEdit(r, p) && t.isEmpty()) p.sendActionBar(Component.text("Sneak + right-click to write a notice.", NamedTextColor.GRAY));
            return;
        }
        // wish tree
        if (hand != null && hand.getType() == Material.PAPER && relics.typeOf(hand) == null) {
            pending.put(p.getUniqueId(), new Object[]{r, "wish"});
            p.sendMessage(Component.text("Type your wish in chat (max " + WISH_CHARS + " characters, type cancel to keep your paper).", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (p.isSneaking() && p.hasPermission("relics.build")) {
            data.set("wishes." + key(r), null);
            save();
            spawn(r);
            p.sendActionBar(Component.text("All wishes taken down.", NamedTextColor.GRAY));
            return;
        }
        List<String> wishes = data.getStringList("wishes." + key(r));
        if (wishes.isEmpty()) {
            p.sendActionBar(Component.text("Hold a sheet of paper and right-click to hang a wish.", NamedTextColor.GRAY));
            return;
        }
        String[] w = wishes.get(ThreadLocalRandom.current().nextInt(wishes.size())).split("\\|", 3);
        String who = w.length > 1 ? w[1] : "someone", text = w.length > 2 ? w[2] : "";
        p.sendActionBar(Component.text("\"" + text + "\"", NamedTextColor.LIGHT_PURPLE).append(Component.text("  - " + who, NamedTextColor.GRAY)));
        r.world.playSound(r.center(), "minecraft:block.cherry_leaves.step", 0.6f, 1.3f);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Object[] pend = pending.remove(e.getPlayer().getUniqueId());
        if (pend == null) return;
        e.setCancelled(true);
        String msg = e.getMessage().trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> finish((Relics.Relic) pend[0], (String) pend[1], p, msg));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }

    private void finish(Relics.Relic r, String kind, Player p, String msg) {
        if (msg.isEmpty() || msg.equalsIgnoreCase("cancel")) {
            p.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
            return;
        }
        if (relics.at(r.world.getBlockAt(r.x, r.y, r.z)) != r) {
            p.sendMessage(Component.text("It is gone.", NamedTextColor.RED));
            return;
        }
        if (kind.equals("notice")) {
            if (!canEdit(r, p)) return;
            if (msg.equalsIgnoreCase("clear")) data.set("notice." + key(r), null);
            else data.set("notice." + key(r), msg.length() > NOTICE_CHARS ? msg.substring(0, NOTICE_CHARS) : msg);
            save();
            spawn(r);
            r.world.playSound(r.center(), "minecraft:entity.item_frame.add_item", 0.8f, 1f);
            p.sendMessage(Component.text("Notice posted.", NamedTextColor.GREEN));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.PAPER) {
            p.sendMessage(Component.text("You need a sheet of paper in your hand to hang a wish.", NamedTextColor.RED));
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        String text = (msg.length() > WISH_CHARS ? msg.substring(0, WISH_CHARS) : msg).replace("|", "/");
        List<String> wishes = new ArrayList<>(data.getStringList("wishes." + key(r)));
        wishes.add(ThreadLocalRandom.current().nextInt(STRIP.length) + "|" + p.getName() + "|" + text);
        while (wishes.size() > MAX_WISHES) wishes.remove(0);   // the oldest wish is taken down for the new one
        data.set("wishes." + key(r), wishes);
        save();
        spawn(r);
        r.world.playSound(r.center(), "minecraft:block.cherry_leaves.place", 0.9f, 1.1f);
        r.world.spawnParticle(org.bukkit.Particle.CHERRY_LEAVES, r.center().add(0, 1.0, 0), 14, 0.35, 0.3, 0.35, 0.02);
        p.sendMessage(Component.text("Your wish is hanging in the tree.", NamedTextColor.LIGHT_PURPLE));
        plugin.adv().grant(p, "wish_tree");
        if (tanabata()) tanabataReward(p);
    }
}
