package com.iekcorbar.kura;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One storage network: every store found from its connector(s), or a single
 * container when a terminal sits on a chest without a connector. Contents are
 * always read live from the stores; only the structure is cached (rebuilt when
 * a block next to it changes).
 */
final class Network {
    final UUID world;
    /** Connectors in this network (empty = a single store without connector). */
    final List<Networks.Pos> roots = new ArrayList<>();
    final Set<Networks.Pos> members = new HashSet<>();
    /** Loaded chunks it spans, and unloaded chunks it stopped at. */
    final Set<Long> chunks = new HashSet<>(), frontier = new HashSet<>();
    final List<Store> stores = new ArrayList<>();
    boolean dirty;
    /** Too many blocks: the search stopped early. */
    boolean truncated;

    Network(UUID world) {
        this.world = world;
    }

    void sortStores() {
        stores.sort(Comparator.comparingInt((Store s) -> -s.priority));
    }

    /** Every kind of item (amount 1) with its total, in first-seen order. */
    Map<ItemStack, Long> counts() {
        Map<ItemStack, Long> out = new LinkedHashMap<>();
        for (Store s : stores) {
            if (!s.valid()) {
                dirty = true;
                continue;
            }
            for (ItemStack it : s.contents()) {
                if (it == null || it.isEmpty()) continue;
                out.merge(it.asOne(), (long) it.getAmount(), Long::sum);
            }
        }
        return out;
    }

    /**
     * Same totals but reused for a second (open terminals redraw every tick and a click redraws
     * immediately after its own change - the insert/take calls below wipe this cache, so a redraw
     * right after our own move always shows the new truth; changes made outside the plugin show
     * up at most a second late, exactly like the timed refresh did before).
     */
    Map<ItemStack, Long> countsCached(long tick) {
        if (countsCache != null && tick - countsAt < 20) return countsCache;
        countsCache = counts();
        countsAt = tick;
        return countsCache;
    }

    private Map<ItemStack, Long> countsCache;
    private long countsAt = Long.MIN_VALUE;

    private void invalidateCounts() {
        countsCache = null;
    }

    long count(ItemStack key) {
        long n = 0;
        for (Store s : stores) {
            if (!s.valid()) continue;
            for (ItemStack it : s.contents()) if (it != null && !it.isEmpty() && it.isSimilar(key)) n += it.getAmount();
        }
        return n;
    }

    /** Puts {@code it} into the network (not into {@code except}); returns how many went in. */
    int insert(ItemStack it, Store except) {
        if (it == null || it.isEmpty()) return 0;
        invalidateCounts();
        int left = it.getAmount();
        // one contents read per store for the whole job (the old double read cloned every barrel twice)
        Map<Store, ItemStack[]> seen = new IdentityHashMap<>();
        // first where the same item already is, then anywhere (highest priority first)
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (Store s : stores) {
                if (s == except || !s.valid() || !s.accepts(it)) continue;   // filtered stores: don't even look inside
                ItemStack[] c = seen.computeIfAbsent(s, k -> k.contents());
                if ((pass == 0) != s.holdsSimilar(it, c)) continue;
                left -= s.insert(it, left, false, c);
                if (left <= 0) break;
            }
        }
        return it.getAmount() - left;
    }

    /** How many of {@code it} would fit. */
    int room(ItemStack it, Store except) {
        int left = it.getAmount();
        Map<Store, ItemStack[]> seen = new IdentityHashMap<>();
        for (Store s : stores) {
            if (s == except || !s.valid() || !s.accepts(it)) continue;
            left -= s.insert(it, left, true, seen.computeIfAbsent(s, k -> k.contents()));
            if (left <= 0) break;
        }
        return it.getAmount() - left;
    }

    /** Takes up to {@code n} of {@code key} out (lowest priority first); returns the items or null. */
    ItemStack take(ItemStack key, int n, Store except) {
        int got = 0;
        for (int i = stores.size() - 1; i >= 0 && got < n; i--) {
            Store s = stores.get(i);
            if (s == except || !s.valid()) continue;
            got += s.extract(key, n - got);
        }
        if (got > 0) invalidateCounts();
        return got <= 0 ? null : key.asQuantity(got);
    }

    int slots() {
        int n = 0;
        for (Store s : stores) if (s.valid()) n += s.slots();
        return n;
    }

    int freeSlots() {
        int n = 0;
        for (Store s : stores) if (s.valid()) n += s.freeSlots();
        return n;
    }
}
