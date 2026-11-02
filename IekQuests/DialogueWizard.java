package com.iekcorbar.quests;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DialogueWizard implements Listener {
    private static final class Holder implements InventoryHolder {
        final UUID owner;
        final String page;
        final String id;
        final int index;
        Holder(UUID owner, String page, String id, int index) {
            this.owner = owner; this.page = page; this.id = id; this.index = index;
        }
        @Override public Inventory getInventory() { return null; }
    }

    private static final class Prompt {
        final String kind;
        final String id;
        final String field;
        Prompt(String kind, String id, String field) { this.kind = kind; this.id = id; this.field = field; }
    }

    private final IekQuestsPlugin plugin;
    private final DialogueManager manager;
    private final NamespacedKey itemKey;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    DialogueWizard(IekQuestsPlugin plugin, DialogueManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.itemKey = new NamespacedKey(plugin, "dialogue_wizard");
    }

    void open(Player player) {
        if (!allowed(player)) {
            player.sendMessage(Component.text("Dialogue editing requires iekquests.dialogue, iekquests.wizard, or iekquests.admin."));
            return;
        }
        home(player);
    }

    private boolean allowed(Player player) {
        return player.hasPermission("iekquests.dialogue") || player.hasPermission("iekquests.wizard") || player.hasPermission("iekquests.admin");
    }

    private void home(Player player) {
        Inventory inv = inventory(player, "home", "", 0, 54, "&8Dialogue and NPC wizard");
        put(inv, 10, Material.WRITABLE_BOOK, "&aCreate dialogue", "create-dialogue");
        put(inv, 11, Material.BOOK, "&eEdit dialogue", "list-dialogues");
        put(inv, 12, Material.WRITABLE_BOOK, "&aCreate response", "create-response");
        put(inv, 13, Material.PAPER, "&eEdit response", "list-responses");
        put(inv, 15, Material.NAME_TAG, "&bDefine player tag", "define-player-tag");
        put(inv, 16, Material.NAME_TAG, "&dDefine server tag", "define-server-tag");
        put(inv, 29, Material.VILLAGER_SPAWN_EGG, "&6Bind Citizens NPC", "bind-npc");
        put(inv, 30, Material.LEAD, "&cUnbind Citizens NPC", "unbind-npc");
        put(inv, 31, Material.BOOKSHELF, "&7List graph entries", "list-all");
        put(inv, 49, Material.ARROW, "&cBack to Quest Wizard", "back");
        put(inv, 53, Material.BARRIER, "&cClose", "close");
        player.openInventory(inv);
    }

    private void list(Player player, String kind, int page) {
        List<String> ids = kind.equals("dialogue") ? manager.dialogueIds() : manager.responseIds();
        ids = ids.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int pages = Math.max(1, (ids.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = inventory(player, "list", kind, page, 54, "&8Edit " + kind + "s " + (page + 1) + "/" + pages);
        int start = page * 45;
        for (int i = start; i < Math.min(ids.size(), start + 45); i++) {
            String id = ids.get(i);
            put(inv, i - start, kind.equals("dialogue") ? Material.WRITABLE_BOOK : Material.PAPER,
                    "&e" + id, "entry:" + id);
        }
        if (page > 0) put(inv, 45, Material.ARROW, "&7Previous page", "page:-");
        put(inv, 49, Material.ARROW, "&cBack", "back");
        if (page + 1 < pages) put(inv, 53, Material.ARROW, "&7Next page", "page:+");
        player.openInventory(inv);
    }

    private void fields(Player player, String kind, String id) {
        Inventory inv = inventory(player, "fields-" + kind, id, 0, 54, "&8Edit " + kind + ": " + id);
        if (kind.equals("dialogue")) {
            put(inv, 10, Material.OAK_SIGN, "&eTitle", "field:title");
            put(inv, 11, Material.WRITABLE_BOOK, "&eText lines", "Enter text with literal \\n between lines.", "field:lines");
            put(inv, 12, Material.NOTE_BLOCK, "&eVoice/sound key", "field:voice");
            put(inv, 13, Material.NAME_TAG, "&ePlayer prerequisites", "Comma-separated; prefix with ! to require absence.", "field:prerequisites");
            put(inv, 14, Material.NAME_TAG, "&eServer prerequisites", "Comma-separated; prefix with ! to require absence.", "field:server-prerequisites");
            put(inv, 15, Material.PAPER, "&eOrdered response IDs", "Comma-separated response IDs.", "field:responses");
            put(inv, 16, Material.COMMAND_BLOCK, "&eCommands", "Comma-separated command entries.", "field:commands");
            put(inv, 19, Material.EMERALD, "&eAdd player tags", "Comma-separated defined tag IDs.", "field:add-player-tags");
            put(inv, 20, Material.DIAMOND, "&eAdd server tags", "Comma-separated defined tag IDs.", "field:add-server-tags");
            put(inv, 21, Material.EMERALD, "&eRewards", "Comma-separated IDs defined in Reward.yml; runs when this dialogue opens.", "field:reward");
            put(inv, 22, Material.CLOCK, "&eSequences", "Comma-separated IDs defined in Sequences.yml.", "field:sequences");
        } else {
            put(inv, 10, Material.OAK_SIGN, "&eResponse text", "field:text");
            put(inv, 11, Material.NOTE_BLOCK, "&eVoice/sound key", "field:voice");
            put(inv, 12, Material.NAME_TAG, "&ePlayer prerequisites", "Comma-separated; prefix with ! to require absence.", "field:prerequisites");
            put(inv, 13, Material.NAME_TAG, "&eServer prerequisites", "Comma-separated; prefix with ! to require absence.", "field:server-prerequisites");
            put(inv, 14, Material.PAPER, "&eNext dialogue", "One ID, ordered comma fallback, or quoted [id; id] random group; use none to end.", "field:next-dialogue");
            put(inv, 15, Material.COMMAND_BLOCK, "&eCommands", "Comma-separated command entries.", "field:commands");
            put(inv, 16, Material.EMERALD, "&eAdd player tags", "Comma-separated defined tag IDs.", "field:add-player-tags");
            put(inv, 19, Material.DIAMOND, "&eAdd server tags", "Comma-separated defined tag IDs.", "field:add-server-tags");
            put(inv, 20, Material.CLOCK, "&ePer-player cooldown", "-1, 1h, 20m, 33s, or 0h13m44s.", "field:per-player-cooldown");
            put(inv, 21, Material.IRON_SWORD, "&eAdd native quest action", "quest-id:action[:target-stage][:repeat-id]", "field:quest-action");
            put(inv, 22, Material.SHIELD, "&eReplace quest actions", "Use action|action or none to clear.", "field:quest-actions");
            put(inv, 23, Material.HOPPER, "&eExecute objective", "Manual objective ID, or none.", "field:executeobjective");
            put(inv, 24, Material.REDSTONE, "&eRemove player tags", "Comma-separated defined tags; applied after executeobjective succeeds.", "field:remove-player-tags");
            put(inv, 25, Material.CLOCK, "&eSequences", "Comma-separated IDs defined in Sequences.yml.", "field:sequences");
        }
        put(inv, 49, Material.ARROW, "&cBack", "back");
        player.openInventory(inv);
    }

    private Inventory inventory(Player player, String page, String id, int index, int size, String title) {
        return Bukkit.createInventory(new Holder(player.getUniqueId(), page, id, index), size, legacy(title));
    }

    private void put(Inventory inventory, int slot, Material material, String name, String tag) {
        put(inventory, slot, material, name, null, tag);
    }

    private void put(Inventory inventory, int slot, Material material, String name, String lore, String tag) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy(name));
        if (lore != null) meta.lore(List.of(legacy(lore)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tag);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder) || !holder.owner.equals(player.getUniqueId())) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (action == null) return;
        route(player, holder, action);
    }

    private void route(Player player, Holder holder, String action) {
        switch (action) {
            case "close" -> { prompts.remove(player.getUniqueId()); player.closeInventory(); }
            case "back" -> {
                prompts.remove(player.getUniqueId());
                if (holder.page.startsWith("fields-")) list(player, holder.page.substring(7), 0);
                else if (holder.page.equals("list")) home(player);
                else if (player.hasPermission("iekquests.wizard") || player.hasPermission("iekquests.admin")) plugin.questWizard().open(player);
                else home(player);
            }
            case "create-dialogue" -> ask(player, new Prompt("create-dialogue", "", ""), "Enter a unique dialogue ID.");
            case "create-response" -> ask(player, new Prompt("create-response", "", ""), "Enter a unique response ID.");
            case "define-player-tag" -> ask(player, new Prompt("define-tag", "player", ""), "Enter the player tag ID.");
            case "define-server-tag" -> ask(player, new Prompt("define-tag", "server", ""), "Enter the server tag ID.");
            case "bind-npc" -> ask(player, new Prompt("bind-npc", "", ""), "Enter key|Citizens ID|display name|dialogue1,dialogue2 (display name may contain spaces).");
            case "unbind-npc" -> ask(player, new Prompt("unbind-npc", "", ""), "Enter the NPC binding key or Citizens ID.");
            case "list-dialogues" -> list(player, "dialogue", 0);
            case "list-responses" -> list(player, "response", 0);
            case "list-all" -> {
                player.sendMessage(Component.text("Dialogues: " + String.join(", ", manager.dialogueIds())));
                player.sendMessage(Component.text("Responses: " + String.join(", ", manager.responseIds())));
                player.sendMessage(Component.text("Player tags: " + String.join(", ", manager.playerTagIds())));
                player.sendMessage(Component.text("Server tags: " + String.join(", ", manager.serverTagIds())));
                home(player);
            }
            default -> {
                if (action.startsWith("page:")) list(player, holder.id, holder.index + (action.endsWith("+") ? 1 : -1));
                else if (action.startsWith("entry:")) fields(player, holder.id, action.substring(6));
                else if (action.startsWith("field:")) ask(player, new Prompt("set", holder.page.substring(7) + ":" + holder.id, action.substring(6)), "Enter the new value for " + action.substring(6) + ". Type cancel to return.");
            }
        }
    }

    private void ask(Player player, Prompt prompt, String message) {
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        player.sendMessage(legacy("&e" + message));
        player.sendMessage(legacy("&7Type cancel to return. For lists, enter comma-separated values; use none for an empty list."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyPrompt(player, prompt, value));
    }

    private void applyPrompt(Player player, Prompt prompt, String value) {
        if (value.equalsIgnoreCase("cancel")) {
            prompts.remove(player.getUniqueId()); home(player); return;
        }
        prompts.remove(player.getUniqueId());
        try {
            switch (prompt.kind) {
                case "create-dialogue" -> {
                    manager.wizardAction(player, "dialogue", "create", value);
                    if (manager.dialogueIds().contains(value)) fields(player, "dialogue", value); else home(player);
                }
                case "create-response" -> {
                    manager.wizardAction(player, "response", "create", value);
                    if (manager.responseIds().contains(value)) fields(player, "response", value); else home(player);
                }
                case "define-tag" -> {
                    manager.wizardAction(player, "tag", "define", prompt.id, value);
                    home(player);
                }
                case "bind-npc" -> {
                    String[] parts = value.split("\\|", 4);
                    if (parts.length != 4) throw new IllegalArgumentException("Use key|Citizens ID|display name|dialogue1,dialogue2.");
                    manager.wizardAction(player, "npc", "bind", parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim());
                    home(player);
                }
                case "unbind-npc" -> {
                    manager.wizardAction(player, "npc", "unbind", value);
                    home(player);
                }
                case "set" -> {
                    String kind = prompt.id.startsWith("response:") ? "response" : "dialogue";
                    String id = prompt.id.substring(prompt.id.indexOf(':') + 1);
                    manager.wizardAction(player, kind, "set", id, prompt.field, value);
                    fields(player, kind, id);
                }
            }
        } catch (Exception ex) {
            player.sendMessage(Component.text("Wizard input failed: " + ex.getMessage()));
            home(player);
        }
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text == null ? "" : text);
    }
}
