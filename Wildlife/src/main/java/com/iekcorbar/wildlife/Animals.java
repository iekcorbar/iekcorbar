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
        int ageStage = 0; // 0=baby, 1=adult, 2=senior
        long busyUntil, tailBack, rideUntil, awakeAt, panicUntil, fleeUntil;
        /** Babies: world time they grow up, and how long was left when a golden dandelion froze them. Adults: love mode (plugin tick) and next breeding (world time). */
        long growAt, frozenLeft, loveUntil, breedAfter, lastFed, birthTime;
        org.bukkit.Location threat;

        Animal(LivingEntity entity, Species species, boolean rare, String model, boolean baby) {
            this.entity = entity;
            this.species = species;
            this.rare = rare;
            this.model = model;
            this.baby = baby;
            this.birthTime = System.currentTimeMillis();
            this.ageStage = baby ? 0 : 1;
        }

        String name() {
            if (species == Species.DRAGON && model != null && model.startsWith("wl_dragon_ender")) return "Ender Dragon";
            return rare ? species.rareName() : species.displayName;
        }
    }

    private final WildlifePlugin plugin;
    final NamespacedKey autoNameKey, hungerKey, speciesKey, rareKey, modelKey, keptKey, babyKey, growKey, frozenKey, breedKey, birthKey, ageKey, trickKey;
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
    birthKey = new NamespacedKey(plugin, "birth_time");
    ageKey = new NamespacedKey(plugin, "age_stage");
    hungerKey = new NamespacedKey(plugin, "hunger");
    autoNameKey = new NamespacedKey(plugin, "auto_name");
    trickKey = new NamespacedKey(plugin, "trick_cooldown");
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
        // the Ender look: an extremely rare Steam Dragon (config dragon.ender-chance, default 0.3%); it counts as rare
        final boolean ender = s == Species.DRAGON && look == null && !baby
                && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("dragon.ender-chance", 0.003);
        final boolean isRare = (rare && s.animal()) || ender;
        final boolean isBaby = baby && s.animal();
        List<String> looks = s.models();
        String adult = look != null ? look : ender ? "wl_dragon_ender" : isRare ? s.rareModel() : looks.get(ThreadLocalRandom.current().nextInt(looks.size()));
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
            pdc.set(birthKey, PersistentDataType.LONG, System.currentTimeMillis());
            pdc.set(ageKey, PersistentDataType.INTEGER, isBaby ? 0 : 1);
            setup((LivingEntity) en, s, isBaby);
            // a plain (hidden) name so death messages and kill logs say "Steam Dragon", not "Parrot"
            String nm = ender ? "Ender Dragon" : isRare ? s.rareName() : s.displayName;
            en.customName(net.kyori.adventure.text.Component.text(nm));
            en.setCustomNameVisible(false);
            pdc.set(autoNameKey, PersistentDataType.STRING, nm);
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
        // custom creatures (sfeerwezens): strip any default equipment the base mob carries
        // (vindicators spawn with an iron axe) so nothing floats in the air
        if (e instanceof org.bukkit.entity.Vindicator v) {
            v.getEquipment().clear();
        }
        if (e instanceof org.bukkit.entity.Allay al) {
            al.getEquipment().clear();
        }
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
        a.birthTime = pdc.getOrDefault(birthKey, PersistentDataType.LONG, System.currentTimeMillis());
        a.ageStage = pdc.getOrDefault(ageKey, PersistentDataType.INTEGER, a.baby ? 0 : 1);
        a.hunger = pdc.getOrDefault(hungerKey, PersistentDataType.INTEGER, 100);
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
        if (named(old)) {
            ne.customName(old.customName());
            ne.setCustomNameVisible(old.isCustomNameVisible());
        }
        if (old.getPersistentDataContainer().has(keptKey, PersistentDataType.BYTE)) ne.getPersistentDataContainer().set(keptKey, PersistentDataType.BYTE, (byte) 1);
        for (String k : new String[]{"dragon_tamer", "dragon_bag"}) {      // a grown dragon stays its owner's (and keeps its saddlebags)
            NamespacedKey nk = new NamespacedKey(plugin, k);
            if (old.getPersistentDataContainer().has(nk, PersistentDataType.STRING))
                ne.getPersistentDataContainer().set(nk, PersistentDataType.STRING, old.getPersistentDataContainer().get(nk, PersistentDataType.STRING));
            else if (old.getPersistentDataContainer().has(nk, PersistentDataType.BYTE_ARRAY))
                ne.getPersistentDataContainer().set(nk, PersistentDataType.BYTE_ARRAY, old.getPersistentDataContainer().get(nk, PersistentDataType.BYTE_ARRAY));
        }
        animals.remove(old.getUniqueId());
        old.remove();
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.add(0, ne.getHeight() * 0.5, 0), 12, 0.4, 0.4, 0.4, 0);
        for (org.bukkit.entity.Player pl : at.getWorld().getNearbyPlayers(at, 24)) plugin.adv().grant(pl, "grown_up");
        return true;
    }

    /** A new coat of paint: the same dragon with another look (a new entity; owner, saddlebags, name, hunger and sitting carry over). */
    boolean reskin(Animal a, String look) {
        LivingEntity old = a.entity;
        Location at = old.getLocation();
        old.eject();
        Animal n = spawn(a.species, at, false, a.baby, look);
        if (n == null) return false;
        LivingEntity ne = n.entity;
        ne.setRotation(at.getYaw(), at.getPitch());
        var from = old.getPersistentDataContainer();
        var to = ne.getPersistentDataContainer();
        if (named(old)) {
            ne.customName(old.customName());
            ne.setCustomNameVisible(old.isCustomNameVisible());
        }
        for (String k : new String[]{"dragon_tamer", "dragon_bag"}) {
            NamespacedKey nk = new NamespacedKey(plugin, k);
            if (from.has(nk, PersistentDataType.STRING)) to.set(nk, PersistentDataType.STRING, from.get(nk, PersistentDataType.STRING));
            else if (from.has(nk, PersistentDataType.BYTE_ARRAY)) to.set(nk, PersistentDataType.BYTE_ARRAY, from.get(nk, PersistentDataType.BYTE_ARRAY));
        }
        NamespacedKey sit = new NamespacedKey(plugin, "dragon_sitting");
        if (from.has(sit, PersistentDataType.BYTE)) to.set(sit, PersistentDataType.BYTE, from.get(sit, PersistentDataType.BYTE));
        if (from.has(keptKey, PersistentDataType.BYTE)) to.set(keptKey, PersistentDataType.BYTE, (byte) 1);
        if (from.has(growKey, PersistentDataType.LONG)) to.set(growKey, PersistentDataType.LONG, from.get(growKey, PersistentDataType.LONG));
        if (from.has(breedKey, PersistentDataType.LONG)) to.set(breedKey, PersistentDataType.LONG, from.get(breedKey, PersistentDataType.LONG));
        n.hunger = a.hunger;
        to.set(hungerKey, PersistentDataType.INTEGER, a.hunger);
        n.growAt = a.growAt;
        n.breedAfter = a.breedAfter;
        animals.remove(old.getUniqueId());
        old.remove();
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.add(0, ne.getHeight() * 0.5, 0), 14, 0.4, 0.4, 0.4, 0);
        return true;
    }

    /** True when a player named it (a name tag); our own hidden species name does not count. */
    boolean named(Entity en) {
        if (en.customName() == null) return false;
        String auto = en.getPersistentDataContainer().get(autoNameKey, PersistentDataType.STRING);
        return auto == null || !auto.equals(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(en.customName()));
    }

    /** A named, leashed or once-leashed animal belongs to someone and stays. */
    boolean kept(Entity en) {
        return named(en) || en.getPersistentDataContainer().has(keptKey, PersistentDataType.BYTE)
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
            // only animals that belong to someone (name tag, lead) get hungry: wild ones find their own food
            boolean owned = kept(e);
            if (owned && tick % 1200 == 0 && a.hunger > 0) {
                a.hunger = Math.max(0, a.hunger - 1);
                e.getPersistentDataContainer().set(hungerKey, PersistentDataType.INTEGER, a.hunger);
            }
            // Hunger effects: slow movement when hungry
            if (owned && a.hunger <= 20) {
                if (e instanceof org.bukkit.entity.Mob mob) {
                    mob.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 200, 1, false, false));
                }
            }
            // Visual feedback for hungry animals (every 5 min, show smoke particles)
            if (owned && a.hunger <= 20 && tick % 600 == 0) {
                w.spawnParticle(org.bukkit.Particle.SMOKE, e.getLocation().add(0, e.getHeight() + 0.3, 0), 3, 0.2, 0.1, 0.2, 0.01);
            }
            // Sick state: when hunger = 0, show poison particles
            if (owned && a.hunger == 0 && tick % 600 == 0) {
                w.spawnParticle(org.bukkit.Particle.ENTITY_EFFECT, e.getLocation().add(0, e.getHeight() + 0.3, 0), 5, 0.2, 0.1, 0.2, 0, org.bukkit.Color.fromRGB(50, 200, 50));
            }
            // Aging: baby -> adult -> senior
            long ageMs = System.currentTimeMillis() - a.birthTime;
            if (a.ageStage == 0 && !a.baby && ageMs > 0) {
                a.ageStage = 1; // grew up
            }
            if (a.ageStage == 1 && ageMs > 30 * 60 * 1000) { // 30 minutes
                a.ageStage = 2; // senior
                e.getPersistentDataContainer().set(ageKey, PersistentDataType.INTEGER, 2);
                w.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, e.getLocation().add(0, e.getHeight() + 0.3, 0), 10, 0.3, 0.2, 0.3, 0);
                w.playSound(e.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "senior_animal");
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
                    if (night) {
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "wraith_night");
                    }
                    if (!night) {
                        for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 16)) plugin.adv().grant(pl, "wraith_dissolve");
                        for (int i = 0; i < 5; i++)
                            w.spawnParticle(Particle.SOUL, e.getLocation().add(0, 0.3 + r.nextDouble() * 1.1, 0), 1, 0.25, 0.2, 0.25, 0.0);
                        e.remove();
                        continue;
                    }
                    Vector v = e.getVelocity();
                    e.setVelocity(v.setY(Math.max(-0.04, Math.min(0.05, v.getY() + (r.nextDouble() - 0.5) * 0.05))));
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.SMOKE, e.getLocation().add(0, 0.2 + r.nextDouble() * 0.5, 0), 1, 0.3, 0.15, 0.3, 0.0);
                    if (r.nextInt(12) == 0)
                        w.spawnParticle(Particle.SOUL, e.getLocation().add(0, 1.25, 0), 1, 0.12, 0.1, 0.12, 0.0);
                }
                case WILLOWISP -> {
                    if (!night) {
                        for (int i = 0; i < 6; i++)
                            w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.3 + r.nextDouble() * 0.6, 0), 1, 0.15, 0.1, 0.15, 0.02);
                        e.remove();
                        continue;
                    }
                    // drift slowly toward the nearest player, luring them deeper
                    Player nearP = nearestPlayer(e, 16);
                    if (nearP != null && r.nextInt(4) == 0) {
                        Vector to = nearP.getLocation().toVector().subtract(e.getLocation().toVector());
                        double len = to.length();
                        if (len > 0.5) {
                            to.normalize().multiply(0.06);
                            e.setVelocity(e.getVelocity().add(to).setY(Math.max(-0.02, Math.min(0.04, e.getVelocity().getY() + (r.nextDouble() - 0.5) * 0.03))));
                        }
                    } else {
                        Vector vv = e.getVelocity();
                        e.setVelocity(vv.setY(Math.max(-0.03, Math.min(0.04, vv.getY() + (r.nextDouble() - 0.5) * 0.04))));
                    }
                    // blue glow
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.2, 0), 1, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 200, 255), 1.1f));
                    if (r.nextInt(8) == 0)
                        w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0.01);
                    // vanish if a player gets too close
                    Player close = nearestPlayer(e, 2);
                    if (close != null) {
                        for (int i = 0; i < 8; i++)
                            w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.4, 0), 1, 0.2, 0.2, 0.2, 0.03);
                        w.playSound(e.getLocation(), "entity.allay.hurt", 0.6f, 1.6f);
                        e.remove();
                        continue;
                    }
                }
                case STEAMGOLEM -> {
                    // steam vents on the back, occasionally a heavy step sound
                    if (r.nextInt(3) == 0)
                        w.spawnParticle(Particle.CLOUD, e.getLocation().add(0, 1.6, -0.4), 1, 0.15, 0.2, 0.15, 0.02);
                    if (r.nextInt(40) == 0)
                        w.playSound(e.getLocation(), "entity.iron_golem.step", 0.7f, 0.6f);
                    if (r.nextInt(60) == 0)
                        w.playSound(e.getLocation(), "block.fire.extinguish", 0.4f, 1.2f);
                    // slow turn toward a nearby player
                    Player p2 = nearestPlayer(e, 10);
                    if (p2 != null && r.nextInt(6) == 0) {
                        Vector to = p2.getLocation().toVector().subtract(e.getLocation().toVector());
                        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
                        e.setRotation(yaw + 90, 0);
                    }
                }
                case ONI -> {
                    if (!night) {
                        for (int i = 0; i < 6; i++)
                            w.spawnParticle(Particle.LARGE_SMOKE, e.getLocation().add(0, 1.0 + r.nextDouble() * 0.8, 0), 1, 0.2, 0.2, 0.2, 0.01);
                        e.remove();
                        continue;
                    }
                    // red glow eyes + faint smoke
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 1.7, 0), 1, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 40, 40), 0.9f));
                    if (r.nextInt(6) == 0)
                        w.spawnParticle(Particle.LARGE_SMOKE, e.getLocation().add(0, 0.6, 0), 1, 0.2, 0.15, 0.2, 0.0);
                    // occasional laugh
                    if (r.nextInt(120) == 0)
                        w.playSound(e.getLocation(), "entity.vindicator.ambient", 0.7f, 0.7f);
                    // keep distance: drift away from the nearest player if too close
                    Player pc = nearestPlayer(e, 6);
                    if (pc != null && r.nextInt(3) == 0) {
                        Vector away = e.getLocation().toVector().subtract(pc.getLocation().toVector());
                        if (away.length() > 0.1) {
                            away.normalize().multiply(0.08);
                            e.setVelocity(e.getVelocity().add(away).setY(e.getVelocity().getY()));
                        }
                    }
                }
                case KITSUNE -> {
                    if (!night) {
                        for (int i = 0; i < 8; i++)
                            w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.6 + r.nextDouble() * 0.8, 0), 1, 0.25, 0.2, 0.25, 0.02);
                        e.remove();
                        continue;
                    }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.9, 0), 1, 0.15, 0.2, 0.15, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 220, 255), 1.0f));
                    if (r.nextInt(4) == 0)
                        w.spawnParticle(Particle.CHERRY_LEAVES, e.getLocation().add(0, 1.0, 0), 1, 0.3, 0.2, 0.3, 0.0);
                    if (r.nextInt(90) == 0)
                        w.playSound(e.getLocation(), "entity.fox.ambient", 0.6f, 1.3f);
                    Player pk = nearestPlayer(e, 5);
                    if (pk != null && r.nextInt(3) == 0) {
                        Vector away = e.getLocation().toVector().subtract(pk.getLocation().toVector());
                        if (away.length() > 0.1) {
                            away.normalize().multiply(0.07);
                            e.setVelocity(e.getVelocity().add(away));
                        }
                    }
                }
                case KAPPA -> {
                    if (r.nextInt(3) == 0)
                        w.spawnParticle(Particle.SPLASH, e.getLocation().add(0, 0.6, 0), 3, 0.2, 0.15, 0.2, 0.05);
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.8, 0), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 180, 160), 0.9f));
                    // pull nearby swimmers gently toward it
                    for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 6)) {
                        if (pl.isInWater() && r.nextInt(4) == 0) {
                            Vector to = e.getLocation().toVector().subtract(pl.getLocation().toVector());
                            if (to.length() < 6 && to.length() > 0.5) {
                                to.normalize().multiply(0.05);
                                pl.setVelocity(pl.getVelocity().add(to));
                            }
                        }
                    }
                    if (r.nextInt(100) == 0)
                        w.playSound(e.getLocation(), "entity.turtle.ambient_land", 0.6f, 0.9f);
                }
                case TENGU -> {
                    if (!night) {
                        for (int i = 0; i < 5; i++)
                            w.spawnParticle(Particle.CLOUD, e.getLocation().add(0, 0.5 + r.nextDouble() * 0.8, 0), 1, 0.3, 0.2, 0.3, 0.02);
                        e.remove();
                        continue;
                    }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 1.2, 0), 1, 0.15, 0.2, 0.15, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 60, 40), 1.0f));
                    if (r.nextInt(3) == 0)
                        w.spawnParticle(Particle.CLOUD, e.getLocation().add(0, 0.8, 0), 1, 0.4, 0.2, 0.4, 0.02);
                    // push nearby players up and away
                    for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 8)) {
                        if (r.nextInt(6) == 0) {
                            pl.setVelocity(pl.getVelocity().setY(Math.max(pl.getVelocity().getY(), 0.15)));
                            pl.getWorld().playSound(pl.getLocation(), "entity.phantom.flap", 0.4f, 1.2f);
                        }
                    }
                    if (r.nextInt(80) == 0)
                        w.playSound(e.getLocation(), "entity.phantom.ambient", 0.5f, 0.9f);
                }
                case AUTOMATON -> {
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.SMOKE, e.getLocation().add(0, 1.4, -0.3), 1, 0.15, 0.2, 0.15, 0.01);
                    if (r.nextInt(3) == 0)
                        w.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1.6, 0), 1, 0.2, 0.2, 0.2, 0.05);
                    if (r.nextInt(40) == 0)
                        w.playSound(e.getLocation(), "block.piston.contract", 0.5f, 1.4f);
                    if (r.nextInt(60) == 0)
                        w.playSound(e.getLocation(), "entity.iron_golem.step", 0.6f, 0.5f);
                    // turn toward nearest player
                    Player pa = nearestPlayer(e, 12);
                    if (pa != null && r.nextInt(8) == 0) {
                        Vector to = pa.getLocation().toVector().subtract(e.getLocation().toVector());
                        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
                        e.setRotation(yaw + 90, 0);
                    }
                }
                case WILLOWISP_GREEN -> {
                    if (!night) { removeWisp(e, w, r); continue; }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 200, 80), 1.1f));
                    // attract monsters nearby
                    for (org.bukkit.entity.Entity en : e.getNearbyEntities(12, 6, 12)) {
                        if (en instanceof org.bukkit.entity.Monster m && r.nextInt(5) == 0) {
                            Vector to = e.getLocation().toVector().subtract(en.getLocation().toVector());
                            if (to.length() > 0.5) {
                                to.normalize().multiply(0.06);
                                en.setVelocity(en.getVelocity().add(to));
                            }
                        }
                    }
                }
                case WILLOWISP_RED -> {
                    if (!night) { removeWisp(e, w, r); continue; }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 40, 40), 1.1f));
                    // hunt the nearest player
                    Player pr = nearestPlayer(e, 12);
                    if (pr != null) {
                        Vector to = pr.getLocation().toVector().subtract(e.getLocation().toVector());
                        if (to.length() > 0.5) {
                            to.normalize().multiply(0.08);
                            e.setVelocity(e.getVelocity().add(to).setY(Math.max(-0.02, Math.min(0.04, e.getVelocity().getY()))));
                        }
                        // sting on contact
                        if (pr.getLocation().distance(e.getLocation()) < 1.5 && r.nextInt(20) == 0) {
                            pr.damage(1.0, e);
                            w.spawnParticle(Particle.FLAME, e.getLocation().add(0, 0.4, 0), 6, 0.2, 0.2, 0.2, 0.02);
                            w.playSound(e.getLocation(), "entity.allay.hurt", 0.8f, 0.8f);
                            removeWisp(e, w, r);
                            continue;
                        }
                    }
                }
                case WILLOWISP_GOLD -> {
                    if (!night) { removeWisp(e, w, r); continue; }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(240, 200, 60), 1.1f));
                    // leave a gift if a player stays near for a while
                    Player pg = nearestPlayer(e, 4);
                    if (pg != null) {
                        if (a.busyUntil == 0) a.busyUntil = plugin.tick + 200;
                        if (plugin.tick >= a.busyUntil) {
                            w.dropItem(e.getLocation().add(0, 0.5, 0), new ItemStack(Material.GOLD_NUGGET, 1 + r.nextInt(3)));
                            w.playSound(e.getLocation(), "entity.experience_orb.pickup", 0.6f, 1.4f);
                            removeWisp(e, w, r);
                            continue;
                        }
                    } else {
                        a.busyUntil = 0;
                    }
                }
                case WILLOWISP_WHITE -> {
                    if (!night) { removeWisp(e, w, r); continue; }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(240, 240, 255), 1.1f));
                    // soothe nearby players
                    for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 6)) {
                        if (r.nextInt(4) == 0) {
                            if (pl.getHealth() < pl.getMaxHealth()) pl.setHealth(Math.min(pl.getMaxHealth(), pl.getHealth() + 0.5));
                            pl.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 40, 0, true, false));
                        }
                    }
                }
                case WILLOWISP_PURPLE -> {
                    if (!night) { removeWisp(e, w, r); continue; }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 80, 220), 1.1f));
                    if (r.nextInt(3) == 0)
                        w.spawnParticle(Particle.PORTAL, e.getLocation().add(0, 0.4, 0), 2, 0.2, 0.2, 0.2, 0.05);
                    // quicken nearby players
                    for (org.bukkit.entity.Player pl : w.getNearbyPlayers(e.getLocation(), 6)) {
                        if (r.nextInt(6) == 0) {
                            pl.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 60, 0, true, false));
                            pl.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 60, 0, true, false));
                        }
                    }
                }
                case GIRAFFE -> {
                    // browses high leaves: eats occasionally
                    if (r.nextInt(200) == 0) {
                        plugin.fmm().play(e, "eat", false);
                        a.busyUntil = tick + 60;
                    }
                    if (r.nextInt(120) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.7f, a.species.pitch);
                }
                case LION -> {
                    // rests in shade; roars occasionally
                    if (r.nextInt(200) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.9f, a.species.pitch * 0.8f);
                    if (r.nextInt(80) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.6, 0), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 180, 60), 0.8f));
                }
                case HYENA -> {
                    // laughs occasionally
                    if (r.nextInt(150) == 0)
                        w.playSound(e.getLocation(), a.species.ambient, 0.8f, a.species.pitch * 0.7f);
                    if (r.nextInt(60) == 0)
                        w.spawnParticle(Particle.SMOKE, e.getLocation().add(0, 0.5, 0), 1, 0.15, 0.1, 0.15, 0.0);
                }
                case ARCTICFOX -> {
                    if (r.nextInt(150) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.7f, a.species.pitch);
                }
                case SNOWYOWL -> {
                    if (r.nextInt(120) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.6f, a.species.pitch);
                    if (r.nextInt(80) == 0)
                        w.spawnParticle(Particle.WHITE_ASH, e.getLocation().add(0, 0.8, 0), 2, 0.2, 0.1, 0.2, 0.0);
                }
                case REINDEER -> {
                    if (r.nextInt(100) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.7f, a.species.pitch);
                }
                case FENNEC -> {
                    if (r.nextInt(120) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.9f, a.species.pitch);
                }
                case MEERKAT -> {
                    // sentinels stand up occasionally
                    if (r.nextInt(60) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.8, 0), 1, 0.05, 0.1, 0.05, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 140, 80), 0.7f));
                    if (r.nextInt(180) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.6f, a.species.pitch);
                }
                case VULTURE -> {
                    if (r.nextInt(100) == 0 && a.species.ambient != null)
                        w.playSound(e.getLocation(), a.species.ambient, 0.5f, a.species.pitch);
                    if (r.nextInt(40) == 0)
                        w.spawnParticle(Particle.ASH, e.getLocation().add(0, 0.6, 0), 1, 0.3, 0.1, 0.3, 0.0);
                }
                case GLOWSPRITE -> {
                    // glow sprite: floating light in caves, vanishes in daylight/surface
                    if (e.getLocation().getY() > 40) {
                        for (int i = 0; i < 6; i++)
                            w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.3, 0), 1, 0.2, 0.2, 0.2, 0.02);
                        e.remove();
                        continue;
                    }
                    if (r.nextInt(2) == 0)
                        w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.2, 0), 1, 0.1, 0.1, 0.1, 0.02);
                    if (r.nextInt(3) == 0)
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.2, 0), 1, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 240, 120), 1.2f));
                    Vector v = e.getVelocity();
                    e.setVelocity(v.setY(Math.max(-0.03, Math.min(0.04, v.getY() + (r.nextDouble() - 0.5) * 0.04))));
                    if (r.nextInt(60) == 0)
                        w.playSound(e.getLocation(), "entity.allay.ambient", 0.4f, 1.8f);
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

    /** Shared fade-out for the will-o'-wisp family: a small burst of light, then gone. */
    private void removeWisp(LivingEntity e, World w, ThreadLocalRandom r) {
        for (int i = 0; i < 6; i++)
            w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.4 + r.nextDouble() * 0.6, 0), 1, 0.2, 0.2, 0.2, 0.03);
        w.playSound(e.getLocation(), "entity.allay.hurt", 0.5f, 1.5f);
        e.remove();
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
        a.entity.setRotation((float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ())) + 90, 0);
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
        // catch a will-o'-wisp in a glass bottle (Zelda-style)
        if (hand.getType() == Material.GLASS_BOTTLE && a.species.name().startsWith("WILLOWISP")) {
            e.setCancelled(true);
            String variant = WildItems.wispVariant(a.species);
            ItemStack bottled = plugin.items().bottledWisp(variant);
            if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            var leftover = e.getPlayer().getInventory().addItem(bottled);
            if (!leftover.isEmpty()) e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), bottled);
            LivingEntity ent = (LivingEntity) e.getRightClicked();
            e.getPlayer().getWorld().playSound(ent.getLocation(), "entity.allay.ambient", 0.8f, 2.0f);
            ent.remove();
            animals.remove(ent.getUniqueId());
            plugin.adv().grant(e.getPlayer(), "first_catch");
            return;
        }
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
        // Starving animals drop nothing (they died of hunger)
        if (a.hunger == 0 && kept(e.getEntity())) {
            e.setDroppedExp(0);
            return;
        }
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
            case GIRAFFE -> { d.add(new ItemStack(Material.LEATHER, 2 + r.nextInt(3))); add(d, Material.BEEF, r.nextInt(3)); }
            case LION -> { d.add(new ItemStack(Material.LEATHER, 1 + r.nextInt(2))); add(d, Material.BEEF, 1 + r.nextInt(3)); }
            case HYENA -> { d.add(new ItemStack(Material.LEATHER, 1 + r.nextInt(2))); add(d, Material.ROTTEN_FLESH, r.nextInt(2)); }
            case ARCTICFOX -> { d.add(new ItemStack(Material.WHITE_WOOL, 1 + r.nextInt(2))); add(d, Material.RABBIT, r.nextInt(2)); }
            case SNOWYOWL -> { add(d, Material.FEATHER, 1 + r.nextInt(3)); add(d, Material.CHICKEN, r.nextInt(2)); }
            case REINDEER -> { d.add(new ItemStack(Material.LEATHER, 1 + r.nextInt(2))); add(d, Material.BEEF, 1 + r.nextInt(3)); }
            case FENNEC -> { d.add(new ItemStack(Material.RABBIT_HIDE, 1 + r.nextInt(2))); add(d, Material.RABBIT, r.nextInt(2)); }
            case MEERKAT -> { add(d, Material.RABBIT, r.nextInt(2)); }
            case VULTURE -> { add(d, Material.FEATHER, 2 + r.nextInt(3)); add(d, Material.ROTTEN_FLESH, r.nextInt(2)); }
            default -> { }
        }
        d.addAll(plugin.drops().roll(a.species, a.rare));
        // Senior animals drop bonus items (age bonus)
        if (a.ageStage == 2 && !a.baby) {
            d.add(new ItemStack(Material.LEATHER, 1 + r.nextInt(2)));
            if (a.species == Species.DEER || a.species == Species.BEAR) {
                d.add(new ItemStack(Material.BONE, 1 + r.nextInt(2)));
            }
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
