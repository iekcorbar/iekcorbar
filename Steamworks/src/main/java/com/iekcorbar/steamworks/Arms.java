package com.iekcorbar.steamworks;

import com.iekcorbar.steamworks.ArmProgram.Code;
import com.iekcorbar.steamworks.ArmProgram.Op;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.WritableBookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Karakuri Arm: a programmable arm (powered from below) with a swappable tool
 * head. Players teach it up to 8 points (sneak + right-click the arm with an
 * empty hand, then click blocks), load a program from a book and quill /
 * written book, and copy programs with punch cards. Without a program it
 * runs a default for its tool (see ArmProgram.fallback). Every action takes
 * a moment that gets shorter the faster the arm turns.
 */
final class Arms implements Listener {
    private static final class Run {
        ArmProgram program;
        String error;
        int pc, busy;
        final Map<Integer, Integer> counters = new HashMap<>();
    }

    private final SteamworksPlugin plugin;
    private final Map<KNode, Run> runs = new HashMap<>();
    private final Map<UUID, KNode> teaching = new HashMap<>();
    private final NamespacedKey programKey;
    private final ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);

    Arms(SteamworksPlugin plugin) {
        this.plugin = plugin;
        programKey = new NamespacedKey(plugin, "program");
    }

    Component docsLink(String text) {
        String url = plugin.settings().docsUrl;
        return Component.text(text, NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(url));
    }

    /** The program changed (points, tool or code): compile again on the next tick. */
    void reset(KNode n) {
        runs.remove(n);
    }

    private Run run(KNode n) {
        return runs.computeIfAbsent(n, k -> {
            Run r = new Run();
            String src = n.program != null && !n.program.isBlank() ? n.program : ArmProgram.fallback(n.tool, n.points.size());
            try {
                r.program = ArmProgram.compile(src, n.points.size());
            } catch (ArmProgram.ProgramError e) {
                r.error = e.getMessage();
            }
            return r;
        });
    }

    // ------------------------------------------------------------ running

    void tick() {
        Settings cfg = plugin.settings();
        for (KNode n : plugin.kinetics().arms) {
            Run r = run(n);
            if (r.program == null || r.program.ops.isEmpty()) continue;
            double s = Math.abs(n.speed());
            if (s <= 0) continue;
            if (r.busy > 0) {
                r.busy--;
                continue;
            }
            int move = (int) Math.max(2, Math.min(200, cfg.armMoveTicksAt16 * 16 / s));
            List<Op> ops = r.program.ops;
            int wraps = 0;
            for (int steps = 0; steps < 64; steps++) {
                if (r.pc >= ops.size()) {
                    r.pc = 0;
                    if (++wraps > 1) break; // nothing that takes time in the whole program: try again next tick
                }
                Op op = ops.get(r.pc);
                boolean stop = step(n, r, op, move);
                if (stop) break;
            }
        }
    }

    /** Runs one instruction. Returns true when this tick is used up (an action, a wait). */
    private boolean step(KNode n, Run r, Op op, int move) {
        switch (op.code()) {
            case PICK, DROP, PLACE, USE, BREAK, LEVER -> {
                Block b = point(n, op.a());
                turn(n, b, move);
                String problem = act(n, op, b);
                if (problem != null) r.error = "line " + op.line() + ": " + problem;
                else if (r.error != null && r.error.startsWith("line " + op.line() + ":")) r.error = null;
                r.busy = move;
                r.pc++;
                return true;
            }
            case WAIT -> {
                r.busy = op.a();
                r.pc++;
                return true;
            }
            case WAIT_REDSTONE -> {
                if (!SteamworksPlugin.hasSignal(n)) return true;
                r.pc++;
            }
            case WAIT_ITEM -> {
                if (!hasItems(point(n, op.a()))) return true;
                r.pc++;
            }
            case LOOP -> r.pc++;
            case REPEAT -> {
                if (op.a() <= 0) r.pc = op.jump() + 1;
                else {
                    r.counters.put(r.pc, op.a());
                    r.pc++;
                }
            }
            case IF_EMPTY, IF_FULL, IF_HAS, IF_REDSTONE, IF_HOLDING -> r.pc = condition(n, op) ? r.pc + 1 : op.jump() + 1;
            case END -> {
                Op start = r.program.ops.get(op.jump());
                if (start.code() == Code.LOOP) r.pc = op.jump() + 1;
                else if (start.code() == Code.REPEAT) {
                    int left = r.counters.getOrDefault(op.jump(), 1) - 1;
                    r.counters.put(op.jump(), left);
                    r.pc = left > 0 ? op.jump() + 1 : r.pc + 1;
                } else r.pc++;
            }
        }
        return false;
    }

    private boolean condition(KNode n, Op op) {
        return switch (op.code()) {
            case IF_EMPTY -> !hasItems(point(n, op.a()));
            case IF_HAS -> hasItems(point(n, op.a()));
            case IF_FULL -> full(point(n, op.a()));
            case IF_REDSTONE -> SteamworksPlugin.hasSignal(n);
            case IF_HOLDING -> n.held != null && !n.held.isEmpty();
            default -> false;
        };
    }

    Block point(KNode n, int p) {
        int[] o = n.points.get(p - 1);
        return n.world.getBlockAt(n.x + o[0], n.y + o[1], n.z + o[2]);
    }

    /** Does the action. Returns a problem text, or null when it went fine (or there was simply nothing to do). */
    private String act(KNode n, Op op, Block b) {
        boolean claw = n.tool == MaterialType.ARM_GRIPPER || n.tool == MaterialType.ARM_PLACER;
        switch (op.code()) {
            case PICK -> {
                if (!claw) return "PICK needs a gripper or placer head";
                if (n.held != null && !n.held.isEmpty()) return null; // claw already full
                n.held = take(b, op.b());
                if (n.held != null) sound(n, Sound.ENTITY_ITEM_PICKUP, 1.2f);
            }
            case DROP -> {
                if (!claw) return "DROP needs a gripper or placer head";
                if (n.held == null || n.held.isEmpty()) return null;
                n.held = give(b, n.held);
                sound(n, Sound.BLOCK_PISTON_CONTRACT, 1.6f);
            }
            case PLACE -> {
                if (n.tool != MaterialType.ARM_PLACER) return "PLACE needs a placer head";
                if (n.held == null || n.held.isEmpty() || !n.held.getType().isBlock()) return null;
                if (!b.isReplaceable() || plugin.kinetics().at(b) != null) return null;
                Boolean ok = plugin.breakers().mayBreak(n, b);
                if (ok == null || !ok) return ok == null ? null : "not allowed to build at point " + op.a();
                b.setType(n.held.getType());
                b.getWorld().playSound(b.getLocation(), b.getBlockData().getSoundGroup().getPlaceSound(), 0.8f, 1f);
                n.held = n.held.getAmount() <= 1 ? null : n.held.asQuantity(n.held.getAmount() - 1);
            }
            case USE -> {
                if (n.tool != MaterialType.ARM_PLACER) return "USE needs a placer head";
                if (n.held == null || n.held.getType() != Material.BONE_MEAL) return null;
                if (b.applyBoneMeal(BlockFace.UP)) n.held = n.held.getAmount() <= 1 ? null : n.held.asQuantity(n.held.getAmount() - 1);
            }
            case BREAK -> {
                if (n.tool != MaterialType.ARM_DRILL) return "BREAK needs a drill head";
                Material m = b.getType();
                if (m.isAir() || b.isLiquid() || m == Material.BARRIER || m.getHardness() < 0 || plugin.kinetics().at(b) != null
                        || b.getState(false) instanceof Container) return null;
                Boolean ok = plugin.breakers().mayBreak(n, b);
                if (ok == null || !ok) return ok == null ? null : "not allowed to break at point " + op.a();
                Location at = b.getLocation().add(0.5, 0.5, 0.5);
                OreType ore = Ores.of(b);
                if (ore != null) b.getWorld().dropItemNaturally(at, plugin.items().createMaterial(ore.drop, ore.minDrop));
                else for (ItemStack s : b.getDrops(pickaxe)) b.getWorld().dropItemNaturally(at, s);
                b.getWorld().spawnParticle(Particle.BLOCK, at, 16, 0.3, 0.3, 0.3, b.getBlockData());
                b.getWorld().playSound(at, b.getBlockData().getSoundGroup().getBreakSound(), 0.8f, 1f);
                b.setType(Material.AIR);
            }
            case LEVER -> {
                if (!(b.getBlockData() instanceof Switch sw) || b.getType() != Material.LEVER) return "point " + op.a() + " is not a lever";
                sw.setPowered(op.b() == 1);
                b.setBlockData(sw, true);
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, op.b() == 1 ? 0.6f : 0.5f);
            }
            default -> { }
        }
        plugin.visuals().armHeld(n);
        plugin.saveSoon(n);
        return null;
    }

    private void sound(KNode n, Sound s, float pitch) {
        n.world.playSound(n.center(), s, 0.4f, pitch);
    }

    private void turn(KNode n, Block target, int duration) {
        double dx = target.getX() - n.x, dz = target.getZ() - n.z;
        if (dx == 0 && dz == 0) return;
        n.beamPhase = Math.atan2(dx, dz);
        plugin.visuals().armTurn(n, (float) n.beamPhase, Math.max(2, duration / 2));
        n.world.playSound(n.center(), "steamworks:arm.servo", 0.35f, 1f);
    }

    // ------------------------------------------------------------ what is at a point

    private ItemStack take(Block b, int max) {
        KNode k = plugin.kinetics().at(b);
        if (k != null && (k.type == PartType.MILLSTONE || k.type == PartType.KETTLE)) {
            if (k.millOutput.isEmpty()) return null;
            ItemStack s = k.millOutput.get(0);
            ItemStack out = s.asQuantity(Math.min(max, s.getAmount()));
            if (s.getAmount() <= out.getAmount()) k.millOutput.remove(0);
            else s.setAmount(s.getAmount() - out.getAmount());
            plugin.saveSoon(k);
            return out;
        }
        if (b.getState(false) instanceof Container c) {
            Inventory inv = c.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s == null || s.isEmpty()) continue;
                ItemStack out = s.asQuantity(Math.min(max, s.getAmount()));
                s.setAmount(s.getAmount() - out.getAmount());
                inv.setItem(i, s.getAmount() <= 0 ? null : s);
                return out;
            }
            return null;
        }
        // items lying there (also on a belt)
        BoundingBox box = k != null && k.type == PartType.BELT ? new BoundingBox(b.getX(), b.getY() + 0.9, b.getZ(), b.getX() + 1, b.getY() + 1.6, b.getZ() + 1)
                : BoundingBox.of(b);
        for (Entity e : b.getWorld().getNearbyEntities(box, en -> en instanceof Item)) {
            Item it = (Item) e;
            ItemStack s = it.getItemStack();
            ItemStack out = s.asQuantity(Math.min(max, s.getAmount()));
            if (s.getAmount() <= out.getAmount()) it.remove();
            else it.setItemStack(s.asQuantity(s.getAmount() - out.getAmount()));
            return out;
        }
        return null;
    }

    /** Puts items into a point. Returns what is left in the claw (null = nothing). */
    private ItemStack give(Block b, ItemStack stack) {
        KNode k = plugin.kinetics().at(b);
        if (k != null) {
            switch (k.type) {
                case KETTLE -> {
                    if (!plugin.mixers().accepts(stack)) return stack;
                    int left = Mixers.addTo(k.kettleItems, stack.clone());
                    plugin.visuals().updateKettle(k);
                    plugin.saveSoon(k);
                    return left <= 0 ? null : stack.asQuantity(left);
                }
                case MILLSTONE -> {
                    if (plugin.millstones().find(stack) == null) return stack;
                    if (k.millInput == null) {
                        k.millInput = stack.clone();
                        plugin.saveSoon(k);
                        return null;
                    }
                    if (!k.millInput.isSimilar(stack)) return stack;
                    int move = Math.min(stack.getAmount(), k.millInput.getMaxStackSize() - k.millInput.getAmount());
                    k.millInput.setAmount(k.millInput.getAmount() + move);
                    plugin.saveSoon(k);
                    return move >= stack.getAmount() ? null : stack.asQuantity(stack.getAmount() - move);
                }
                case BELT -> {
                    Item it = b.getWorld().dropItem(b.getLocation().add(0.5, 1.05, 0.5), stack);
                    it.setVelocity(new org.bukkit.util.Vector());
                    return null;
                }
                default -> {
                    return stack;
                }
            }
        }
        if (b.getState(false) instanceof Container c) {
            Map<Integer, ItemStack> rest = c.getInventory().addItem(stack.clone());
            int left = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            return left <= 0 ? null : stack.asQuantity(left);
        }
        if (b.isPassable()) {
            Item it = b.getWorld().dropItem(b.getLocation().add(0.5, 0.2, 0.5), stack);
            it.setVelocity(new org.bukkit.util.Vector());
            return null;
        }
        return stack;
    }

    private boolean hasItems(Block b) {
        KNode k = plugin.kinetics().at(b);
        if (k != null && (k.type == PartType.MILLSTONE || k.type == PartType.KETTLE)) return !k.millOutput.isEmpty();
        if (b.getState(false) instanceof Container c) return !c.getInventory().isEmpty();
        return !b.getWorld().getNearbyEntities(BoundingBox.of(b).expand(0, 0.6, 0), en -> en instanceof Item).isEmpty();
    }

    private static boolean full(Block b) {
        if (!(b.getState(false) instanceof Container c)) return false;
        for (ItemStack s : c.getInventory().getStorageContents()) if (s == null || s.isEmpty() || s.getAmount() < s.getMaxStackSize()) return false;
        return true;
    }

    // ------------------------------------------------------------ players

    /** Right-click on an arm. Returns true if handled. */
    boolean interact(KNode n, Player p, ItemStack hand) {
        boolean empty = hand == null || hand.isEmpty();
        if (empty && p.isSneaking()) {
            toggleTeach(p, n);
            return true;
        }
        MaterialType m = plugin.items().materialOf(hand);
        if (m == MaterialType.ARM_GRIPPER || m == MaterialType.ARM_PLACER || m == MaterialType.ARM_DRILL) {
            ItemStack old = n.tool == null ? null : plugin.items().createMaterial(n.tool, 1);
            n.tool = m;
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            if (old != null) for (ItemStack left : p.getInventory().addItem(old).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            changed(n);
            plugin.visuals().spawn(n);
            p.sendActionBar(Component.text("Tool head: " + m.displayName, NamedTextColor.GOLD));
            p.playSound(n.center(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.4f);
            return true;
        }
        if (!empty && (hand.getType() == Material.WRITABLE_BOOK || hand.getType() == Material.WRITTEN_BOOK)) {
            load(n, p, readBook(hand));
            return true;
        }
        if (m == MaterialType.PUNCH_CARD) {
            ItemMeta meta = hand.getItemMeta();
            String code = meta.getPersistentDataContainer().get(programKey, PersistentDataType.STRING);
            if (code == null) {
                if (n.program == null || n.program.isBlank()) {
                    p.sendActionBar(Component.text("This arm has no program to punch onto the card.", NamedTextColor.YELLOW));
                    return true;
                }
                ItemStack card = punched(n.program);
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                for (ItemStack left : p.getInventory().addItem(card).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.sendActionBar(Component.text("Program punched onto the card.", NamedTextColor.GOLD));
                p.playSound(n.center(), Sound.UI_LOOM_TAKE_RESULT, 0.7f, 1f);
            } else {
                load(n, p, code);
            }
            return true;
        }
        if (empty && n.held != null && !n.held.isEmpty()) {
            for (ItemStack left : p.getInventory().addItem(n.held).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            n.held = null;
            plugin.visuals().armHeld(n);
            plugin.saveSoon(n);
            return true;
        }
        return false;
    }

    private ItemStack punched(String code) {
        ItemStack card = plugin.items().createMaterial(MaterialType.PUNCH_CARD, 1);
        ItemMeta meta = card.getItemMeta();
        meta.displayName(Component.text("Punch Card (punched)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        String[] lines = code.split("\\R");
        for (int i = 0; i < Math.min(6, lines.length); i++)
            lore.add(Component.text(lines[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (lines.length > 6) lore.add(Component.text("... (" + lines.length + " lines)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click a Karakuri Arm to load it.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(programKey, PersistentDataType.STRING, code);
        card.setItemMeta(meta);
        return card;
    }

    private static String readBook(ItemStack book) {
        if (!(book.getItemMeta() instanceof WritableBookMeta meta)) return "";
        return String.join("\n", meta.getPages());
    }

    private void load(KNode n, Player p, String code) {
        try {
            ArmProgram prog = ArmProgram.compile(code, n.points.size());
            n.program = code;
            changed(n);
            p.sendMessage(Component.text("Program loaded: " + prog.ops.size() + " instruction(s). ", NamedTextColor.GOLD).append(docsLink("Language guide")));
            p.playSound(n.center(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.6f);
        } catch (ArmProgram.ProgramError e) {
            p.sendMessage(Component.text("Program not loaded - " + e.getMessage() + ". ", NamedTextColor.RED).append(docsLink("Language guide")));
            p.playSound(n.center(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
        }
    }

    private void changed(KNode n) {
        reset(n);
        plugin.saveSoon(n);
    }

    private void toggleTeach(Player p, KNode n) {
        if (teaching.get(p.getUniqueId()) == n) {
            teaching.remove(p.getUniqueId());
            p.sendMessage(Component.text("Teaching done: " + n.points.size() + " point(s).", NamedTextColor.GOLD));
            return;
        }
        teaching.put(p.getUniqueId(), n);
        p.sendMessage(Component.text("Teaching the Karakuri Arm: right-click blocks within " + plugin.settings().armRange
                + " blocks to add them as point 1, 2, 3... (click a point again to remove it). Sneak + right-click the arm when done. ", NamedTextColor.GOLD)
                .append(docsLink("How to program the arm")));
        showPoints(n, p);
    }

    private void showPoints(KNode n, Player p) {
        for (int i = 0; i < n.points.size(); i++) {
            Block b = point(n, i + 1);
            p.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 1.1, 0.5), 6, 0.2, 0.1, 0.2, 0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeachClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        KNode n = teaching.get(p.getUniqueId());
        if (n == null) return;
        Block b = e.getClickedBlock();
        if (!n.alive) {
            teaching.remove(p.getUniqueId());
            return;
        }
        if (plugin.kinetics().at(b) == n) return; // let the arm click through (ends teaching when sneaking)
        e.setCancelled(true);
        int dx = b.getX() - n.x, dy = b.getY() - n.y, dz = b.getZ() - n.z;
        int range = plugin.settings().armRange;
        if (Math.abs(dx) > range || Math.abs(dy) > range || Math.abs(dz) > range) {
            p.sendActionBar(Component.text("Too far from the arm (max " + range + " blocks).", NamedTextColor.RED));
            return;
        }
        for (int i = 0; i < n.points.size(); i++) {
            int[] o = n.points.get(i);
            if (o[0] == dx && o[1] == dy && o[2] == dz) {
                n.points.remove(i);
                changed(n);
                p.sendActionBar(Component.text("Point " + (i + 1) + " removed - later points moved up one number.", NamedTextColor.YELLOW));
                return;
            }
        }
        if (n.points.size() >= 8) {
            p.sendActionBar(Component.text("An arm can remember 8 points.", NamedTextColor.RED));
            return;
        }
        n.points.add(new int[]{dx, dy, dz});
        changed(n);
        String what = b.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        p.sendActionBar(Component.text("Point " + n.points.size() + ": " + what, NamedTextColor.GREEN));
        p.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 1.1, 0.5), 8, 0.2, 0.1, 0.2, 0);
        p.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
    }

    Component info(KNode n) {
        Run r = run(n);
        Component c = Component.text("Karakuri Arm: ", NamedTextColor.GOLD)
                .append(Component.text((n.tool == null ? "no tool head" : n.tool.displayName) + " | " + n.points.size() + " point(s) | ", NamedTextColor.GRAY));
        if (r.error != null) c = c.append(Component.text(r.error, NamedTextColor.RED));
        else if (r.program == null || r.program.ops.isEmpty())
            c = c.append(Component.text(n.tool == null ? "put a tool head on it" : "teach points (sneak + right-click)", NamedTextColor.YELLOW));
        else {
            String what = n.program == null || n.program.isBlank() ? "default program" : "program";
            Op op = r.program.ops.get(Math.min(r.pc, r.program.ops.size() - 1));
            c = c.append(Component.text(what + ", line " + op.line() + (n.speed() == 0 ? " (not turning)" : ""), NamedTextColor.GREEN));
        }
        if (n.held != null && !n.held.isEmpty())
            c = c.append(Component.text(" | holding " + n.held.getAmount() + " " + n.held.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), NamedTextColor.GRAY));
        return c;
    }

    /** What falls out when the arm is broken. */
    List<ItemStack> contents(KNode n) {
        List<ItemStack> out = new ArrayList<>();
        if (n.tool != null) out.add(plugin.items().createMaterial(n.tool, 1));
        if (n.held != null && !n.held.isEmpty()) out.add(n.held);
        return out;
    }

    void forget(KNode n) {
        runs.remove(n);
        teaching.values().removeIf(k -> k == n);
    }
}
