package com.iekcorbar.steamworks;

import com.iekcorbar.steamworks.Train.Car;
import com.iekcorbar.steamworks.trains.CarType;
import com.iekcorbar.steamworks.trains.RailWalker;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trains on vanilla rails, without minecarts: our own rail walkers move the
 * cars and display entities show them (the client smooths the movement with
 * teleport interpolation). Seats are empty item displays players ride; an
 * Interaction entity per car takes the clicks. The driver in a locomotive
 * steers with W/S (read from the player's input), jump = whistle.
 */
final class Trains implements Listener {
    private static final float MODEL_LIFT = 1.71f; // model rail level (-12 px, x2) + the 3.4 px track head

    private final SteamworksPlugin plugin;
    final Set<Train> trains = new LinkedHashSet<>();
    private final Map<UUID, Car> byEntity = new HashMap<>();
    /** Per world: rail block key -> station / signal standing next to it, and which train stands on it. */
    private final Map<UUID, Map<Long, KNode>> stationRails = new HashMap<>(), signalRails = new HashMap<>();
    private final Map<UUID, Map<Long, Train>> occupied = new HashMap<>();

    enum Stop { STATION, SIGNAL, TRAIN, END }

    record Obstacle(Stop kind, double dist, long rail, KNode node) {}
    private final File file;

    Trains(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trains.yml");
    }

    // ------------------------------------------------------------ rails

    private static RailWalker.Rails rails(World w) {
        return (x, y, z) -> {
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
            return w.getBlockAt(x, y, z).getBlockData() instanceof Rail r ? r.getShape() : null;
        };
    }

    // ------------------------------------------------------------ placing

    /** Right-click on a rail with a car item. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        CarType type = plugin.items().carOf(e.getItem());
        if (type == null) return;
        e.setCancelled(true);
        Block b = e.getClickedBlock();
        if (b == null || !(b.getBlockData() instanceof Rail rail)) {
            e.getPlayer().sendActionBar(Component.text("Put train cars on rails.", NamedTextColor.RED));
            return;
        }
        Player p = e.getPlayer();
        PartListener.ProbeBreakEvent probe = new PartListener.ProbeBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(probe);
        if (probe.isCancelled()) return;
        if (place(p, b, rail.getShape(), type)) {
            if (p.getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
            b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_PLACE, 0.4f, 1.4f);
            save();
        }
    }

    private boolean place(Player p, Block b, Rail.Shape shape, CarType type) {
        World w = b.getWorld();
        RailWalker.Rails rails = rails(w);
        Vector click = b.getLocation().toVector().add(new Vector(0.5, 0, 0.5));

        // couple to the end of a train when the new car would stand on the clicked rail
        for (Train t : trains) {
            if (t.world != w || t.cars.isEmpty()) continue;
            Car tail = t.cars.get(t.cars.size() - 1);
            RailWalker f = tail.rear.copy(), r;
            if (f.move(-Train.GAP, rails) && (r = f.copy()).move(-Train.WHEELBASE, rails) && near(f, r, click)) {
                return add(t, new Car(type, f, r), t.cars.size(), p);
            }
            Car head = t.cars.get(0);
            RailWalker r2 = head.front.copy(), f2;
            if (r2.move(Train.GAP, rails) && (f2 = r2.copy()).move(Train.WHEELBASE, rails) && near(f2, r2, click)) {
                return add(t, new Car(type, f2, r2), 0, p);
            }
        }

        // a new train, pointing the way the player looks
        BlockFace[] ends = RailWalker.ends(shape);
        Vector look = p.getLocation().getDirection().setY(0);
        BlockFace to = Kinetics.vec(ends[0]).dot(look) >= Kinetics.vec(ends[1]).dot(look) ? ends[0] : ends[1];
        BlockFace from = to == ends[0] ? ends[1] : ends[0];
        RailWalker mid = new RailWalker(b.getX(), b.getY(), b.getZ(), shape, from, to, 0);
        mid.t = mid.length() / 2;
        RailWalker f = mid.copy(), r = mid.copy();
        if (!f.move(Train.WHEELBASE / 2, rails) || !r.move(-Train.WHEELBASE / 2, rails)) {
            p.sendActionBar(Component.text("Needs at least two rails in a row.", NamedTextColor.RED));
            return false;
        }
        Car car = new Car(type, f, r);
        if (blocked(car, w)) {
            p.sendActionBar(Component.text("There is already a train there.", NamedTextColor.RED));
            return false;
        }
        Train t = new Train(UUID.randomUUID(), w);
        trains.add(t);
        return add(t, car, 0, p);
    }

    private static boolean near(RailWalker f, RailWalker r, Vector click) {
        return f.pos().add(r.pos()).multiply(0.5).distance(click) < 2.4;
    }

    private boolean add(Train t, Car car, int index, Player p) {
        if (blocked(car, t.world)) {
            p.sendActionBar(Component.text("There is already a train there.", NamedTextColor.RED));
            if (t.cars.isEmpty()) trains.remove(t);
            return false;
        }
        car.train = t;
        if (car.type == CarType.CARGO_WAGON) car.cargo = Bukkit.createInventory(null, 27, Component.text("Cargo Wagon"));
        t.cars.add(index, car);
        spawn(car);
        return true;
    }

    /** True if the car would stand (partly) where another car already is. */
    private boolean blocked(Car car, World w) {
        Vector c = car.center();
        for (Train t : trains) {
            if (t.world != w) continue;
            for (Car o : t.cars) if (o.center().distanceSquared(c) < 3.0 * 3.0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ entities

    private void spawn(Car car) {
        despawn(car);
        World w = car.train.world;
        Location at = loc(car);
        float sc = (float) Train.SCALE;
        car.model = w.spawn(at, ItemDisplay.class, d -> {
            tag(d);
            d.setItemStack(plugin.items().displayItem(car.type.model()));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0, MODEL_LIFT, 0), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
            d.setTeleportDuration(2);
            d.setViewRange(2.5f);
        });
        for (double[] s : car.type.seats) {
            ItemDisplay seat = w.spawn(seatLoc(car, s), ItemDisplay.class, d -> {
                tag(d);
                d.setTeleportDuration(2);
            });
            car.seats.add(seat);
            byEntity.put(seat.getUniqueId(), car);
        }
        car.hitbox = w.spawn(at, Interaction.class, i -> {
            tag(i);
            i.setInteractionWidth(1.1f * sc);
            i.setInteractionHeight(1.5f * sc);
            i.setResponsive(true);
        });
        byEntity.put(car.model.getUniqueId(), car);
        byEntity.put(car.hitbox.getUniqueId(), car);
    }

    private void tag(Entity e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(plugin.visuals().tag, PersistentDataType.BYTE, (byte) 1);
    }

    private void despawn(Car car) {
        if (car.model != null) {
            byEntity.remove(car.model.getUniqueId());
            car.model.remove();
        }
        for (ItemDisplay s : car.seats) {
            byEntity.remove(s.getUniqueId());
            s.eject();
            s.remove();
        }
        car.seats.clear();
        if (car.hitbox != null) {
            byEntity.remove(car.hitbox.getUniqueId());
            car.hitbox.remove();
        }
        car.model = null;
        car.hitbox = null;
    }

    // ------------------------------------------------------------ track visuals

    private final Map<String, ItemDisplay> track = new HashMap<>();

    /**
     * Steampunk track (ballast, sleepers, brass fishplates) on the rails trains actually drive on:
     * walk the line both ways from every train, one non-persistent display per rail block. Other
     * rails stay vanilla - that is what "only train routes" means without a global pack override.
     */
    private void refreshTrack() {
        Set<String> want = new HashSet<>();
        for (Train t : trains) {
            if (t.cars.isEmpty() || !loaded(t)) continue;
            RailWalker.Rails rails = rails(t.world);
            for (RailWalker seed : new RailWalker[]{t.cars.get(0).front, t.cars.get(t.cars.size() - 1).rear})
                for (double dir : new double[]{-0.4, 0.4}) {
                    RailWalker w = seed.copy();
                    for (int i = 0; i < 256; i++) {
                        want.add(t.world.getUID() + ":" + w.x + "," + w.y + "," + w.z);
                        if (!w.move(dir, rails)) break;
                    }
                }
        }
        track.entrySet().removeIf(en -> {
            if (want.contains(en.getKey())) return false;
            en.getValue().remove();
            return true;
        });
        for (String k : want) {
            ItemDisplay d = track.get(k);
            if (d == null || !d.isValid()) spawnTrack(k);
        }
    }

    private void spawnTrack(String k) {
        World w = Bukkit.getWorld(java.util.UUID.fromString(k.substring(0, k.indexOf(':'))));
        if (w == null) return;
        String[] p = k.substring(k.indexOf(':') + 1).split(",");
        int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
        if (!(w.getBlockAt(x, y, z).getBlockData() instanceof Rail rail)) return;
        Rail.Shape s = rail.getShape();
        boolean slope = s.name().startsWith("ASCENDING");
        boolean straight = !slope && (s == Rail.Shape.NORTH_SOUTH || s == Rail.Shape.EAST_WEST);
        String model = slope ? "sw_track_slope" : straight ? "sw_track_straight" : "sw_track_curve";
        Location at = new Location(w, x + 0.5, y + (slope ? 0.5 : 0.0), z + 0.5);
        ItemDisplay d = w.spawn(at, ItemDisplay.class, e -> {
            tag(e);
            e.setItemStack(plugin.items().displayItem(model));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), trackRot(s), new Vector3f(1, 1, 1), new Quaternionf()));
        });
        track.put(k, d);
    }

    /** Track is modelled north-south (curve: north leg to east leg); yaw and pitch put it in place. */
    private static Quaternionf trackRot(Rail.Shape s) {
        float q = (float) Math.toRadians(90), h = (float) Math.toRadians(45);
        return switch (s) {
            case NORTH_SOUTH, NORTH_EAST -> new Quaternionf();
            case EAST_WEST, NORTH_WEST -> new Quaternionf().rotateY(q);
            case SOUTH_EAST -> new Quaternionf().rotateY(-q);
            case SOUTH_WEST -> new Quaternionf().rotateY((float) Math.toRadians(180));
            case ASCENDING_NORTH -> new Quaternionf().rotateX(h);
            case ASCENDING_SOUTH -> new Quaternionf().rotateX(-h);
            case ASCENDING_EAST -> new Quaternionf().rotateY(q).rotateX(-h);
            case ASCENDING_WEST -> new Quaternionf().rotateY(-q).rotateX(-h);
        };
    }

    private static Location loc(Car car) {
        Vector c = car.center(), d = car.dir();
        Location l = c.toLocation(car.train.world);
        l.setYaw((float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ())));
        l.setPitch((float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d.getY())))));
        return l;
    }

    private static Location seatLoc(Car car, double[] s) {
        Vector c = car.center(), d = car.dir();
        Vector right = new Vector(-d.getZ(), 0, d.getX());
        if (right.lengthSquared() > 1e-9) right.normalize();
        Location l = c.add(d.clone().multiply(s[0] * Train.SCALE)).add(new Vector(0, s[1] * Train.SCALE, 0)).add(right.multiply(s[2] * Train.SCALE)).toLocation(car.train.world);
        l.setYaw((float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ())));
        return l;
    }

    private static void moveEntities(Car car) {
        Location at = loc(car);
        car.model.teleport(at);
        for (int i = 0; i < car.seats.size(); i++) {
            ItemDisplay s = car.seats.get(i);
            if (s.isValid()) s.teleport(seatLoc(car, car.type.seats[i]), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
        if (car.hitbox != null && car.hitbox.isValid()) car.hitbox.teleport(at);
    }

    // ------------------------------------------------------------ ticking

    private boolean loaded(Train t) {
        for (Car c : t.cars) {
            if (!t.world.isChunkLoaded(c.front.x >> 4, c.front.z >> 4) || !t.world.isChunkLoaded(c.rear.x >> 4, c.rear.z >> 4)) return false;
        }
        return true;
    }

    Player driver(Train t) {
        for (Car c : t.cars) {
            if (c.type != CarType.LOCOMOTIVE || c.seats.isEmpty()) continue;
            for (Entity e : c.seats.get(0).getPassengers()) if (e instanceof Player p) return p;
        }
        return null;
    }

    void tick(long tick) {
        Settings cfg = plugin.settings();
        if (tick % 20 == 0) { indexRails(); refreshTrack(); }
        buildOccupied();
        if (tick % 10 == 0) updateSignals();
        for (Train t : new ArrayList<>(trains)) {
            if (t.cars.isEmpty()) {
                trains.remove(t);
                continue;
            }
            if (!loaded(t)) {
                t.speed = 0;
                releaseTickets(t);
                continue;
            }
            for (Car c : t.cars) if (!c.spawned()) spawn(c);
            if (t.whistleCooldown > 0) t.whistleCooldown--;

            Player driver = driver(t);
            boolean steam = false;
            if (driver != null && t.auto) {
                var in = driver.getCurrentInput();
                if (in.isForward() || in.isBackward()) {
                    t.auto = false; // the driver takes over
                    driver.sendActionBar(Component.text("Autopilot off", NamedTextColor.YELLOW));
                }
            }
            if (t.auto) {
                steam = autopilot(t, cfg);
                if (driver != null && tick % 10 == 0) driver.sendActionBar(dashboard(t).append(Component.text("  |  autopilot", NamedTextColor.AQUA)));
            } else if (driver != null) {
                var in = driver.getCurrentInput();
                if (in.isForward()) {
                    if (t.speed < 0) t.speed = Math.min(0, t.speed + cfg.trainBrake);
                    else if (t.fuel > 0) {
                        t.speed = Math.min(cfg.trainMaxSpeed, t.speed + cfg.trainAccel);
                        steam = true;
                    }
                } else if (in.isBackward()) {
                    if (t.speed > 0) t.speed = Math.max(0, t.speed - cfg.trainBrake);
                    else if (t.fuel > 0) {
                        t.speed = Math.max(-cfg.trainReverseMax, t.speed - cfg.trainAccel);
                        steam = true;
                    }
                }
                if (in.isJump() && t.whistleCooldown == 0) {
                    whistle(t);
                    plugin.eggs().whistle(driver, driver.getLocation());
                }
                if (tick % 10 == 0) {
                    Component dash = dashboard(t);
                    Obstacle o = t.speed == 0 ? null : scan(t, 24, (int) Math.signum(t.speed), false);
                    if (o != null && o.kind() == Stop.SIGNAL) dash = dash.append(Component.text("  |  RED SIGNAL AHEAD", NamedTextColor.RED));
                    if (o != null && o.kind() == Stop.TRAIN) dash = dash.append(Component.text("  |  TRAIN AHEAD", NamedTextColor.RED));
                    driver.sendActionBar(dash);
                }
            }
            if (tick % 10 == 0 && t.speed != 0) announce(t);
            if (steam) t.fuel = Math.max(0, t.fuel - 1);
            else if (!t.auto) t.speed = Math.signum(t.speed) * Math.max(0, Math.abs(t.speed) - cfg.trainFriction * (driver == null ? 4 : 1));

            if (t.speed != 0) {
                drive(t);
                for (Car c : t.cars) if (c.spawned()) moveEntities(c);
                pushAhead(t);
                effects(t, steam);
            }
            tickets(t, driver != null && t.speed != 0);
        }
        if (tick % 2400 == 0) save();
    }

    private void drive(Train t) {
        RailWalker.Rails rails = rails(t.world);
        List<RailWalker[]> moved = new ArrayList<>();
        for (Car c : t.cars) {
            // a rail taken away under the train: stop
            if (rails.shape(c.front.x, c.front.y, c.front.z) != c.front.shape || rails.shape(c.rear.x, c.rear.y, c.rear.z) != c.rear.shape) {
                bump(t);
                return;
            }
            RailWalker f = c.front.copy(), r = c.rear.copy();
            if (!f.move(t.speed, rails) || !r.move(t.speed, rails)) {
                bump(t);
                return;
            }
            moved.add(new RailWalker[]{f, r});
        }
        // never drive into another train
        int li = t.speed > 0 ? 0 : t.cars.size() - 1;
        Vector lead = moved.get(li)[0].pos().add(moved.get(li)[1].pos()).multiply(0.5);
        for (Train o : trains) {
            if (o == t || o.world != t.world) continue;
            for (Car c : o.cars) {
                if (c.center().distanceSquared(lead) < (Train.WHEELBASE + Train.GAP) * (Train.WHEELBASE + Train.GAP)) {
                    bump(t);
                    return;
                }
            }
        }
        for (int i = 0; i < t.cars.size(); i++) {
            t.cars.get(i).front = moved.get(i)[0];
            t.cars.get(i).rear = moved.get(i)[1];
        }
        t.sinceStation += Math.abs(t.speed);
        if (t.sinceStation > 4) t.lastStation = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------ stations, signals, autopilot

    private static long railKey(RailWalker w) {
        return KNode.key(w.x, w.y, w.z);
    }

    private void indexRails() {
        stationRails.clear();
        signalRails.clear();
        index(plugin.kinetics().stations, stationRails);
        index(plugin.kinetics().signals, signalRails);
    }

    /** A station/signal belongs to the rails beside it (same height or one lower, e.g. on a platform). */
    private static void index(Set<KNode> nodes, Map<UUID, Map<Long, KNode>> out) {
        for (KNode n : nodes) {
            Map<Long, KNode> m = out.computeIfAbsent(n.world.getUID(), k -> new HashMap<>());
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                for (int dy = 0; dy >= -1; dy--) {
                    int x = n.x + f.getModX(), y = n.y + dy, z = n.z + f.getModZ();
                    if (n.world.isChunkLoaded(x >> 4, z >> 4) && n.world.getBlockAt(x, y, z).getBlockData() instanceof Rail)
                        m.putIfAbsent(KNode.key(x, y, z), n);
                }
            }
        }
    }

    private void buildOccupied() {
        occupied.clear();
        for (Train t : trains) {
            Map<Long, Train> m = occupied.computeIfAbsent(t.world.getUID(), k -> new HashMap<>());
            for (Car c : t.cars) {
                m.put(railKey(c.front), t);
                m.put(railKey(c.rear), t);
            }
        }
    }

    /**
     * Walks the track in front of a train (dir +1 = towards the front) and
     * returns the first thing it has to stop for, or null.
     */
    Obstacle scan(Train t, double range, int dir, boolean stations) {
        RailWalker.Rails rails = rails(t.world);
        RailWalker w = (dir > 0 ? t.cars.get(0).front : t.cars.get(t.cars.size() - 1).rear).copy();
        double step = 0.5 * dir;
        Map<Long, Train> occ = occupied.getOrDefault(t.world.getUID(), Map.of());
        Map<Long, KNode> st = stationRails.getOrDefault(t.world.getUID(), Map.of());
        Map<Long, KNode> sg = signalRails.getOrDefault(t.world.getUID(), Map.of());
        long last = railKey(w);
        for (double d = 0; d < range; d += 0.5) {
            if (!w.move(step, rails)) return new Obstacle(Stop.END, d, last, null);
            long k = railKey(w);
            if (k == last) continue;
            last = k;
            Train o = occ.get(k);
            if (o != null && o != t) return new Obstacle(Stop.TRAIN, d, k, null);
            KNode signal = sg.get(k);
            if (signal != null && (SteamworksPlugin.hasSignal(signal) || sectionTaken(t.world, t, w.copy(), step))) return new Obstacle(Stop.SIGNAL, d, k, signal);
            KNode station = st.get(k);
            if (stations && station != null && k != t.lastStation) return new Obstacle(Stop.STATION, d + 0.5, k, station);
        }
        return null;
    }

    /** True if the track after a signal (up to the next signal) holds a train other than t (t null = any train). */
    private boolean sectionTaken(World world, Train t, RailWalker w, double step) {
        RailWalker.Rails rails = rails(world);
        Map<Long, Train> occ = occupied.getOrDefault(world.getUID(), Map.of());
        Map<Long, KNode> sg = signalRails.getOrDefault(world.getUID(), Map.of());
        long start = railKey(w), last = start;
        Train here = occ.get(start);
        if (here != null && here != t) return true;
        int max = plugin.settings().trainSectionLength * 2;
        for (int i = 0; i < max; i++) {
            if (!w.move(step, rails)) return false;
            long k = railKey(w);
            if (k == last) continue;
            last = k;
            if (sg.containsKey(k) && k != start) return false; // next signal: end of this section
            Train o = occ.get(k);
            if (o != null && o != t) return true;
        }
        return false;
    }

    /** Signal lamps: red with redstone or while the track on either side up to the next signal holds a train. */
    private void updateSignals() {
        for (KNode n : plugin.kinetics().signals) {
            boolean red = SteamworksPlugin.hasSignal(n);
            if (!red) {
                outer:
                for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                    for (int dy = 0; dy >= -1; dy--) {
                        Block b = n.world.getBlockAt(n.x + f.getModX(), n.y + dy, n.z + f.getModZ());
                        if (!(b.getBlockData() instanceof Rail rail)) continue;
                        BlockFace[] e = RailWalker.ends(rail.getShape());
                        RailWalker w = new RailWalker(b.getX(), b.getY(), b.getZ(), rail.getShape(), e[0], e[1], 0);
                        w.t = w.length() / 2;
                        if (sectionTaken(n.world, null, w.copy(), 0.5) || sectionTaken(n.world, null, w.copy(), -0.5)) {
                            red = true;
                            break outer;
                        }
                    }
                }
            }
            if (red != n.signalRed) {
                n.signalRed = red;
                plugin.visuals().updateSignal(n);
                n.world.playSound(n.center(), Sound.BLOCK_LEVER_CLICK, 0.4f, red ? 0.7f : 1.1f);
            }
        }
    }

    /** Autopilot for one tick. Returns true while it gives steam. */
    private boolean autopilot(Train t, Settings cfg) {
        if (t.waitTicks > 0) {
            t.speed = 0;
            if (--t.waitTicks == 0 && t.turnAfterWait) {
                t.turnAfterWait = false;
                t.autoDir = -t.autoDir;
            }
            return false;
        }
        double v = Math.abs(t.speed);
        if (t.fuel <= 0) {
            t.speed = Math.signum(t.speed) * Math.max(0, v - cfg.trainFriction * 2);
            return false;
        }
        double range = v * v / (2 * cfg.trainBrake) + 4;
        Obstacle o = scan(t, range, t.autoDir, true);
        double allowed = cfg.trainAutoSpeed;
        if (o != null) {
            double stopAt = switch (o.kind()) {
                case STATION -> o.dist();
                case SIGNAL -> o.dist() - 1.0;
                case TRAIN -> o.dist() - 2.5;
                case END -> o.dist();
            };
            allowed = Math.min(allowed, Math.sqrt(Math.max(0, 2 * cfg.trainBrake * stopAt)));
            if (stopAt <= 0.3 && v < 0.08) {
                t.speed = 0;
                if (o.kind() == Stop.STATION) {
                    t.waitTicks = cfg.trainStationWait;
                    t.lastStation = o.rail();
                    t.sinceStation = 0;
                    arrived(t, o.node());
                } else if (o.kind() == Stop.END) {
                    t.waitTicks = cfg.trainStationWait;
                    t.turnAfterWait = true;
                }
                return false;
            }
        }
        double cur = t.speed * t.autoDir; // speed in the direction we want to go
        if (cur < 0) { // still rolling the other way: brake first
            t.speed = t.autoDir * Math.min(0, cur + cfg.trainBrake);
            return false;
        }
        if (cur < allowed) {
            t.speed = t.autoDir * Math.min(allowed, cur + cfg.trainAccel);
            return true;
        }
        t.speed = t.autoDir * Math.max(allowed, cur - cfg.trainBrake);
        return false;
    }

    private List<Player> riders(Train t) {
        List<Player> out = new ArrayList<>();
        for (Car c : t.cars) for (ItemDisplay s : c.seats) for (Entity e : s.getPassengers()) if (e instanceof Player p) out.add(p);
        return out;
    }

    /** "Next stop" for everyone on board when a station comes up. */
    private void announce(Train t) {
        Obstacle o = scan(t, 14, (int) Math.signum(t.speed), true);
        if (o == null || o.kind() != Stop.STATION) return;
        String name = o.node().label == null ? "Station" : o.node().label;
        if (name.equals(t.announced)) return;
        t.announced = name;
        for (Player p : riders(t)) {
            p.showTitle(net.kyori.adventure.title.Title.title(Component.empty(),
                    Component.text("Next stop: " + name, NamedTextColor.GOLD)));
        }
    }

    private void arrived(Train t, KNode station) {
        String name = station.label == null ? "Station" : station.label;
        t.announced = null;
        for (Player p : riders(t)) {
            p.showTitle(net.kyori.adventure.title.Title.title(Component.text(name, NamedTextColor.GOLD),
                    Component.text("Departing in " + plugin.settings().trainStationWait / 20 + " seconds", NamedTextColor.GRAY)));
        }
        Location l = station.center();
        t.world.playSound(l, "steamworks:station.bell", 1f, 1f);
    }


    private static void bump(Train t) {
        if (Math.abs(t.speed) > 0.1) {
            Location l = (t.speed > 0 ? t.cars.get(0) : t.cars.get(t.cars.size() - 1)).center().toLocation(t.world);
            t.world.playSound(l, Sound.BLOCK_ANVIL_LAND, 0.4f, 0.6f);
        }
        t.speed = 0;
    }

    /** Living things on the track in front of the train are pushed aside. */
    private void pushAhead(Train t) {
        Car lead = t.speed > 0 ? t.cars.get(0) : t.cars.get(t.cars.size() - 1);
        Vector d = lead.dir().multiply(Math.signum(t.speed));
        Vector p = lead.center().add(d.clone().multiply(1.05 * Train.SCALE));
        // horizontal footprint scales with the car; the vertical range stays put so small mobs on the track are still felt
        BoundingBox box = BoundingBox.of(p.toLocation(t.world), 0.6 * Train.SCALE, 1.0, 0.6 * Train.SCALE).shift(0, 0.8, 0);
        Set<Entity> riders = new HashSet<>();
        for (Car c : t.cars) for (ItemDisplay s : c.seats) riders.addAll(s.getPassengers());
        for (Entity e : t.world.getNearbyEntities(box, en -> en instanceof LivingEntity)) {
            if (riders.contains(e)) continue;
            Vector side = new Vector(-d.getZ(), 0, d.getX());
            if (side.dot(e.getLocation().toVector().subtract(p)) < 0) side.multiply(-1);
            e.setVelocity(d.clone().multiply(Math.abs(t.speed) * 1.5).add(side.multiply(0.4)).setY(0.25));
        }
    }

    private void effects(Train t, boolean steam) {
        for (Car c : t.cars) {
            if (c.type != CarType.LOCOMOTIVE) continue;
            Location chimney = c.center().add(c.dir().multiply(0.75 * Train.SCALE)).add(new Vector(0, 1.45 * Train.SCALE, 0)).toLocation(t.world);
            int every = (int) Math.max(3, 16 - Math.abs(t.speed) * 24);
            if (++t.chuff % every == 0) {
                t.world.spawnParticle(steam ? Particle.CAMPFIRE_COSY_SMOKE : Particle.CLOUD, chimney, steam ? 1 : 2, 0.05, 0.05, 0.05, steam ? 0.02 : 0.01);
                if (steam) t.world.playSound(chimney, "steamworks:train.chuff", 0.7f, (float) Math.min(1.3, 0.85 + Math.abs(t.speed)));
            }
            // wheels over rail joints: faster train, more clacks
            int clack = (int) Math.max(4, 24 - Math.abs(t.speed) * 40);
            if (Math.abs(t.speed) > 0.05 && t.chuff % clack == 0)
                t.world.playSound(c.center().toLocation(t.world), "steamworks:train.clack", 0.45f, 1f);
        }
    }

    private void whistle(Train t) {
        t.whistleCooldown = 40;
        for (Car c : t.cars) {
            if (c.type != CarType.LOCOMOTIVE) continue;
            Location l = c.center().add(new Vector(0, 1.4 * Train.SCALE, 0)).toLocation(t.world);
            t.world.playSound(l, "steamworks:train.whistle", 3f, 1f);
            t.world.spawnParticle(Particle.CLOUD, l.add(0, 0.3, 0), 6, 0.1, 0.2, 0.1, 0.03);
        }
    }

    private static Component dashboard(Train t) {
        double mps = Math.abs(t.speed) * 20;
        int secs = (int) (t.fuel / 20);
        NamedTextColor fc = t.fuel <= 0 ? NamedTextColor.RED : t.fuel < 1200 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        return Component.text(String.format("%s %.1f m/s", t.speed < 0 ? "Reverse" : "Speed", mps), NamedTextColor.GOLD)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(t.fuel <= 0 ? "No fuel - right-click the locomotive with coal"
                        : String.format("Fuel %d:%02d", secs / 60, secs % 60), fc));
    }

    /** A moving train with a driver keeps the chunks it is in (and the next one) loaded. */
    private void tickets(Train t, boolean moving) {
        if (!moving) {
            if (++t.idleTicks > 100) releaseTickets(t);
            return;
        }
        t.idleTicks = 0;
        Set<Long> want = new HashSet<>();
        for (Car c : t.cars) {
            want.add(chunkKey(c.front.x >> 4, c.front.z >> 4));
            Vector ahead = c.center().add(c.dir().multiply(Math.signum(t.speed) * 16));
            want.add(chunkKey(ahead.getBlockX() >> 4, ahead.getBlockZ() >> 4));
        }
        for (long k : want) if (t.tickets.add(k)) t.world.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
        for (long k : new ArrayList<>(t.tickets)) {
            if (want.contains(k)) continue;
            t.tickets.remove(k);
            t.world.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
        }
    }

    private void releaseTickets(Train t) {
        for (long k : t.tickets) t.world.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
        t.tickets.clear();
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEntityEvent e) {
        Car car = byEntity.get(e.getRightClicked().getUniqueId());
        if (car == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Settings cfg = plugin.settings();
        if (car.type == CarType.LOCOMOTIVE && hand.getType() == Material.CLOCK) {
            Train t = car.train;
            t.auto = !t.auto;
            t.waitTicks = 0;
            t.turnAfterWait = false;
            if (t.auto) t.autoDir = t.speed < 0 ? -1 : 1;
            p.sendActionBar(Component.text(t.auto ? "Autopilot on: stops at stations, waits at red signals, turns round at the end"
                    : "Autopilot off", t.auto ? NamedTextColor.AQUA : NamedTextColor.YELLOW));
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, t.auto ? 1.2f : 0.8f);
            save();
            return;
        }
        if (car.type == CarType.LOCOMOTIVE && plugin.items().materialOf(hand) == MaterialType.FUEL_CANISTER) {
            Train t = car.train;
            if (t.fuel + cfg.canisterFuelTicks > cfg.trainMaxFuel) {
                p.sendActionBar(Component.text("The fuel tank is full.", NamedTextColor.YELLOW));
                return;
            }
            t.fuel += cfg.canisterFuelTicks;
            if (p.getGameMode() != GameMode.CREATIVE) p.getInventory().setItemInMainHand(plugin.items().createMaterial(MaterialType.CANISTER, 1));
            p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.6f, 0.7f);
            p.sendActionBar(dashboard(t));
            return;
        }
        Integer burn = hand.isEmpty() ? null : cfg.trainFuel.get(hand.getType());
        if (car.type == CarType.LOCOMOTIVE && burn != null) {
            Train t = car.train;
            int room = (int) ((cfg.trainMaxFuel - t.fuel) / burn);
            int use = p.isSneaking() ? Math.min(room, hand.getAmount()) : Math.min(room, 1);
            if (use <= 0) {
                p.sendActionBar(Component.text("The firebox is full.", NamedTextColor.YELLOW));
                return;
            }
            t.fuel += (double) use * burn;
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - use);
            p.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.5f, 0.8f);
            p.sendActionBar(dashboard(t));
            return;
        }
        if (car.type == CarType.CARGO_WAGON) {
            p.openInventory(car.cargo);
            return;
        }
        if (p.getVehicle() != null) return;
        for (ItemDisplay seat : car.seats) {
            if (seat.isValid() && seat.getPassengers().isEmpty()) {
                seat.addPassenger(p);
                plugin.adv().grant(p, "ride");
                if (car.type == CarType.LOCOMOTIVE)
                    p.sendActionBar(Component.text("W = steam on, S = brake / reverse, jump = whistle, sneak = get off", NamedTextColor.GOLD));
                return;
            }
        }
        p.sendActionBar(Component.text("No free seat.", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(PrePlayerAttackEntityEvent e) {
        Car car = byEntity.get(e.getAttacked().getUniqueId());
        if (car == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = plugin.kinetics().tick;
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (now - car.lastHit > 20) car.hits = 0;
            car.lastHit = now;
            if (++car.hits < plugin.settings().breakHits) {
                p.playSound(car.center().toLocation(car.train.world), Sound.BLOCK_COPPER_HIT, 0.6f, 0.8f);
                return;
            }
        }
        Block under = car.train.world.getBlockAt(car.front.x, car.front.y, car.front.z);
        PartListener.ProbeBreakEvent probe = new PartListener.ProbeBreakEvent(under, p);
        Bukkit.getPluginManager().callEvent(probe);
        if (probe.isCancelled()) return;
        removeCar(car, p.getGameMode() != GameMode.CREATIVE);
    }

    /** Takes a car off the rails; a car from the middle splits the train in two. */
    void removeCar(Car car, boolean drop) {
        Train t = car.train;
        Location at = car.center().toLocation(t.world).add(0, 0.5, 0);
        if (drop) t.world.dropItemNaturally(at, plugin.items().createCar(car.type, 1));
        if (car.cargo != null) {
            for (ItemStack s : car.cargo.getContents()) if (s != null && !s.isEmpty()) t.world.dropItemNaturally(at, s);
            for (var viewer : new ArrayList<>(car.cargo.getViewers())) viewer.closeInventory();
        }
        despawn(car);
        t.world.playSound(at, Sound.BLOCK_ANVIL_BREAK, 0.5f, 1.2f);
        int i = t.cars.indexOf(car);
        t.cars.remove(car);
        if (i > 0 && i < t.cars.size()) {
            Train back = new Train(UUID.randomUUID(), t.world);
            back.fuel = 0;
            while (t.cars.size() > i) {
                Car c = t.cars.remove(i);
                c.train = back;
                back.cars.add(c);
            }
            trains.add(back);
        }
        t.speed = 0;
        if (t.cars.isEmpty()) {
            releaseTickets(t);
            trains.remove(t);
        }
        save();
    }

    // ------------------------------------------------------------ saving

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        int n = 0;
        for (Train t : trains) {
            if (t.cars.isEmpty()) continue;
            ConfigurationSection s = y.createSection("trains." + (n++));
            s.set("world", t.world.getUID().toString());
            s.set("fuel", t.fuel);
            s.set("auto", t.auto);
            s.set("auto-dir", t.autoDir);
            List<Map<String, Object>> cars = new ArrayList<>();
            for (Car c : t.cars) {
                Map<String, Object> m = new HashMap<>();
                m.put("type", c.type.name());
                m.put("front", c.front.save());
                m.put("rear", c.rear.save());
                if (c.cargo != null) m.put("cargo", java.util.Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(
                        Arrays.stream(c.cargo.getContents()).map(i -> i == null ? ItemStack.empty() : i).toList())));
                cars.add(m);
            }
            s.set("cars", cars);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save trains.yml: " + e.getMessage());
        }
    }

    void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection all = y.getConfigurationSection("trains");
        if (all == null) return;
        for (String key : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(key);
            try {
                World w = Bukkit.getWorld(UUID.fromString(s.getString("world")));
                if (w == null) continue;
                Train t = new Train(UUID.randomUUID(), w);
                t.fuel = s.getDouble("fuel");
                t.auto = s.getBoolean("auto");
                t.autoDir = s.getInt("auto-dir", 1) < 0 ? -1 : 1;
                for (Map<?, ?> m : s.getMapList("cars")) {
                    Car c = new Car(CarType.valueOf(String.valueOf(m.get("type"))),
                            RailWalker.load(String.valueOf(m.get("front"))), RailWalker.load(String.valueOf(m.get("rear"))));
                    c.train = t;
                    if (c.type == CarType.CARGO_WAGON) {
                        c.cargo = Bukkit.createInventory(null, 27, Component.text("Cargo Wagon"));
                        Object data = m.get("cargo");
                        if (data != null) {
                            ItemStack[] items = ItemStack.deserializeItemsFromBytes(java.util.Base64.getDecoder().decode(String.valueOf(data)));
                            for (int i = 0; i < items.length && i < 27; i++) c.cargo.setItem(i, items[i] == null || items[i].isEmpty() ? null : items[i]);
                        }
                    }
                    t.cars.add(c);
                }
                if (!t.cars.isEmpty()) trains.add(t);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping bad train '" + key + "' in trains.yml: " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + trains.size() + " train(s).");
    }

    void shutdown(boolean stopping) {
        save();
        if (stopping) return; // entities are not persistent and chunk tickets die with the server
        for (Train t : trains) {
            releaseTickets(t);
            for (Car c : t.cars) despawn(c);
        }
    }
}
