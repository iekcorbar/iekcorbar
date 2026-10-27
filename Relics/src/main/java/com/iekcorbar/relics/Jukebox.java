package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The music props (harp, bone xylophone, music box, gramophone): right-click plays the songs as a playlist on
 * loop and pauses them again; sneak + right-click opens a menu to pick a song, shuffle the order, pause, skip
 * or turn the music off. Looping runs from the relic tick: when a song ends the next one starts, and nothing
 * plays while nobody is close. How close is "close" - the config music-radius in blocks; one music box should
 * be able to fill a whole town square, so players do not need one box on every corner. The volume sent to
 * playSound scales with it (Bukkit carries a sound 16 blocks per volume point).
 */
final class Jukebox implements Listener {
    /** The tunes of one kind of prop: sound ids (relics:&lt;prefix&gt;.&lt;id&gt;), names and lengths in ticks. */
    private record Tunes(String prefix, String[] ids, String[] names, int[] ticks, Material icon, String title) {
    }

    private static final Tunes MUSIC = new Tunes("music_box", new String[]{"jingle_bells", "deck_the_halls", "we_wish", "ghosts_of_the_samurai"},
            new String[]{"Jingle Bells", "Deck the Halls", "We Wish You a Merry Christmas", "Ghosts of the Samurai"},
            new int[]{360, 220, 200, 4160}, Material.NOTE_BLOCK, "Music Box");  // ghosts_of_the_samurai = the real disc track (4160 t = 208 s),
    // its music_box.<id> event is an alias onto the disc ogg - see tools/make_discs.py MUSIC_BOX.
    private static final Tunes HARP = new Tunes("harp", new String[]{"minstrel_boy", "londonderry_air", "molly_malone", "county_down", "fair_moved"},
            new String[]{"The Minstrel Boy", "Londonderry Air", "Molly Malone", "The Star of the County Down", "She Moved Through the Fair"},
            new int[]{300, 600, 365, 305, 360}, Material.NOTE_BLOCK, "Celtic Harp");
    private static final Tunes BONES = new Tunes("bones", new String[]{"shuffle", "waltz", "lullaby", "mountain_king"},
            new String[]{"Skeleton Shuffle", "Graveyard Waltz", "Cobweb Lullaby", "In the Hall of the Mountain King"},
            new int[]{170, 295, 230, 495}, Material.BONE, "Bone Xylophone");
    private static final Tunes GRAMOPHONE = new Tunes("gramophone", new String[]{"sakura"}, new String[]{"Sakura Sakura"}, new int[]{500},
            Material.MUSIC_DISC_CAT, "Gramophone");

    private final RelicsPlugin plugin;
    private final Relics relics;
    private final Set<Relics.Relic> playing = new HashSet<>();

    Jukebox(RelicsPlugin plugin, Relics relics) {
        this.plugin = plugin;
        this.relics = relics;
    }

    private static Tunes tunes(Relics.Relic r) {
        return switch (r.type.use) {
            case MUSIC -> MUSIC;
            case HARP -> HARP;
            case BONES -> BONES;
            default -> GRAMOPHONE;
        };
    }

    private static NamedTextColor colour(Relics.Relic r) {
        return switch (r.type.use) {
            case BONES -> NamedTextColor.GOLD;
            case HARP -> NamedTextColor.GREEN;
            default -> NamedTextColor.AQUA;
        };
    }

    // ------------------------------------------------------------ carrying distance (config music-radius)

    /** How far the music carries in blocks; a music box fills its town square, not just the room around it. */
    private float radius() {
        return Math.max(16f, plugin.getConfig().getInt("music-radius", 64));
    }

    /** Bukkit carries a playSound about 16 blocks per volume point. */
    private float volume() {
        return radius() / 16f;
    }

    // ------------------------------------------------------------ shuffle state (saved in the relic's variant field;
    // music props never use a variant model, so it is free and survives reloads)

    private static boolean shuffle(Relics.Relic r) {
        return "sh".equals(r.variant);
    }

    private void setShuffle(Relics.Relic r, boolean on) {
        r.variant = on ? "sh" : "";
        relics.save(r);
    }

    // ------------------------------------------------------------ use

