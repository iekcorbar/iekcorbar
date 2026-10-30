package com.iekcorbar.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IekQuests - de eigen quest-engine van iekcorbar (30-10).
 *
 * Alles zit in quests.yml: gespreksregels, doelen (praten / X craften / X afleveren /
 * naar een plek lopen), eenmalige beloningen (money|eco|title|sound|commando) en
 * herhaalbare contracten met eigen cooldown. De speler opent alles via /questbook of
 * door een gekoppelde Citizens-NPC aan te klikken (npc-id in de config). NPC-praten
 * speelt optioneel een stemgeluid uit het eigen pack (voice: steamworks:milo.lineXX).
 *
 * Progressie-per-speler: state 0 = niet gestart, 1..N = actieve stage, N+1 = klaar.
 * Data blijft klein (data.yml), periodiek + immediate opgeslagen.
 *
 * BetonQuest-quotes (greyarts) blijven gewoon draaien zolang BQ op de server staat;
 * dit systeem is de opvolger en heeft NOTHING van BQ nodig - alleen CMI/Vault
 * (via console-commando's) en EliteMobs voor de eco-regels.
 */
public final class IekQuestsPlugin extends JavaPlugin implements Listener {

    // ---------------------------------------------------------------- model

    static final class Stage {
        String kind = "talk";          // talk|craft|deliver|walk
        String material = "PAPER";
        int amount = 1;
        Location loc;
        double radius = 6;
        String label = "&aVerder";
        List<String> lines = new ArrayList<>();
        String voice = "";             // bv. steamworks:milo.line01 (leeg = knopklik-geluid)
    }

    static final class Repeat {
        String id;
        String material = "PUMPKIN";
        int amount = 2;
        long cooldown = 600;
        int money = 100;
        int eco = 2;
        String label = "&aInleveren";
        String voice = "";
    }

    static final class Quest {
        String id = "", title = "&fQuest", subtitle = "", icon = "BOOK";
        int npcId = -1;                // Citizens-id; -1 = alleen menu, geen NPC-klik
        int order = 0;
        List<Stage> stages = new ArrayList<>();
        List<Repeat> repeats = new ArrayList<>();
        List<String> rewards = new ArrayList<>();
    }

    static final class Prog {
        int state = 0;
        Map<String, Long> cd = new HashMap<>();   // repeat-id -> unix-secs deadline
    }

    /** Herkenbare GUI's: slot -> commando-tag. */
    static final class Holder implements InventoryHolder {
        final UUID who;
        final String quest;            // "" = hoofdmenu
        int page;
        Holder(UUID who, String quest) { this.who = who; this.quest = quest == null ? "" : quest; }
        @Override public Inventory getInventory() { return null; }
    }

    private final List<Quest> quests = new ArrayList<>();
    private final Map<UUID, Map<String, Prog>> prog = new HashMap<>();
    private YamlConfiguration data;
    private NamespacedKey tagKey;

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onEnable() {
        tagKey = new NamespacedKey(this, "tag");
        if (!new File(getDataFolder(), "quests.yml").exists()) saveResource("quests.yml", false);
        Bukkit.getPluginManager().registerEvents(this, this);
        var cmd = Bukkit.getPluginCommand("questbook");
        if (cmd != null) cmd.setExecutor((sender, command, label, args) -> {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("iekquests.admin")) {
                load();
                sender.sendMessage(Component.text("IekQuests: " + quests.size() + " quest(s) herladen."));
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("iekquests.admin")) {
                var pl = Bukkit.getPlayerExact(args[1]);
                UUID u = pl != null ? pl.getUniqueId() : UUID.fromString(args[1]);
                var mm = prog.get(u);
                if (mm != null) {
                    if (args.length >= 3) mm.remove(args[2]); else mm.clear();
                    save();
                    sender.sendMessage(Component.text("Progressie reset voor " + args[1]));
                }
                return true;
            }
            if (sender instanceof Player p) openMenu(p);
            else sender.sendMessage(Component.text("questbook: in-console alleen reload/reset"));
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(this, this::refreshOpenGuis, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 600L, 600L);
        load();
        getLogger().info("IekQuests " + getDescription().getVersion() + " klaar - " + quests.size() + " quest(s).");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ---------------------------------------------------------------- load + save

    private void load() {
        quests.clear();
        var qc = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "quests.yml"));
        var sec = qc.getConfigurationSection("quests");
        if (sec != null) for (String id : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(id);
            if (s == null) continue;
            Quest q = new Quest();
            q.id = id;
            q.title = s.getString("title", id);
            q.subtitle = s.getString("subtitle", "");
            q.icon = s.getString("icon", "BOOK");
            q.npcId = s.getInt("npc-id", -1);
            q.order = s.getInt("order", 0);
            for (var r : s.getStringList("rewards")) q.rewards.add(r);
            var st = s.getConfigurationSection("stages");
            if (st != null) for (String k : st.getKeys(true)) {
                var v = st.getConfigurationSection(k);
                if (v == null) continue;
                Stage g = new Stage();
                g.kind = v.getString("type", "talk");
                g.material = v.getString("material", "PAPER").toUpperCase();
                g.amount = v.getInt("amount", 1);
                g.radius = v.getDouble("radius", 6);
                g.label = v.getString("label", "&aVerder");
                g.voice = v.getString("voice", "");
                g.lines.addAll(v.getStringList("lines"));
                String locStr = v.getString("location", "");
                if (!locStr.isEmpty()) {
                    g.loc = parseLoc(locStr);
                    if (g.loc == null) getLogger().warning("quest " + id + " stage " + k + ": slechte location '" + locStr + "'");
                }
                q.stages.add(g);
            }
            var rp = s.getConfigurationSection("repeat");
            if (rp != null) for (String k : rp.getKeys(false)) {
                var v = rp.getConfigurationSection(k);
                if (v == null) continue;
                Repeat r = new Repeat();
                r.id = k;
                r.material = v.getString("material", "PUMPKIN").toUpperCase();
                r.amount = v.getInt("amount", 2);
                r.cooldown = v.getLong("cooldown", 600);
                r.money = v.getInt("money", 0);
                r.eco = v.getInt("eco", 0);
                r.label = v.getString("label", "&aLever " + r.amount + " in");
                r.voice = v.getString("voice", "");
                q.repeats.add(r);
            }
            quests.add(q);
        }
        quests.sort((a, b) -> Integer.compare(a.order, b.order));

        data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));
        prog.clear();
        var ps = data.getConfigurationSection("players");
        if (ps != null) for (String uuid : ps.getKeys(false)) {
            Map<String, Prog> m = new HashMap<>();
            var qs = ps.getConfigurationSection(uuid + ".quests");
            if (qs != null) for (String qid : qs.getKeys(false)) {
                Prog pr = new Prog();
                pr.state = qs.getInt(qid + ".state");
                var cds = qs.getConfigurationSection(qid + ".cd");
                if (cds != null) for (String k : cds.getKeys(false)) pr.cd.put(k, cds.getLong(k));
                m.put(qid, pr);
            }
            try { prog.put(UUID.fromString(uuid), m); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void save() { saveData(); }

    private void saveData() {
        if (data == null) return;
        data.set("players", null);
        var ps = data.createSection("players");
        for (var e : prog.entrySet()) {
            var sec = ps.createSection(e.getKey().toString() + ".quests");
            for (var qe : e.getValue().entrySet()) {
                sec.set(qe.getKey() + ".state", qe.getValue().state);
                for (var ce : qe.getValue().cd.entrySet()) sec.set(qe.getKey() + ".cd." + ce.getKey(), ce.getValue());
            }
        }
        try {
            data.save(new File(getDataFolder(), "data.yml"));
        } catch (Exception ex) {
            getLogger().warning("data.yml niet opgeslagen: " + ex.getMessage());
        }
    }

    private Prog pr(Player p, Quest q) {
        return prog.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).computeIfAbsent(q.id, k -> new Prog());
    }

    private Quest byId(String id) {
        for (var q : quests) if (q.id.equals(id)) return q;
        return null;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        int id = citizensId(e.getRightClicked());
        if (id < 0) return;
        for (var q : quests) {
            if (q.npcId == id) {
                e.setCancelled(true);
                openQuest(e.getPlayer(), q);
                return;
            }
        }
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onCraft(CraftItemEvent e) {
        var result = e.getInventory().getResult();
        if (result == null) return;
        Player p = (Player) e.getWhoClicked();
        var m = prog.get(p.getUniqueId());
        if (m == null) return;
        for (var q : quests) {
            Prog pr = m.get(q.id);
            if (pr == null || pr.state < 1 || pr.state > q.stages.size()) continue;
            Stage s = q.stages.get(pr.state - 1);
            if (!s.kind.equals("craft") || !s.material.equals(result.getType().name())) continue;
            Bukkit.getScheduler().runTask(this, () -> {
                if (held(p, s.material) >= s.amount) advance(p, q, pr);
            });
            return;
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        var to = e.getTo();
        if (to == null) return;
        if (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockY() == to.getBlockY()
                && e.getFrom().getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        var m = prog.get(p.getUniqueId());
        if (m == null) return;
        for (var q : quests) {
            Prog pr = m.get(q.id);
            if (pr == null || pr.state < 1 || pr.state > q.stages.size()) continue;
            Stage s = q.stages.get(pr.state - 1);
            if (!s.kind.equals("walk") || s.loc == null || !s.loc.getWorld().equals(p.getWorld())) continue;
            if (p.getLocation().distanceSquared(s.loc) <= s.radius * s.radius) advance(p, q, pr);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prog.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        var holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Holder h) || !h.who.equals(p.getUniqueId())) return;
        event.setCancelled(true);
        var cur = event.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        String tag = loreTag(cur);
        if (tag == null) return;
        if (h.quest.isEmpty()) {
            if (tag.startsWith("open:")) {
                var q = byId(tag.substring(5));
                if (q != null) openQuest(p, q);
            }
            return;
        }
        var q = byId(h.quest);
        if (q == null) return;
        Prog pr = pr(p, q);
        switch (tag) {
            case "back" -> openMenu(p);
            case "start" -> { pr.state = 1; refresh(p, q); save(); }
            default -> {
                if (tag.equals("act")) {
                    int st = pr.state;
                    if (st < 1 || st > q.stages.size()) return;
                    Stage s = q.stages.get(st - 1);
                    boolean ok = switch (s.kind) {
                        case "talk" -> true;
                        case "walk" -> s.loc != null && p.getWorld().equals(s.loc.getWorld())
                                && p.getLocation().distanceSquared(s.loc) <= s.radius * s.radius;
                        default -> held(p, s.material) >= s.amount;
                    };
                    if (ok) {
                        if (s.kind.equals("deliver")) take(p, s.material, s.amount);
                        advance(p, q, pr);
                    } else {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
                    }
                } else if (tag.startsWith("repeat:")) {
                    doRepeat(p, q, pr, tag.substring(7));
                } else if (tag.startsWith("compass:")) {
                    String[] a = tag.substring(8).split(";");
                    var w = Bukkit.getWorld(a[0]);
                    if (w != null) setCompass(p, new Location(w,
                            Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])));
                }
            }
        }
    }

    /** Cooldown-tellers in open GUI's live verversen. */
    private void refreshOpenGuis() {
        for (var p : Bukkit.getOnlinePlayers()) {
            var holder = p.getOpenInventory().getTopInventory().getHolder();
            if (!(holder instanceof Holder h)) continue;
            var m = prog.get(p.getUniqueId());
            if (m == null) continue;
            if (h.quest.isEmpty()) {
                boolean dirty = false;
                for (var q : quests) {
                    var pr = m.get(q.id);
                    if (pr == null) continue;
                    for (var r : q.repeats) if (pr.cd.getOrDefault(r.id, 0L) <= now()) dirty = true;
                }
                if (dirty) openMenu(p);
            } else {
                var q = byId(h.quest);
                if (q != null) openQuest(p, q);
            }
        }
    }

    private long now() { return System.currentTimeMillis() / 1000L; }

    // ---------------------------------------------------------------- stage flow

    private void advance(Player p, Quest q, Prog pr) {
        pr.state = Math.min(pr.state + 1, q.stages.size() + 1);
        if (pr.state == q.stages.size() + 1 && !q.rewards.isEmpty()) {
            runRewards(p, q.rewards);
        } else {
            var s = q.stages.get(Math.min(pr.state, q.stages.size()) - 1);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.3f);
            if (s != null) speak(p, s.voice);
        }
        refresh(p, q);
        save();
    }

    private void doRepeat(Player p, Quest q, Prog pr, String id) {
        Repeat r = null;
        for (var x : q.repeats) if (x.id.equals(id)) r = x;
        if (r == null) return;
        Long cd = pr.cd.get(r.id);
        if (cd != null && cd > now()) {
            p.sendMessage(legacy("&7Milo's papier moet nog even rusten - nog " + fmt(cd - now()) + " te gaan."));
            return;
        }
        if (held(p, r.material) < r.amount) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
            return;
        }
        take(p, r.material, r.amount);
        if (r.money > 0) dispatch(p, "cmi money give " + p.getName() + " -s " + r.money);
        if (r.eco > 0) dispatch(p, "em money add " + p.getName() + " " + r.eco);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.9f, 1.4f);
        speak(p, r.voice);
        pr.cd.put(r.id, now() + r.cooldown);
        refresh(p, q);
        save();
    }

    private void runRewards(Player p, List<String> rewards) {
        for (String raw : rewards) {
            var r = raw.trim();
            if (r.isEmpty()) continue;
            r = r.replace("%player%", p.getName());
            int sp = r.indexOf(' ');
            String key = sp < 0 ? r : r.substring(0, sp);
            String val = sp < 0 ? "" : r.substring(sp + 1);
            switch (key) {
                case "money" -> dispatch(p, "cmi money give " + p.getName() + " -s " + val);
                case "eco" -> dispatch(p, "em money add " + p.getName() + " " + val);
                case "sound" -> {
                    var snd = sound(val);
                    p.playSound(p.getLocation(), snd == null ? Sound.BLOCK_NOTE_BLOCK_PLING : snd, SoundCategory.PLAYERS, 0.8f, 1f);
                }
                case "title" -> {
                    String[] ab = val.split("\\|");
                    p.showTitle(Title.title(legacy(ab[0]), ab.length > 1 ? legacy(ab[1]) : Component.empty()));
                }
                case "say" -> p.sendMessage(legacy(val));
                default -> dispatch(p, r);
            }
        }
    }

    private void dispatch(Player p, String consoleCmd) {
        Bukkit.getScheduler().runTask(this,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd));
    }

    private void speak(Player p, String key) {
        if (key == null || key.isEmpty()) return;
        var s = sound(key);
        if (s != null) p.playSound(p.getLocation(), s, SoundCategory.RECORDS, 0.9f, 1f);
    }

    private Sound sound(String key) {
        try {
            NamespacedKey k = key.contains(":") ? NamespacedKey.fromString(key) : new NamespacedKey("steamworks", key);
            return k == null ? null : Registry.SOUNDS.get(k);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers: inventaris

    private int held(Player p, String material) {
        var mt = Material.matchMaterial(material);
        if (mt == null) return 0;
        return p.getInventory().all(mt).values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    private void take(Player p, String material, int amount) {
        var mt = Material.matchMaterial(material);
        if (mt == null) return;
        int left = amount;
        var content = p.getInventory().getStorageContents();
        for (int i = 0; i < content.length && left > 0; i++) {
            var it = content[i];
            if (it == null || it.getType() != mt) continue;
            int use = Math.min(it.getAmount(), left);
            left -= use;
            if (use >= it.getAmount()) p.getInventory().setItem(i, null);
            else { it.setAmount(it.getAmount() - use); p.getInventory().setItem(i, it); }
        }
    }

    // ---------------------------------------------------------------- GUI's

    private void openMenu(Player p) {
        int size = Math.max(27, ((quests.size() / 9) + 1) * 9);
        var inv = Bukkit.createInventory(new Holder(p.getUniqueId(), ""), size, legacy("&6&l⚒ Questboek"));
        int slot = 10;
        for (var q : quests) {
            if (q.stages.isEmpty()) continue;
            List<String> lo = new ArrayList<>();
            if (!q.subtitle.isEmpty()) lo.add("&8" + q.subtitle);
            lo.add("   " + statusLine(q, pr(p, q)));
            var it = item(q.icon, q.title, lo);
            setTag(it, "open:" + q.id);
            inv.setItem(slot, it);
            slot++;
        }
        p.openInventory(inv);
    }

    private void openQuest(Player p, Quest q) {
        Prog pr = pr(p, q);
        int n = q.stages.size();
        var inv = Bukkit.createInventory(new Holder(p.getUniqueId(), q.id), 45, legacy(q.title));

        // midden: boek met gesprekstekst van nu
        List<String> lines = new ArrayList<>();
        if (pr.state == 0) lines.add("&eKlik hieronder om te beginnen.");
        else if (pr.state <= n) lines.addAll(q.stages.get(pr.state - 1).lines);
        else lines.add("&aDe quest is voltooid!");
        if (!q.subtitle.isEmpty()) lines.add(0, "&8" + q.subtitle);
        var book = item(q.icon, q.title, lines);
        inv.setItem(13, book);

        // onder: acties
        if (pr.state == 0) {
            inv.setItem(31, tagged(Material.LIME_DYE, "&a✔ Quest starten", "start"));
        } else if (pr.state <= n) {
            var s = q.stages.get(pr.state - 1);
            inv.setItem(31, tagged(Material.PAPER, labelFor(p, s), "act"));
            if ((s.kind.equals("walk")) && s.loc != null)
                inv.setItem(33, tagged(Material.COMPASS, "&6Wijst " + s.loc.getWorld().getName()
                        + " " + s.loc.getBlockX() + ", " + s.loc.getBlockZ(),
                        "compass:" + s.loc.getWorld().getName() + ";" + s.loc.getBlockX()
                                + ";" + s.loc.getBlockY() + ";" + s.loc.getBlockZ()));
        } else if (!q.repeats.isEmpty()) {
            int slot = 29;
            for (var r : q.repeats) {
                Long cd = pr.cd.get(r.id);
                boolean ready = cd == null || cd <= now();
                String lab = (ready ? "&a" : "&7") + r.label + " (" + held(p, r.material) + "/" + r.amount + ")"
                        + (ready ? "" : " &7nog " + fmt(cd - now()));
                if (slot < 35) { inv.setItem(slot, tagged(Material.LIME_DYE, lab, "repeat:" + r.id)); slot++; }
            }
        }
        inv.setItem(40, tagged(Material.BARRIER, "&cTerug", "back"));
        p.openInventory(inv);
    }

    /** Hernoemt de hoofd-GUI van de speler na elke voortgang zonder close/open-flits. */
    private void refresh(Player p, Quest q) {
        var holder = p.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof Holder h && h.quest.equals(q.id)) {
            openQuest(p, q);
            return;
        }
        var h = p.getOpenInventory().getTopInventory().getHolder();
        if (h instanceof Holder) openQuest(p, q);   // menu staat open? quest openen is prima feedback
    }

    private String labelFor(Player p, Stage s) {
        switch (s.kind) {
            case "craft": return "&e" + s.label + " &8(" + held(p, s.material) + "/" + s.amount + ")";
            case "deliver": return "&a" + s.label + " &8(" + held(p, s.material) + "/" + s.amount + ")";
            case "walk": return "&e" + s.label + " &8[" + (s.loc == null ? "?" : s.loc.getBlockX() + "," + s.loc.getBlockZ()) + "]";
            default: return "&a" + s.label;
        }
    }

    private String statusLine(Quest q, Prog pr) {
        int n = q.stages.size();
        if (pr.state == 0) return "&7Nog niet gestart";
        if (pr.state <= n) return "&eStage " + pr.state + "/" + n;
        return "&a✔ Voltooid";
    }

    private ItemStack item(String material, String name, List<String> lore) {
        var mt = Material.matchMaterial(material);
        var it = new ItemStack(mt == null ? Material.BOOK : mt);
        var im = it.getItemMeta();
        im.displayName(legacy(name));
        List<Component> lo = new ArrayList<>();
        for (var l : lore) lo.add(legacy(l));
        im.lore(lo);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack tagged(Material m, String name, String tag) {
        var it = new ItemStack(m);
        var im = it.getItemMeta();
        im.displayName(legacy(name));
        im.getPersistentDataContainer().set(tagKey, org.bukkit.persistence.PersistentDataType.STRING, tag);
        it.setItemMeta(im);
        return it;
    }

    private void setTag(ItemStack it, String tag) {
        var im = it.getItemMeta();
        if (im == null) return;
        im.getPersistentDataContainer().set(tagKey, org.bukkit.persistence.PersistentDataType.STRING, tag);
        it.setItemMeta(im);
    }

    private String loreTag(ItemStack it) {
        var im = it.getItemMeta();
        if (im == null) return null;
        return im.getPersistentDataContainer().get(tagKey, org.bukkit.persistence.PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- misc

    /** Citizens id van een entity via reflectie; -1 als het er geen is of Citizens ontbreekt. */
    private Method mRegistry, mGetNpc, mId;
    private boolean citizensBroken;

    private int citizensId(Entity ent) {
        if (citizensBroken) return -1;
        try {
            if (mRegistry == null) {
                Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
                mRegistry = api.getMethod("getNPCRegistry");
            }
            Object registry = mRegistry.invoke(null);
            if (mGetNpc == null) mGetNpc = registry.getClass().getMethod("getNPC", Entity.class);
            Object npc = mGetNpc.invoke(registry, ent);
            if (npc == null) return -1;
            if (mId == null) mId = npc.getClass().getMethod("getId");
            return (int) mId.invoke(npc);
        } catch (Exception e) {
            citizensBroken = true;
            getLogger().warning("Citizens niet bereikbaar - NPC-klikken werken alleen via het menu: " + e.getMessage());
            return -1;
        }
    }

    /** MetaCompass (Paper): wijs de client-compass naar een plek. Reflectief voor zekerheid. */
    private void setCompass(Player p, Location loc) {
        try {
            var mc = p.getClass().getMethod("getMetaCompass").invoke(p);
            mc.getClass().getMethod("setLocation", Location.class).invoke(mc, loc);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.8f, 1.6f);
        } catch (Exception e) {
            p.sendMessage(legacy("&7De compass-modus is op deze server niet beschikbaar."));
        }
    }

    private Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s);
    }

    private Location parseLoc(String s) {
        String[] a = s.split(";");
        if (a.length < 4) return null;
        var w = Bukkit.getWorld(a[3].trim());
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String fmt(long secs) {
        if (secs <= 0) return "";
        long m = secs / 60, s = secs % 60;
        return m > 0 ? m + "m" + (s > 0 ? " " + s + "s" : "") : s + "s";
    }
}
