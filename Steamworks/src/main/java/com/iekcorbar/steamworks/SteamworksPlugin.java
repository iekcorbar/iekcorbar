package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SteamworksPlugin extends JavaPlugin implements KineticsHost {
    private Settings settings;
    private Kinetics kinetics;
    private Visuals visuals;
    private PartItems items;
    private Manual manual;
    private NodeStorage storage;
    private Millstones millstones;
    private Hammers hammers;
    private Fans fans;
    private Mixers mixers;
    private Trains trains;
    private Ores ores;
    private Decor decor;
    private DecorShapes shapes;
    private Oil oil;
    private Breakers breakers;
    private Belts belts;
    private Arms arms;
    private Chains chains;
    private PowerPlants plants;
    private Crushers crushers;
    private Adv adv;
    private EasterEggs eggs;
    private final ExtParts customParts = new ExtParts(this);
    private final Set<Chunk> saveQueue = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "machines.yml").exists()) saveResource("machines.yml", false);
        settings = new Settings(getConfig());
        kinetics = new Kinetics(this);
        visuals = new Visuals(this);
        items = new PartItems(this);
        storage = new NodeStorage(this);
        millstones = new Millstones(this);
        millstones.load(new File(getDataFolder(), "machines.yml"));
        hammers = new Hammers(this);
        hammers.load(new File(getDataFolder(), "machines.yml"));
        fans = new Fans(this);
        fans.load(new File(getDataFolder(), "machines.yml"));
        mixers = new Mixers(this);
        mixers.load(new File(getDataFolder(), "machines.yml"));
        items.registerRecipes();

        manual = new Manual(this);
        getServer().getPluginManager().registerEvents(manual, this);
        getServer().getPluginManager().registerEvents(new PartListener(this), this);
        // items sold by wandering traders (WanderingTrades hands out a plain paper, see TraderSwap)
        getServer().getPluginManager().registerEvents(new TraderSwap(this, "Steamworks", name -> {
            for (PartType t : PartType.values()) if (t != PartType.CUSTOM && t.displayName.equals(name)) return items.create(t, 1);
            for (MaterialType t : MaterialType.values()) if (t.displayName.equals(name)) return items.createMaterial(t, 1);
            return null;
        }), this);
        ores = new Ores(this);
        ores.load(getConfig().getConfigurationSection("ores"));
        getServer().getPluginManager().registerEvents(ores, this);
        decor = new Decor(this);
        decor.registerRecipes();
        getServer().getPluginManager().registerEvents(decor, this);
        shapes = new DecorShapes(this);
        shapes.registerRecipes();
        getServer().getPluginManager().registerEvents(shapes, this);
        oil = new Oil(this);
        breakers = new Breakers(this);
        belts = new Belts(this);
        arms = new Arms(this);
        chains = new Chains(this);
        plants = new PowerPlants(this);
        crushers = new Crushers(this);
        crushers.load(new File(getDataFolder(), "machines.yml"));
        adv = Achievements.create(this);
        adv.start();
        eggs = new EasterEggs(this);
        // the recipe viewer (Zukan plugin) reads our items and recipes through this service
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this), this, org.bukkit.plugin.ServicePriority.Normal);
        // machine output containers, for the Kura copper-golem couriers
        getServer().getServicesManager().register(java.util.function.Predicate.class, new OutputSpots(this), this, org.bukkit.plugin.ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(arms, this);
        getServer().getPluginManager().registerEvents(oil, this);
        trains = new Trains(this);
        getServer().getPluginManager().registerEvents(trains, this);
        SteamworksCommand cmd = new SteamworksCommand(this);
        getCommand("steamworks").setExecutor(cmd);
        getCommand("steamworks").setTabCompleter(cmd);

        // FreeMinecraftModels loads its models after startup; spawn ours once it knows them
        getServer().getScheduler().runTaskLater(this, () -> items.waitForModels(() -> {
            for (World w : getServer().getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e.getPersistentDataContainer().has(visuals.tag, PersistentDataType.BYTE)) e.remove();
                }
            }
            trains.load();
            for (Player p : getServer().getOnlinePlayers()) p.discoverRecipes(items.recipeKeys);
            // Rebuild the machines chunk by chunk, a few chunks per tick: reading and spawning
            // every loaded chunk in one go is what made the server stutter for seconds at startup.
            java.util.List<Chunk> queue = new java.util.ArrayList<>();
            for (World w : getServer().getWorlds()) queue.addAll(java.util.Arrays.asList(w.getLoadedChunks()));
            int[] at = {0};
            getServer().getScheduler().runTaskTimer(this, t -> {
                int end = Math.min(queue.size(), at[0] + 8);
                for (; at[0] < end; at[0]++) {
                    Chunk c = queue.get(at[0]);
                    if (c.isLoaded()) {
                        loadChunk(c);
                        shapes.loadChunk(c);
                    }
                }
                if (at[0] >= queue.size()) t.cancel();
            }, 1L, 1L);
        }), 40L);

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (kinetics.tick % 2 == 0) checkRedstone();
            kinetics.tick();
            trains.tick(kinetics.tick);
            oil.tick(kinetics.tick);
            if (kinetics.tick % 2 == 0) breakers.tick(kinetics.tick);
            belts.tick();
            belts.eject();
            shapes.tick();
            arms.tick();
            plants.tick(kinetics.tick);
            if (kinetics.tick % 5 == 0) crushers.tick();
            if (kinetics.tick % 5 == 0) {
                millstones.tick(kinetics.tick);
                hammers.tick();
                fans.tick(kinetics.tick);
                mixers.tick(kinetics.tick);
            }
            if (kinetics.tick % 30 == 0) machineSounds();
            kinetics.tickCustoms(kinetics.tick);
            if (kinetics.tick % 1200 == 0) {
                for (KNode m : kinetics.mills) saveQueue.add(m.world.getChunkAt(m.x >> 4, m.z >> 4));
                for (KNode m : kinetics.kettles) saveQueue.add(m.world.getChunkAt(m.x >> 4, m.z >> 4));
            }
            if (!saveQueue.isEmpty() && kinetics.tick % 20 == 0) {
                for (Chunk c : saveQueue) if (c.isLoaded()) storage.save(c);
                saveQueue.clear();
            }
        }, 1L, 1L);
        getLogger().info("Steamworks enabled.");
    }

    /** Running machines make their own sounds when someone is close enough to hear them. */
    private void machineSounds() {
        float scale = settings.machineVolume;
        if (scale <= 0f) return;
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (KNode n : kinetics.all()) {
            boolean running = n.speed() != 0 || (n.type == PartType.STEAM_BOILER && n.burnTicks > 0);
            if (!running || n.world.getNearbyPlayers(n.center(), 20).isEmpty()) continue;
            String key;
            float vol;
            switch (n.type) {
                case BELLOWS_FAN -> { key = "fan.whoosh"; vol = 0.3f; }
                case DRILL, SAW -> {
                    if (n.world.getBlockAt(n.x, n.y, n.z).getRelative(n.facing).getType().isAir()) continue;
                    key = n.type == PartType.DRILL ? "drill.grind" : "saw.buzz";
                    vol = 0.45f;
                }
                case BELT -> { if (r.nextInt(3) != 0) continue; key = "belt.rumble"; vol = 0.2f; }
                case PUMPJACK -> { key = "pumpjack.creak"; vol = 0.4f; }
                case WINDMILL -> { if (r.nextInt(8) != 0) continue; key = "windmill.woosh"; vol = 0.12f; }
                case GEARBOX -> { if (r.nextInt(4) != 0) continue; key = "gear.clatter"; vol = 0.15f; }
                case STEAM_BOILER -> { key = "boiler.hiss"; vol = 0.3f; }
                default -> { continue; }
            }
            n.world.playSound(n.center(), "steamworks:" + key, vol * scale, 0.9f + r.nextFloat() * 0.2f);
        }
    }

    /**
     * The door for add-on plugins: register your {@link com.iekcorbar.steamworks.api.CustomPart}s
     * from your onEnable (softdepend: [Steamworks]) and their machines join the network.
     */
    public com.iekcorbar.steamworks.api.CustomPartRegistry customParts() {
        return customParts;
    }

    /** Runs {@code then} once FreeMinecraftModels knows the models (use it to register recipes safely). */
    public void whenModelsReady(Runnable then) {
        items.waitForModels(then);
    }

    /**
     * Add-on machines call this after changing their own state ({@code CustomNode.data()}): the
     * chunk goes into the save queue, so {@code saveState()} runs again within a second and the
     * change survives a restart even if nobody touches the chunk itself.
     */
    public void markDirty(org.bukkit.block.Block at) {
        KNode n = kinetics.at(at);
        if (n != null && n.alive) saveSoon(n);
    }

    @Override
    public void onDisable() {
        if (kinetics == null) return;
        boolean stopping = org.bukkit.Bukkit.isStopping();
        if (trains != null) trains.shutdown(stopping);
        // Only chunks whose saved state can differ from what was written when the part was placed/changed:
        // queued ones and chunks holding parts with contents. Rewriting every chunk with parts made stopping stall.
        Set<Chunk> chunks = new HashSet<>(saveQueue);
        for (KNode n : kinetics.all()) {
            switch (n.type) {
                case MILLSTONE, KETTLE, TANK, STEAM_BOILER, ARM -> chunks.add(n.world.getChunkAt(n.x >> 4, n.z >> 4));
                default -> { }
            }
            // display entities are not persistent: on a server stop they vanish by themselves
            if (!stopping) visuals.remove(n);
        }
        for (Chunk c : chunks) if (c.isLoaded()) storage.save(c);
        saveQueue.clear();
        if (!stopping) items.unregisterRecipes();
    }

    @Override public Settings settings() { return settings; }
    @Override public void rebuilt(Network net) { visuals.rebuilt(net); }
    @Override public void spin(Network net, int period) { visuals.spin(net, period); }
    Kinetics kinetics() { return kinetics; }
    Visuals visuals() { return visuals; }
    PartItems items() { return items; }
    Manual manual() { return manual; }
    Adv adv() { return adv; }
    EasterEggs eggs() { return eggs; }
    Millstones millstones() { return millstones; }
    Hammers hammers() { return hammers; }
    Fans fans() { return fans; }
    Mixers mixers() { return mixers; }
    Crushers crushers() { return crushers; }
    Trains trains() { return trains; }
    Ores ores() { return ores; }
    Decor decor() { return decor; }
    DecorShapes shapes() { return shapes; }
    Oil oil() { return oil; }
    Breakers breakers() { return breakers; }
    Arms arms() { return arms; }
    Chains chains() { return chains; }
    PowerPlants plants() { return plants; }

    void reload() {
        reloadConfig();
        settings = new Settings(getConfig());
        millstones.load(new File(getDataFolder(), "machines.yml"));
        hammers.load(new File(getDataFolder(), "machines.yml"));
        fans.load(new File(getDataFolder(), "machines.yml"));
        mixers.load(new File(getDataFolder(), "machines.yml"));
        crushers.load(new File(getDataFolder(), "machines.yml"));
        ores.load(getConfig().getConfigurationSection("ores"));
        for (KNode n : kinetics.all()) {
            if (n.type == PartType.WATER_WHEEL) n.generated = kinetics.waterSpeed(n);
            if (n.type == PartType.HAND_CRANK && n.crankTicks > 0) n.generated = settings.crankRpm * n.crankDir;
            visuals.spawn(n);
            kinetics.markDirty(n);
        }
    }

    // ------------------------------------------------------------ chunks

    // ------------------------------------------------------------ redstone

    static boolean hasSignal(KNode n) {
        var b = n.block();
        return b.isBlockPowered() || b.isBlockIndirectlyPowered();
    }

    /** Clutches/gearshifts follow the redstone around them (polled: a barrier gets no physics updates we can hook). */
    private void checkRedstone() {
        if (kinetics.switches.isEmpty()) return;
        for (KNode n : new java.util.ArrayList<>(kinetics.switches)) {
            boolean p = hasSignal(n);
            if (p == n.powered) continue;
            kinetics.setPowered(n, p);
            visuals.updateCasing(n);
            n.world.playSound(n.center(), Sound.BLOCK_PISTON_EXTEND, 0.3f, p ? 1.4f : 1.1f);
        }
    }

    void loadChunk(Chunk c) {
        if (!kinetics.inChunk(c.getWorld(), c.getX(), c.getZ()).isEmpty()) return;
        List<KNode> nodes = storage.load(c);
        for (KNode n : nodes) {
            if (n.type.isSwitch()) n.powered = hasSignal(n);
            kinetics.add(n);
            visuals.spawn(n);
        }
        // neighbours in other chunks connect to these now
        for (KNode n : nodes) {
            kinetics.markDirty(n);
            if (Oil.fluidPart(n)) oil.refreshAround(n); // pipe arms need all neighbours in place
        }
    }

    void unloadChunk(Chunk c) {
        var nodes = kinetics.inChunk(c.getWorld(), c.getX(), c.getZ());
        if (nodes.isEmpty()) return;
        storage.save(c);
        saveQueue.remove(c);
        for (KNode n : nodes) {
            if (n.type == PartType.CUSTOM && n.ext != null) n.ext.onRemove(ExtParts.view(n));
            visuals.remove(n);
            kinetics.remove(n);
        }
    }

    void saveSoon(KNode n) {
        saveQueue.add(n.world.getChunkAt(n.x >> 4, n.z >> 4));
    }

    // ------------------------------------------------------------ parts

    void addNode(KNode n) {
        if (n.type.isSwitch()) n.powered = hasSignal(n);
        kinetics.add(n);
        visuals.spawn(n);
        if (Oil.fluidPart(n)) oil.refreshAround(n);
        storage.save(n.world.getChunkAt(n.x >> 4, n.z >> 4));
    }

    
    /** Picks a part up (player = null: no protection check, always drops). */
    boolean breakNode(KNode n, Player p, boolean silent) {
        return breakNode(n, p, silent, false);
    }

    /**
     * Picks a part up. toInventory = Create-style wrench dismantle: the part lands in the
     * player's inventory (overflow still drops on the ground) instead of on the floor.
     */
    boolean breakNode(KNode n, Player p, boolean silent, boolean toInventory) {
        if (!n.alive) return false;
        if (p != null) {
            PartListener.ProbeBreakEvent probe = new PartListener.ProbeBreakEvent(n.block(), p);
            getServer().getPluginManager().callEvent(probe);
            if (probe.isCancelled()) return false;
        }
        boolean drop = p == null || p.getGameMode() != GameMode.CREATIVE;
        if (drop) {
            ItemStack part = n.type == PartType.CUSTOM && n.ext != null ? customParts.item(n.ext, 1) : items.create(n.type, 1);
            if (toInventory && p != null)
                for (ItemStack rest : p.getInventory().addItem(part).values())
                    n.world.dropItemNaturally(n.center(), rest);
            else n.world.dropItemNaturally(n.center(), part);
        }
        for (ItemStack s : Millstones.contents(n)) n.world.dropItemNaturally(n.center(), s);
        for (ItemStack s : n.kettleItems) n.world.dropItemNaturally(n.center(), s);
        if (n.type == PartType.SPROCKET && n.chain != null) chains.unlink(n, true);
        if (n.type == PartType.ARM) {
            for (ItemStack s : arms.contents(n)) n.world.dropItemNaturally(n.center(), s);
            n.tool = null;
            n.held = null;
            arms.forget(n);
        }
        n.millInput = null;
        n.millOutput.clear();
        n.kettleItems.clear();
        if (n.type == PartType.CUSTOM && n.ext != null) n.ext.onRemove(ExtParts.view(n));
        oil.broken(n);
        visuals.remove(n);
        kinetics.remove(n);
        if (Oil.fluidPart(n)) oil.refreshAround(n);
        n.block().setType(Material.AIR);
        storage.save(n.world.getChunkAt(n.x >> 4, n.z >> 4));
        if (!silent) {
            n.world.playSound(n.center(), n.type.placeSound(), 1f, 0.7f);
            n.world.spawnParticle(Particle.BLOCK, n.center(), 20, 0.25, 0.25, 0.25, n.type.breakParticle.createBlockData());
        }
        return true;
    }

    /** Overspeed: the part drops as an item. */
    @Override
    public void snapBreakerPin(KNode n) {
        n.world.playSound(n.center(), Sound.ENTITY_ITEM_BREAK, 1f, 0.6f);
        n.world.spawnParticle(Particle.CRIT, n.center(), 12, 0.3, 0.3, 0.3, 0.1);
        breakNode(n, null, true);
    }

    /**
     * Steam wrench: Create-style turn around the clicked face's axis, re-orient its models, let
     * the network re-find its routes and save the new axis/facing. Returns what the part now looks
     * like, "" when this face cannot turn it (caller stays quiet), or null when the part cannot
     * turn at all.
     */
    String rotatePart(KNode n, org.bukkit.block.BlockFace clicked) {
        if (!Kinetics.canRotate(n)) return null;
        org.bukkit.Axis oldAxis = n.axis;
        org.bukkit.block.BlockFace oldFacing = n.facing;
        if (!Kinetics.turnFace(n, clicked)) return "";
        if ((n.type == PartType.COGWHEEL || n.type == PartType.LARGE_COGWHEEL) && !kinetics().cogsFit(n)) {
            n.axis = oldAxis;                                       // canSurvive-style: undo the turn
            n.facing = oldFacing;
            return "";
        }
        visuals.spawn(n);
        kinetics.relink(n);
        storage.save(n.world.getChunkAt(n.x >> 4, n.z >> 4));
        n.world.playSound(n.center(), Sound.BLOCK_COPPER_PLACE, 0.5f, 1.5f);
        return orientText(n);
    }

    /** How a part is aimed right now - same wording for the wrench action bar and the info line. */
    private static String orientText(KNode n) {
        return switch (n.type) {
            case HAND_CRANK, BELLOWS_FAN, DRILL, SAW, WINDMILL -> "points " + faceName(n.facing);
            case BELT -> "carries " + faceName(n.facing);
            case STATION, SIGNAL -> "faces " + faceName(n.facing);
            case SHAFT, COGWHEEL, LARGE_COGWHEEL, GEARBOX, CLUTCH, GEARSHIFT, SPROCKET, CRUSHING_WHEEL,
                    SPEED_GAUGE, STRESS_GAUGE, STEAM_ENGINE ->
                    n.axis == org.bukkit.Axis.X ? "east-west" : n.axis == org.bukkit.Axis.Y ? "up-down" : "north-south";
            default -> null;
        };
    }

    private static Component orient(Component c, KNode n) {
        String o = orientText(n);
        return o == null ? c : c.append(Component.text("  |  " + o, NamedTextColor.DARK_GRAY));
    }

    private static String faceName(org.bukkit.block.BlockFace f) {
        return switch (f) {
            case NORTH -> "north";
            case EAST -> "east";
            case SOUTH -> "south";
            case WEST -> "west";
            case UP -> "up";
            case DOWN -> "down";
            default -> f.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    // ------------------------------------------------------------ info

    void showInfo(KNode n, Player p) {
        if (n.type == PartType.KETTLE) eggs.kettleLook(n, p);
        if (n.speed() != 0) manual.markSpinning(p, n.type);   // the manual knows what you have seen turn
        p.sendActionBar(info(n));
    }

    Component info(KNode n) {
        Network net = n.network;
        if (n.type == PartType.KETTLE) return mixers.info(n);
        if (n.type == PartType.ARM) return arms.info(n);
        if (n.type == PartType.STEAM_BOILER) return plants.info(n);
        if ((n.type == PartType.WINDMILL || n.type == PartType.STEAM_ENGINE) && (n.network == null || n.speed() == 0)) return plants.info(n);
        if (n.type == PartType.TANK || n.type == PartType.PIPE || n.type == PartType.REFINERY) return oil.info(n);
        if (n.type == PartType.PUMPJACK && (n.network == null || n.network.jammed || n.speed() == 0)) return oil.info(n);
        if (n.type == PartType.STATION)
            return Component.text("Station: ", NamedTextColor.GOLD).append(Component.text(n.label == null ? "(no name - rename it with a name tag)" : n.label, NamedTextColor.WHITE));
        if (n.type == PartType.SIGNAL)
            return Component.text("Signal: ", NamedTextColor.GOLD).append(n.signalRed
                    ? Component.text("red - the track after it is taken (or redstone)", NamedTextColor.RED)
                    : Component.text("green - the track is clear", NamedTextColor.GREEN));
        Component name = Component.text((n.type == PartType.CUSTOM && n.ext != null ? n.ext.displayName() : n.type.displayName) + ": ", NamedTextColor.GOLD);
        if (n.type == PartType.CLUTCH && n.powered)
            return name.append(Component.text("disengaged by redstone - the two sides are separate", NamedTextColor.YELLOW));
        if (n.type == PartType.GEARSHIFT && n.powered)
            name = name.append(Component.text("[reversing] ", NamedTextColor.YELLOW));
        if (net == null) return name.append(Component.text("not connected", NamedTextColor.GRAY));
        if (net.jammed) {
            return name.append(Component.text("Jammed - " + net.reason, NamedTextColor.RED))
                    .append(Component.text(String.format(" (load %.0f / %.0f Tq)", net.load, net.capacity), NamedTextColor.GRAY));
        }
        double s = Math.abs(n.speed());
        if (s == 0) return name.append(Component.text("standing still - no power source turning", NamedTextColor.GRAY));
        NamedTextColor c;
        String level;
        if (s < settings.mediumSpeed) { c = NamedTextColor.GREEN; level = "slow"; }
        else if (s < settings.fastSpeed) { c = NamedTextColor.AQUA; level = "medium"; }
        else { c = NamedTextColor.LIGHT_PURPLE; level = "fast"; }
        Component out = name.append(Component.text(String.format("%.1f RPM (%s)", s, level), c))
                .append(Component.text(String.format("  |  load %.0f / %.0f Tq", net.load, net.capacity), NamedTextColor.GRAY));
        if (n.type == PartType.STEAM_HAMMER) {
            double secs = settings.hammerCycle / Hammers.rate(settings, s) / 20.0;
            out = out.append(Component.text(String.format("  |  1 strike / %.1f s", secs), NamedTextColor.GRAY));
        }
        if (n.type == PartType.BELLOWS_FAN)
            out = out.append(Component.text("  |  reach " + Fans.range(settings, s) + " blocks", NamedTextColor.GRAY));
        return out;
    }
}
