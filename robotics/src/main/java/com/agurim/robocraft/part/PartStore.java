package com.agurim.robocraft.part;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where every placed part is and what it belongs to (Tier-1 location -> identity map).
 *
 * <p>Persisted to placements.yml - deliberately NOT parts.yml, which is bundled content.
 */
public class PartStore {

    private final RoboCraftPlugin plugin;
    private final File file;
    private final Map<String, Placed> placed = new LinkedHashMap<>();

    public PartStore(RoboCraftPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "placements.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            placed.put(key, new Placed(
                    s.getString("part", ""),
                    face(s.getString("facing", "NORTH")),
                    s.getString("robot", ""),
                    s.getString("port", "")));
        }
    }

    private static BlockFace face(String name) {
        try { return BlockFace.valueOf(name); } catch (IllegalArgumentException e) { return BlockFace.NORTH; }
    }

    public static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    /** Turn a stored key back into a Location, or null if that world is not loaded. */
    public static Location fromKey(String key) {
        String[] p = key.split(";");
        if (p.length != 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void put(Location loc, Placed p)  { placed.put(key(loc), p); save(); }
    public Placed get(Location loc)          { return placed.get(key(loc)); }
    public Placed byKey(String key)          { return placed.get(key); }
    public boolean isPart(Location loc)      { return placed.containsKey(key(loc)); }

    public void remove(Location loc) {
        placed.remove(key(loc));
        save();
    }

    /** Every part attached to this controller, keyed by location. */
    public Map<String, Placed> partsOf(String robotKey) {
        Map<String, Placed> out = new LinkedHashMap<>();
        placed.forEach((k, v) -> { if (robotKey.equals(v.robot())) out.put(k, v); });
        return out;
    }

    /** Every controller location key currently placed. */
    public java.util.List<String> controllers() {
        java.util.List<String> out = new java.util.ArrayList<>();
        placed.forEach((k, v) -> {
            Part part = plugin.parts().get(v.partId());
            if (part != null && part.isController()) out.add(k);
        });
        return out;
    }

    /** Detach every part of a robot - used when its controller is broken. */
    public void detachAll(String robotKey) {
        placed.replaceAll((k, v) -> robotKey.equals(v.robot()) ? new Placed(v.partId(), v.facing(), "", "") : v);
        save();
    }

    /**
     * The next free port on this robot: S1, S2... for sensors, A1, A2... for actuators.
     * Ports are pin numbers - that is the whole point of them.
     */
    public String nextPort(String robotKey, boolean sensor) {
        String prefix = sensor ? "S" : "A";
        int n = 1;
        java.util.Set<String> taken = new java.util.HashSet<>();
        partsOf(robotKey).values().forEach(v -> taken.add(v.port()));
        while (taken.contains(prefix + n)) n++;
        return prefix + n;
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        placed.forEach((k, v) -> {
            yml.set(k + ".part", v.partId());
            yml.set(k + ".facing", v.facing().name());
            yml.set(k + ".robot", v.robot());
            yml.set(k + ".port", v.port());
        });
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save placements.yml: " + e.getMessage()); }
    }
}
