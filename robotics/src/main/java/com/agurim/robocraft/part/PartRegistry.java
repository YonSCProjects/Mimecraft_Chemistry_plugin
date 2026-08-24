package com.agurim.robocraft.part;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads parts.yml. Order of keys is the order of tiles on the Component Board. */
public class PartRegistry {

    private final Map<String, Part> parts = new LinkedHashMap<>();

    public PartRegistry(RoboCraftPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "parts.yml");
        if (!file.exists()) plugin.saveResource("parts.yml", false);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String id : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(id);
            if (s == null) continue;
            Material mat = Material.matchMaterial(s.getString("block", ""));
            if (mat == null) {
                plugin.getLogger().warning("parts.yml: part '" + id + "' has an unknown block - skipped.");
                continue;
            }
            parts.put(id, new Part(
                    id,
                    s.getString("name", id),
                    s.getString("kind", "sensor"),
                    mat,
                    s.getString("sensor", ""),
                    s.getString("actuator", ""),
                    s.getInt("capacity", 0),
                    s.getInt("drain", 0),
                    s.getInt("reach", 16),
                    s.getInt("radius", 5),
                    s.getString("range", ""),
                    s.getString("hint", ""),
                    s.getBoolean("unlocked", false)));
        }
    }

    public Part get(String id)     { return parts.get(id); }
    public boolean has(String id)  { return parts.containsKey(id); }
    public Collection<Part> all()  { return parts.values(); }
    public int size()              { return parts.size(); }

    /**
     * The part whose block this material is, or null. Parts are matched by EXACT material, which
     * is why parts.yml must never use a block that changes state on its own (oxidizing copper).
     */
    public Part byBlock(Material mat) {
        for (Part p : parts.values()) if (p.block() == mat) return p;
        return null;
    }
}
