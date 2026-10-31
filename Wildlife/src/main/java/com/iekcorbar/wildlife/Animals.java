package com.iekcorbar.wildlife;

import com.destroystokyo.paper.event.entity.TurtleLayEggEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityEnterBlockEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * All loaded wildlife. An animal is a vanilla base mob marked in its data
 * (wildlife:species / rare / model), silent, invisible under its FMM model and
 * cut off from the base mob's own uses (breeding, taming, eggs, horns, saddles).
 * Unnamed animals are removed when their chunk unloads, so they stay scarce and
 * never pile up; a name tag keeps one. One central tick runs the behaviours.
 */
final class Animals implements Listener {
    static final class Animal {
        final LivingEntity entity;
        final Species species;
        final boolean rare;
        final String model;
        final boolean baby;
        boolean attached, curled, sleeping, hiding, waving, frozen, fishing;
        int hunger = 100; // 0-100, 100 = full, 0 = starving
        long busyUntil, tailBack, rideUntil, awakeAt, panicUntil, fleeUntil;
        /** Babies: world time they grow up, and how long was left when a golden dandelion froze them. Adults: love mode (plugin tick) and next breeding (world time). */
        long growAt, frozenLeft, loveUntil, breedAfter, lastFed;
        org.bukkit.Location threat;

        Animal(LivingEntity entity, Species species, boolean rare, String model, boolean baby) {
            this.entity = entity;
            this.species = species;
            this.rare = rare;
            this.model = model;
            this.baby = baby;
        }

        String name() {
            return rare ? species.rareName() : species.displayName;
        }
    }

    private final WildlifePlugin plugin;
    final NamespacedKey speciesKey, rareKey, modelKey, keptKey, babyKey, growKey, frozenKey, breedKey;
    final Breeding breeding;
    private final Map<UUID, Animal> animals = new HashMap<>();
    private boolean warnedModels;
    private final Brain brain;

    Animals(WildlifePlugin plugin) {
        this.plugin = plugin;
        speciesKey = new NamespacedKey(plugin, "species");
        keptKey = new NamespacedKey(plugin, "kept");
        rareKey = new NamespacedKey(plugin, "rare");
        modelKey = new NamespacedKey(plugin, "model");
        babyKey = new NamespacedKey(plugin, "baby");
        growKey = new NamespacedKey(plugin, "grow_at");
        frozenKey = new NamespacedKey(plugin, "frozen_left");
        breedKey = new NamespacedKey(plugin, "breed_after");
        brain = new Brain(plugin);
        breeding = new Breeding(plugin, this);
    }

    Animal get(Entity e) {
        return e == null ? null : animals.get(e.getUniqueId());
    }

    Collection<Animal> all() {
        return animals.values();
    }

    // ------------------------------------------------------------ spawning + loading

    Animal spawn(Species s, Location at, boolean rare) {
        return spawn(s, at, rare, false, null);
    }

    /** look = the adult model to use (null: a random common look, or the rare one); babies get "<look>_baby". */
    Animal spawn(Species s, Location at, boolean rare, boolean baby, String look) {
        // custom creatures are decoration, not animals: there is no _rare and no _baby model,
        // so those flags are off before anything can ask FreeMinecraftModels for them
        final boolean isRare = rare && s.animal();
        final boolean isBaby = baby && s.animal();
        List<String> looks = s.models();
        String adult = look != null ? look : isRare ? s.rareModel() : looks.get(ThreadLocalRandom.current().nextInt(looks.size()));
        String model = isBaby ? adult + "_baby" : adult;
        Entity e = at.getWorld().spawnEntity(at, s.base, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, en -> {
            PersistentDataContainer pdc = en.getPersistentDataContainer();
            pdc.set(speciesKey, PersistentDataType.STRING, s.id());
            pdc.set(rareKey, PersistentDataType.BYTE, (byte) (isRare ? 1 : 0));
            pdc.set(modelKey, PersistentDataType.STRING, model);
            if (isBaby) {
                pdc.set(babyKey, PersistentDataType.BYTE, (byte) 1);
                pdc.set(growKey, PersistentDataType.LONG, en.getWorld().getFullTime() + breeding.growTicks());
            }
            setup((LivingEntity) en, s, isBaby);
        });
        Animal a = register((LivingEntity) e);
        if (a == null || !a.attached) {
            // FMM doesn't know the model (yet): never leave a bare horse/pig standing there
            if (a != null) animals.remove(e.getUniqueId());
            e.remove();
            if (!warnedModels) {
                warnedModels = true;
                plugin.getLogger().warning("Model " + model + " is unknown to FreeMinecraftModels - copy fmm-models/wildlife into "
                        + "plugins/FreeMinecraftModels/models/ and reload FMM. No animals spawn until then.");
            }
            return null;
        }
        warnedModels = false;
        if (s == Species.MOLE && !baby) plugin.molehills().digNear(at);   // a mole comes up somewhere: that is a molehill
        return a;
    }

