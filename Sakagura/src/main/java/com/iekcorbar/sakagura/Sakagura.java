package com.iekcorbar.sakagura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sakagura: brewing and a fruit garden in one plugin (replaces the old Brewery plugin).
 * Cauldron -> (distill in a brewing stand) -> (age in a sake cask) -> a drink whose quality
 * depends on how well the recipe was followed. Recipes in recipes.yml (the ones from
 * iekcorbar.com/brewing plus our own Japanese/steampunk drinks). Drunkenness like Brewery.
 * Garden crops are structure-void markers with their type and stage in chunk data (see Fruit,
 * Garden); the plant you see is an ItemDisplay, like Kitchen's crops.
 */
public final class Sakagura extends JavaPlugin implements TabExecutor {
    static final class ProbeBreakEvent extends BlockBreakEvent {
        ProbeBreakEvent(Block block, Player player) {
            super(block, player);
        }
    }

    long tick;
    private Recipes recipes;
    private Goods goods;
    private Cauldrons cauldrons;
    private Stills stills;
    private Casks casks;
    private Drunk drunk;
    private Garden garden;
    private GardenDecor gardenDecor;
    private Adv adv;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "recipes.yml").exists()) saveResource("recipes.yml", false);
        goods = new Goods(this);
        recipes = new Recipes(this);
        recipes.load();
        cauldrons = new Cauldrons(this);
        stills = new Stills(this);
        casks = new Casks(this);
        drunk = new Drunk(this);
        garden = new Garden(this);
        gardenDecor = new GardenDecor(this);
        var pm = getServer().getPluginManager();
        for (var l : new org.bukkit.event.Listener[]{goods, cauldrons, stills, casks, drunk, garden}) pm.registerEvents(l, this);
        pm.registerEvents(gardenDecor.decor, this);
        // items sold by wandering traders (WanderingTrades hands out a plain paper, see TraderSwap)
        pm.registerEvents(new TraderSwap(this, "Sakagura", name -> {
            for (Fruit f : Fruit.values()) {
                if (f.seed != null && Goods.label(f.seed).equals(name)) return goods.item(f.seed, 1);
                if (f.displayName.equals(name)) return goods.item(f.produce, 1);
            }
            return null;
        }), this);
        goods.registerRecipes();
        gardenDecor.decor.registerRecipes();
        adv = Achievements.create(this);
        adv.start();
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this), this, org.bukkit.plugin.ServicePriority.Normal); // recipe viewer (Zukan)
        getCommand("brew").setExecutor(this);
        getCommand("brew").setTabCompleter(this);
        getServer().getScheduler().runTask(this, () -> {
            for (World w : getServer().getWorlds())
                for (Chunk c : w.getLoadedChunks()) {
                    cauldrons.load(c);
                    casks.loadChunk(c);
                    stills.loadChunk(c);
                    garden.loadChunk(c);
                    gardenDecor.decor.loadChunk(c);
                }
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick++;
            cauldrons.tick(tick);
            stills.tick();
            drunk.tick();
            garden.tick();
            if (tick % 30 == 0) {
                casks.relight();
                stills.relight();
            }
        }, 20L, 20L);
        long[] decorTick = {0};
        getServer().getScheduler().runTaskTimer(this, () -> gardenDecor.decor.tick(decorTick[0] += 10), 20L, 10L);
        getLogger().info("Sakagura enabled. Guide: " + getConfig().getString("docs-url"));
    }

    @Override
    public void onDisable() {
        if (cauldrons == null) return;
        cauldrons.saveAll();
        garden.saveAll();
        if (!getServer().isStopping()) {
            casks.removeAll();
            stills.removeAll();
            garden.removeAllDisplays();
            gardenDecor.decor.removeAllDisplays();
            gardenDecor.decor.unregisterRecipes();
        }
    }

    boolean canBreak(Player p, Block b) {
        ProbeBreakEvent ev = new ProbeBreakEvent(b, p);
        getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    Recipes recipes() { return recipes; }
    Goods goods() { return goods; }
    Stills stills() { return stills; }
    Adv adv() { return adv; }
    Drunk drunk() { return drunk; }
    GardenDecor gardenDecor() { return gardenDecor; }

    // ------------------------------------------------------------ /brew

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("sakagura.admin");
        switch (sub) {
            case "recipes" -> {
                StringBuilder sb = new StringBuilder();
                for (Recipe r : recipes.all()) sb.append(sb.isEmpty() ? "" : ", ").append(r.name(5));
                s.sendMessage(Component.text("Drinks: ", NamedTextColor.GOLD).append(Component.text(sb.toString(), NamedTextColor.GRAY)));
            }
            case "giveall" -> {
                if (!admin) return deny(s);
                Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player self ? self : null;
                if (target == null) {
                    s.sendMessage(Component.text("/" + label + " giveall [player]", NamedTextColor.RED));
                    return true;
                }
                int n = GiveAll.give(target, new ZukanData(this), "Sakagura");
                s.sendMessage(Component.text("Gave " + n + " Sakagura items to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (!admin) return deny(s);
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null || a.length < 3) {
                    s.sendMessage(Component.text("/" + label + " give <player> <drink|item|cask_<wood>> [quality 1-10 | amount]", NamedTextColor.RED));
                    return true;
                }
                ItemStack it = null;
                Recipe r = recipes.get(a[2].toLowerCase(Locale.ROOT));
                if (r != null) it = goods.bottle(perfect(r, a.length > 3 ? Integer.parseInt(a[3]) : 10), 1);
                else if (a[2].startsWith("cask_") && Woods.byId(a[2].substring(5)) != null) it = goods.cask(Woods.byId(a[2].substring(5)), 1);
                else if (Fruit.byId(a[2]) != null || Fruit.byPlantItem(a[2]) != null) it = goods.item(a[2], a.length > 3 ? Integer.parseInt(a[3]) : 1);
                if (it == null) {
                    s.sendMessage(Component.text("Unknown drink/item - see /" + label + " recipes", NamedTextColor.RED));
                    return true;
                }
                t.getInventory().addItem(it);
                s.sendMessage(Component.text("Gave " + a[2] + " to " + t.getName(), NamedTextColor.GREEN));
            }
            case "take" -> {
                if (!admin) return deny(s);
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null || a.length < 3) {
                    s.sendMessage(Component.text("/" + label + " take <player> <drink|item|cask_<wood>> [amount, all when left out]", NamedTextColor.RED));
                    return true;
                }
                String want = a[2].toLowerCase(Locale.ROOT);
                Recipe r = recipes.get(want);
                Woods wood = want.startsWith("cask_") ? Woods.byId(want.substring(5)) : null;
                Fruit f = Fruit.byId(want);
                if (r == null && wood == null && f == null) {
                    s.sendMessage(Component.text("Unknown drink/item - see /" + label + " recipes", NamedTextColor.RED));
                    return true;
                }
                int max = Integer.MAX_VALUE;
                try {
                    if (a.length > 3) max = Math.max(1, Integer.parseInt(a[3]));
                } catch (NumberFormatException ignored) { }
                int taken = 0;
                ItemStack[] slots = t.getInventory().getStorageContents();
                for (int i = 0; i < slots.length && taken < max; i++) {
                    ItemStack st = slots[i];
                    if (st == null || st.isEmpty()) continue;
                    boolean match;
                    if (r != null) {
                        Brew b = goods.brewOf(st);
                        Recipes.Match m = b == null ? null : recipes.match(b);
                        match = m != null && m.recipe().slug.equals(r.slug);
                    } else if (wood != null) match = goods.caskWood(st) == wood;
                    else {
                        String id = goods.idOf(st);
                        match = id != null && f != null && (id.equals(f.produce) || id.equals(f.plantItem()));
                    }
                    if (!match) continue;
                    int use = Math.min(st.getAmount(), max - taken);
                    taken += use;
                    if (use >= st.getAmount()) slots[i] = null;
                    else {
                        st.setAmount(st.getAmount() - use);
                        slots[i] = st;
                    }
                }
                if (taken > 0) t.getInventory().setStorageContents(slots);
                s.sendMessage(Component.text(taken == 0 ? t.getName() + " carries no " + a[2] + "."
                        : "Took " + taken + " x " + a[2] + " from " + t.getName(), taken == 0 ? NamedTextColor.RED : NamedTextColor.GOLD));
            }
            case "drunk" -> {
                Player t = a.length > 1 && admin ? Bukkit.getPlayerExact(a[1]) : s instanceof Player p ? p : null;
                if (t == null) return true;
                if (admin && a.length > 2) drunk.set(t, Double.parseDouble(a[2]));
                s.sendMessage(Component.text(t.getName() + " is " + Math.round(drunk.level(t)) + "% drunk.", NamedTextColor.GOLD));
            }
            case "reload" -> {
                if (!admin) return deny(s);
                reloadConfig();
                recipes.load();
                drunk.load();
                s.sendMessage(Component.text("Sakagura reloaded (" + recipes.all().size() + " recipes).", NamedTextColor.GREEN));
            }
            default -> {
                String url = getConfig().getString("docs-url", "https://iekcorbar.com/wiki?page=sakagura");
                s.sendMessage(Component.text("Sakagura - brewing and the fruit garden", NamedTextColor.GOLD));
                s.sendMessage(Component.text("1. Water cauldron + heat below, throw in ingredients, wait, take bottles out.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("2. Some drinks: distill them in a Steam Still (bottles + glowstone inside, fire below). 3. Some: age in a Sake Cask.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Recipes on the website: ", NamedTextColor.GRAY).append(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));
                s.sendMessage(Component.text("/" + label + " recipes | drunk" + (admin ? " | give | take | reload" : ""), NamedTextColor.GRAY));
            }
        }
        return true;
    }

    /** A brew that follows the recipe exactly, at about the given quality (for staff). */
    private Brew perfect(Recipe r, int quality) {
        Brew b = new Brew();
        b.ingredients.putAll(r.first().ingredients());
        for (Recipe.Step st : r.steps) {
            switch (st.type()) {
                case 'C' -> b.cook = st.time();
                case 'M' -> b.mix = st.time();
                case 'D' -> b.distills = st.runs();
                case 'A' -> {
                    b.years = st.years();
                    b.wood = st.wood().equals("any") ? "oak" : st.wood();
                }
                default -> { }
            }
            b.step(st.type());
        }
        // lower quality: cook a bit off
        double off = (10 - Math.max(1, Math.min(10, quality))) * 0.6;
        if (b.cook > 0) b.cook += off;
        else b.mix += off;
        return b;
    }

    private static boolean deny(CommandSender s) {
        s.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String v : new String[]{"help", "recipes", "drunk", "give", "take", "giveall", "reload"}) if (v.startsWith(last)) out.add(v);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take") || a[0].equalsIgnoreCase("drunk"))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take"))) {
            for (Recipe r : recipes.all()) if (r.slug.startsWith(last)) out.add(r.slug);
            for (Woods w : Woods.values()) if (("cask_" + w.id()).startsWith(last)) out.add("cask_" + w.id());
            for (Fruit f : Fruit.values()) {
                if (f.produce.startsWith(last)) out.add(f.produce);
                if (f.seed != null && f.seed.startsWith(last)) out.add(f.seed);
            }
        }
        return out;
    }
}
