package com.iekcorbar.steamworks;

import org.bukkit.Axis;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parts are saved in the chunk itself (its PersistentDataContainer), so they
 * travel with the world files - no database. One line per part:
 * "x,y,z,TYPE,AXIS,FACING"; millstone contents under their own key.
 */
final class NodeStorage {
    private final SteamworksPlugin plugin;
    private final NamespacedKey nodesKey, itemsKey, progressKey, outKey, waterKey, labelKey, extKey;

    NodeStorage(SteamworksPlugin plugin) {
        this.plugin = plugin;
        nodesKey = new NamespacedKey(plugin, "nodes");
        itemsKey = new NamespacedKey(plugin, "items");
        progressKey = new NamespacedKey(plugin, "progress");
        outKey = new NamespacedKey(plugin, "out");
        waterKey = new NamespacedKey(plugin, "water");
        labelKey = new NamespacedKey(plugin, "label");
        extKey = new NamespacedKey(plugin, "ext");
    }

    private NamespacedKey millKey(KNode n) {
        return new NamespacedKey(plugin, ("mill_" + n.x + "_" + n.y + "_" + n.z).replace('-', 'm'));
    }

    void save(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) {
            if (k.getNamespace().equals(nodesKey.getNamespace()) && k.getKey().startsWith("mill_")) pdc.remove(k);
        }
        StringBuilder sb = new StringBuilder();
        for (KNode n : plugin.kinetics().inChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(n.x).append(',').append(n.y).append(',').append(n.z).append(',')
                    .append(n.type == PartType.CUSTOM && n.ext != null ? "CUSTOM:" + n.ext.id() : n.type.name()).append(',')
                    .append(n.axis.name()).append(',').append(n.facing.name());
            if (n.owner != null) sb.append(',').append(n.owner);
            if (n.type == PartType.CUSTOM && n.ext != null) {
                byte[] blob = n.ext.saveState(ExtParts.view(n));
                if (blob != null && blob.length > 0) {
                    PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                    k.set(extKey, PersistentDataType.BYTE_ARRAY, blob);
                    pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
                }
            }
            if (n.type == PartType.MILLSTONE) {
                List<ItemStack> items = new ArrayList<>();
                items.add(n.millInput == null ? ItemStack.empty() : n.millInput);
                items.addAll(n.millOutput);
                PersistentDataContainer mill = pdc.getAdapterContext().newPersistentDataContainer();
                mill.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(items));
                mill.set(progressKey, PersistentDataType.DOUBLE, n.millProgress);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, mill);
            }
            if (n.type == PartType.SIEVE) {
                List<ItemStack> items = new ArrayList<>();
                items.add(n.sieveInput == null ? ItemStack.empty() : n.sieveInput);
                items.addAll(n.sieveOutput);
                PersistentDataContainer sieve = pdc.getAdapterContext().newPersistentDataContainer();
                sieve.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(items));
                sieve.set(progressKey, PersistentDataType.DOUBLE, n.sieveProgress);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, sieve);
            }
            if (n.type == PartType.TANK && n.fluid != null && n.fluidAmount > 0) {
                PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                k.set(labelKey, PersistentDataType.STRING, n.fluid.name());
                k.set(waterKey, PersistentDataType.INTEGER, n.fluidAmount);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
            }
            if (n.type == PartType.PIPE && n.itemModes != null && !n.itemModes.equals("NNNNNN")) {
                PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                k.set(labelKey, PersistentDataType.STRING, n.itemModes);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
            }
            if (n.type == PartType.STEAM_BOILER && n.burnTicks > 0) {
                PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                k.set(waterKey, PersistentDataType.INTEGER, n.burnTicks);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
            }
            if (n.type == PartType.SPROCKET && n.chain != null) {
                PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                k.set(labelKey, PersistentDataType.STRING, n.chain[0] + "," + n.chain[1] + "," + n.chain[2]);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
            }
            if (n.type == PartType.ARM) {
                PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                StringBuilder pts = new StringBuilder();
                for (int[] o : n.points) pts.append(pts.isEmpty() ? "" : ";").append(o[0]).append(',').append(o[1]).append(',').append(o[2]);
                k.set(labelKey, PersistentDataType.STRING, pts.toString());
                if (n.program != null) k.set(outKey, PersistentDataType.BYTE_ARRAY, n.program.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (n.tool != null) k.set(progressKey, PersistentDataType.STRING, n.tool.name());
                if (n.held != null && !n.held.isEmpty()) k.set(itemsKey, PersistentDataType.BYTE_ARRAY, n.held.serializeAsBytes());
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
            }
            if (n.type == PartType.STATION && n.label != null) {
                PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                k.set(labelKey, PersistentDataType.STRING, n.label);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
            }
            if (n.type == PartType.KETTLE) {
                PersistentDataContainer k = pdc.getAdapterContext().newPersistentDataContainer();
                k.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(n.kettleItems));
                k.set(outKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(n.millOutput));
                k.set(waterKey, PersistentDataType.INTEGER, n.kettleWater);
                pdc.set(millKey(n), PersistentDataType.TAG_CONTAINER, k);
            }
        }
        if (sb.isEmpty()) pdc.remove(nodesKey);
        else pdc.set(nodesKey, PersistentDataType.STRING, sb.toString());
    }

    /** Parts saved in this chunk (whose barrier block still exists). */
    List<KNode> load(Chunk chunk) {
        List<KNode> out = new ArrayList<>();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String data = pdc.get(nodesKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return out;
        boolean lost = false;
        for (String line : data.split(";")) {
            String[] p = line.split(",");
            try {
                int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                PartType type;
                com.iekcorbar.steamworks.api.CustomPart ext = null;
                if (p[3].startsWith("CUSTOM:")) {                 // add-on machine: is its plugin here?
                    type = PartType.CUSTOM;
                    ext = plugin.customParts().byId(p[3].substring(7));
                    if (ext == null) {
                        plugin.getLogger().warning("Machine " + p[3] + " at " + x + "," + y + "," + z
                                + " belongs to an add-on that is not installed - left untouched.");
                        continue;
                    }
                } else type = PartType.valueOf(p[3]);
                if (chunk.getWorld().getBlockAt(x, y, z).getType() != Material.BARRIER) {
                    lost = true; // removed by something else (WorldEdit, /setblock)
                    continue;
                }
                KNode n = new KNode(chunk.getWorld(), x, y, z, type, Axis.valueOf(p[4]), BlockFace.valueOf(p[5]));
                if (type == PartType.MILLSTONE && n.axis == Axis.Y)
                    n.axis = Axis.X;   // 0.3.24: the millstone became side-driven - an old vertical
                                       // axle from below is turned sideways; rewire the shaft to a side
                if (p.length > 6) n.owner = java.util.UUID.fromString(p[6]);
                if (ext != null) {
                    n.ext = ext;
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    byte[] blob = k == null ? null : k.get(extKey, PersistentDataType.BYTE_ARRAY);
                    if (blob != null) ext.loadState(ExtParts.view(n), blob);
                }
                if (type == PartType.MILLSTONE) {
                    PersistentDataContainer mill = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (mill != null) {
                        byte[] bytes = mill.get(itemsKey, PersistentDataType.BYTE_ARRAY);
                        if (bytes != null) {
                            List<ItemStack> items = new ArrayList<>(Arrays.asList(ItemStack.deserializeItemsFromBytes(bytes)));
                            if (!items.isEmpty()) {
                                ItemStack in = items.remove(0);
                                n.millInput = in == null || in.isEmpty() ? null : in;
                            }
                            for (ItemStack i : items) if (i != null && !i.isEmpty()) n.millOutput.add(i);
                        }
                        Double prog = mill.get(progressKey, PersistentDataType.DOUBLE);
                        n.millProgress = prog == null ? 0 : prog;
                    }
                }
                if (type == PartType.SIEVE) {
                    PersistentDataContainer sieve = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (sieve != null) {
                        byte[] bytes = sieve.get(itemsKey, PersistentDataType.BYTE_ARRAY);
                        if (bytes != null) {
                            List<ItemStack> items = new ArrayList<>(Arrays.asList(ItemStack.deserializeItemsFromBytes(bytes)));
                            if (!items.isEmpty()) {
                                ItemStack in = items.remove(0);
                                n.sieveInput = in == null || in.isEmpty() ? null : in;
                            }
                            for (ItemStack i : items) if (i != null && !i.isEmpty()) n.sieveOutput.add(i);
                        }
                        Double prog = sieve.get(progressKey, PersistentDataType.DOUBLE);
                        n.sieveProgress = prog == null ? 0 : prog;
                    }
                }
                if (type == PartType.TANK) {
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (k != null) {
                        try {
                            n.fluid = FluidType.valueOf(k.get(labelKey, PersistentDataType.STRING));
                            n.fluidAmount = k.getOrDefault(waterKey, PersistentDataType.INTEGER, 0);
                        } catch (RuntimeException ignored) {
                            n.fluid = null;
                        }
                    }
                }
                if (type == PartType.PIPE) {
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (k != null) n.itemModes = k.get(labelKey, PersistentDataType.STRING);
                }
                if (type == PartType.STEAM_BOILER) {
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (k != null) n.burnTicks = k.getOrDefault(waterKey, PersistentDataType.INTEGER, 0);
                }
                if (type == PartType.SPROCKET) {
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    String c = k == null ? null : k.get(labelKey, PersistentDataType.STRING);
                    if (c != null) {
                        String[] v = c.split(",");
                        n.chain = new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])};
                    }
                }
                if (type == PartType.ARM) {
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (k != null) {
                        String pts = k.get(labelKey, PersistentDataType.STRING);
                        if (pts != null && !pts.isEmpty()) for (String pt : pts.split(";")) {
                            String[] c = pt.split(",");
                            n.points.add(new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])});
                        }
                        byte[] prog = k.get(outKey, PersistentDataType.BYTE_ARRAY);
                        if (prog != null) n.program = new String(prog, java.nio.charset.StandardCharsets.UTF_8);
                        String tool = k.get(progressKey, PersistentDataType.STRING);
                        if (tool != null) n.tool = MaterialType.byId(tool);
                        byte[] held = k.get(itemsKey, PersistentDataType.BYTE_ARRAY);
                        if (held != null) n.held = ItemStack.deserializeBytes(held);
                    }
                }
                if (type == PartType.STATION) {
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (k != null) n.label = k.get(labelKey, PersistentDataType.STRING);
                }
                if (type == PartType.KETTLE) {
                    PersistentDataContainer k = pdc.get(millKey(n), PersistentDataType.TAG_CONTAINER);
                    if (k != null) {
                        byte[] in = k.get(itemsKey, PersistentDataType.BYTE_ARRAY);
                        if (in != null) for (ItemStack i : ItemStack.deserializeItemsFromBytes(in)) if (i != null && !i.isEmpty()) n.kettleItems.add(i);
                        byte[] res = k.get(outKey, PersistentDataType.BYTE_ARRAY);
                        if (res != null) for (ItemStack i : ItemStack.deserializeItemsFromBytes(res)) if (i != null && !i.isEmpty()) n.millOutput.add(i);
                        n.kettleWater = k.getOrDefault(waterKey, PersistentDataType.INTEGER, 0);
                    }
                }
                out.add(n);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping bad part entry '" + line + "' in chunk " + chunk.getX() + "," + chunk.getZ());
            }
        }
        if (lost) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) save(chunk);
        });
        return out;
    }
}