    void use(Relics.Relic r, Player p) {
        long now = System.currentTimeMillis();
        if (now - r.lastUseMs < 200) return;   // one click can reach us as two events
        r.lastUseMs = now;
        Tunes t = tunes(r);
        if (p.isSneaking() && t.ids.length > 1) {
            open(r, p, t);
            return;
        }
        if (playing.contains(r)) {
            pause(r);
            p.sendActionBar(Component.text("Paused: " + t.names[r.tune] + " - right-click to play again", NamedTextColor.GRAY));
            return;
        }
        start(r, p);
    }

    /** Start (or resume) the playlist at the selected song. */
    private void start(Relics.Relic r, Player p) {
        Tunes t = tunes(r);
        r.tune = Math.floorMod(r.tune, t.ids.length);
        stopSound(r);
        playing.add(r);
        playNow(r, t);
        if (p != null) p.sendActionBar(Component.text("Now playing: " + t.names[r.tune] + (t.ids.length > 1
                ? " - playlist on loop (sneak + right-click: songs, shuffle, pause)" : " (on loop - right-click to pause)"), colour(r)));
    }

    private void playNow(Relics.Relic r, Tunes t) {
        r.world.playSound(r.center(), "relics:" + t.prefix + "." + t.ids[r.tune], SoundCategory.RECORDS, volume(), 1f);
        r.busyUntil = plugin.tick + t.ticks[r.tune];
    }

    /** Move to the next song: a random one when this relic shuffles, otherwise the next in the list. */
    private void next(Relics.Relic r, Tunes t) {
        if (t.ids.length < 2) return;
        if (shuffle(r)) {
            int n;
            do n = ThreadLocalRandom.current().nextInt(t.ids.length); while (n == r.tune);
            r.tune = n;
        } else r.tune = (r.tune + 1) % t.ids.length;
        relics.save(r);
    }

    private void pause(Relics.Relic r) {
        if (!playing.remove(r)) return;
        stopSound(r);
    }

    void stop(Relics.Relic r) {
        pause(r);
    }

    private void stopSound(Relics.Relic r) {
        Tunes t = tunes(r);
        double reach = radius() + 32;
        reach *= reach;
        for (String id : t.ids) {
            String key = "relics:" + t.prefix + "." + id;
            for (Player p : r.world.getPlayers()) if (p.getLocation().distanceSquared(r.center()) < reach) p.stopSound(key, SoundCategory.RECORDS);
        }
    }

    boolean isPlaying(Relics.Relic r) {
        return playing.contains(r);
    }

    // ------------------------------------------------------------ staff

    /** Is this prop one of the music props (music box, harp, bone xylophone, gramophone)? */
    static boolean isMusicProp(Relics.Relic r) {
        return switch (r.type.use) {
            case MUSIC, HARP, BONES, GRAMOPHONE -> true;
            default -> false;
        };
    }

    void play(Relics.Relic r) {
        if (!playing.contains(r)) start(r, null);
    }

    /** Staff: jump to the next song; a playing prop picks it up right away. */
    void skip(Relics.Relic r) {
        Tunes t = tunes(r);
        next(r, t);
        if (playing.contains(r)) playNow(r, t);
    }

    void toggleShuffle(Relics.Relic r) {
        setShuffle(r, !shuffle(r));
    }

    /** One line of state, for /relics info and /relics music list. */
    String state(Relics.Relic r) {
        Tunes t = tunes(r);
        String name = t.names[Math.floorMod(r.tune, t.ids.length)];
        return (playing.contains(r) ? "playing: " : "paused at: ") + name + (shuffle(r) ? " (shuffle on)" : " (in order)");
    }

    /** Every 10 ticks: start the next song when this one has ended and somebody is close, and show a few notes. */
    void tick(long tick) {
        double reach = radius() * 1.2;
        reach *= reach;
        for (Relics.Relic r : new ArrayList<>(playing)) {
            if (!r.world.isChunkLoaded(r.x >> 4, r.z >> 4)) continue;
            if (relics.at(r.world.getBlockAt(r.x, r.y, r.z)) != r) {   // reloaded or removed: this object is stale
                playing.remove(r);
                continue;
            }
            boolean near = false;
            for (Player p : r.world.getPlayers()) if (p.getLocation().distanceSquared(r.center()) < reach) {
                near = true;
                break;
            }
            if (!near) {
                r.busyUntil = 0;   // nobody hears it: start fresh when somebody comes back
                continue;
            }
            Tunes t = tunes(r);
            if (tick >= r.busyUntil) {
                next(r, t);        // the playlist: every song gets its turn, forever
                playNow(r, t);
            }
            if (tick % 20 == 0) r.world.spawnParticle(Particle.NOTE, r.center().add(0, 0.9, 0), 2, 0.3, 0.3, 0.3, 1);
        }
    }

