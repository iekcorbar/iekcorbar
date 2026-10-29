package com.iekcorbar.steamworks;

import com.iekcorbar.steamworks.trains.CarType;
import com.iekcorbar.steamworks.trains.RailWalker;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A train: cars in a row (index 0 = front), one speed for all of them. Every
 * car stands on two wheels (rail walkers) WHEELBASE apart, cars are GAP apart.
 * Positive speed = towards the front. The entities are not saved with the
 * world; Trains respawns them whenever the car's chunk is loaded.
 */
final class Train {
    static final double WHEELBASE = 2.4, GAP = 1.6;
    /** Everything is drawn at this size: one constant scales the model, seats, hitbox and all offsets. */
    static final double SCALE = 2.0;

    final UUID id;
    final World world;
    final List<Car> cars = new ArrayList<>();
    double speed;      // blocks per tick
    double fuel;       // ticks of steam left
    int whistleCooldown, idleTicks, chuff;
    // autopilot: drives itself, stops at stations, waits at red signals, turns round at the end of the track
    boolean auto;
    int autoDir = 1, waitTicks;
    long lastStation = Long.MIN_VALUE;
    double sinceStation;
    String announced;
    boolean turnAfterWait;
    final Set<Long> tickets = new HashSet<>();
    // Lijnen: ordered station names this train serves. Empty = stops at every
    // station (old behaviour). With a line the autopilot runs it as a shuttle:
    // dwell, next stop, bounce at the ends of the list.
    final List<String> line = new ArrayList<>();
    int linePos, lineDir = 1;

    Train(UUID id, World world) {
        this.id = id;
        this.world = world;
    }

    static final class Car {
        final CarType type;
        RailWalker front, rear;
        Train train;
        ItemDisplay model;
        final List<ItemDisplay> seats = new ArrayList<>();
        Interaction hitbox;
        Inventory cargo;
        // tank wagon: one liquid on board (mB), plus the floating canister that shows it
        FluidType fluid;
        int fluidAmount;
        ItemDisplay sign;
        // plateau wagon: cargo is a one-slot inventory, 'load' renders that block on the deck
        ItemDisplay load;
        int hits;
        long lastHit;

        Car(CarType type, RailWalker front, RailWalker rear) {
            this.type = type;
            this.front = front;
            this.rear = rear;
        }

        Vector center() {
            return front.pos().add(rear.pos()).multiply(0.5);
        }

        /** Unit vector from the rear wheel to the front wheel. */
        Vector dir() {
            Vector d = front.pos().subtract(rear.pos());
            return d.lengthSquared() < 1e-9 ? new Vector(0, 0, 1) : d.normalize();
        }

        boolean spawned() {
            return model != null && model.isValid();
        }
    }
}