    private void setup(LivingEntity e, Species s, boolean baby) {
        e.setSilent(true);
        e.setInvisible(true); // the FMM model is what players see
        if (e instanceof Mob m) {
            m.setRemoveWhenFarAway(false); // = persistent: not counted in the vanilla mob cap
            m.setAI(true); // ensure pathfinding is active (some base mobs spawn with AI off)
        }
        // Suppress vanilla base-mob behaviors that fight the FMM model:
        // 1. Horses (DEER, ZEBRA) jump over obstacles on their own, which makes the model look like
        //    it is "springing but not walking". Disable jumping entirely.
        if (e instanceof org.bukkit.entity.Horse h) {
            h.setJumpStrength(0.0); // no jumping at all - it walks around obstacles instead
            h.setDomestication(h.getMaxDomestication()); // fully tamed: no rearing animation
        }
        // 2. Chickens (DUCK, CRANE, OSTRICH, PENGUIN) flap their wings when falling, which can
        //    trigger unwanted FMM animation states. Prevent flapping by zeroing fall distance.
        if (e instanceof org.bukkit.entity.Chicken) {
            e.setFallDistance(0f);
        }
        // 3. Goats (BOAR) can ram - already cancelled via onHarm, but also disable the ram animation.
        if (e instanceof org.bukkit.entity.Goat g) {
            g.setScreaming(false);
        }
        double hp = baby ? Math.max(2, s.health / 2) : s.health;
        set(e, Attribute.MAX_HEALTH, hp);
        e.setHealth(hp);
        set(e, Attribute.MOVEMENT_SPEED, s.speed);
        set(e, Attribute.SCALE, baby ? s.scale * 0.5 : s.scale);   // babies are half size, hitbox included
        if (s.habitat == Species.Habitat.AIR || s.habitat == Species.Habitat.CAVE)
            set(e, Attribute.FLYING_SPEED, s.speed * (s == Species.DRAGON ? 2.6 : 1.6));
        if (s == Species.DRAGON) e.setGravity(false);              // dragons simply fly: no falls, no fall damage
        if (e instanceof Breedable b) {
            b.setAdult();
            b.setAgeLock(true);
            b.setBreed(false);
        }
        if (e instanceof Bee bee) {
            bee.setCannotEnterHiveTicks(Integer.MAX_VALUE);
            bee.setAnger(0);
        }
        // the boar keeps its horns (tusks): a goat without horns does not ram, and the horn drops are cancelled in onDrop anyway
        if (e instanceof Goat g) {
            g.setLeftHorn(true);
            g.setRightHorn(true);
        }
        if (e instanceof Chicken c) c.setEggLayTime(Integer.MAX_VALUE);
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    /** Known if the entity carries our data; puts the model on it when FMM is ready. */
    Animal register(LivingEntity e) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        Species s = Species.byId(pdc.get(speciesKey, PersistentDataType.STRING));
        if (s == null) return null;
        Byte r = pdc.get(rareKey, PersistentDataType.BYTE);
        String model = pdc.getOrDefault(modelKey, PersistentDataType.STRING, s.models().get(0));
        Animal a = new Animal(e, s, r != null && r == 1, model, pdc.has(babyKey, PersistentDataType.BYTE));
        a.growAt = pdc.getOrDefault(growKey, PersistentDataType.LONG, 0L);
        a.breedAfter = pdc.getOrDefault(breedKey, PersistentDataType.LONG, 0L);
        if (pdc.has(frozenKey, PersistentDataType.LONG)) {
            a.frozen = true;
            a.frozenLeft = pdc.get(frozenKey, PersistentDataType.LONG);
        }
        animals.put(e.getUniqueId(), a);
        attach(a);
        brain.apply(a);
        return a;
    }

