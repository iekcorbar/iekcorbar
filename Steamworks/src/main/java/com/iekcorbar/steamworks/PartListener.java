package com.iekcorbar.steamworks;

import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * A part is a barrier block (solid, unpushable, blast-proof, invisible) with
 * display models on it. Right-click with a part item places one (protection
 * checked with a normal BlockPlaceEvent); left-click on a part picks it up
 * (BlockBreakEvent check). Right-click on a crank turns it, on a millstone
 * fills/empties it, on anything else shows its speed and the network load.
 */
final class PartListener implements Listener {
    private final SteamworksPlugin plugin;
    /** Last place(): same target twice in a row = the creative double event, not two clicks. */
    private long lastPlaceNano;
    private long lastPlaceTarget;

    PartListener(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Our own protection probe - ignored by our break handler. */
    static final class ProbeBreakEvent extends BlockBreakEvent {
        ProbeBreakEvent(Block block, Player player) {
            super(block, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            // our own paper items must never reach vanilla's dual-hand interaction (creative
            // fires the off-hand event for the same click too -> that was the "one click,
            // several parts" report): cancel ours unconditionally on the off hand.
            if (plugin.items().isOurs(e.getItem())) e.setCancelled(true);
            else if (e.getClickedBlock() != null && plugin.kinetics().at(e.getClickedBlock()) != null) e.setCancelled(true);
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        KNode n = plugin.kinetics().at(b);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (n == null) return;
            e.setCancelled(true);
            hit(n, p);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (plugin.manual().isManual(e.getItem())) {            // the manual always opens, wherever you click
            e.setCancelled(true);
            p.openBook(plugin.manual().create(p));
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = e.getItem();
        PartType held = plugin.items().typeOf(hand);
        if (n != null) {
            boolean block = hand != null && hand.getType().isBlock() && !hand.getType().isAir();
            if (plugin.items().materialOf(hand) == MaterialType.WRENCH) {
                e.setCancelled(true);                              // steam wrench
                if (p.isSneaking()) {                              // sneak = Create-style dismantle
                    if (plugin.breakNode(n, p, false, true)) p.swingMainHand();
                    return;
                }
                String look = plugin.rotatePart(n, e.getBlockFace());   // turn around clicked face
                if (look == null) plugin.showInfo(n, p);           // cannot turn -> act like an empty hand
                else if (look.isEmpty()) p.sendActionBar(net.kyori.adventure.text.Component
                        .text("Can't turn that here.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                else {
                    p.sendActionBar(net.kyori.adventure.text.Component.text(n.type.displayName + ": " + look,
                            net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    p.swingMainHand();
                }
                return;
            }
            if (held != null) {
                e.setCancelled(true);
                place(p, hand, held, b.getRelative(e.getBlockFace()), b, e.getBlockFace());
            } else if (n.type == PartType.MILLSTONE && plugin.millstones().find(hand) != null) {
                e.setCancelled(true);
                if (plugin.millstones().interact(n, p, hand)) plugin.saveSoon(n);
            } else if (n.type == PartType.SPROCKET && (Chains.isChain(hand) || (p.isSneaking() && (hand == null || hand.isEmpty()) && n.chain != null))) {
                e.setCancelled(true);
                plugin.chains().interact(n, p, hand);
            } else if (n.type == PartType.STEAM_BOILER && plugin.plants().fuelFor(hand) > 0) {
                e.setCancelled(true);
                plugin.plants().feed(n, p, hand);
            } else if (n.type == PartType.ARM) {
                e.setCancelled(true);
                if (!plugin.arms().interact(n, p, hand)) plugin.showInfo(n, p);
            } else if (n.type == PartType.TANK && plugin.items().materialOf(hand) != null) {
                e.setCancelled(true);
                if (!plugin.oil().interact(n, p, hand)) plugin.showInfo(n, p);
            } else if (n.type == PartType.PUMP && plugin.items().materialOf(hand) != null) {
                e.setCancelled(true);
                if (!plugin.oil().pumpInteract(n, p, hand)) plugin.showInfo(n, p);
            } else if (n.type == PartType.WHISTLE && !block) {
                e.setCancelled(true);
                n.world.playSound(n.center(), org.bukkit.Sound.BLOCK_BELL_USE, 1.4f, 0.72f);
                n.world.playSound(n.center(), org.bukkit.Sound.BLOCK_BELL_RESONATE, 0.7f, 0.9f);
                n.world.spawnParticle(org.bukkit.Particle.CLOUD, n.center().add(0, 0.6, 0), 7, 0.12, 0.05, 0.12, 0.03);
                p.swingMainHand();
            } else if (n.type == PartType.KETTLE && (!block || plugin.mixers().accepts(hand))) {
                e.setCancelled(true);
                if (!plugin.mixers().interact(n, p, hand)) plugin.showInfo(n, p);
            } else if (block) {
                // holding a normal block: let vanilla place it against the part
            } else {
                e.setCancelled(true);
                if (n.type == PartType.HAND_CRANK) crank(n, p);
                else if (n.type == PartType.STATION && hand != null && hand.getType() == Material.NAME_TAG && hand.hasItemMeta() && hand.getItemMeta().hasCustomName()) {
                    n.label = plugin.items().customName(hand, "Station");
                    plugin.visuals().spawn(n);
                    plugin.saveSoon(n);
                    p.sendActionBar(net.kyori.adventure.text.Component.text("Station renamed to " + n.label, net.kyori.adventure.text.format.NamedTextColor.GOLD));
                }
                else if (n.type == PartType.MILLSTONE && plugin.millstones().interact(n, p, hand)) plugin.saveSoon(n);
                else plugin.showInfo(n, p);
            }
            return;
        }
        if (held == null) return;
        if (!p.isSneaking() && b.getType().isInteractable()) return; // open the chest/door as usual
        e.setCancelled(true);
        Block target = b.isReplaceable() ? b : b.getRelative(e.getBlockFace());
        place(p, hand, held, target, b, e.getBlockFace());
    }

    private void crank(KNode n, Player p) {
        int dir = p.isSneaking() ? -1 : 1;
        boolean starting = n.crankTicks <= 0 || dir != n.crankDir;
        plugin.kinetics().crank(n, dir);
        plugin.eggs().crank(p);
        if (p.getGameMode() != GameMode.CREATIVE) p.setExhaustion(p.getExhaustion() + (float) plugin.settings().crankExhaustion);
        if (starting || plugin.kinetics().tick % 8 == 0)
            n.world.playSound(n.center(), Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.35f, 0.6f);
        p.swingMainHand();
    }

    private void hit(KNode n, Player p) {
        long now = plugin.kinetics().tick;
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (now - n.lastHit > 20) n.hits = 0;
            n.lastHit = now;
            if (++n.hits < plugin.settings().breakHits) {
                n.world.playSound(n.center(), Sound.BLOCK_COPPER_HIT, 0.6f, 0.8f);
                return;
            }
        }
        plugin.breakNode(n, p, false);
    }

    private void place(Player p, ItemStack hand, PartType type, Block target, Block against, BlockFace face) {
        if (!target.isReplaceable() || plugin.kinetics().at(target) != null) return;
        long tk = KNode.key(target.getX(), target.getY(), target.getZ());
        long now = System.nanoTime();
        if (tk == lastPlaceTarget && now - lastPlaceNano < 40_000_000L) return;   // second event for one click
        BoundingBox box = BoundingBox.of(target);
        if (!target.getWorld().getNearbyEntities(box, en -> en instanceof LivingEntity && !(en instanceof ArmorStand)).isEmpty()) return;

        Axis faceAxis = Kinetics.axisOf(face);
        Axis axis = faceAxis;
        BlockFace facing = face;
        KNode beside = plugin.kinetics().at(against);
        switch (type) {
            case COGWHEEL, LARGE_COGWHEEL -> {
                // against the rim of another cog: take over its axis so they mesh
                if (beside != null && (beside.type == PartType.COGWHEEL || beside.type == PartType.LARGE_COGWHEEL)
                        && beside.axis != faceAxis) axis = beside.axis;
            }
            case GEARBOX -> axis = faceAxis == Axis.Y ? Kinetics.axisOf(p.getFacing()) : Axis.Y;
            case MILLSTONE, KETTLE, MIXER, PIPE, TANK, REFINERY, ARM, STEAM_BOILER, DEPOT, WHISTLE -> axis = Axis.Y;
            case STATION, SIGNAL -> {
                axis = Axis.Y;
                facing = p.getFacing().getOppositeFace(); // the board faces the player
            }
            // shaft runs sideways: in line with what it was placed against, else left-right as the player sees it
            case STEAM_HAMMER, PUMPJACK, STEAM_ENGINE, CRUSHING_WHEEL -> axis = faceAxis != Axis.Y ? faceAxis
                    : Kinetics.axisOf(p.getFacing()) == Axis.X ? Axis.Z : Axis.X;
            // blows away from what it was placed against (shaft behind it); sneak = blow toward it
            case BELT, CHUTE, FUNNEL -> {
                facing = p.getFacing();                       // the way it is laid out
                axis = Kinetics.axisOf(facing) == Axis.X ? Axis.Z : Axis.X; // rollers across it
            }
            case BELLOWS_FAN, DRILL, SAW, WINDMILL, EJECTOR -> {
                facing = p.isSneaking() ? face.getOppositeFace() : face;
                axis = Kinetics.axisOf(facing);
            }
            case SPEED_GAUGE, STRESS_GAUGE -> facing = p.getFacing().getOppositeFace(); // dials look at the player
            default -> { }
        }

        if ((type == PartType.COGWHEEL || type == PartType.LARGE_COGWHEEL) && !plugin.kinetics().cogsFit(
                new KNode(target.getWorld(), target.getX(), target.getY(), target.getZ(), type, axis, facing))) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("The cogs would collide there.",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return;
        }

        BlockState old = target.getState();
        BlockPlaceEvent ev = new BlockPlaceEvent(target, old, against, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) return;   // world untouched: no ghost barriers behind cancelled events
        target.setType(Material.BARRIER, false);
        KNode n = new KNode(target.getWorld(), target.getX(), target.getY(), target.getZ(), type, axis, facing);
        if (type == PartType.STATION) n.label = plugin.items().customName(hand, type.displayName);
        n.owner = p.getUniqueId();
        plugin.addNode(n);
        lastPlaceTarget = tk;
        lastPlaceNano = now;
        plugin.adv().grant(p, "placed");
        plugin.manual().markPlaced(p, type);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        target.getWorld().playSound(n.center(), type.placeSound(), 1f, 0.9f);
        p.swingMainHand();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof ProbeBreakEvent) return;
        KNode n = plugin.kinetics().at(e.getBlock());
        if (n == null) return;
        e.setCancelled(true);
        plugin.breakNode(n, e.getPlayer(), false);
    }

    /** Creative middle-click on a part used to hand out the raw BARRIER it is built on. */
    @EventHandler(ignoreCancelled = true)
    public void onPick(io.papermc.paper.event.player.PlayerPickBlockEvent e) {
        KNode n = plugin.kinetics().at(e.getBlock());
        if (n == null) return;
        e.setCancelled(true);
        int slot = e.getTargetSlot();
        if (slot >= 0 && slot < e.getPlayer().getInventory().getSize())
            e.getPlayer().getInventory().setItem(slot, plugin.items().create(n.type, 1));
    }

    /** A hopper under a running steam hammer only takes what the hammer can't press (any more). */
    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Hopper h)) return;
        if (plugin.hammers().holdsBack(h.getBlock(), e.getItem().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(plugin.items().recipeKeys);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() == null) return;
        if (!plugin.items().validCraft(e.getRecipe(), e.getInventory().getMatrix())) e.getInventory().setResult(null);
        else if (plugin.items().isOurs(e.getInventory().getResult())) e.getInventory().setResult(plugin.items().refreshModel(e.getInventory().getResult()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (e.getChunk().isLoaded()) plugin.loadChunk(e.getChunk());
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        plugin.unloadChunk(e.getChunk());
    }
}
