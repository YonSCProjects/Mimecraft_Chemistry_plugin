package com.agurim.robocraft.robot;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.program.Program;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The persistent half of a robot - owner, battery charge and the program - in robots.yml.
 * Runtime state (memory, live readings, which rule fired) lives on {@link Robot} and is not saved.
 */
public class RobotStore {

    private final RoboCraftPlugin plugin;
    private final File file;
    private final Map<String, Robot> robots = new LinkedHashMap<>();

    public RobotStore(RoboCraftPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "robots.yml");
        int slots = plugin.getConfig().getInt("program.memory-slots", 4);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            UUID owner = null;
            try { owner = UUID.fromString(s.getString("owner", "")); } catch (IllegalArgumentException ignored) { }
            Robot r = new Robot(key, owner, slots);
            r.energy(s.getInt("energy", 0));
            r.program(Program.decode(s.getStringList("rules")));
            robots.put(key, r);
        }
    }

    /** The robot for this controller, created (unsaved) if this is the first time we've seen it. */
    public Robot getOrCreate(String key, UUID owner) {
        return robots.computeIfAbsent(key, k ->
                new Robot(k, owner, plugin.getConfig().getInt("program.memory-slots", 4)));
    }

    public Robot get(String key)          { return robots.get(key); }
    public Map<String, Robot> all()       { return robots; }

    public void remove(String key) {
        robots.remove(key);
        save();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        robots.forEach((k, r) -> {
            if (r.owner() != null) yml.set(k + ".owner", r.owner().toString());
            yml.set(k + ".energy", r.energy());
            yml.set(k + ".rules", r.program().encode());
        });
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save robots.yml: " + e.getMessage()); }
    }
}