    private void attach(Animal a) {
        if (a.attached) return;
        if (plugin.fmm().modeled(a.entity)) {
            a.attached = true;
            return;
        }
        a.attached = plugin.fmm().attach(a.entity, a.model);
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en instanceof LivingEntity le && en.getPersistentDataContainer().has(speciesKey, PersistentDataType.STRING)) register(le);
        }
    }

    /** Unnamed animals don't outlive their chunk: wildlife stays scarce and is respawned near players. */
    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) {
            Animal a = animals.remove(en.getUniqueId());
            if (a != null && !kept(en)) en.remove();
        }
    }

    /** A baby grows up: the same animal as an adult (new entity with the adult model, name and "kept" mark carried over). False if it could not. */
    boolean grow(Animal a) {
        LivingEntity old = a.entity;
        String adultModel = a.model.endsWith("_baby") ? a.model.substring(0, a.model.length() - 5) : a.model;
        Location at = old.getLocation();
        old.eject();
        Animal n = spawn(a.species, at, a.rare, false, adultModel);
        if (n == null) return false;
        LivingEntity ne = n.entity;
        ne.setRotation(at.getYaw(), at.getPitch());
        if (old.customName() != null) {
            ne.customName(old.customName());
            ne.setCustomNameVisible(old.isCustomNameVisible());
        }
        if (old.getPersistentDataContainer().has(keptKey, PersistentDataType.BYTE)) ne.getPersistentDataContainer().set(keptKey, PersistentDataType.BYTE, (byte) 1);
        animals.remove(old.getUniqueId());
        old.remove();
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.add(0, ne.getHeight() * 0.5, 0), 12, 0.4, 0.4, 0.4, 0);
        for (org.bukkit.entity.Player pl : at.getWorld().getNearbyPlayers(at, 24)) plugin.adv().grant(pl, "grown_up");
        return true;
    }

    /** A named, leashed or once-leashed animal belongs to someone and stays. */
    boolean kept(Entity en) {
        return en.customName() != null || en.getPersistentDataContainer().has(keptKey, PersistentDataType.BYTE)
                || (en instanceof org.bukkit.entity.Mob m && m.isLeashed());
    }

    /** Putting a lead on an animal keeps it for good (zoo animals tied to a fence post). */
    @EventHandler(ignoreCancelled = true)
    public void onLeash(org.bukkit.event.entity.PlayerLeashEntityEvent e) {
        if (get(e.getEntity()) != null) plugin.adv().grant(e.getPlayer(), "kept");
        if (get(e.getEntity()) != null) e.getEntity().getPersistentDataContainer().set(keptKey, PersistentDataType.BYTE, (byte) 1);
    }

    void removeAllUnnamed() {
        for (Animal a : new ArrayList<>(animals.values())) if (!kept(a.entity)) a.entity.remove();
        animals.clear();
    }

    // ------------------------------------------------------------ behaviour (every 10 ticks)

    void tick(long tick) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Animal a : new ArrayList<>(animals.values())) {
            LivingEntity e = a.entity;
            if (!e.isValid()) {
                animals.remove(e.getUniqueId());
                continue;
            }
            if (!a.attached && tick % 100 == 0) attach(a);
            breeding.tick(a, tick);
            if (!e.isValid()) continue;   // grew up: replaced by an adult
            World w = e.getWorld();
            // Hunger decay: animals get hungry over time (~1 point per 2 min)
            if (tick % 1200 == 0 && a.hunger > 0) a.hunger = Math.max(0, a.hunger - 1);
            // Visual feedback for hungry animals (every 5 min, show smoke particles)
            if (a.hunger <= 20 && tick % 600 == 0) {
                w.spawnParticle(org.bukkit.Particle.SMOKE, e.getLocation().add(0, e.getHeight() + 0.3, 0), 3, 0.2, 0.1, 0.2, 0.01);
            }
            long time = w.getTime();
            boolean night = time > 13000 && time < 23000;
            Player near = nearestPlayer(e, 16);
            if (a.species.ambient != null && near != null && r.nextInt(60) == 0)
                w.playSound(e.getLocation(), a.species.ambient, org.bukkit.SoundCategory.NEUTRAL, a.species.habitat == Species.Habitat.AIR ? 0.3f : 0.7f,
                        a.species.pitch * (0.92f + r.nextFloat() * 0.16f));
            if (near != null && Brain.tame(a) && tick >= a.panicUntil) {
                for (Entity n : e.getNearbyEntities(7, 3, 7)) {
                    if (n instanceof org.bukkit.entity.Monster) {
                        a.panicUntil = tick + 60;
                        a.threat = n.getLocation();
                        break;
                    }
                }
            }
            // General safari shyness (0.6.0): tiered flight radius - an upright/sprinting player
            // inside the animal's radius makes it bolt; a sneaking, still one can watch and record.
            int radius = a.species.fleeRadius();
            if (radius > 0 && tick >= a.busyUntil) {
                Player threat = nearestPlayer(e, radius);
                if (threat != null && (!threat.isSneaking() || threat.isSprinting())) {
                    if (a.species == Species.DEER) w.playSound(e.getLocation(), "wildlife:deer.snort", 0.8f, 1f);
                    flee(e, threat, 2.2 + a.species.shy() * 0.25);
                    for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "first_flee");
                    a.fleeUntil = tick + 40; // any running spyglass observation breaks for 2 s
                    a.busyUntil = tick + 20 + 10 * a.species.shy(); // one reaction per 1-1.5 s, no jittering
                }
                else if (a.species == Species.DEER && r.nextInt(120) == 0 && tick >= a.busyUntil) {
                    plugin.fmm().play(e, "eat", false);
                    a.busyUntil = tick + 50;
                }
            }
            switch (a.species) {
                case HEDGEHOG, SNAIL -> {
                    boolean scared = e instanceof Armadillo ar && ar.getState() != Armadillo.State.IDLE;
                    Player close = nearestPlayer(e, 3);
                    if (close != null && !close.isSneaking()) scared = true;
                    if (scared && !a.curled) {
                        a.curled = true;
                        plugin.fmm().play(e, "curl", true);
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "first_curl");
                        if (e instanceof Mob m) m.setAware(false);
                    } else if (!scared && a.curled) {
                        a.curled = false;
                        plugin.fmm().stop(e);
                        if (e instanceof Mob m) m.setAware(true);
                    }
                }
                case TORTOISE -> {
                    Player close = nearestPlayer(e, 3);
                    boolean hide = close != null && !close.isSneaking();
                    if (hide && !a.hiding) {
                        a.hiding = true;
                        plugin.fmm().play(e, "hide", true);
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "first_curl");
                        w.playSound(e.getLocation(), "wildlife:tortoise.hide", 0.5f, 1f);
                        if (e instanceof Mob m) m.setAware(false);
                    } else if (!hide && a.hiding && tick >= a.busyUntil) {
                        a.hiding = false;
                        plugin.fmm().stop(e);
                        if (e instanceof Mob m) m.setAware(true);
                    }
                    if (hide) a.busyUntil = tick + 60;
                }
                case BEAR -> {
                    boolean calm = !(e instanceof Mob m) || m.getTarget() == null;
                    if (night && calm && !a.sleeping) {
                        a.sleeping = true;
                        plugin.fmm().play(e, "sleep", true);
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "first_sleep");
                        if (e instanceof Mob m) m.setAware(false);
                    } else if ((!night || !calm) && a.sleeping) {
                        wake(a);
                    }
                    if (!a.sleeping && r.nextInt(40) == 0) eatBerries(a, tick);
                    // Bear fishing: sits at the water's edge and comes up with a salmon (sometimes)
                    if (a.fishing) {
                        if (tick >= a.busyUntil) fishResult(a, tick);
                    } else if (calm && !a.sleeping && !night && tick >= a.busyUntil && r.nextInt(30) == 0) {
                        Location water = waterNear(e, 5);
                        if (water != null) startFishing(a, water, tick);
                    }
                }
                case KOALA -> {
                    // koalas nap in the sun and wake on their own (or when something happens)
                    boolean calm2 = !(e instanceof Mob mm) || mm.getTarget() == null;
                    if (!a.sleeping && !night && calm2 && r.nextInt(200) == 0) {
                        a.sleeping = true;
                        plugin.fmm().play(e, "sleep", true);
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "first_sleep");
                        if (e instanceof Mob m2) m2.setAware(false);
                    } else if (a.sleeping && (night || !calm2 || r.nextInt(500) == 0)) {
                        wake(a);
                    }
                }
                case FIREFLY -> {
                    if (!night) {
                        e.remove();
                        continue;
                    }
                    if (r.nextInt(3) == 0) {
                        Particle.DustOptions dust = new Particle.DustOptions(a.rare ? org.bukkit.Color.fromRGB(120, 220, 255)
                                : org.bukkit.Color.fromRGB(250, 236, 110), 0.6f);
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.15, 0), 1, 0.02, 0.02, 0.02, 0, dust);
                    }
                }
                case WRAITH -> {
                    // A placed atmosphere piece, not an animal (0.7.0): it owns the dark and the
                    // morning takes it away, exactly like the fireflies do. No fight, no flight.
                    if (night) {
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "wraith_night");
                    }
                    if (!night) {
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "wraith_dissolve");
                        for (int i = 0; i < 5; i++)   // it unravels into soul flames at dawn
                            w.spawnParticle(Particle.SOUL, e.getLocation().add(0, 0.3 + r.nextDouble() * 1.1, 0), 1, 0.25, 0.2, 0.25, 0.0);
                        e.remove();
                        continue;
                    }
                    // slow dwarreling: a small random lift now and then, capped so it never takes off
                    Vector v = e.getVelocity();
                    e.setVelocity(v.setY(Math.max(-0.04, Math.min(0.05, v.getY() + (r.nextDouble() - 0.5) * 0.05))));
                    // mist off the hem, a cold spark at the hood, both modest
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.SMOKE, e.getLocation().add(0, 0.2 + r.nextDouble() * 0.5, 0), 1, 0.3, 0.15, 0.3, 0.0);
                    if (r.nextInt(12) == 0)
                        w.spawnParticle(Particle.SOUL, e.getLocation().add(0, 1.25, 0), 1, 0.12, 0.1, 0.12, 0.0);
                }
                case CRAB -> {
                    // waves its big claw at a player who comes close, turned to face them
                    Player close = nearestPlayer(e, 6);
                    if (close != null && tick >= a.busyUntil && r.nextInt(3) == 0) {
                        Vector to = close.getLocation().toVector().subtract(e.getLocation().toVector());
                        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
                        // the model's face looks along its -X: turn the body a quarter so that side faces the player
                        e.setRotation(yaw + 90, 0);
                        if (e instanceof Mob m) m.setAware(false);
                        plugin.fmm().play(e, "wave", false);
                        w.playSound(e.getLocation(), "wildlife:crab.wave", 0.6f, 1f);
                        a.busyUntil = tick + 120;
                        a.waving = true;
                        a.awakeAt = tick + 32;
                    } else if (a.waving && tick >= a.awakeAt) {
                        a.waving = false;
                        if (e instanceof Mob m) m.setAware(true);
                    }
                }
                case CAPYBARA -> {
                    if (!e.getPassengers().isEmpty() && tick > a.rideUntil) e.eject();
                    else if (e.getPassengers().size() < 5 && r.nextInt(400) == 0) {
                        for (Entity n : e.getNearbyEntities(6, 3, 6)) {
                            Animal o = get(n);
                            if (o != null && (o.species == Species.DUCK || o.species == Species.SONGBIRD) && !n.isInsideVehicle()) {
                                e.addPassenger(n);
                                a.rideUntil = tick + 1200;
                                for (org.bukkit.entity.Player pl : e.getWorld().getNearbyPlayers(e.getLocation(), 24)) plugin.adv().grant(pl, "capy_love");
                                int riders = 0;
                                for (Entity ps : e.getPassengers()) {
                                    Animal q = get(ps);
                                    if (q != null && (q.species == Species.DUCK || q.species == Species.SONGBIRD)) riders++;
                                }
                                if (riders >= 5) {   // joke: the bus is full
                                    e.getWorld().playSound(e.getLocation(), "minecraft:block.bell.use", 1.2f, 0.9f);
                                    for (org.bukkit.entity.Player pl : e.getWorld().getNearbyPlayers(e.getLocation(), 24)) {
                                        pl.sendActionBar(net.kyori.adventure.text.Component.text("Next stop: pond.", net.kyori.adventure.text.format.NamedTextColor.GOLD));
                                        plugin.adv().grant(pl, "pond_stop");
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                case ELEPHANT -> {
                    // an elephant jumps when a tiny animal scuttles by
                    if (r.nextInt(30) == 0 && e.isOnGround()) {
                        for (Entity n : e.getNearbyEntities(2.5, 1.5, 2.5)) {
                            Animal o = get(n);
                            if (o == null || (o.species != Species.HEDGEHOG && o.species != Species.LIZARD && o.species != Species.SNAIL && o.species != Species.CRAB)) continue;
                            e.setVelocity(e.getVelocity().setY(0.45));
                            e.getWorld().playSound(e.getLocation(), "wildlife:elephant.hurt", 1f, 1.3f);
                            for (org.bukkit.entity.Player pl : e.getWorld().getNearbyPlayers(e.getLocation(), 24)) plugin.adv().grant(pl, "tiny_scare");
                            break;
                        }
                    }
                }
                case TANUKI -> {
                    // joke: at night a tanuki winks at a player who stands very still
                    long wt = e.getWorld().getTime();
                    if (wt > 13000 && wt < 23000 && tick > a.rideUntil && r.nextInt(30) == 0) {
                        Player close = nearestPlayer(e, 6);
                        if (close != null && close.getLocation().distanceSquared(e.getLocation()) <= 36
                                && close.getVelocity().lengthSquared() < 0.01 && !close.isSprinting()) {
                            a.rideUntil = tick + 600;
                            e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, 0.7, 0), 1,
                                    0.04, 0.02, 0.04, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 214, 120), 0.5f));
                            e.getWorld().playSound(e.getLocation(), "minecraft:entity.fox.ambient", 0.4f, 1.5f);
                            close.sendActionBar(net.kyori.adventure.text.Component.text("You've made a friend.", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                            plugin.adv().grant(close, "tanuki_wink");
                        }
                    }
                }
                case BUTTERFLY -> pollinate(a, r);
                default -> { }
            }
            if (e instanceof Bee bee && bee.getAnger() > 0) bee.setAnger(0);
        }
    }

    private void wake(Animal a) {
        a.sleeping = false;
        plugin.fmm().stop(a.entity);
        if (a.entity instanceof Mob m) m.setAware(true);
    }

    private void eatBerries(Animal a, long tick) {
        if (tick < a.busyUntil) return;
        Location l = a.entity.getLocation();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = l.getBlock().getRelative(dx, 0, dz);
                if (b.getType() == Material.SWEET_BERRY_BUSH && b.getBlockData() instanceof Ageable ag && ag.getAge() >= 2) {
                    ag.setAge(1);
                    b.setBlockData(ag);
                    plugin.fmm().play(a.entity, "eat", false);
                    a.entity.getWorld().playSound(b.getLocation(), "block.sweet_berry_bush.pick_berries", 0.8f, 0.8f);
                    a.busyUntil = tick + 600;
                    return;
                }
            }
        }
    }

    /** The nearest water block within {@code radius} of the animal (its bank included), or null. */
    private static Location waterNear(LivingEntity e, int radius) {
        Location c = e.getLocation();
        World w = c.getWorld();
        int bx = c.getBlockX(), by = c.getBlockY(), bz = c.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.hypot(dx, dz) > radius) continue;
                for (int dy = 1; dy >= -1; dy--) {
                    Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
                    if (b.getType() == Material.WATER) return b.getLocation();
                }
            }
        }
        return null;
    }

    /** Bear fishing: it settles at the water's edge, paws dip in (the eat anim), and a few seconds later it has caught a salmon. */
    private void startFishing(Animal a, Location water, long tick) {
        if (!(a.entity instanceof Mob m)) return;
        Location spot = water.clone().add(0.5, 1.0, 0.5);
        Vector to = spot.toVector().subtract(a.entity.getLocation().toVector());
        a.entity.setRotation((float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ())), 0);
        m.getPathfinder().moveTo(spot.clone().subtract(to.clone().normalize().multiply(1.2)), 1.0);
        a.fishing = true;
        a.busyUntil = tick + 60;
        a.entity.getWorld().playSound(spot, "entity.generic.splash", 0.5f, 1.1f);
        plugin.fmm().play(a.entity, "eat", false);
    }

    /** The catch: water particles, a splash, a salmon flopping onto the bank - and a witness gets a hidden advancement. */
    private void fishResult(Animal a, long tick) {
        a.fishing = false;
        a.busyUntil = tick + 100;
        Location at = a.entity.getLocation();
        World w = at.getWorld();
        if (ThreadLocalRandom.current().nextInt(100) < 65) {
            w.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.4, 0), 14, 0.25, 0.15, 0.25, 0.2);
            w.playSound(at, "entity.salmon.flop", 0.7f, 0.9f);
            plugin.fmm().play(a.entity, "eat", false);
            w.dropItem(at.clone().add(0, 0.5, 0), new ItemStack(Material.SALMON));
            for (org.bukkit.entity.Player pl : w.getNearbyPlayers(at, 16)) plugin.adv().grant(pl, "first_fish");
            for (Player p : w.getNearbyPlayers(at, 16)) plugin.adv().grant(p, "fisher");
        }
    }

    /**
     * Butterfly pollination: a monarch that rests on a flower nudges the crops below it a growth stage
     * further and sometimes drops a new flower seed on the grass beside it. Fields with butterflies ripen
     * visibly faster - the naturalist's best friend.
     */
    private void pollinate(Animal a, ThreadLocalRandom r) {
        if (r.nextInt(8) != 0) return;   // one nudge per animal every four seconds on average, near flowers
        Location c = a.entity.getLocation();
        World w = c.getWorld();
        int bx = c.getBlockX(), by = c.getBlockY(), bz = c.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block soil = w.getBlockAt(bx + dx, by - 1, bz + dz);
                Material t = soil.getType();
                // a ripening crop under the butterfly gets one growth stage
                if ((t == Material.WHEAT || t == Material.CARROTS || t == Material.POTATOES || t == Material.BEETROOTS)
                        && soil.getBlockData() instanceof Ageable ag && ag.getAge() < ag.getMaximumAge()) {
                    ag.setAge(ag.getAge() + 1);
                    soil.setBlockData(ag);
                    pollinated(soil, r);
                    return;
                }
                // flower the butterfly sits on spreads itself to bare grass nearby
                if (Tag.FLOWERS.isTagged(t) && r.nextInt(3) == 0) {
                    for (int i = 0; i < 4; i++) {
                        Block g = w.getBlockAt(bx + dx + r.nextInt(3) - 1, by - 1, bz + dz + r.nextInt(3) - 1);
                        Block air = g.getRelative(0, 1, 0);
                        if (g.getType() == Material.GRASS_BLOCK && air.getType() == Material.AIR && Tag.FLOWERS.isTagged(t)) {
                            air.setType(t);
                            w.spawnParticle(Particle.HAPPY_VILLAGER, air.getLocation().add(0.5, 0.6, 0.5), 4, 0.2, 0.1, 0.2, 0);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void pollinated(Block crop, ThreadLocalRandom r) {
        Location c = crop.getLocation().add(0.5, 1.1, 0.5);
        crop.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c, 3, 0.15, 0.1, 0.15, 0);
        if (r.nextInt(4) != 0) return;   // the advancement only pops sometimes, not on every nudge
        for (Player p : crop.getWorld().getNearbyPlayers(c, 12)) plugin.adv().grant(p, "pollinator");
    }

    private static void flee(LivingEntity e, Player from, double speed) {
        if (!(e instanceof Mob m)) return;
        Vector away = e.getLocation().toVector().subtract(from.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
        Location to = e.getLocation().add(away.normalize().multiply(10));
        m.getPathfinder().moveTo(to, speed);
    }

    private static Player nearestPlayer(LivingEntity e, double r) {
        Player best = null;
        double bd = r * r;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ keep the base mob from being itself

    /** Right-click: name tags and leads work (they keep the animal); food and golden dandelions are for breeding; everything else is cancelled. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        Animal a = get(e.getRightClicked());
        if (a == null) return;
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (hand.getType() == Material.AIR) {
            plugin.adv().grant(e.getPlayer(), "first_pet");
        }
        if (hand.getType() == Material.NAME_TAG) {
            plugin.adv().grant(e.getPlayer(), "named");
            return; // name tags keep an animal
        }
        if (hand.getType() == Material.LEAD) return; // leads keep an animal
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) {
            // dragons answer to coal (taming) and to their owner's empty hand (sit/ride) before breeding thinks of it
            if (a.species == Species.DRAGON && plugin.dragon().interact(a, e.getPlayer(), hand, e.getPlayer().isSneaking())) return;
            breeding.use(a, e.getPlayer(), hand);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (get(e.getRightClicked()) == null) return;
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (hand.getType() == Material.NAME_TAG || hand.getType() == Material.LEAD) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHurtPanic(org.bukkit.event.entity.EntityDamageEvent e) {
        Animal a = get(e.getEntity());
        if (a == null || !Brain.tame(a)) return;
        a.panicUntil = plugin.tick + 100;
        a.threat = e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent d ? d.getDamager().getLocation() : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent e) {
        if (get(e.getEntity()) != null) e.setCancelled(true); // eggs, scutes, goat horns
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (get(e.getMother()) != null || get(e.getFather()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (get(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (get(e.getEntity()) != null) e.setCancelled(true); // pig -> piglin etc.
    }

    @EventHandler(ignoreCancelled = true)
    public void onEgg(TurtleLayEggEvent e) {
        if (get(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHive(EntityEnterBlockEvent e) {
        if (get(e.getEntity()) != null) e.setCancelled(true);
    }

    /** Only the bear and the boar ever fight back; every other animal (birds, bees underneath, ...) never picks a target. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        Animal a = get(e.getEntity());
        if (a != null && (a.entity instanceof Bee || !Brain.dangerous(a))) e.setCancelled(true);
    }

    /** Safety net: whatever the base mob would do, a harmless animal never hurts anyone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHarm(EntityDamageByEntityEvent e) {
        Animal a = get(e.getDamager());
        if (a != null && !Brain.dangerous(a)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHurt(EntityDamageEvent e) {
        Animal a = get(e.getEntity());
        if (a == null) return;
        if (a.species.hurt != null) e.getEntity().getWorld().playSound(e.getEntity().getLocation(), a.species.hurt, 0.8f, a.species.pitch);
        if (a.sleeping) wake(a);
        if (a.species == Species.LIZARD && e instanceof EntityDamageByEntityEvent && plugin.tick >= a.tailBack) {
            a.tailBack = plugin.tick + 20 * 300; // grows back in 5 minutes
            e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation(), plugin.items().lizardTail());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Animal a = animals.remove(e.getEntity().getUniqueId());
        if (a == null) return;
        e.getDrops().clear();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ItemStack> d = e.getDrops();
        if (a.baby) {   // babies drop nothing
            e.setDroppedExp(0);
            if (a.species.death != null) e.getEntity().getWorld().playSound(e.getEntity().getLocation(), a.species.death, 0.9f, a.species.pitch * 1.4f);
            return;
        }
        switch (a.species) {
            case ELEPHANT -> { d.add(new ItemStack(Material.LEATHER, 2 + r.nextInt(3))); d.add(new ItemStack(Material.BEEF, 2 + r.nextInt(4))); }
            case ZEBRA -> { add(d, Material.LEATHER, 1 + r.nextInt(2)); add(d, Material.BEEF, r.nextInt(3)); }
            case DEER -> { d.add(new ItemStack(Material.MUTTON, 1 + r.nextInt(2))); add(d, Material.LEATHER, r.nextInt(3)); }
            case BEAR -> { d.add(new ItemStack(Material.LEATHER, 1 + r.nextInt(3))); add(d, Material.SALMON, r.nextInt(2)); }
            case BOAR -> { d.add(new ItemStack(Material.PORKCHOP, 1 + r.nextInt(3))); add(d, Material.LEATHER, r.nextInt(2)); }
            case DUCK -> { d.add(new ItemStack(Material.CHICKEN)); d.add(new ItemStack(Material.FEATHER, 1 + r.nextInt(2))); }
            case SNAIL -> { if (r.nextInt(3) == 0) d.add(new ItemStack(Material.SLIME_BALL)); }
            case CAPYBARA -> add(d, Material.LEATHER, r.nextInt(3));
            case TORTOISE -> { if (r.nextInt(4) == 0) d.add(new ItemStack(Material.TURTLE_SCUTE)); }
            case CRAB -> { if (r.nextInt(100) < 15) d.add(plugin.items().crabClaw()); }
            case SONGBIRD, OSTRICH, MACAW -> add(d, Material.FEATHER, 1 + r.nextInt(a.species == Species.SONGBIRD ? 2 : 3));
            case CRANE -> add(d, Material.FEATHER, 1 + r.nextInt(2));
            case TANUKI, MACAQUE -> { }   // foragers, not farms: they drop nothing
            case DRAGON -> {               // scrap from a broken machine, never meat
                add(d, Material.COPPER_INGOT, r.nextInt(3));
                add(d, Material.COAL, r.nextInt(2));
            }
            default -> { }
        }
        // Hunter's trophy (0.6.2): whoever recorded an animal in the Field Journal and then brings one down
        // may take its statue home - common 10 %, rare 30 %. Observing first is the naturalist's deal.
        Player killer = e.getEntity().getKiller();
        if (killer != null && r.nextInt(100) < (a.rare ? 30 : 10) && plugin.journal().seen(killer, false).contains(a.species.id())) {
            DecorDef trophy = plugin.zoo().decor.defs.get("animal_" + a.species.id() + (a.rare ? "_rare" : ""));
            if (trophy != null) {
                d.add(plugin.zoo().decor.item(trophy, 1));
                plugin.adv().grant(killer, "trophy");
            }
        }
        boolean tiny = a.species.habitat == Species.Habitat.AIR || a.species == Species.SNAIL || a.species == Species.HEDGEHOG || a.species.habitat == Species.Habitat.POND;
        if (!d.isEmpty()) for (org.bukkit.entity.Player pl : e.getEntity().getWorld().getNearbyPlayers(e.getEntity().getLocation(), 16)) plugin.adv().grant(pl, "first_item");
        e.setDroppedExp(tiny ? 0 : 1 + r.nextInt(3));
        if (a.species.death != null) e.getEntity().getWorld().playSound(e.getEntity().getLocation(), a.species.death, 0.9f, a.species.pitch);
    }

    private static void add(List<ItemStack> d, Material m, int n) {
        if (n > 0) d.add(new ItemStack(m, n));
    }

    int count() {
        return animals.size();
    }

    int countNear(Location at, double radius, Species only) {
        int n = 0;
        double r2 = radius * radius;
        for (Animal a : animals.values()) {
            if (a.entity.getWorld() != at.getWorld()) continue;
            if (only != null && a.species != only) continue;
            if (a.entity.getLocation().distanceSquared(at) <= r2) n++;
        }
        return n;
    }
}
