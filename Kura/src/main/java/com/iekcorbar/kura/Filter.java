package com.iekcorbar.kura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a filter slot lets through. Built from the item in the slot: an Item Filter
 * (chosen items, allow/deny, match components), a Tag Filter (item groups), a
 * Polymorphic Filter (only what the container already holds) or any plain item
 * (just that kind of item).
 */
final class Filter {
    enum Kind { PLAIN, ITEMS, TAGS, POLY }

    final Kind kind;
    final List<ItemStack> items;
    final List<Tag<Material>> tags;
    final boolean deny, components;

    private Filter(Kind kind, List<ItemStack> items, List<Tag<Material>> tags, boolean deny, boolean components) {
        this.kind = kind;
        this.items = items;
        this.tags = tags;
        this.deny = deny;
        this.components = components;
    }

    /** Null for an empty slot. */
    static Filter of(Items itemsApi, ItemStack slot) {
        if (slot == null || slot.isEmpty()) return null;
        KItem t = itemsApi.typeOf(slot);
        if (t == KItem.ITEM_FILTER) {
            return new Filter(Kind.ITEMS, itemsApi.filterItems(slot), List.of(), itemsApi.flag(slot, "filter_deny"), itemsApi.flag(slot, "filter_nbt"));
        }
        if (t == KItem.TAG_FILTER) {
            List<Tag<Material>> tags = new ArrayList<>();
            for (String id : itemsApi.filterTags(slot)) {
                NamespacedKey key = NamespacedKey.fromString(id);
                Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                if (tag != null) tags.add(tag);
            }
            return new Filter(Kind.TAGS, List.of(), tags, itemsApi.flag(slot, "filter_deny"), false);
        }
        if (t == KItem.POLY_FILTER) return new Filter(Kind.POLY, List.of(), List.of(), false, false);
        return new Filter(Kind.PLAIN, List.of(slot.asOne()), List.of(), false, false);
    }

    /** {@code store} = the container being filled (needed by the Polymorphic Filter), may be null. */
    boolean test(ItemStack it, Store store) {
        switch (kind) {
            case PLAIN -> {
                return sameKind(it, items.get(0));
            }
            case ITEMS -> {
                boolean match = false;
                for (ItemStack f : items) if (components ? f.isSimilar(it.asOne()) : sameKind(f, it)) { match = true; break; }
                return match != deny;
            }
            case TAGS -> {
                boolean match = false;
                for (Tag<Material> tag : tags) if (tag.isTagged(it.getType())) { match = true; break; }
                return match != deny;
            }
            case POLY -> {
                return store == null || store.holdsKind(it);
            }
        }
        return true;
    }

    /** Same kind of item: same material and same item model (so Kitchen foods, Steamworks plates etc. stay apart). */
    static boolean sameKind(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getType() != b.getType()) return false;
        return Objects.equals(a.getData(DataComponentTypes.ITEM_MODEL), b.getData(DataComponentTypes.ITEM_MODEL));
    }
}
