package com.iekcorbar.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;









final class ItemLibrary implements Listener, TabExecutor {
    private final IekQuestsPlugin plugin;
    private final NamespacedKey idKey;
    private final File file;
    private final Map<String, ConfigurationSection> defs = new LinkedHashMap<>();
    private YamlConfiguration yaml = new YamlConfiguration();

    ItemLibrary(IekQuestsPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item");
        this.file = new File(plugin.getDataFolder(), "items.yml");
    }

    // ---------------------------------------------------------------- laden

    void load() {
        if (!file.exists()) plugin.saveResource("items.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
        defs.clear();
        var root = yaml.getConfigurationSection("items");
        if (root != null) for (String id : root.getKeys(false)) {
            var s = root.getConfigurationSection(id);
            if (s != null) defs.put(id.toLowerCase(Locale.ROOT), s);
        }
        plugin.getLogger().info("IekItems: " + defs.size() + " item(s) geladen.");
    }

    java.util.Set<String> ids() { return defs.keySet(); }

    ConfigurationSection def(String id) { return defs.get(id.toLowerCase(Locale.ROOT)); }

    
    void put(String id, String field, Object value) {
        yaml.set("items." + id.toLowerCase(Locale.ROOT) + "." + field, value);
        saveYaml();
        load();
    }

    void remove(String id) {
        yaml.set("items." + id.toLowerCase(Locale.ROOT), null);
        saveYaml();
        load();
    }

    void setEnchants(String id, List<String> lines) {
        String base = "items." + id.toLowerCase(Locale.ROOT) + ".enchants";
        yaml.set(base, null);
        for (String l : lines) {
            String[] x = l.trim().split("\s+");
            if (x[0].isEmpty()) continue;
            int lvl = 1;
            try { if (x.length > 1) lvl = Integer.parseInt(x[1]); } catch (Exception ignore) { }
            yaml.set(base + "." + x[0].toLowerCase(Locale.ROOT), lvl);
        }
        saveYaml();
        load();
    }

    void saveFromStack(String id, ItemStack st) {
        String base = "items." + id.toLowerCase(Locale.ROOT) + ".";
        yaml.set(base + "material", st.getType().name());
        var im = st.getItemMeta();
        if (im != null) {
            var ser = LegacyComponentSerializer.legacyAmpersand();
            if (im.displayName() != null) yaml.set(base + "name", ser.serialize(im.displayName()));
            if (im.lore() != null) {
                List<String> lo = new ArrayList<>();
                for (var c : im.lore()) lo.add(ser.serialize(c));
                yaml.set(base + "lore", lo);
            }
            for (var en : im.getEnchants().entrySet())
                yaml.set(base + "enchants." + en.getKey().getKey().getKey(), en.getValue());
            if (im.isUnbreakable()) yaml.set(base + "unbreakable", true);
            if (im.hasItemModel() && im.getItemModel() != null) yaml.set(base + "item-model", im.getItemModel().asString());
        }
        saveYaml();
        load();
    }

    ItemEditor editor;

    boolean exists(String id) { return defs.containsKey(id.toLowerCase(Locale.ROOT)); }

    Material materialOf(String id) {
        var def = defs.get(id.toLowerCase(Locale.ROOT));
        return def == null ? null : Material.matchMaterial(def.getString("material", "PAPER"));
    }

    
    String idOf(ItemStack st) {
        if (st == null || !st.hasItemMeta()) return null;
        return st.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    

    ItemStack build(String id, int amount) {
        var s = defs.get(id.toLowerCase(Locale.ROOT));
        if (s == null) return null;
        Material mt = Material.matchMaterial(s.getString("material", "PAPER"));
        if (mt == null) {
            plugin.getLogger().warning("IekItems: " + id + ": onbekend material " + s.getString("material"));
            mt = Material.PAPER;
        }
        var it = new ItemStack(mt, Math.max(1, amount));
        var im = it.getItemMeta();
        if (im == null) return it;
        if (s.contains("name")) im.displayName(legacy(s.getString("name")).decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String l : s.getStringList("lore")) lore.add(legacy(l).decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) im.lore(lore);

        var ench = s.getConfigurationSection("enchants");
        if (ench != null) for (String k : ench.getKeys(false)) {
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT)));
            if (e == null) { plugin.getLogger().warning("IekItems: " + id + ": onbekende enchant " + k); continue; }
            im.addEnchant(e, ench.getInt(k, 1), true);
        }
        if (s.getBoolean("glow")) im.setEnchantmentGlintOverride(true);
        if (s.getBoolean("unbreakable")) im.setUnbreakable(true);

