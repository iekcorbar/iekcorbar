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
    private Trees trees;
    private GardenDecor gardenDecor;
    private Adv adv;
    private Competition competition;
    private CompetitionArea compArea;
    private WorldEditIntegration worldEdit;
    private Seasons seasons;
    private Sommelier sommelier;
    private Fmm fmm;

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
        trees = new Trees(this);
        gardenDecor = new GardenDecor(this);
        var pm = getServer().getPluginManager();
        for (var l : new org.bukkit.event.Listener[]{goods, cauldrons, stills, casks, drunk, garden, trees}) pm.registerEvents(l, this);
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
        competition = new Competition(this);
        compArea = new CompetitionArea(this);
        worldEdit = new WorldEditIntegration(this);
        seasons = new Seasons(this);
        sommelier = new Sommelier(this);
        getServer().getPluginManager().registerEvents(sommelier, this);
        fmm = new Fmm(getLogger());
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
                    trees.loadChunk(c);
                    gardenDecor.decor.loadChunk(c);
                }
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick++;
            cauldrons.tick(tick);
            stills.tick();
            drunk.tick();
            garden.tick();
            trees.tick();
            seasons.tick();
            if (tick % 30 == 0) {
                casks.relight();
                stills.relight();
                trees.relight();
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
        if (sommelier != null) sommelier.save();
        if (!getServer().isStopping()) {
            casks.removeAll();
            stills.removeAll();
            garden.removeAllDisplays();
            trees.removeAllDisplays();
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
    Trees trees() { return trees; }
    Adv adv() { return adv; }
    Drunk drunk() { return drunk; }
    GardenDecor gardenDecor() { return gardenDecor; }
    Competition competition() { return competition; }
    CompetitionArea compArea() { return compArea; }
    WorldEditIntegration worldEdit() { return worldEdit; }
    Seasons seasons() { return seasons; }
    Sommelier sommelier() { return sommelier; }
    Fmm fmm() { return fmm; }

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
                    s.sendMessage(Component.text("/" + label + " give <player> <drink|item|cask_<wood>> [quality 1-10] [amount]", NamedTextColor.RED));
                    return true;
                }
                int amount = 1, amount2 = 1;
                try {
                    if (a.length > 3) amount = Integer.parseInt(a[3]);
                    if (a.length > 4) amount2 = Integer.parseInt(a[4]);
                } catch (NumberFormatException e) {
                    s.sendMessage(Component.text("Quality and amount have to be numbers.", NamedTextColor.RED));
                    return true;
                }
                Recipe r = recipes.get(a[2].toLowerCase(Locale.ROOT));
                Woods wood = a[2].startsWith("cask_") ? Woods.byId(a[2].substring(5)) : null;
                if (r == null && wood == null && Fruit.byId(a[2]) == null && Fruit.byPlantItem(a[2]) == null && Tree.bySapling(a[2]) == null) {
                    s.sendMessage(Component.text("Unknown drink/item - see /" + label + " recipes", NamedTextColor.RED));
                    return true;
                }
                if (r != null) {
                    // After a DRINK the first number was always the quality, and 1-10 still is (one
                    // bottle, as before). Anything above 10 can only mean an amount (a quality of 64
                    // makes no sense), and "... bloody_mary 8 64" gives quality AND amount explicitly.
                    // Drinks are potions - they never stack - so every bottle is its own item; a full
                    // backpack that can't take them drops the rest at the receiver's feet.
                    int quality = 10, total = 1;
                    if (a.length > 3) {
                        if (amount >= 1 && amount <= 10) quality = amount;
                        else total = Math.max(1, Math.min(576, amount));
                    }
                    if (a.length > 4) {
                        quality = Math.max(1, Math.min(10, amount));
                        total = Math.max(1, Math.min(576, amount2));
                    }
                    Brew br = perfect(r, quality);
                    for (int i = 0; i < total; i++)
                        for (ItemStack left : t.getInventory().addItem(goods.bottle(br, 1)).values())
                            t.getWorld().dropItemNaturally(t.getLocation(), left);
                    s.sendMessage(Component.text("Gave " + total + " x " + a[2] + " (quality " + quality + ") to " + t.getName(), NamedTextColor.GREEN));
                } else {
                    // Casks (barrels) and garden items do stack: hand them out per full stack.
                    int total = Math.max(1, Math.min(2304, a.length > 4 ? amount2 : amount));
                    for (int left = total; left > 0; ) {
                        int chunk = Math.min(64, left);
                        left -= chunk;
                        ItemStack it = wood != null ? goods.cask(wood, chunk) : goods.item(a[2].toLowerCase(Locale.ROOT), chunk);
                        for (ItemStack over : t.getInventory().addItem(it).values())
                            t.getWorld().dropItemNaturally(t.getLocation(), over);
                    }
                    s.sendMessage(Component.text("Gave " + total + " x " + a[2] + " to " + t.getName(), NamedTextColor.GREEN));
                }
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
            case "comp" -> {
                String sub2 = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
                switch (sub2) {
                    case "start" -> {
                        if (!admin) return deny(s);
                        int days = 7;
                        if (a.length > 2) try { days = Math.max(1, Math.min(30, Integer.parseInt(a[2]))); } catch (NumberFormatException ignored) { }
                        String theme = a.length > 3 ? a[3] : "any";
                        if (competition.start(s instanceof Player p ? p : null, days, theme)) {
                            s.sendMessage(Component.text("Brewing competition started! Theme: " + theme + ", duration: " + days + " days.", NamedTextColor.GREEN));
                            Bukkit.broadcast(Component.text("A brewing competition has started! Theme: " + theme + ". Use /brew comp submit to enter.", NamedTextColor.GOLD));
                        } else {
                            s.sendMessage(Component.text("A competition is already running.", NamedTextColor.RED));
                        }
                    }
                    case "stop" -> {
                        if (!admin) return deny(s);
                        if (competition.stop()) {
                            s.sendMessage(Component.text("Competition stopped. Giving rewards...", NamedTextColor.GREEN));
                            competition.giveRewards();
                        } else {
                            s.sendMessage(Component.text("No competition running.", NamedTextColor.RED));
                        }
                    }
                    case "submit" -> {
                        if (!(s instanceof Player p)) return true;
                        ItemStack drink = p.getInventory().getItemInMainHand();
                        String result = competition.submit(p, drink);
                        s.sendMessage(Component.text(result, result.startsWith("Submitted") ? NamedTextColor.GREEN : NamedTextColor.RED));
                    }
                    case "leaderboard", "lb" -> {
                        List<Competition.LeaderEntry> lb = competition.leaderboard();
                        if (lb.isEmpty()) {
                            s.sendMessage(Component.text("No submissions yet.", NamedTextColor.GRAY));
                            return true;
                        }
                        s.sendMessage(Component.text("=== Brewing Competition Leaderboard ===", NamedTextColor.GOLD));
                        for (int i = 0; i < Math.min(10, lb.size()); i++) {
                            Competition.LeaderEntry e = lb.get(i);
                            s.sendMessage(Component.text("#" + (i + 1) + " " + e.playerName + " - " + e.drinkName +
                                    " (" + e.quality + " stars, diff " + e.difficulty + ") Score: " + (int) e.score, NamedTextColor.GRAY));
                        }
                        if (s instanceof Player p) {
                            int rank = competition.rank(p.getUniqueId());
                            if (rank > 0) s.sendMessage(Component.text("Your rank: #" + rank, NamedTextColor.AQUA));
                        }
                    }
                    case "status" -> {
                        s.sendMessage(competition.statusLine());
                    }
                    case "area" -> {
                        String sub3 = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
                        switch (sub3) {
                            case "set" -> {
                                if (!admin) return deny(s);
                                if (!(s instanceof Player p)) return true;
                                if (a.length < 4) {
                                    s.sendMessage(Component.text("Usage: /brew comp area set <name>", NamedTextColor.RED));
                                    return true;
                                }
                                String areaName = a[3];
                                if (compArea.hasFirstCorner(areaName)) {
                                    int result = compArea.setCorner(areaName, p.getLocation(), 2);
                                    if (result == 2) {
                                        CompetitionArea.Area ar = compArea.getArea(areaName);
                                        s.sendMessage(Component.text("Area '" + areaName + "' created: " + ar.bounds(), NamedTextColor.GREEN));
                                    } else if (result == -1) {
                                        s.sendMessage(Component.text("Both corners must be in the same world.", NamedTextColor.RED));
                                    } else {
                                        s.sendMessage(Component.text("Failed to create area.", NamedTextColor.RED));
                                    }
                                } else {
                                    compArea.setCorner(areaName, p.getLocation(), 1);
                                    s.sendMessage(Component.text("First corner set for '" + areaName + "'. Move to the opposite corner and run the command again.", NamedTextColor.YELLOW));
                                }
                            }
                            case "remove" -> {
                                if (!admin) return deny(s);
                                if (a.length < 4) {
                                    s.sendMessage(Component.text("Usage: /brew comp area remove <name>", NamedTextColor.RED));
                                    return true;
                                }
                                if (compArea.removeArea(a[3])) {
                                    s.sendMessage(Component.text("Area '" + a[3] + "' removed.", NamedTextColor.GREEN));
                                } else {
                                    s.sendMessage(Component.text("Area not found.", NamedTextColor.RED));
                                }
                            }
                            case "activate", "use" -> {
                                if (!admin) return deny(s);
                                if (a.length < 4) {
                                    s.sendMessage(Component.text("Usage: /brew comp area activate <name>", NamedTextColor.RED));
                                    return true;
                                }
                                if (compArea.getArea(a[3]) == null) {
                                    s.sendMessage(Component.text("Area not found.", NamedTextColor.RED));
                                    return true;
                                }
                                compArea.setActiveArea(a[3]);
                                s.sendMessage(Component.text("Active competition area: " + a[3], NamedTextColor.GREEN));
                            }
                            case "deactivate" -> {
                                if (!admin) return deny(s);
                                compArea.setActiveArea(null);
                                s.sendMessage(Component.text("Competition area deactivated (no area restriction).", NamedTextColor.GREEN));
                            }
                            case "we", "worldedit" -> {
                                if (!admin) return deny(s);
                                if (!(s instanceof Player p)) return true;
                                if (!worldEdit.worldEditAvailable()) {
                                    s.sendMessage(Component.text("WorldEdit is not installed on this server.", NamedTextColor.RED));
                                    return true;
                                }
                                if (a.length < 4) {
                                    s.sendMessage(Component.text("Usage: /brew comp area we <name> [protect]", NamedTextColor.RED));
                                    return true;
                                }
                                String areaName = a[3];
                                boolean protect = a.length > 4 && a[4].equalsIgnoreCase("protect");
                                if (worldEdit.createRegion(p, areaName, protect)) {
                                    // Also store in our area system for tracking
                                    org.bukkit.Location min = p.getLocation(); // placeholder
                                    s.sendMessage(Component.text("WorldGuard region '" + areaName + "' created from your WorldEdit selection.", NamedTextColor.GREEN));
                                    if (protect) s.sendMessage(Component.text("Region is protected (no building/breaking).", NamedTextColor.YELLOW));
                                    // Store the area name as active
                                    compArea.setActiveArea(areaName);
                                    s.sendMessage(Component.text("Set as active competition area.", NamedTextColor.AQUA));
                                } else {
                                    s.sendMessage(Component.text("Failed to create region. Make sure you have a WorldEdit selection (//pos1, //pos2).", NamedTextColor.RED));
                                }
                            }
                            case "remove-we", "remove-worldedit" -> {
                                if (!admin) return deny(s);
                                if (!(s instanceof Player p)) return true;
                                if (a.length < 4) {
                                    s.sendMessage(Component.text("Usage: /brew comp area remove-we <name>", NamedTextColor.RED));
                                    return true;
                                }
                                if (worldEdit.removeRegion(p.getWorld(), a[3])) {
                                    s.sendMessage(Component.text("WorldGuard region '" + a[3] + "' removed.", NamedTextColor.GREEN));
                                } else {
                                    s.sendMessage(Component.text("Region not found or WorldGuard not available.", NamedTextColor.RED));
                                }
                            }
                            case "we-status", "worldedit-status" -> {
                                s.sendMessage(worldEdit.status());
                            }
                            case "list" -> {
                                List<String> areas = compArea.listAreas();
                                if (areas.isEmpty()) {
                                    s.sendMessage(Component.text("No competition areas defined.", NamedTextColor.GRAY));
                                    return true;
                                }
                                s.sendMessage(Component.text("=== Competition Areas ===", NamedTextColor.GOLD));
                                for (String line : areas) {
                                    s.sendMessage(Component.text(line, NamedTextColor.GRAY));
                                }
                                if (compArea.activeAreaName() != null) {
                                    s.sendMessage(Component.text("Active: " + compArea.activeAreaName(), NamedTextColor.AQUA));
                                }
                            }
                            default -> {
                                s.sendMessage(Component.text("Usage: /brew comp area [set <name> | remove <name> | activate <name> | deactivate | list]", NamedTextColor.GRAY));
                            }
                        }
                    }
                    default -> {
                        s.sendMessage(Component.text("Usage: /brew comp [start <days> [theme] | stop | submit | leaderboard | status]", NamedTextColor.GRAY));
                    }
                }
            }
            case "season" -> {
                String sub2 = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
                switch (sub2) {
                    case "status" -> {
                        s.sendMessage(Component.text("Current season: " + seasons.status(), NamedTextColor.GOLD));
                    }
                    case "set" -> {
                        if (!admin) return deny(s);
                        if (a.length < 3) {
                            s.sendMessage(Component.text("Usage: /brew season set <spring|summer|autumn|winter>", NamedTextColor.RED));
                            return true;
                        }
                        try {
                            Seasons.Season season = Seasons.Season.valueOf(a[2].toUpperCase(Locale.ROOT));
                            seasons.setSeason(season);
                            s.sendMessage(Component.text("Season set to " + season.displayName + ".", NamedTextColor.GREEN));
                            Bukkit.broadcast(Component.text("The season has changed to " + season.displayName + "! " + season.description, NamedTextColor.GOLD));
                        } catch (IllegalArgumentException e) {
                            s.sendMessage(Component.text("Unknown season. Use: spring, summer, autumn, winter", NamedTextColor.RED));
                        }
                    }
                    case "list" -> {
                        s.sendMessage(Component.text("=== Seasons ===", NamedTextColor.GOLD));
                        for (Seasons.Season se : Seasons.Season.values()) {
                            String marker = se == seasons.current() ? " <-- current" : "";
                            s.sendMessage(Component.text(se.displayName + ": " + se.description + " (" + se.crops.length + " crops)" + marker, NamedTextColor.GRAY));
                        }
                    }
                    case "crops" -> {
                        Seasons.Season se = seasons.current();
                        s.sendMessage(Component.text("Crops that grow in " + se.displayName + ":", NamedTextColor.GOLD));
                        StringBuilder sb = new StringBuilder();
                        for (String c : se.crops) sb.append(sb.isEmpty() ? "" : ", ").append(c);
                        s.sendMessage(Component.text(sb.toString(), NamedTextColor.GRAY));
                    }
                    default -> {
                        s.sendMessage(Component.text("Usage: /brew season [status|set <season>|list|crops]", NamedTextColor.GRAY));
                    }
                }
            }
            case "taste" -> {
                if (!(s instanceof Player p)) return true;
                org.bukkit.inventory.ItemStack drink = p.getInventory().getItemInMainHand();
                String result = sommelier.evaluate(p, drink);
                if (result == null) {
                    s.sendMessage(Component.text("You must be holding a Sakagura drink.", NamedTextColor.RED));
                } else if (result.equals("unfinished")) {
                    s.sendMessage(Component.text("This drink isn't finished yet. Complete the brewing process first.", NamedTextColor.RED));
                }
            }
            case "stats" -> {
                if (!(s instanceof Player p)) return true;
                s.sendMessage(Component.text("Sommelier stats: " + sommelier.stats(p), NamedTextColor.GOLD));
            }
            case "woods" -> {
                s.sendMessage(Component.text("=== Cask Woods & Flavor Profiles ===", NamedTextColor.GOLD));
                for (Woods w : Woods.values()) {
                    s.sendMessage(Component.text(w.label() + ": " + w.flavorProfile() + " (aging bonus: " + String.format("%.0f%%", w.agingBonus() * 100) + ")", NamedTextColor.GRAY));
                }
            }
            case "model" -> {
                String sub2 = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
                switch (sub2) {
                    case "status" -> {
                        s.sendMessage(Component.text("FMM available: " + fmm.available(), NamedTextColor.GOLD));
                    }
                    case "spawn" -> {
                        if (!admin) return deny(s);
                        if (!(s instanceof Player p)) return true;
                        if (!fmm.available()) {
                            s.sendMessage(Component.text("FreeMinecraftModels is not installed.", NamedTextColor.RED));
                            return true;
                        }
                        if (a.length < 3) {
                            s.sendMessage(Component.text("Usage: /brew model spawn <sommelier|cask|still|tree|fountain>", NamedTextColor.RED));
                            return true;
                        }
                        String modelName = "sakagura:" + a[2].toLowerCase(Locale.ROOT);
                        // Spawn an invisible villager and attach the model
                        org.bukkit.Location loc = p.getLocation();
                        org.bukkit.entity.Villager v = loc.getWorld().spawn(loc, org.bukkit.entity.Villager.class);
                        v.setInvisible(true);
                        v.setAI(false);
                        v.setSilent(true);
                        if (fmm.attach(v, modelName)) {
                            fmm.play(v, "idle", true);
                            s.sendMessage(Component.text("Spawned " + modelName + " at your location.", NamedTextColor.GREEN));
                        } else {
                            v.remove();
                            s.sendMessage(Component.text("Failed to attach model. Is it loaded in FMM?", NamedTextColor.RED));
                        }
                    }
                    case "item" -> {
                        if (!admin) return deny(s);
                        if (!(s instanceof Player p)) return true;
                        if (!fmm.available()) {
                            s.sendMessage(Component.text("FreeMinecraftModels is not installed.", NamedTextColor.RED));
                            return true;
                        }
                        if (a.length < 3) {
                            s.sendMessage(Component.text("Usage: /brew model item <sommelier|cask|still|tree|fountain>", NamedTextColor.RED));
                            return true;
                        }
                        String modelName = "sakagura:" + a[2].toLowerCase(Locale.ROOT);
                        org.bukkit.inventory.ItemStack item = p.getInventory().getItemInMainHand();
                        if (item == null || item.getType().isAir()) {
                            s.sendMessage(Component.text("Hold an item in your main hand.", NamedTextColor.RED));
                            return true;
                        }
                        if (fmm.itemModel(item, modelName)) {
                            s.sendMessage(Component.text("Applied " + modelName + " to the item in your hand.", NamedTextColor.GREEN));
                        } else {
                            s.sendMessage(Component.text("Failed to apply model. Is it loaded in FMM?", NamedTextColor.RED));
                        }
                    }
                    default -> {
                        s.sendMessage(Component.text("Usage: /brew model [status|spawn <name>|item <name>]", NamedTextColor.GRAY));
                    }
                }
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
                s.sendMessage(Component.text("/" + label + " recipes | drunk | taste | stats | model [status|spawn|item] | comp [...] | season [...] | woods" + (admin ? " | give | take | reload" : ""), NamedTextColor.GRAY));
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
            for (Tree t : Tree.values()) if (t.sapling().startsWith(last)) out.add(t.sapling());
        }
        return out;
    }
}
