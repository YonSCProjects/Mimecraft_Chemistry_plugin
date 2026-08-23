package com.agurim.chemcraft.molecule;

import com.agurim.chemcraft.ChemCraftPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Stores explicit bond orders between adjacent atom blocks. Adjacent atoms default to order 1. */
public class BondStore {

    private final ChemCraftPlugin plugin;
    private final File file;
    private final Map<String, Integer> bonds = new HashMap<>();

    public BondStore(ChemCraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bonds.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) bonds.put(key, yml.getInt(key));
    }

    private String locKey(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private String edgeKey(Location a, Location b) {
        String ka = locKey(a), kb = locKey(b);
        return (ka.compareTo(kb) <= 0) ? ka + "|" + kb : kb + "|" + ka;
    }

    /** Bond order between two adjacent atoms (1 if never set). */
    public int order(Location a, Location b) {
        return bonds.getOrDefault(edgeKey(a, b), 1);
    }

    public void setOrder(Location a, Location b, int order) {
        bonds.put(edgeKey(a, b), order);
        save();
    }

    /** Remove every bond touching this location (when its atom is broken). */
    public void removeAround(Location loc) {
        String k = locKey(loc);
        bonds.keySet().removeIf(edge -> {
            String[] ends = edge.split("\\|");
            return ends.length == 2 && (ends[0].equals(k) || ends[1].equals(k));
        });
        save();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        bonds.forEach(yml::set);
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save bonds.yml: " + e.getMessage()); }
    }
}