    // ------------------------------------------------------------ menu

    /** Menu layout (27 slots): the songs centred in the middle row, controls in the bottom row. */
    private static final int SKIP = 18, PLAY = 20, SHUFFLE = 22, STOP = 24;

    private static final class Menu implements InventoryHolder {
        final Relics.Relic relic;
        Inventory inv;

        Menu(Relics.Relic relic) {
            this.relic = relic;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void open(Relics.Relic r, Player p, Tunes t) {
        Menu m = new Menu(r);
        m.inv = Bukkit.createInventory(m, 27, Component.text(t.title + " - playlist"));
        fill(m.inv, r, t);
        p.openInventory(m.inv);
    }

    private ItemStack icon(Material mat, String name, NamedTextColor c, boolean glow, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(name, c).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Component.text(x, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        im.lore(l);
        if (glow) im.setEnchantmentGlintOverride(true);
        it.setItemMeta(im);
        return it;
    }

    private void fill(Inventory inv, Relics.Relic r, Tunes t) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, false);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        boolean on = playing.contains(r);
        boolean shuf = shuffle(r);
        for (int i = 0; i < t.ids.length; i++) {
            boolean current = i == r.tune;
            inv.setItem(9 + i, icon(t.icon, t.names[i],
                    on && current ? NamedTextColor.GREEN : current ? NamedTextColor.YELLOW : NamedTextColor.WHITE, on && current,
                    on && current ? "Playing now" : current ? "Selected - press Play to start here" : "Click to play the playlist from this song"));
        }
        inv.setItem(SKIP, icon(Material.ARROW, "Next song", NamedTextColor.AQUA, false, "Skip to the " + (shuf ? "next random" : "next") + " song"));
        inv.setItem(PLAY, on ? icon(Material.GOLD_INGOT, "Pause", NamedTextColor.GOLD, false, "Stops here, the same song resumes on Play")
                : icon(Material.LIME_DYE, "Play", NamedTextColor.GREEN, false, "Play the playlist on loop, from the selected song"));
        inv.setItem(SHUFFLE, icon(shuf ? Material.SPECTRAL_ARROW : Material.BOW, "Shuffle: " + (shuf ? "ON" : "off"),
                shuf ? NamedTextColor.GREEN : NamedTextColor.GRAY, shuf, "Take the songs in a random order"));
        inv.setItem(STOP, icon(Material.BARRIER, "Turn off", NamedTextColor.RED, false, "Stop the music (right-clicking the prop also pauses)"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory() || !(e.getWhoClicked() instanceof Player p)) return;
        Relics.Relic r = m.relic;
        if (!relics.all().contains(r)) {
            p.closeInventory();
            return;
        }
        Tunes t = tunes(r);
        int slot = e.getSlot();
        if (slot >= 9 && slot < 9 + t.ids.length) {
            r.tune = slot - 9;
            relics.save(r);
            if (playing.contains(r)) playNow(r, t); else start(r, p);
        } else if (slot == SKIP) {
            next(r, t);
            if (playing.contains(r)) playNow(r, t);
            else p.sendActionBar(Component.text("Selected: " + t.names[r.tune], colour(r)));
        } else if (slot == PLAY) {
            if (playing.contains(r)) {
                pause(r);
                p.sendActionBar(Component.text("Paused: " + t.names[r.tune], NamedTextColor.GRAY));
            } else start(r, p);
        } else if (slot == SHUFFLE) {
            setShuffle(r, !shuffle(r));
            p.sendActionBar(Component.text("Shuffle " + (shuffle(r) ? "on - random order" : "off - play the list in order"), NamedTextColor.GRAY));
        } else if (slot == STOP) {
            pause(r);
            fill(e.getInventory(), r, t);
            p.playSound(p.getLocation(), "minecraft:ui.button.click", 0.6f, 0.8f);
            p.closeInventory();
            p.sendActionBar(Component.text("Music off.", NamedTextColor.GRAY));
            return;
        } else {
            return;
        }
        fill(e.getInventory(), r, t);
        p.playSound(p.getLocation(), "minecraft:ui.button.click", 0.6f, 1.2f);
    }
}
