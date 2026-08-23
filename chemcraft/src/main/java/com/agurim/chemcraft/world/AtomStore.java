package com.agurim.chemcraft.world;

import com.agurim.chemcraft.ChemCraftPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Tracks element identity for player-placed atom blocks (Tier-1 location -> symbol map). */
public class AtomStore {

    private final ChemCraftPlugin plugin;
    private final File file;
    private final Map<String, String> atoms = new HashMap<>();

    public AtomStore(ChemCraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "atoms.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) atoms.put(key, yml.getString(key));
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public void put(Location loc, String symbol) { atoms.put(key(loc), symbol); save(); }
    public String get(Location loc)              { return atoms.get(key(loc)); }
    public void remove(Location loc)             { atoms.remove(key(loc)); save(); }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        atoms.forEach(yml::set);
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save atoms.yml: " + e.getMessage()); }
    }
}
