package com.agurim.robocraft.plot;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Lays plots out in a grid in one shared world and maps locations &lt;-&gt; plot index.
 *
 * <p>Copied from ChemCraft rather than shared: see the root CLAUDE.md for why there is no core
 * module yet. The only coupling was the plugin type, used purely as a JavaPlugin - which is
 * exactly the mechanical swap the note predicted.
 */
public class PlotManager {

    private final RoboCraftPlugin plugin;

    public PlotManager(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public World world() {
        String name = plugin.getConfig().getString("world", "world");
        World w = plugin.getServer().getWorld(name);
        return (w != null) ? w : plugin.getServer().getWorlds().get(0);
    }

    private int cell()    { return size() + plugin.getConfig().getInt("plot.gap", 8); }
    private int size()    { return plugin.getConfig().getInt("plot.size", 96); }
    private int perRow()  { return Math.max(1, plugin.getConfig().getInt("plot.per-row", 8)); }
    private int originX() { return plugin.getConfig().getInt("plot.origin-x", 0); }
    private int originZ() { return plugin.getConfig().getInt("plot.origin-z", 0); }
    private int groundY() { return plugin.getConfig().getInt("plot.ground-y", 64); }

    /** Corner (min-x, min-z) of the plot at this index, at ground level. */
    public Location plotCorner(int index) {
        int col = index % perRow();
        int row = index / perRow();
        return new Location(world(), originX() + col * cell(), groundY(), originZ() + row * cell());
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
}
