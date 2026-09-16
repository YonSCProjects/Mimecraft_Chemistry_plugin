package com.agurim.robocraft.plot;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Random;

/**
 * Open land: everywhere in the workshop world that is nobody's plot.
 *
 * <p>Yon, 2026-09-15: "let's add a command that teleports to a place that is nobody's plot." A
 * student's own plot is 96 blocks square with the workshop in one corner, and a classmate's may
 * be dug but not built on - so a student with a big idea needs somewhere else to put it, and on
 * terrain nothing in the world shows where the grid ends.
 *
 * <p>The grid runs east from its origin in {@code per-row} columns and south without end. So
 * north of it, west of it and east of it are outside every plot for good, and that is where a
 * spot is chosen: a random side, a random distance from 24 to 256 blocks out, inside the world
 * border, on dry ground with head room, and not within 24 blocks of another student's spot.
 */
public final class Commons {

    private Commons() {}

    public static final int NEAR = 24, FAR = 256, SPREAD = 24, ATTEMPTS = 12;

    /**
     * May a student call this place home? Anywhere they may build: their own plot, or open land.
     * Not a classmate's plot - a home there would be a bed in somebody else's workshop, and the
     * student could not build around it anyway. Pure, so the self-test can hold the rule.
     */
    public static boolean canSettle(int plotHere, int ownPlot) {
        return plotHere < 0 || plotHere == ownPlot;
    }

    /** Outside every plot, in the workshop world. Gaps between plots count. */
    public static boolean isCommons(RoboCraftPlugin plugin, Location loc) {
        return loc.getWorld() != null && loc.getWorld().equals(plugin.plots().world())
                && plugin.plots().plotIndexAt(loc) < 0;
    }

    /** A candidate x/z outside the grid. Touches config only, so the self-test can draw thousands. */
    public static int[] candidate(RoboCraftPlugin plugin, Random rng) {
        PlotManager plots = plugin.plots();
        int cell = plots.size() + plugin.getConfig().getInt("plot.gap", 8);
        int ox = plugin.getConfig().getInt("plot.origin-x", 0);
        int oz = plugin.getConfig().getInt("plot.origin-z", 0);
        int gridW = plots.perRow() * cell;
        int d = NEAR + rng.nextInt(FAR - NEAR);
        int along = 2 * cell + 256;
        return switch (rng.nextInt(3)) {
            case 0  -> new int[] { ox - 128 + rng.nextInt(gridW + 256), oz - d };   // north
            case 1  -> new int[] { ox - d, oz - 128 + rng.nextInt(along) };         // west
            default -> new int[] { ox + gridW + d, oz - 128 + rng.nextInt(along) }; // east
        };
    }

    /** Somewhere to stand and build, or null if twelve tries found only water, trees and neighbours. */
    public static Location pick(RoboCraftPlugin plugin, List<Location> taken, Random rng) {
        World w = plugin.plots().world();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int[] c = candidate(plugin, rng);
            Location probe = new Location(w, c[0] + 0.5, 64, c[1] + 0.5);
            // The cheap checks first: every later one loads, and may generate, a chunk.
            if (!isCommons(plugin, probe) || !w.getWorldBorder().isInside(probe)) continue;
            if (crowded(probe, taken)) continue;
            Location stand = standAt(w, c[0], c[1]);
            if (stand != null) return stand;
        }
        return null;
    }

    /** The place to stand at this column: on dry solid ground with two blocks of head room. */
    public static Location standAt(World w, int x, int z) {
        int y = PadBuilder.surfaceAt(w, x, z);
        Material ground = w.getBlockAt(x, y, z).getType();
        if (!ground.isSolid() || ground == Material.ICE || ground == Material.MAGMA_BLOCK) return null;
        if (!w.getBlockAt(x, y + 1, z).isPassable() || !w.getBlockAt(x, y + 2, z).isPassable()) return null;
        if (w.getBlockAt(x, y + 1, z).getType() == Material.WATER) return null;
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    private static boolean crowded(Location probe, List<Location> taken) {
        for (Location t : taken) {
            if (t.getWorld() != null && t.getWorld().equals(probe.getWorld())
                    && Math.hypot(t.getX() - probe.getX(), t.getZ() - probe.getZ()) < SPREAD) return true;
        }
        return false;
    }
}
