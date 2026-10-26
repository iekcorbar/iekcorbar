package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /props - a creative-tab-like chest menu with every prop item: the base item
 * of each props.yml family plus the Steam Wrench. Click = 1, shift-click = a
 * full stack. Only for holders of iekcorbar.props.creative, and only while in
 * creative mode (same idea as the free heads: survival players craft them).
 * The real creative inventory can't be extended - its tabs are client-side.
 */
public class PropsMenu implements CommandExecutor, Listener {

    private static final int PAGE_SIZE = 45;
    private static final int PREV = 45, INFO = 49, NEXT = 53;

    private final IekcorbarSiteSync plugin;
    private final PropPlacementManager props;
    private final SteamWrenchManager wrench;

    /** Marks our inventories and remembers what each slot gives. */
    private static final class Holder implements InventoryHolder {
        final int page;
        final List<ItemStack> items;
        Inventory inventory;

        Holder(int page, List<ItemStack> items) {
            this.page = page;
            this.items = items;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public PropsMenu(IekcorbarSiteSync plugin, PropPlacementManager props, SteamWrenchManager wrench) {
        this.plugin = plugin;
        this.props = props;
        this.wrench = wrench;
    }

    public void enable() {
        if (plugin.getCommand("props") != null) plugin.getCommand("props").setExecutor(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the props menu.");
            return true;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.sendMessage(AdvancementManager.text("&cThe props menu only works in creative mode. In survival, craft them!"));
            return true;
        }
        List<ItemStack> items = allItems();
        if (items.isEmpty()) {
            player.sendMessage(AdvancementManager.text("&cNo props available (is FreeMinecraftModels running?)."));
            return true;
        }
        open(player, 0, items);
        return true;
    }

    private List<ItemStack> allItems() {
        List<ItemStack> items = new ArrayList<>();
        for (String base : props.bases()) {
            ItemStack item = props.baseItem(base, 1);
            if (item != null) items.add(item);
        }
        items.add(wrench.wrench());
        return items;
    }

    private void open(Player player, int page, List<ItemStack> items) {
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder(page, items);
        Inventory inv = Bukkit.createInventory(holder, 54,
                AdvancementManager.text("&8Steampunk Props" + (pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : "")));
        holder.inventory = inv;
        for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < items.size(); i++) {
            inv.setItem(i, items.get(page * PAGE_SIZE + i));
        }
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = PAGE_SIZE; slot < 54; slot++) inv.setItem(slot, filler);
        if (page > 0) inv.setItem(PREV, button(Material.ARROW, "&ePrevious page"));
        if (page < pages - 1) inv.setItem(NEXT, button(Material.ARROW, "&eNext page"));
        inv.setItem(INFO, button(Material.BOOK, "&6Click: take 1 &7| &6Shift-click: take a stack"));
        player.openInventory(inv);
    }

    private static ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(AdvancementManager.text(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); // nothing moves in or out except what we hand out
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == PREV && holder.page > 0) {
            open(player, holder.page - 1, holder.items);
        } else if (slot == NEXT) {
            open(player, holder.page + 1, holder.items);
        } else if (slot < PAGE_SIZE) {
            int index = holder.page * PAGE_SIZE + slot;
            if (index >= holder.items.size()) return;
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.closeInventory();
                player.sendMessage(AdvancementManager.text("&cThe props menu only works in creative mode."));
                return;
            }
            ItemStack give = holder.items.get(index).clone();
            give.setAmount(event.isShiftClick() ? give.getMaxStackSize() : 1);
            player.getInventory().addItem(give);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
