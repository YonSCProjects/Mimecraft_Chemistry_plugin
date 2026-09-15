package com.agurim.robocraft.plot;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every block a person has placed in the workshop world, and whose it is - the line between
 * "someone's work" and "the land".
 *
 * <p>Why it exists (2026-09-15): the world became real terrain, and Tuval's class were upset that
 * they could not dig on a classmate's plot. Digging is most of what this age group wants from
 * Minecraft, and on generated terrain it is harmless - but nobody built the hill either. What must
 * stay protected is what somebody made. The plot grid alone cannot tell those apart; this can.
 *
 * <p>The owner arrived the same day, with open land ({@link Commons}): outside the grid there is
 * no plot to say whose a block is, so the block has to. The owner is the placer's plot index,
 * which every student has from their first join.
 *
 * <p>One world, so a location is three coordinates packed into a long - a class places thousands
 * of blocks in a lesson and a map of longs costs nothing. Saved on a timer and on disable rather
 * than on every placement, because a student building a wall places a block twice a second.
 */
public class BuildLog {

    /** A block placed before owners were recorded. Anyone may take it apart on open land. */
    public static final int UNKNOWN = -1;

    private final RoboCraftPlugin plugin;
    private final File file;
    private final Map<Long, Integer> placed = new HashMap<>();
    private boolean dirty;

    public BuildLog(RoboCraftPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "built.yml");
        load();
    }

    // ---------------------------------------------------------------- keys

    /**
     * x, z to 26 bits each and y to 12 - Minecraft's own packing, with y biased so the world's
     * negative floor (-64 on 26.2) survives the round trip.
     */
    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    public static long key(Location loc) { return key(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()); }

    public static int keyX(long k) { return (int) (k >> 38); }
    public static int keyZ(long k) { return (int) (k << 26 >> 38); }
    public static int keyY(long k) { return (int) (k & 0xFFF) - 2048; }

    // --------------------------------------------------------------- state

    /** Did a person put this block here? */
    public boolean contains(Location loc) { return placed.containsKey(key(loc)); }

    /** The plot index of whoever placed it; {@link #UNKNOWN} if not recorded; null if nobody placed it. */
    public Integer owner(Location loc) { return placed.get(key(loc)); }

    public void add(Location loc, int ownerPlot) {
        Integer prev = placed.put(key(loc), ownerPlot);
        if (!Objects.equals(prev, ownerPlot)) dirty = true;
    }

    /** Broken, so it is land again - otherwise the log only ever grows. */
    public void remove(Location loc) {
        if (placed.remove(key(loc)) != null) dirty = true;
    }

    public int size() { return placed.size(); }

    // ---------------------------------------------------------- persistence

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        List<Long> keys = yml.getLongList("placed");
        List<Integer> owners = yml.getIntegerList("owners");
        boolean aligned = owners.size() == keys.size();   // the first day's file had no owners
        for (int i = 0; i < keys.size(); i++) placed.put(keys.get(i), aligned ? owners.get(i) : UNKNOWN);
        plugin.getLogger().info("built.yml: " + placed.size() + " player-placed blocks remembered.");
    }

    public void save() {
        if (!dirty) return;
        List<Long> keys = new ArrayList<>(placed.size());
        List<Integer> owners = new ArrayList<>(placed.size());
        for (Map.Entry<Long, Integer> e : placed.entrySet()) { keys.add(e.getKey()); owners.add(e.getValue()); }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("placed", keys);
        yml.set("owners", owners);
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save built.yml: " + e.getMessage());
        }
    }

    /** Save every few minutes rather than on every block: a wall is placed twice a second. */
    public void startAutoSave() {
        long period = 20L * 60L * Math.max(1, plugin.getConfig().getInt("plot-shield.save-minutes", 2));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::save, period, period);
    }
}
