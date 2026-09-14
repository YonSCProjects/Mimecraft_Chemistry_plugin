package com.agurim.robocraft.plot;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

/**
 * Lays plots out in a grid in one world and maps locations &lt;-&gt; plot index.
 *
 * <p>Copied from ChemCraft rather than shared: see the root CLAUDE.md for why there is no core
 * module yet.
 *
 * <p><b>The floor is per plot (2026-09-14).</b> The first term ran on a flat world with every
 * plot at one configured height. Yon chose real terrain - "a regular world plus the robotics" -
 * so now each plot's floor height is read from the terrain the first time the plot is looked at,
 * remembered in {@code plots.yml}, and {@link PadBuilder} carves a flat pad at that height for
 * the workshop fixtures. Everything that builds on a plot positions itself from
 * {@link #plotCorner}, so nothing else had to learn about hills. A number in
 * {@code plot.ground-y} still pins every plot to one height, which is the old flat mode.
 */
public class PlotManager {

    private final RoboCraftPlugin plugin;
    private final File file;
    private final YamlConfiguration yml;

    public PlotManager(RoboCraftPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "plots.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public RoboCraftPlugin plugin() { return plugin; }

    public World world() {
        String name = plugin.getConfig().getString("world", "world");
        World w = plugin.getServer().getWorld(name);
        return (w != null) ? w : plugin.getServer().getWorlds().get(0);
    }

    private int cell()    { return size() + plugin.getConfig().getInt("plot.gap", 8); }
    public  int size()    { return plugin.getConfig().getInt("plot.size", 96); }
    public  int perRow()  { return Math.max(1, plugin.getConfig().getInt("plot.per-row", 8)); }
    private int originX() { return plugin.getConfig().getInt("plot.origin-x", 0); }
    private int originZ() { return plugin.getConfig().getInt("plot.origin-z", 0); }

    /** A number pins every plot to that floor (flat mode); anything else means "from the terrain". */
    public boolean fixedGround() {
        return plugin.getConfig().isInt("plot.ground-y");
    }

    /**
     * The floor height of a plot. Fixed mode: the configured number. Terrain mode: surveyed once
     * from the land at the plot's pad and remembered, so the board, shelf and pad agree for ever
     * after even if a student digs the hill away.
     */
    public int groundY(int index) {
        if (fixedGround()) return plugin.getConfig().getInt("plot.ground-y");
        String key = "plots." + index + ".ground";
        if (yml.contains(key)) return yml.getInt(key);
        int y = PadBuilder.survey(this, index);
        yml.set(key, y);
        save();
        return y;
    }

    public boolean isPadBuilt(int index)  { return yml.getBoolean("plots." + index + ".pad", false); }
    public void setPadBuilt(int index)    { yml.set("plots." + index + ".pad", true); save(); }

    /** Corner (min-x, min-z) of the plot at this index, at floor level. */
    public Location plotCorner(int index) {
        int col = index % perRow();
        int row = index / perRow();
        return new Location(world(), originX() + col * cell(), groundY(index), originZ() + row * cell());
    }

    /** Corner without touching the terrain or the record - x/z only, y is 0. For bounds maths. */
    public Location plotCornerXZ(int index) {
        int col = index % perRow();
        int row = index / perRow();
        return new Location(world(), originX() + col * cell(), 0, originZ() + row * cell());
    }

    public Location plotCenter(int index) {
        Location c = plotCorner(index);
        return new Location(world(), c.getBlockX() + size() / 2.0, c.getBlockY() + 1, c.getBlockZ() + size() / 2.0);
    }

    /** Which plot index contains this location, or -1 if it sits in a gap / outside the grid. */
    public int plotIndexAt(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(world())) return -1;
        int lx = loc.getBlockX() - originX();
        int lz = loc.getBlockZ() - originZ();
        if (lx < 0 || lz < 0) return -1;
        int col = lx / cell();
        int row = lz / cell();
        if (col >= perRow()) return -1;
        if ((lx - col * cell()) >= size()) return -1; // in the gap on x
        if ((lz - row * cell()) >= size()) return -1; // in the gap on z
        return row * perRow() + col;
    }

    public void teleportToPlot(Player player, int index) {
        Location c = plotCenter(index);
        c.setYaw(player.getLocation().getYaw());
        c.setPitch(player.getLocation().getPitch());
        player.teleport(c);
    }

    /**
     * A border around the grid, so a class explores the hills near the workshop rather than
     * generating terrain to the horizon. Once per enable; 0 turns it off.
     */
    public void applyBorder() {
        int border = plugin.getConfig().getInt("plot.border", 2400);
        if (border <= 0) return;
        int rows = Math.max(1, (int) Math.ceil(64.0 / perRow()));
        double cx = originX() + (perRow() * cell()) / 2.0;
        double cz = originZ() + (rows * cell()) / 2.0;
        World w = world();
        w.getWorldBorder().setCenter(cx, cz);
        w.getWorldBorder().setSize(border);
    }

    public void save() {
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save plots.yml: " + e.getMessage()); }
    }
}
