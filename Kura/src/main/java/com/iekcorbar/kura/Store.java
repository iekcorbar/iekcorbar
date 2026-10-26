package com.iekcorbar.kura;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** One place a network keeps items: a vanilla container or a Filing Cabinet. */
abstract class Store {
    /** -2 lowest .. 2 highest: filled first, emptied last. */
    int priority;
    Filter filter;
    boolean keepLast;

    abstract boolean valid();

    /** Copy of what is inside (may hold nulls). */
    abstract ItemStack[] contents();

    /** Puts up to {@code max} of {@code it} in; returns how many fit. */
    abstract int insert(ItemStack it, int max, boolean simulate);

    /** Takes up to {@code n} items similar to {@code key}; returns how many were taken. */
    abstract int extract(ItemStack key, int n);

    abstract int slots();

    abstract int freeSlots();

    boolean accepts(ItemStack it) {
        return filter == null || filter.test(it, this);
    }

    boolean holdsSimilar(ItemStack it) {
        for (ItemStack s : contents()) if (s != null && !s.isEmpty() && s.isSimilar(it)) return true;
        return false;
    }

    boolean holdsKind(ItemStack it) {
        for (ItemStack s : contents()) if (s != null && !s.isEmpty() && Filter.sameKind(s, it)) return true;
        return false;
    }

    // ------------------------------------------------------------ vanilla container

    static final class Container extends Store {
        final Block block;
        final Material type;
        final Inventory inv;
        private final boolean shulker;

        Container(Block block, Inventory inv) {
            this.block = block;
            this.type = block.getType();
            this.inv = inv;
            this.shulker = Tag.SHULKER_BOXES.isTagged(type);
        }

        @Override
        boolean valid() {
            // something may have removed the block without an event (another plugin's machine)
            return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) && block.getType() == type;
        }

        @Override
        ItemStack[] contents() {
            return inv.getStorageContents();
        }

        @Override
        int insert(ItemStack it, int max, boolean simulate) {
            if (max <= 0 || !accepts(it) || (shulker && Tag.SHULKER_BOXES.isTagged(it.getType()))) return 0;
            int left = max, stack = Math.min(it.getMaxStackSize(), inv.getMaxStackSize());
            ItemStack[] c = inv.getStorageContents();
            for (int i = 0; i < c.length && left > 0; i++) {
                ItemStack s = c[i];
                if (s == null || s.isEmpty() || s.getAmount() >= stack || !s.isSimilar(it)) continue;
                int n = Math.min(left, stack - s.getAmount());
                if (!simulate) inv.setItem(i, s.asQuantity(s.getAmount() + n));
                left -= n;
            }
            for (int i = 0; i < c.length && left > 0; i++) {
                if (c[i] != null && !c[i].isEmpty()) continue;
                int n = Math.min(left, stack);
                if (!simulate) inv.setItem(i, it.asQuantity(n));
                c[i] = it.asQuantity(n); // so the next empty slot is used next
                left -= n;
            }
            return max - left;
        }

        @Override
        int extract(ItemStack key, int n) {
            int got = 0;
            ItemStack[] c = inv.getStorageContents();
            for (int i = 0; i < c.length && got < n; i++) {
                ItemStack s = c[i];
                if (s == null || s.isEmpty() || !s.isSimilar(key)) continue;
                int avail = keepLast ? s.getAmount() - 1 : s.getAmount();
                if (avail <= 0) continue;
                int take = Math.min(avail, n - got);
                inv.setItem(i, s.getAmount() - take <= 0 ? null : s.asQuantity(s.getAmount() - take));
                got += take;
            }
            return got;
        }

        @Override
        int slots() {
            return inv.getStorageContents().length;
        }

        @Override
        int freeSlots() {
            int n = 0;
            for (ItemStack s : inv.getStorageContents()) if (s == null || s.isEmpty()) n++;
            return n;
        }
    }

    // ------------------------------------------------------------ Filing Cabinet

    static final class Cabinet extends Store {
        final Part part;
        final int capacity;
        private final Runnable changed;

        Cabinet(Part part, int capacity, Runnable changed) {
            this.part = part;
            this.capacity = capacity;
            this.changed = changed;
        }

        @Override
        boolean valid() {
            return part.main != null || part.world.isChunkLoaded(part.x >> 4, part.z >> 4);
        }

        @Override
        ItemStack[] contents() {
            ItemStack[] out = new ItemStack[part.contents.size()];
            for (int i = 0; i < out.length; i++) out[i] = part.contents.get(i).clone();
            return out;
        }

        @Override
        int insert(ItemStack it, int max, boolean simulate) {
            if (max <= 0 || !accepts(it)) return 0;
            List<ItemStack> c = part.contents;
            if (!c.isEmpty() && c.get(0).getType() != it.getType()) return 0; // one kind of item per cabinet
            int room = capacity - part.stored(), left = Math.min(max, room);
            if (left <= 0) return 0;
            int want = left, stack = it.getMaxStackSize();
            for (int i = 0; i < c.size() && left > 0; i++) {
                ItemStack s = c.get(i);
                if (s.getAmount() >= stack || !s.isSimilar(it)) continue;
                int n = Math.min(left, stack - s.getAmount());
                if (!simulate) s.setAmount(s.getAmount() + n);
                left -= n;
            }
            while (left > 0) {
                int n = Math.min(left, stack);
                if (!simulate) c.add(it.asQuantity(n));
                left -= n;
            }
            if (!simulate) changed.run();
            return want;
        }

        @Override
        int extract(ItemStack key, int n) {
            int got = 0;
            for (int i = part.contents.size() - 1; i >= 0 && got < n; i--) {
                ItemStack s = part.contents.get(i);
                if (!s.isSimilar(key)) continue;
                int take = Math.min(s.getAmount(), n - got);
                if (take == s.getAmount()) part.contents.remove(i);
                else s.setAmount(s.getAmount() - take);
                got += take;
            }
            if (got > 0) changed.run();
            return got;
        }

        @Override
        int slots() {
            return Math.max(1, capacity / 64);
        }

        @Override
        int freeSlots() {
            return Math.max(0, (capacity - part.stored()) / 64);
        }
    }
}
