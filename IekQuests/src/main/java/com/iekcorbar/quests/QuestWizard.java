package com.iekcorbar.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.iekcorbar.quests.IekQuestsPlugin.Choice;
import com.iekcorbar.quests.IekQuestsPlugin.Cond;
import com.iekcorbar.quests.IekQuestsPlugin.Quest;
import com.iekcorbar.quests.IekQuestsPlugin.Repeat;
import com.iekcorbar.quests.IekQuestsPlugin.Stage;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * QuestWizard 0.3 - de in-game bouw-wizard voor nieuwe quests (+NPC's).
 *
 * Alles via GUI-knoppen en chat-input; het resultaat gaat naar
 * plugins/IekQuests/wizard-quests.yml - handmatige quests uit quests.yml
 * blijven onaangeroerd. Alleen met iekquests.admin te openen.
 *
 * Stappen:_basis, stages (talk/craft/deliver/walk/kill) met keuzes,
 * herhaal-contracten, beloningen, condities, NPC binden of nieuws aanmaken
 * (via Citizens-reflectie) en opslaan + direct reload.
 */
final class QuestWizard implements Listener {

    // ---------------------------------------------------------------- staat

    static final class WH implements InventoryHolder {
        final UUID who;
        final String page;
        final int a, b;
        WH(UUID who, String page, int a, int b) { this.who = who; this.page = page; this.a = a; this.b = b; }
        @Override public Inventory getInventory() { return null; }
    }

    static final class Input {
        final String ctx;
        final boolean multiline;
        final List<String> acc = new ArrayList<>();
        Input(String ctx, boolean ml) { this.ctx = ctx; this.multiline = ml; }
    }

    private final IekQuestsPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Quest> drafts = new HashMap<>();
    private final Map<UUID, Input> waiting = new HashMap<>();

    QuestWizard(IekQuestsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "wiz");
    }

    // ---------------------------------------------------------------- entry

    void open(Player p) {
        if (!p.hasPermission("iekquests.admin")) {
            p.sendMessage(legacy("&cYou need iekquests.admin for the quest wizard."));
            return;
        }
        drafts.computeIfAbsent(p.getUniqueId(), u -> {
            Quest q = new Quest();
            q.id = "new_quest";
            q.title = "&fNew quest";
            q.fromWizard = true;
            Stage t = new Stage();
            t.key = "s1";
            t.label = "&aContinue";
            t.lines.add("&fNPC: &7Hello there!");
            q.stages.put("s1", t);
            return q;
        });
        p.sendMessage(legacy("&7Wizard: everything is saved to &ewizard-quests.yml&7. "
                + "Hand-made quests in quests.yml are never touched."));
        main(p);
    }

    // ---------------------------------------------------------------- pages

    private void main(Player p) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null) { open(p); return; }
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "main", 0, 0), 54,
                legacy("&8✚ Quest wizard &7- " + q.id));
        put(inv, 0, Material.BARRIER, "&cQuest id", "&7current: &f" + q.id, "q.id");
        put(inv, 1, Material.NAME_TAG, "&cTitle", "&7current: " + q.title, "q.title");
        put(inv, 2, Material.PAPER, "&7Subtitle", "&7current: &f" + q.subtitle, "q.sub");
        put(inv, 3, mat(q.icon, Material.BOOK), "&6Icon material", "&7current: &f" + q.icon, "q.icon");
        put(inv, 4, Material.HOPPER, "&6Order", "&7current: &f" + q.order, "q.order");
        put(inv, 5, Material.PLAYER_HEAD, "&aNPC binding",
                "&7npc-id: &f" + (q.npcId < 0 ? "menu only" : q.npcId), "npc");
        put(inv, 6, Material.TRIPWIRE_HOOK, "&dStart conditions",
                "&7current: &f" + condText(q.startConds), "q.start");
        put(inv, 7, Material.BOOK, "&dJournal lines", "&7lines: &f" + q.journal.size(), "q.journal");
        put(inv, 8, Material.CHEST, "&dQuest rewards", "&7lines: &f" + q.rewards.size(), "q.rewards");

        int s = 18;
        for (var st : q.stages.values()) {
            if (s > 26) break;
            put(inv, s++, mat(stageIcon(st.kind), Material.PAPER), "&e" + st.key + " &8(" + st.kind + ")",
                    "&7" + plain(st.label), "stage:" + idxOfStage(q, st));
        }
        put(inv, 27, Material.LIME_DYE, "&a+ Add stage", "addstage");

        int r = 28;
        for (var rp : q.repeats) {
            if (r > 35) break;
            put(inv, r++, mat("HOPPER", Material.HOPPER), "&b" + rp.id,
                    "&7" + rp.amount + "x " + rp.material + " - " + rp.cooldown + "s", "rep:" + idxOfRep(q, rp));
        }
        put(inv, 36, Material.MAP, "&a+ Add repeat contract", "addrep");

        put(inv, 45, Material.OAK_SIGN, "&fHelp (syntax)", "help");
        put(inv, 47, Material.CHEST_MINECART, "&6Manage quests", "manage");
        put(inv, 49, Material.ENDER_EYE, "&d&lSave & activate", "save");
        put(inv, 53, Material.BARRIER, "&cClose", "close");
        p.openInventory(inv);
    }

    private void npcPage(Player p) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null) { open(p); return; }
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "npc", 0, 0), 45,
                legacy("&8✚ NPC binding"));
        put(inv, 13, Material.PLAYER_HEAD, "&aNPC: " + (q.npcId < 0 ? "&cnot bound" : "&f" + q.npcId),
                q.npcId < 0 ? "&7bind or create one below" : "&7the quest opens on clicking this NPC", "-");
        put(inv, 19, Material.PAPER, "&eEnter existing NPC id", "&7e.g. 79 - the Citizens id", "npcid");
        put(inv, 20, Material.ARMOR_STAND, "&aCreate new NPC", "&7name via chat, spawns at YOU", "npcnew");
        put(inv, 21, Material.ENDER_PEARL, "&dPull NPC to me", "&7teleport the bound NPC here", "npcpull");
        put(inv, 40, Material.BARRIER, "&cBack", "back");
        p.openInventory(inv);
    }

    private void stagePage(Player p, int i) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null || i < 0 || i >= q.stages.size()) { main(p); return; }
        Stage st = nthStage(q, i);
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "stage", i, 0), 54,
                legacy("&8✚ Stage: " + st.key));
        put(inv, 0, Material.COMPARATOR, "&cStage key", "&7current: &f" + st.key, "s.key:" + i);
        put(inv, 1, Material.OAK_SIGN, "&cButton label", "&7" + st.label, "s.label:" + i);
        put(inv, 2, Material.TRIPWIRE_HOOK, "&dStage conditions", "&7" + condText(st.conds), "s.cond:" + i);
        put(inv, 3, Material.NOTE_BLOCK, "&dVoice sound key", "&7current: &f" + (st.voice.isEmpty() ? "-" : st.voice), "s.voice:" + i);
        put(inv, 4, mat(st.material, Material.CAULDRON), "&6Material", "&7current: &f" + st.material, "s.mat:" + i);
        put(inv, 5, Material.CLOCK, "&6Amount", "&7current: &f" + st.amount, "s.amount:" + i);
        put(inv, 6, Material.BLAZE_ROD, "&6Mob type (kill)", "&7current: &f" + (st.mobType.isEmpty() ? "-" : st.mobType), "s.mob:" + i);
        put(inv, 7, st.loc == null ? Material.GRAY_DYE : Material.GREEN_DYE, "&6Location (walk)",
                st.loc == null ? "&7not set - click to use your spot" : "&7" + fmtLoc(st.loc), "s.loc:" + i);
        put(inv, 8, Material.COMPASS, "&6Radius", "&7current: &f" + st.radius, "s.radius:" + i);
        put(inv, 9, Material.ARROW, "&eNext stage key", "&7current: &f" + (st.next.isEmpty() ? "(automatic)" : st.next), "s.next:" + i);
        put(inv, 10, Material.LAVA_BUCKET, "&eGoto override", "&7current: &f" + (st.gotoKey.isEmpty() ? "-" : st.gotoKey), "s.goto:" + i);
        put(inv, 19, Material.WRITABLE_BOOK, "&dConversation lines", "&7lines: &f" + st.lines.size(), "s.lines:" + i);
        put(inv, 20, Material.OAK_DOOR, "&dChoices (branching)", "&7choices: &f" + st.choices.size(), "s.choices:" + i);
        put(inv, 21, Material.CHEST, "&dStage rewards", "&7lines: &f" + st.rewards.size(), "s.rewards:" + i);
        put(inv, 29, Material.LIME_DYE, "&aType: talk", st.kind.equals("talk") ? "&2◄ current" : "", "s.kind:" + i + ":talk");
        put(inv, 30, Material.CRAFTING_TABLE, "&aType: craft", st.kind.equals("craft") ? "&2◄ current" : "", "s.kind:" + i + ":craft");
        put(inv, 31, Material.HOPPER, "&aType: deliver", st.kind.equals("deliver") ? "&2◄ current" : "", "s.kind:" + i + ":deliver");
        put(inv, 32, Material.LEATHER_BOOTS, "&aType: walk", st.kind.equals("walk") ? "&2◄ current" : "", "s.kind:" + i + ":walk");
        put(inv, 33, Material.DIAMOND_SWORD, "&aType: kill", st.kind.equals("kill") ? "&2◄ current" : "", "s.kind:" + i + ":kill");
        put(inv, 45, Material.ARROW, "&cBack", "back");
        put(inv, 48, Material.ARROW, "&aMove up", "s.up:" + i);
        put(inv, 49, Material.ARROW, "&aMove down", "s.down:" + i);
        put(inv, 53, Material.LAVA_BUCKET, "&cDelete stage", "s.del:" + i);
        p.openInventory(inv);
    }

    private void choicesPage(Player p, int i) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null) { main(p); return; }
        var st = nthStage(q, i);
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "choices", i, 0), 54,
                legacy("&8✚ Choices of " + st.key));
        int s = 10;
        for (var c : st.choices) {
            if (s > 34) break;
            put(inv, s++, Material.OAK_SIGN, "&e" + plain(c.label), "&7goto: &f" + (c.gotoKey.isEmpty() ? "-" : c.gotoKey),
                    "choice:" + i + ":" + st.choices.indexOf(c));
        }
        put(inv, 40, Material.LIME_DYE, "&a+ Add choice", "addchoice:" + i);
        put(inv, 45, Material.ARROW, "&cBack", "back");
        p.openInventory(inv);
    }

    private void choicePage(Player p, int i, int j) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null || j >= nthStage(q, i).choices.size()) { choicesPage(p, i); return; }
        var c = nthStage(q, i).choices.get(j);
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "choice", i, j), 45,
                legacy("&8✚ Choice " + (j + 1) + " of " + nthStage(q, i).key));
        put(inv, 0, Material.OAK_SIGN, "&cLabel (button text)", "&7" + c.label, "c.label:" + i + ":" + j);
        put(inv, 1, Material.WRITABLE_BOOK, "&eNPC reply (say)", "&7" + (c.say == null || c.say.isEmpty() ? "-" : c.say), "c.say:" + i + ":" + j);
        put(inv, 2, Material.ARROW, "&dGo to stage key", "&7current: &f" + (c.gotoKey.isEmpty() ? "-" : c.gotoKey), "c.goto:" + i + ":" + j);
        put(inv, 3, Material.TRIPWIRE_HOOK, "&6Conditions", "&7" + condText(c.conds), "c.cond:" + i + ":" + j);
        put(inv, 4, Material.NOTE_BLOCK, "&6Voice sound key", "&7current: &f" + (c.voice.isEmpty() ? "-" : c.voice), "c.voice:" + i + ":" + j);
        put(inv, 6, Material.CHEST, "&aDo-actions (rewards syntax)", "&7lines: &f" + c.doList.size(), "c.do:" + i + ":" + j);
        put(inv, 40, Material.LAVA_BUCKET, "&cDelete this choice", "c.del:" + i + ":" + j);
        put(inv, 45, Material.ARROW, "&cBack", "back");
        p.openInventory(inv);
    }

    private void repPage(Player p, int i) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null || i >= q.repeats.size()) { main(p); return; }
        var rp = q.repeats.get(i);
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "rep", i, 0), 45,
                legacy("&8✚ Contract: " + rp.id));
        put(inv, 0, Material.OAK_SIGN, "&cButton label", "&7" + rp.label, "r.label:" + i);
        put(inv, 1, mat(rp.material, Material.CAULDRON), "&6Material", "&7current: &f" + rp.material, "r.mat:" + i);
        put(inv, 2, Material.CLOCK, "&6Amount", "&7current: &f" + rp.amount, "r.amount:" + i);
        put(inv, 3, Material.GOLD_INGOT, "&6Money reward", "&7current: &f" + rp.money, "r.money:" + i);
        put(inv, 4, Material.EMERALD, "&6Elite coins", "&7current: &f" + rp.eco, "r.eco:" + i);
        put(inv, 5, Material.HOPPER, "&6Cooldown seconds", "&7current: &f" + rp.cooldown, "r.cd:" + i);
        put(inv, 6, rp.kind.equals("walk") ? Material.LEATHER_BOOTS : Material.HOPPER, "&eType: " + rp.kind,
                "&7click to toggle deliver/walk", "r.kind:" + i);
        put(inv, 7, rp.loc == null ? Material.GRAY_DYE : Material.GREEN_DYE, "&6Location (walk)",
                rp.loc == null ? "&7not set - click to use your spot" : "&7" + fmtLoc(rp.loc), "r.loc:" + i);
        put(inv, 8, Material.NOTE_BLOCK, "&6Voice sound key", "&7current: &f" + (rp.voice.isEmpty() ? "-" : rp.voice), "r.voice:" + i);
        put(inv, 10, Material.CHEST, "&dExtra reward lines", "&7lines: &f" + rp.rewards.size(), "r.rewards:" + i);
        put(inv, 12, Material.DAYLIGHT_DETECTOR, "&dOnce per day", rp.daily ? "&2ON" : "&7off", "r.daily:" + i);
        put(inv, 40, Material.LAVA_BUCKET, "&cDelete contract", "r.del:" + i);
        put(inv, 45, Material.ARROW, "&cBack", "back");
        p.openInventory(inv);
    }

    private void helpPage(Player p) {
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "help", 0, 0), 54, legacy("&8✚ Wizard help"));
        List<String> txt = List.of(
                "&eConditions (comma = and):", "&ftag:name &7| &f!tag:name &7| &fpoint:cat>=N",
                "&fitem:MAT>=N &7| &fperm:node &7| &fday &7| &fnight",
                "&fmoney>=N &7| &fmoney<N",
                "&eRewards / do-actions:", "&fmoney N &7| &feco N &7| &fpay N (cost) &7| &ftitle A|B &7| &fsound ns:key",
                "&fsay text &7| &ftag+ name &7| &ftag- name", "&fpoint cat +N &7| &feffect TYPE secs amp",
                "&fgive MAT N &7| &fshop console-cmd %player%",
                "&fteleport x;y;z;world &8(staff only - players get a compass)",
                "&fcompass x;y;z;world &7| &fperm+ node &7| &fperm- node",
                "&7anything else runs as a &fconsole command&7.",
                "&eStage extras: 'name' filter on deliver/craft",
                "&7(matches only items whose display name contains it - e.g. Techcore steel).",
                "&eVoices: steamworks:milo.line01 ... igor.line12",
                "&eNPCs: bind by Citizens id (79 = Vorkan) or create new.",
                "&eSave writes to &fwizard-quests.yml&e - never to quests.yml.");
        int s = 10;
        for (String t : txt) {
            if (s > 44) break;
            if (s == 13) s = 14;
            inv.setItem(s++, lore(Material.PAPER, t));
        }
        put(inv, 45, Material.ARROW, "&cBack", "back");
        p.openInventory(inv);
    }

    private void managePage(Player p) {
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "manage", 0, 0), 54, legacy("&8✚ Manage quests"));
        List<Quest> all = plugin.questsView();
        int s = 0;
        for (var q : all) {
            if (s > 44) break;
            var it = lore(mat(q.icon, Material.BOOK), "&e" + plain(q.title),
                    q.fromWizard ? "&awizard-made - click to edit/delete" : "&8from quests.yml - edit in repo");
            var im2 = it.getItemMeta();
            im2.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                    q.fromWizard ? "manage:" + q.id : "noop");
            it.setItemMeta(im2);
            inv.setItem(s++, it);
        }
        put(inv, 49, Material.ARROW, "&cBack", "back");
        p.openInventory(inv);
    }

    private void confirmPage(Player p, String qid) {
        var inv = Bukkit.createInventory(new WH(p.getUniqueId(), "confirm", 0, qid.hashCode()), 45,
                legacy("&8✚ " + qid));
        put(inv, 20, Material.WRITABLE_BOOK, "&aEdit this quest", "edit:" + qid);
        put(inv, 24, Material.LAVA_BUCKET, "&cDelete this quest", "delq:" + qid);
        put(inv, 40, Material.BARRIER, "&cBack", "back");
        p.openInventory(inv);
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof WH h) || !h.who.equals(p.getUniqueId())) return;
        e.setCancelled(true);
        var cur = e.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        ItemMeta im = cur.getItemMeta();
        String tag = im.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (tag == null) return;
        route(p, h, tag);
    }

    private void route(Player p, WH h, String tag) {
        Quest q = drafts.get(p.getUniqueId());
        switch (tag) {
            case "close" -> { p.closeInventory(); waiting.remove(p.getUniqueId()); return; }
            case "back" -> {
                waiting.remove(p.getUniqueId());
                switch (h.page) {
                    case "npc" -> main(p);
                    case "stage" -> main(p);
                    case "choices", "choice" -> stagePage(p, h.a);
                    case "rep" -> main(p);
                    case "help", "manage", "confirm" -> main(p);
                    default -> main(p);
                }
                return;
            }
            case "help" -> { helpPage(p); return; }
            case "manage" -> { managePage(p); return; }
            case "save" -> { save(p); return; }
            case "npc" -> { npcPage(p); return; }
            case "npcid" -> { ask(p, "q.npcid", "Enter the Citizens NPC id (number, or -1 for menu-only)", q == null ? "" : String.valueOf(q.npcId)); return; }
            case "npcnew" -> { ask(p, "q.npcnew", "Type the NAME for the new NPC - it spawns right at you", ""); return; }
            case "npcpull" -> {
                if (q == null || q.npcId < 0) msg(p, "&7Nothing bound to pull.");
                else if (pullNpc(p, q.npcId)) msg(p, "&aNPC " + q.npcId + " teleported to you.");
                else msg(p, "&cCould not reach Citizens for that NPC id.");
                npcPage(p);
                return;
            }
            case "addstage" -> {
                if (q == null) return;
                Stage st = new Stage();
                st.key = "s" + (q.stages.size() + 1);
                st.label = "&aContinue";
                int guard = 0;
                while (q.stages.containsKey(st.key) && guard++ < 50) st.key = "s" + (guard + q.stages.size() + 1);
                q.stages.put(st.key, st);
                stagePage(p, idxOfStage(q, st));
                return;
            }
            case "addrep" -> {
                if (q == null) return;
                IekQuestsPlugin.Repeat rp = new IekQuestsPlugin.Repeat();
                rp.id = "r" + (q.repeats.size() + 1);
                rp.label = "&aHand in the goods";
                rp.voice = "";
                q.repeats.add(rp);
                repPage(p, q.repeats.size() - 1);
                return;
            }
        }
        if (tag.startsWith("stage:")) { stagePage(p, Integer.parseInt(tag.substring(6))); return; }
        if (tag.startsWith("rep:")) { repPage(p, Integer.parseInt(tag.substring(4))); return; }
        if (tag.startsWith("choice:")) {
            String[] a = tag.substring(7).split(":");
            choicePage(p, Integer.parseInt(a[0]), Integer.parseInt(a[1]));
            return;
        }
        if (tag.startsWith("addchoice:")) {
            var st = nthStage(q, Integer.parseInt(tag.substring(10)));
            IekQuestsPlugin.Choice c = new IekQuestsPlugin.Choice();
            c.label = "&aOption " + (st.choices.size() + 1);
            c.say = "";
            st.choices.add(c);
            choicePage(p, Integer.parseInt(tag.substring(10)), st.choices.size() - 1);
            return;
        }
        if (tag.startsWith("edit:")) {
            var src = plugin.questById(tag.substring(5));
            if (src != null) {
                drafts.put(p.getUniqueId(), copyOf(src));
                msg(p, "&aLoaded &f" + src.id + "&a into the wizard - changes save to wizard-quests.yml.");
            }
            main(p);
            return;
        }
        if (tag.startsWith("delq:")) {
            if (plugin.removeWizardQuest(tag.substring(5))) {
                msg(p, "&aQuest &f" + tag.substring(5) + "&a deleted and config rewritten.");
                managePage(p);
            } else msg(p, "&cThat quest is not wizard-made (edit quests.yml in the repo instead).");
            return;
        }
        if (tag.startsWith("manage:")) {
            var sq = plugin.questById(tag.substring(7));
            if (sq != null && sq.fromWizard) confirmPage(p, sq.id);
            else managePage(p);
            return;
        }
        if (q == null) { open(p); return; }   // rest heeft een draft nodig
        if (tag.startsWith("s.kind:")) {
            String[] a = tag.substring(7).split(":");
            var st = nthStage(q, Integer.parseInt(a[0]));
            st.kind = a[1];
            stagePage(p, Integer.parseInt(a[0]));
            return;
        }
        if (tag.startsWith("s.loc:")) {
            var st = nthStage(q, Integer.parseInt(tag.substring(6)));
            st.loc = p.getLocation();
            stagePage(p, Integer.parseInt(tag.substring(6)));
            return;
        }
        if (tag.startsWith("s.up:")) { int i = Integer.parseInt(tag.substring(5)); moveStage(q, i, i - 1); main(p); return; }
        if (tag.startsWith("s.down:")) { int i = Integer.parseInt(tag.substring(6)); moveStage(q, i, i + 1); main(p); return; }
        if (tag.startsWith("s.del:")) {
            int i = Integer.parseInt(tag.substring(6));
            q.stages.remove(nthStage(q, i).key);
            main(p);
            return;
        }
        if (tag.startsWith("r.loc:")) {
            int i = Integer.parseInt(tag.substring(6));
            q.repeats.get(i).loc = p.getLocation();
            repPage(p, i);
            return;
        }
        if (tag.startsWith("r.kind:")) {
            int i = Integer.parseInt(tag.substring(7));
            q.repeats.get(i).kind = q.repeats.get(i).kind.equals("deliver") ? "walk" : "deliver";
            repPage(p, i);
            return;
        }
        if (tag.startsWith("r.daily:")) {
            int i = Integer.parseInt(tag.substring(8));
            q.repeats.get(i).daily = !q.repeats.get(i).daily;
            repPage(p, i);
            return;
        }
        if (tag.startsWith("r.del:")) {
            q.repeats.remove(Integer.parseInt(tag.substring(6)));
            main(p);
            return;
        }
        if (tag.startsWith("c.del:")) {
            String[] a = tag.substring(6).split(":");
            nthStage(q, Integer.parseInt(a[0])).choices.remove(Integer.parseInt(a[1]));
            choicesPage(p, Integer.parseInt(a[0]));
            return;
        }
        // veld-inputs
        if (tag.startsWith("q.")) {
            if (q == null) return;
            switch (tag) {
                case "q.id" -> ask(p, "q.id", "Quest id - lowercase letters, digits and underscores", q.id);
                case "q.title" -> ask(p, "q.title", "Quest title (color codes with & allowed)", q.title);
                case "q.sub" -> ask(p, "q.sub", "Subtitle (one line)", q.subtitle);
                case "q.icon" -> ask(p, "q.icon", "Icon: a vanilla material name (BOOK, IRON_SWORD, PUMPKIN...)", q.icon);
                case "q.order" -> ask(p, "q.order", "Menu order - smaller shows first (number)", String.valueOf(q.order));
                case "q.start" -> ask(p, "q.start", "Start conditions: e.g. !tag:vorkan_done,point:rep>=3 (or empty)", join(q.startConds));
                case "q.journal" -> askMulti(p, "q.journal", "Journal lines - one chat line each, finish with 'done'", q.journal);
                case "q.rewards" -> askMulti(p, "q.rewards", "Reward lines, rewards-syntax (see Help), finish with 'done'", q.rewards);
                default -> { }
            }
            return;
        }
        if (tag.startsWith("s.")) { field(p, tag.substring(2), 0); return; }
        if (tag.startsWith("c.")) { field(p, tag.substring(2), 1); return; }
        if (tag.startsWith("r.")) { field(p, tag.substring(2), 2); return; }
    }

    /** s.<field>:<stage> / c.<field>:<stage>:<choice> / r.<field>:<rep> - chat-prompts. */
    private void field(Player p, String spec, int mode) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null) return;
        String[] a = spec.split(":");
        String f = a[0];
        int i = Integer.parseInt(a[1]);
        if (mode == 0) {
            Stage st = nthStage(q, i);
            switch (f) {
                case "key" -> ask(p, "s.key:" + i, "Stage key (lowercase, used by goto)", st.key);
                case "label" -> ask(p, "s.label:" + i, "Button label", st.label);
                case "cond" -> ask(p, "s.cond:" + i, "Conditions (comma = and, empty = always)", join(st.conds));
                case "voice" -> ask(p, "s.voice:" + i, "Voice sound key (or empty)", st.voice);
                case "mat" -> ask(p, "s.mat:" + i, "Material name", st.material);
                case "amount" -> ask(p, "s.amount:" + i, "Amount (number)", String.valueOf(st.amount));
                case "mob" -> ask(p, "s.mob:" + i, "Mob type for kill stages (entity name or monster)", st.mobType);
                case "radius" -> ask(p, "s.radius:" + i, "Radius in blocks (number)", String.valueOf(st.radius));
                case "next" -> ask(p, "s.next:" + i, "Next stage key (empty = automatic order)", st.next);
                case "goto" -> ask(p, "s.goto:" + i, "Goto override after completing (empty = none)", st.gotoKey);
                case "lines" -> askMulti(p, "s.lines:" + i, "Conversation lines, finish with 'done'", st.lines);
                case "rewards" -> askMulti(p, "s.rewards:" + i, "Stage reward lines (rewards syntax), 'done' to end", st.rewards);
                case "choices" -> choicesPage(p, i);
                default -> { }
            }
        } else if (mode == 1) {
            int j = Integer.parseInt(a[2]);
            Choice c = nthStage(q, i).choices.get(j);
            switch (f) {
                case "label" -> ask(p, "c.label:" + i + ":" + j, "Choice button label", c.label);
                case "say" -> ask(p, "c.say:" + i + ":" + j, "What the NPC says (empty = silence)", c.say == null ? "" : c.say);
                case "goto" -> ask(p, "c.goto:" + i + ":" + j, "Stage key this choice opens (empty = stay)", c.gotoKey);
                case "cond" -> ask(p, "c.cond:" + i + ":" + j, "Conditions to allow this choice", join(c.conds));
                case "voice" -> ask(p, "c.voice:" + i + ":" + j, "Voice sound key (or empty)", c.voice);
                case "do" -> askMulti(p, "c.do:" + i + ":" + j, "Do-action lines (rewards syntax), 'done' to end", c.doList);
                default -> { }
            }
        } else {
            Repeat rp = q.repeats.get(i);
            switch (f) {
                case "label" -> ask(p, "r.label:" + i, "Contract button label", rp.label);
                case "mat" -> ask(p, "r.mat:" + i, "Material name", rp.material);
                case "amount" -> ask(p, "r.amount:" + i, "Amount (number)", String.valueOf(rp.amount));
                case "money" -> ask(p, "r.money:" + i, "Money reward (number, 0 = none)", String.valueOf(rp.money));
                case "eco" -> ask(p, "r.eco:" + i, "Elite coins reward (number)", String.valueOf(rp.eco));
                case "cd" -> ask(p, "r.cd:" + i, "Cooldown in seconds (1320 = same as the merchants)", String.valueOf(rp.cooldown));
                case "voice" -> ask(p, "r.voice:" + i, "Voice sound key (or empty)", rp.voice);
                case "rewards" -> askMulti(p, "r.rewards:" + i, "Extra reward lines, 'done' to end", rp.rewards);
                default -> { }
            }
        }
    }

    // ---------------------------------------------------------------- chat

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Input in = waiting.get(p.getUniqueId());
        if (in == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, in, msg));
    }

    private void handle(Player p, Input in, String text) {
        UUID u = p.getUniqueId();
        if (text.equalsIgnoreCase("cancel")) { waiting.remove(u); main(p); return; }
        if (in.multiline) {
            if (text.equalsIgnoreCase("done")) {
                waiting.remove(u);
                commitMulti(p, in);
                resume(p, in.ctx);
            } else in.acc.add(text);
            return;
        }
        waiting.remove(u);
        if (!commitOne(p, in.ctx, text)) { waiting.put(u, in); return; }   // ongeldige input: opnieuw vragen
        resume(p, in.ctx);
    }

    private void resume(Player p, String ctx) {
        if (ctx.startsWith("q.npc")) npcPage(p);
        else if (ctx.startsWith("s.")) stagePage(p, tailInt(ctx));
        else if (ctx.startsWith("c.")) {
            String[] a = ctx.split(":");
            choicePage(p, Integer.parseInt(a[1]), Integer.parseInt(a[2]));
        } else if (ctx.startsWith("r.")) repPage(p, tailInt(ctx));
        else main(p);
    }

    private boolean commitOne(Player p, String ctx, String text) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null) return true;
        try {
            String[] a = ctx.split(":");
            if (ctx.startsWith("q.")) {
                switch (a[0]) {
                    case "q.id" -> q.id = text.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                    case "q.title" -> q.title = text;
                    case "q.sub" -> q.subtitle = text;
                    case "q.icon" -> {
                        if (Material.matchMaterial(text) == null) { msg(p, "&cUnknown material: &f" + text); return false; }
                        q.icon = text.toUpperCase();
                    }
                    case "q.order" -> q.order = Integer.parseInt(text);
                    case "q.start" -> q.startConds = plugin.condsOf(text);
                    case "q.npcid" -> q.npcId = (int) Double.parseDouble(text);
                    case "q.npcnew" -> {
                        Integer id = createNpc(p, text);
                        if (id == null) { msg(p, "&cCitizens not reachable - bind an existing NPC id instead."); return false; }
                        q.npcId = id;
                        msg(p, "&aNPC &f" + text + "&a created with id &f" + id + "&a and bound.");
                    }
                }
                return true;
            }
            if (ctx.startsWith("s.")) {
                var st = nthStage(q, Integer.parseInt(a[1]));
                switch (a[0]) {
                    case "s.key" -> {
                        String nk = text.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                        if (!nk.isEmpty() && !nk.equals(st.key)) {
                            var m = new LinkedHashMap<String, Stage>();
                            for (var en : q.stages.entrySet())
                                m.put(en.getKey().equals(st.key) ? nk : en.getKey(), en.getValue());
                            st.key = nk;
                            q.stages.clear();
                            q.stages.putAll(m);
                        }
                    }
                    case "s.label" -> st.label = text;
                    case "s.cond" -> st.conds = plugin.condsOf(text);
                    case "s.voice" -> st.voice = text;
                    case "s.mat" -> st.material = text.toUpperCase();
                    case "s.amount" -> st.amount = (int) Double.parseDouble(text);
                    case "s.mob" -> st.mobType = text.toUpperCase();
                    case "s.radius" -> st.radius = Double.parseDouble(text);
                    case "s.next" -> st.next = text.trim().isEmpty() ? "" : text.toLowerCase();
                    case "s.goto" -> st.gotoKey = text.trim().isEmpty() ? "" : text.toLowerCase();
                }
                return true;
            }
            if (ctx.startsWith("c.")) {
                var c = nthStage(q, Integer.parseInt(a[1])).choices.get(Integer.parseInt(a[2]));
                switch (a[0]) {
                    case "c.label" -> c.label = text;
                    case "c.say" -> c.say = text;
                    case "c.goto" -> c.gotoKey = text.trim().isEmpty() ? "" : text.toLowerCase();
                    case "c.cond" -> c.conds = plugin.condsOf(text);
                    case "c.voice" -> c.voice = text;
                }
                return true;
            }
            if (ctx.startsWith("r.")) {
                var rp = q.repeats.get(Integer.parseInt(a[1]));
                switch (a[0]) {
                    case "r.label" -> rp.label = text;
                    case "r.mat" -> rp.material = text.toUpperCase();
                    case "r.amount" -> rp.amount = (int) Double.parseDouble(text);
                    case "r.money" -> rp.money = (int) Double.parseDouble(text);
                    case "r.eco" -> rp.eco = (int) Double.parseDouble(text);
                    case "r.cd" -> rp.cooldown = (long) Double.parseDouble(text);
                    case "r.voice" -> rp.voice = text;
                }
                return true;
            }
        } catch (Exception ex) {
            msg(p, "&cThat did not parse - try again (or 'cancel').");
            return false;
        }
        return true;
    }

    private void commitMulti(Player p, Input in) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null) return;
        String[] a = in.ctx.split(":");
        List<String> target = switch (a[0]) {
            case "q.journal" -> q.journal;
            case "q.rewards" -> q.rewards;
            default -> null;
        };
        if (target == null) {
            if (a[0].equals("s.lines") && q != null) { var st = nthStage(q, Integer.parseInt(a[1])); target = st.lines; }
            else if (a[0].equals("s.rewards")) target = nthStage(q, Integer.parseInt(a[1])).rewards;
            else if (a[0].equals("c.do")) target = nthStage(q, Integer.parseInt(a[1])).choices.get(Integer.parseInt(a[2])).doList;
            else if (a[0].equals("r.rewards")) target = q.repeats.get(Integer.parseInt(a[1])).rewards;
        }
        if (target == null) return;
        target.clear();
        target.addAll(in.acc);
    }

    private void ask(Player p, String ctx, String prompt, String current) {
        p.closeInventory();
        waiting.remove(p.getUniqueId());
        waiting.put(p.getUniqueId(), new Input(ctx, false));
        p.sendMessage(legacy("&e▶ " + prompt));
        if (current != null && !current.isEmpty())
            p.sendMessage(legacy("&8(now: " + current + ") &7- type your answer in chat, &fcancel&7 to abort."));
        else p.sendMessage(legacy("&7- type your answer in chat, &fcancel&7 to abort."));
    }

    private void askMulti(Player p, String ctx, String prompt, List<String> current) {
        p.closeInventory();
        waiting.remove(p.getUniqueId());
        waiting.put(p.getUniqueId(), new Input(ctx, true));
        p.sendMessage(legacy("&e▶ " + prompt + " &7(" + current.size() + " line(s) now; typing 'done' replaces them)"));
    }

    // ---------------------------------------------------------------- save

    private void save(Player p) {
        Quest q = drafts.get(p.getUniqueId());
        if (q == null) return;
        if (!q.id.matches("[a-z0-9_]{2,32}")) { msg(p, "&cFix the quest id first: lowercase, 2-32 chars."); return; }
        if (q.stages.isEmpty() && q.repeats.isEmpty()) { msg(p, "&cAdd at least one stage or contract."); return; }
        for (var st : q.stages.values()) {
            if (st.kind.equals("craft") || st.kind.equals("deliver")) {
                if (Material.matchMaterial(st.material) == null) { msg(p, "&cMaterial unknown in stage &f" + st.key + "&c: " + st.material); return; }
            }
        }
        q.fromWizard = true;
        plugin.storeWizardQuest(q);
        drafts.remove(p.getUniqueId());
        waiting.remove(p.getUniqueId());
        p.closeInventory();
        msg(p, "&aQuest &f" + q.id + "&a saved to &fwizard-quests.yml&a and live now - "
                + (q.npcId >= 0 ? "&aNPC " + q.npcId + " opens it." : "&7menu-only (no NPC bound)."));
    }

    private void msg(Player p, String s) { p.sendMessage(legacy(s)); }

    // ---------------------------------------------------------------- citizens

    private Integer createNpc(Player at, String name) {
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object reg = api.getMethod("getNPCRegistry").invoke(null);
            Method create = reg.getClass().getMethod("createNPC", EntityType.class, String.class);
            Object npc = create.invoke(reg, EntityType.PLAYER, name);
            npc.getClass().getMethod("spawn", Location.class, boolean.class).invoke(npc, at.getLocation(), true);
            return (int) npc.getClass().getMethod("getId").invoke(npc);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean pullNpc(Player at, int id) {
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object reg = api.getMethod("getNPCRegistry").invoke(null);
            Object npc = reg.getClass().getMethod("getNPC", int.class).invoke(reg, id);
            if (npc == null) return false;
            npc.getClass().getMethod("teleport", Location.class).invoke(npc, at.getLocation());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Stage nthStage(Quest q, int i) {
        return new ArrayList<>(q.stages.values()).get(i);
    }

    private static int idxOfStage(Quest q, Stage st) {
        return new ArrayList<>(q.stages.keySet()).indexOf(st.key);
    }

    private static int idxOfRep(Quest q, IekQuestsPlugin.Repeat rp) {
        return q.repeats.indexOf(rp);
    }

    private static int tailInt(String ctx) {
        String[] a = ctx.split(":");
        return Integer.parseInt(a[a.length - 1]);
    }

    private static void moveStage(Quest q, int i, int to) {
        List<String> ks = new ArrayList<>(q.stages.keySet());
        if (to < 0 || to >= ks.size()) return;
        String k = ks.remove(i);
        ks.add(to, k);
        var m = new LinkedHashMap<String, Stage>();
        for (String kk : ks) m.put(kk, q.stages.get(kk));
        q.stages.clear();
        q.stages.putAll(m);
    }

    /** Diepe kopie - de wizard edit nooit de live-lijst direct. */
    private static Quest copyOf(Quest s) {
        Quest q = new Quest();
        q.id = s.id; q.title = s.title; q.subtitle = s.subtitle; q.icon = s.icon;
        q.npcId = s.npcId; q.order = s.order; q.fromWizard = true;
        q.startConds = new ArrayList<>(s.startConds);
        q.rewards = new ArrayList<>(s.rewards);
        q.journal = new ArrayList<>(s.journal);
        for (var e : s.stages.entrySet()) {
            Stage st = e.getValue();
            Stage n = new Stage();
            n.key = st.key; n.kind = st.kind; n.material = st.material; n.mobType = st.mobType;
            n.amount = st.amount; n.loc = st.loc; n.radius = st.radius; n.label = st.label;
            n.voice = st.voice; n.lines = new ArrayList<>(st.lines); n.conds = new ArrayList<>(st.conds);
            n.next = st.next; n.gotoKey = st.gotoKey; n.rewards = new ArrayList<>(st.rewards);
            for (var c : st.choices) {
                IekQuestsPlugin.Choice cc = new IekQuestsPlugin.Choice();
                cc.label = c.label; cc.say = c.say; cc.gotoKey = c.gotoKey; cc.voice = c.voice;
                cc.conds = new ArrayList<>(c.conds); cc.doList = new ArrayList<>(c.doList);
                n.choices.add(cc);
            }
            q.stages.put(n.key, n);
        }
        for (var r : s.repeats) {
            IekQuestsPlugin.Repeat n = new IekQuestsPlugin.Repeat();
            n.id = r.id; n.kind = r.kind; n.material = r.material; n.amount = r.amount;
            n.loc = r.loc; n.radius = r.radius; n.cooldown = r.cooldown; n.money = r.money;
            n.eco = r.eco; n.daily = r.daily; n.label = r.label; n.voice = r.voice;
            n.rewards = new ArrayList<>(r.rewards);
            q.repeats.add(n);
        }
        return q;
    }

    private static String condText(List<IekQuestsPlugin.Cond> cs) {
        if (cs.isEmpty()) return "&7none";
        StringBuilder sb = new StringBuilder("&f");
        for (int i = 0; i < cs.size(); i++) { if (i > 0) sb.append(", &f"); sb.append(cs.get(i).raw); }
        return sb.toString();
    }

    private static String stageIcon(String kind) {
        return switch (kind) {
            case "craft" -> "CRAFTING_TABLE";
            case "deliver" -> "HOPPER";
            case "walk" -> "LEATHER_BOOTS";
            case "kill" -> "DIAMOND_SWORD";
            default -> "OAK_SIGN";
        };
    }

    private static String fmtLoc(Location l) {
        return Math.round(l.getX()) + ";" + Math.round(l.getY()) + ";" + Math.round(l.getZ()) + ";" + l.getWorld().getName();
    }

    private Material mat(String name, Material fallback) {
        var m = Material.matchMaterial(name);
        return m == null ? fallback : m;
    }

    private ItemStack put(Inventory inv, int slot, Material m, String name, String tag) {
        return put(inv, slot, m, name, null, tag);
    }

    private ItemStack put(Inventory inv, int slot, Material m, String name, String loreLine, String tag) {
        var it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(legacy(name));
        if (loreLine != null) im.lore(List.of(legacy(loreLine)));
        im.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
        it.setItemMeta(im);
        inv.setItem(slot, it);
        return it;
    }

    private ItemStack lore(Material m, String... lines) {
        var it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(legacy(lines[0]));
        var lo = new ArrayList<Component>();
        for (int i = 1; i < lines.length; i++) lo.add(legacy(lines[i]));
        im.lore(lo);
        it.setItemMeta(im);
        return it;
    }

    private Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s);
    }

    private static String plain(String s) {
        return org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s));
    }

    // ---------------------------------------------------------------- config

    /** Schrijft alle wizard-quests weg naar wizard-quests.yml (handmatige blijft heel). */
    static void writeWizardFile(File f, List<Quest> wizardQuests) {
        StringBuilder sb = new StringBuilder();
        sb.append("# IekQuests - wizard-made quests (Quest Wizard GUI, ").append("admin-editable by hand too).\n");
        sb.append("# Loaded on top of quests.yml; deleting a quest here = remove its block or use the wizard.\n");
        sb.append("quests:\n");
        for (var q : wizardQuests) emit(sb, q);
        sb.append("\nsequences: {}\n");
        try {
            java.nio.file.Files.writeString(f.toPath(), sb.toString(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void emit(StringBuilder sb, Quest q) {
        sb.append("  ").append(q.id).append(":\n");
        line(sb, 4, "title", q.title);
        if (!q.subtitle.isEmpty()) line(sb, 4, "subtitle", q.subtitle);
        sb.append("    icon: ").append(q.icon).append('\n');
        sb.append("    npc-id: ").append(q.npcId).append('\n');
        sb.append("    order: ").append(q.order).append('\n');
        if (!q.startConds.isEmpty()) line(sb, 4, "start", join(q.startConds));
        list(sb, "journal", q.journal, 4);
        list(sb, "rewards", q.rewards, 4);
        if (!q.stages.isEmpty()) {
            sb.append("    stages:\n");
            for (var st : q.stages.values()) {
                sb.append("      ").append(st.key).append(":\n");
                sb.append("        type: ").append(st.kind).append('\n');
                if (st.kind.equals("craft") || st.kind.equals("deliver")) sb.append("        material: ").append(st.material).append('\n');
                if (st.kind.equals("kill")) sb.append("        mob: ").append(st.mobType).append('\n');
                if (st.kind.equals("walk") && st.loc != null) line(sb, 8, "location", fmtLoc(st.loc));
                if (st.kind.equals("walk")) sb.append("        radius: ").append(st.radius).append('\n');
                if (st.kind.equals("craft") || st.kind.equals("deliver") || st.kind.equals("kill"))
                    sb.append("        amount: ").append(st.amount).append('\n');
                line(sb, 8, "label", st.label);
                if (!st.voice.isEmpty()) line(sb, 8, "voice", st.voice);
                list(sb, "lines", st.lines, 8);
                if (!st.conds.isEmpty()) line(sb, 8, "cond", join(st.conds));
                if (!st.next.isEmpty()) line(sb, 8, "next", st.next);
                if (!st.gotoKey.isEmpty()) line(sb, 8, "goto", st.gotoKey);
                list(sb, "rewards", st.rewards, 8);
                if (!st.choices.isEmpty()) {
                    sb.append("        choices:\n");
                    int n = 1;
                    for (var c : st.choices) {
                        sb.append("          c").append(n++).append(":\n");
                        line(sb, 12, "label", c.label);
                        if (c.say != null && !c.say.isEmpty()) line(sb, 12, "say", c.say);
                        if (!c.gotoKey.isEmpty()) line(sb, 12, "goto", c.gotoKey);
                        if (!c.voice.isEmpty()) line(sb, 12, "voice", c.voice);
                        if (!c.conds.isEmpty()) line(sb, 12, "cond", join(c.conds));
                        list(sb, "do", c.doList, 12);
                    }
                }
            }
        }
        if (!q.repeats.isEmpty()) {
            sb.append("    repeat:\n");
            for (var rp : q.repeats) {
                sb.append("      ").append(rp.id).append(":\n");
                sb.append("        type: ").append(rp.kind).append('\n');
                sb.append("        material: ").append(rp.material).append('\n');
                sb.append("        amount: ").append(rp.amount).append('\n');
                sb.append("        cooldown: ").append(rp.cooldown).append('\n');
                if (rp.daily) sb.append("        daily: true\n");
                sb.append("        money: ").append(rp.money).append('\n');
                sb.append("        eco: ").append(rp.eco).append('\n');
                line(sb, 8, "label", rp.label);
                if (!rp.voice.isEmpty()) line(sb, 8, "voice", rp.voice);
                if (rp.loc != null) line(sb, 8, "location", fmtLoc(rp.loc));
                list(sb, "rewards", rp.rewards, 8);
            }
        }
    }

    private static String esc(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + '"';
    }

    private static void line(StringBuilder sb, int ind, String k, String v) {
        sb.append(" ".repeat(ind)).append(k).append(": ").append(esc(v)).append('\n');
    }

    private static void list(StringBuilder sb, String k, List<String> items, int ind) {
        if (items == null || items.isEmpty()) return;
        sb.append(" ".repeat(ind)).append(k).append(":\n");
        for (var it : items) sb.append(" ".repeat(ind + 2)).append("- ").append(esc(it)).append('\n');
    }

    private static String join(List<IekQuestsPlugin.Cond> cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.size(); i++) { if (i > 0) sb.append(", "); sb.append(cs.get(i).raw); }
        return sb.toString();
    }
}
