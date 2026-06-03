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
 *   <uuid>.plot         -> assigned plot index
 *   <uuid>.wall-built   -> has their wall been spawned
 *   <uuid>.discovered   -> list of discovered element symbols
 *   _meta.next-plot     -> next free plot index to hand out
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

    public void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }
}
