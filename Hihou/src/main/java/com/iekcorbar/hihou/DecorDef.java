package com.iekcorbar.hihou;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One placeable prop: a hidden base block (barrier / light / nothing) plus an ItemDisplay with the model
 * hihou:&lt;model&gt;. Shared engine, copied into each plugin by propkit/sync.py - edit it only there.
 */
final class DecorDef {
    enum Base { SOLID, LIGHT, NONE }

    interface Use {
        /** Right-click. Return true when handled (otherwise the engine does nothing). */
        boolean use(Decor d, Decor.Prop p, Player pl, ItemStack hand);
    }

    interface Tick {
        /** Called every {@link #tickEvery} engine ticks (10 game ticks each) while a player is within 24 blocks. */
        void tick(Decor d, Decor.Prop p, long tick);
    }

    final String id, name, lore, model;
    Base base = Base.SOLID;
    int height = 1;
    int light;
    float hitW = 0.8f, hitH = 0.95f;
    float scale = 1f;
    /** Joins neighbouring props of the same group: model "&lt;model&gt;_&lt;mask&gt;" (N=1, E=2, S=4, W=8). */
    boolean connect;
    String group;
    /** Has a closed model and "&lt;model&gt;_open"; open() clears the base blocks for a while. */
    boolean openable;
    /** May also be mounted on a wall (the clicked side face). */
    boolean wall;
    /** Right-click sits on it. */
    boolean seat;
    float seatY = 0.4f;
    /** Random model on placing (e.g. colours); the chosen one is kept with the prop. Empty = just {@link #model}. */
    String[] variants = new String[0];
    /** Shapeless recipe of Material / Tag / RecipeChoice. */
    final List<Object> recipe = new ArrayList<>();
    int recipeAmount = 1;
    Use use;
    Tick tick;
    int tickEvery = 2;
    String placeSound = "minecraft:block.wood.place";

    DecorDef(String id, String name, String lore) {
        this(id, name, lore, id);
    }

    DecorDef(String id, String name, String lore, String model) {
        this.id = id;
        this.name = name;
        this.lore = lore;
        this.model = model;
        this.group = id;
    }

    DecorDef base(Base b) { base = b; return this; }
    DecorDef height(int h) { height = h; return this; }
    DecorDef light(int l) { base = Base.LIGHT; light = l; return this; }
    /** Solid prop whose top block gives light (lamp post). */
    DecorDef glow(int l) { light = l; return this; }
    DecorDef hit(float w, float h) { base = Base.NONE; hitW = w; hitH = h; return this; }
    DecorDef scale(float s) { scale = s; return this; }
    DecorDef connect(String g) { connect = true; group = g; return this; }
    DecorDef group(String g) { group = g; return this; }
    DecorDef openable() { openable = true; return this; }
    DecorDef wall() { wall = true; return this; }
    DecorDef seat(float y) { seat = true; seatY = y; return this; }
    DecorDef variants(String... v) { variants = v; return this; }
    DecorDef recipe(int amount, Object... ing) { recipeAmount = amount; for (Object o : ing) recipe.add(o); return this; }
    DecorDef use(Use u) { use = u; return this; }
    DecorDef tick(int every, Tick t) { tick = t; tickEvery = every; return this; }
    DecorDef sound(String s) { placeSound = s; return this; }
}