        String model = s.getString("item-model", "");
        if (!model.isEmpty()) {
            var nk = NamespacedKey.fromString(model.toLowerCase(Locale.ROOT));
            if (nk != null) im.setItemModel(nk);
        }

        // kleur (leren armor / drankjes), schedel-textuur, model-data, stackgrootte
        String color = s.getString("color", "");
        if (!color.isEmpty()) {
            try {
                var col = org.bukkit.Color.fromRGB(Integer.parseInt(color.replace("#", ""), 16));
                if (im instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm) lm.setColor(col);
                else if (im instanceof org.bukkit.inventory.meta.PotionMeta pm) pm.setColor(col);
            } catch (Exception ex) { plugin.getLogger().warning("IekItems: " + id + ": color '" + color + "' is geen hex"); }
        }
        String tex = s.getString("texture", "");
        if (!tex.isEmpty() && im instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            var prof = org.bukkit.Bukkit.createProfile(java.util.UUID.nameUUIDFromBytes(tex.getBytes()), null);
            prof.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", tex));
            sm.setPlayerProfile(prof);
        }
        if (s.contains("custom-model-data")) im.setCustomModelData(s.getInt("custom-model-data"));
        if (s.contains("max-stack")) im.setMaxStackSize(Math.max(1, Math.min(99, s.getInt("max-stack", 64))));

        
        int n = 0;
        for (String a : s.getStringList("attributes")) {
            String[] x = a.trim().split("\\s+");
            if (x.length < 2) continue;
            try {
                var attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(x[0].toLowerCase(Locale.ROOT).replace("generic.", "")));
                if (attr == null) { plugin.getLogger().warning("IekItems: " + id + ": onbekend attribute " + x[0]); continue; }
                var op = x.length > 2 ? AttributeModifier.Operation.valueOf(x[2].toUpperCase(Locale.ROOT)) : AttributeModifier.Operation.ADD_NUMBER;
                var slot = x.length > 3 ? EquipmentSlotGroup.getByName(x[3].toLowerCase(Locale.ROOT)) : EquipmentSlotGroup.ANY;
                if (slot == null) slot = EquipmentSlotGroup.ANY;
                im.addAttributeModifier(attr, new AttributeModifier(
                        new NamespacedKey(plugin, id.toLowerCase(Locale.ROOT) + "_" + (n++)),
                        Double.parseDouble(x[1]), op, slot));
            } catch (Exception ex) {
                plugin.getLogger().warning("IekItems: " + id + ": attribute '" + a + "': " + ex.getMessage());
            }
        }
        for (String f : s.getStringList("flags")) {
            try { im.addItemFlags(ItemFlag.valueOf(f.toUpperCase(Locale.ROOT))); }
            catch (Exception ex) { plugin.getLogger().warning("IekItems: " + id + ": onbekende flag " + f); }
        }
        im.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toLowerCase(Locale.ROOT));
        it.setItemMeta(im);
        return it;
    }

    void give(Player p, String id, int amount) {
        var it = build(id, 1);
        if (it == null) return;
        int left = Math.max(1, amount);
        int max = Math.max(1, it.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            left -= n;
            var st = it.clone();
            st.setAmount(n);
            var rest = p.getInventory().addItem(st);
            for (var r : rest.values()) p.getWorld().dropItem(p.getLocation(), r);
        }
    }

    

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var st = e.getItem();
        String id = idOf(st);
        if (id == null) return;
        var s = defs.get(id);
        if (s == null) return;
        var use = s.getStringList("use");
        if (use.isEmpty()) return;
        e.setCancelled(true);
        var p = e.getPlayer();
        long now = System.currentTimeMillis();
        long cd = (long) (s.getDouble("use-cooldown", 1.0) * 1000);
        Long last = lastUse.get(p.getUniqueId().toString() + id);
        if (last != null && now - last < cd) return;
        lastUse.put(p.getUniqueId().toString() + id, now);
        String perm = s.getString("use-permission", "");
        if (!perm.isEmpty() && !p.hasPermission(perm)) {
            p.sendMessage(legacy(s.getString("use-denied", "&cYou can't use that.")));
            return;
        }
        if (s.getBoolean("consume")) st.setAmount(st.getAmount() - 1);
        String snd = s.getString("use-sound", "");
        if (!snd.isEmpty()) p.playSound(p.getLocation(), snd, org.bukkit.SoundCategory.PLAYERS, 1f, 1f);
        String umsg = s.getString("use-message", "");
        if (!umsg.isEmpty()) p.sendMessage(legacy(umsg));
        plugin.runItemActions(p, use);
    }

    private final Map<String, Long> lastUse = new java.util.HashMap<>();

    
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        var it = e.getDrops().iterator();
        while (it.hasNext()) {
            var st = it.next();
            String id = idOf(st);
            var d = id == null ? null : defs.get(id);
            if (d != null && d.getBoolean("soulbound")) { e.getItemsToKeep().add(st); it.remove(); }
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        String id = idOf(e.getItemDrop().getItemStack());
        var d = id == null ? null : defs.get(id);
        if (d != null && d.getBoolean("undroppable")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(legacy("&7You can't drop that."));
        }
    }

    // ---------------------------------------------------------------- /iekitem

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("iekquests.items") && !sender.hasPermission("iekquests.admin")) {
            sender.sendMessage(Component.text("IekItems is for staff only."));
            return true;
        }
        String sub = a.length == 0 ? (sender instanceof Player ? "browse" : "help") : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "edit", "browse", "gui" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(legacy("&cPlayers only.")); return true; }
                if (a.length >= 2 && exists(a[1])) editor.edit(p, a[1].toLowerCase(Locale.ROOT));
                else editor.open(p);
            }
            case "list" -> {
                sender.sendMessage(legacy("&6IekItems (" + defs.size() + "): &7" + String.join(", ", defs.keySet())));
            }
            case "reload" -> { load(); sender.sendMessage(legacy("&aIekItems reloaded: " + defs.size() + " item(s).")); }
            case "give" -> {
                if (a.length < 3) { sender.sendMessage(legacy("&cUsage: /" + label + " give <player> <id> [amount]")); return true; }
                var p = org.bukkit.Bukkit.getPlayerExact(a[1]);
                if (p == null) { sender.sendMessage(legacy("&cPlayer not online.")); return true; }
                if (!exists(a[2])) { sender.sendMessage(legacy("&cUnknown item: " + a[2])); return true; }
                int n = a.length > 3 ? parse(a[3]) : 1;
                give(p, a[2], n);
                sender.sendMessage(legacy("&aGave " + n + "x " + a[2] + " to " + p.getName() + "."));
            }
            case "get" -> {
                if (!(sender instanceof Player p) || a.length < 2) { sender.sendMessage(legacy("&cUsage (player): /" + label + " get <id> [amount]")); return true; }
                if (!exists(a[1])) { sender.sendMessage(legacy("&cUnknown item: " + a[1])); return true; }
                give(p, a[1], a.length > 2 ? parse(a[2]) : 1);
            }
            case "create" -> {
                if (!(sender instanceof Player p) || a.length < 2) {
                    sender.sendMessage(legacy("&cHold the item and type: /" + label + " create <id>")); return true;
                }
                create(p, a[1]);
            }
            case "delete" -> {
                if (a.length < 2 || !exists(a[1])) { sender.sendMessage(legacy("&cUnknown item.")); return true; }
                remove(a[1]);
                sender.sendMessage(legacy("&aDeleted " + a[1] + " from items.yml."));
            }
            case "info" -> {
                if (!(sender instanceof Player p)) return true;
                String id = idOf(p.getInventory().getItemInMainHand());
                sender.sendMessage(legacy(id == null ? "&7That is not an IekItem." : "&6IekItem: &f" + id
                        + (exists(id) ? "" : " &c(no longer in items.yml)")));
            }
            default -> {
                sender.sendMessage(legacy("&6/" + label + " &7(no args = editor) edit [id] | list | get <id> [n] | give <player> <id> [n] | create <id> | delete <id> | info | reload"));
                sender.sendMessage(legacy("&7Quests: reward &fitem <id> [n]&7, deliver/craft with material &fitem:<id>&7."));
            }
        }
        return true;
    }

    
    private void create(Player p, String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        var st = p.getInventory().getItemInMainHand();
        if (id.isEmpty() || st.getType() == Material.AIR) {
            p.sendMessage(legacy("&cHold an item in your main hand and use a simple id (a-z, 0-9, _)."));
            return;
        }
        saveFromStack(id, st);
        p.sendMessage(legacy("&aSaved &f" + id + "&a. Open the editor with &f/iekitem edit " + id + "&a."));
    }

    void saveYaml() {
        try { yaml.save(file); } catch (Exception e) { plugin.getLogger().warning("items.yml: " + e.getMessage()); }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("edit", "list", "get", "give", "create", "delete", "info", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if ((a.length == 2 && List.of("get", "delete", "edit").contains(a[0].toLowerCase())) || (a.length == 3 && a[0].equalsIgnoreCase("give"))) out.addAll(defs.keySet());
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private static int parse(String s) {
        try { return Math.max(1, Integer.parseInt(s)); } catch (Exception e) { return 1; }
    }

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s);
    }
}
