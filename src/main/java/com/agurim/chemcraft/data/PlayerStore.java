package com.agurim.chemcraft.data;

import com.agurim.chemcraft.ChemCraftPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player progress, persisted to players.yml:
 *   <uuid>.plot             -> assigned plot index
 *   <uuid>.wall-built       -> has their wall been spawned
 *   <uuid>.discovered       -> list of discovered element symbols
 *   <uuid>.kit-claimed      -> has the one-time starter kit been taken
 *   <uuid>.name             -> last seen player name (offline-mode server: cache, never trust getOfflinePlayer)
 *   <uuid>.assists          -> times this player's atoms/demos led a classmate to a first discovery
 *   <uuid>.pending-assists  -> assists earned while offline, announced on next join
 *   <uuid>.tile-credit.<sym>-> helper name shown forever on this player's wall tile for <sym>
 *   _meta.next-plot         -> next free plot index to hand out
 */
public class PlayerStore {

    private final ChemCraftPlugin plugin;
    private final File file;
    private final YamlConfiguration yml;

    public PlayerStore(ChemCraftPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean hasPlot(UUID id) {
        return yml.contains(id + ".plot");
    }

    /** Returns this player's plot index, assigning the next free one if needed. */
    public int getOrAssignPlotIndex(UUID id) {
        if (hasPlot(id)) return yml.getInt(id + ".plot");
        int next = yml.getInt("_meta.next-plot", 0);
        yml.set(id + ".plot", next);
        yml.set("_meta.next-plot", next + 1);
        save();
        return next;
    }

    public boolean isWallBuilt(UUID id)            { return yml.getBoolean(id + ".wall-built", false); }
    public void setWallBuilt(UUID id, boolean v)   { yml.set(id + ".wall-built", v); save(); }

    public Set<String> getDiscovered(UUID id) {
        return new LinkedHashSet<>(yml.getStringList(id + ".discovered"));
    }

    public boolean isDiscovered(UUID id, String sym) {
        return yml.getStringList(id + ".discovered").contains(sym);
    }

    public void discover(UUID id, String sym) {
        Set<String> set = getDiscovered(id);
        if (set.add(sym)) {
            yml.set(id + ".discovered", new ArrayList<>(set));
            save();
        }
    }

    public void clearDiscovered(UUID id) {
        yml.set(id + ".discovered", new ArrayList<String>());
        save();
    }

    public boolean isKitClaimed(UUID id)  { return yml.getBoolean(id + ".kit-claimed", false); }
    public void setKitClaimed(UUID id)    { yml.set(id + ".kit-claimed", true); save(); }

    public void cacheName(UUID id, String name) { yml.set(id + ".name", name); save(); }
    public String getName(UUID id)              { return yml.getString(id + ".name", "?"); }

    public int getAssists(UUID id)  { return yml.getInt(id + ".assists", 0); }
    public int addAssist(UUID id) {
        int n = getAssists(id) + 1;
        yml.set(id + ".assists", n);
        save();
        return n;
    }

    public void addPendingAssist(UUID id) {
        yml.set(id + ".pending-assists", yml.getInt(id + ".pending-assists", 0) + 1);
        save();
    }

    /** Returns the number of assists earned while offline and clears the counter. */
    public int flushPendingAssists(UUID id) {
        int n = yml.getInt(id + ".pending-assists", 0);
        if (n > 0) { yml.set(id + ".pending-assists", 0); save(); }
        return n;
    }

    public void setTileCredit(UUID id, String sym, String helperName) {
        yml.set(id + ".tile-credit." + sym, helperName);
        save();
    }

    /** Wipe all tile credits (used by /cc reset so old helpers don't haunt fresh discoveries). */
    public void clearTileCredit(UUID id) {
        yml.set(id + ".tile-credit", null);
        save();
    }

    /** Helper name credited on this player's tile for an element, or null. */
    public String tileCredit(UUID id, String sym) {
        return yml.getString(id + ".tile-credit." + sym, null);
    }

    /** Owner of a plot index, or null (linear scan - fine at classroom scale). */
    public UUID uuidByPlot(int plotIndex) {
        for (String key : yml.getKeys(false)) {
            if (key.startsWith("_")) continue;
            if (yml.getInt(key + ".plot", -1) == plotIndex) {
                try { return UUID.fromString(key); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    /** All (uuid, assists) pairs with at least one assist, for the sidebar. */
    public java.util.Map<UUID, Integer> allAssists() {
        java.util.Map<UUID, Integer> out = new java.util.LinkedHashMap<>();
        for (String key : yml.getKeys(false)) {
            if (key.startsWith("_")) continue;
            int n = yml.getInt(key + ".assists", 0);
            if (n > 0) {
                try { out.put(UUID.fromString(key), n); } catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
    }

    public void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }
}
