package com.agurim.robocraft.plot;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Carves the workshop floor into real terrain: a flat grass pad at the plot's corner, big enough
 * for the board, the trophy shelf, the charging pad and the spot a student lands on.
 *
 * <p>The rest of the plot is left as the world made it - hills, trees, a river if there is one -
 * and is the student's to build on. Only the pad is flattened, because only the fixtures need a
 * floor, and a whole plot flattened would be the flat world again with extra steps.
 *
 * <p>Height: the pad sits at the highest surface sampled across it, ignoring trees, so it never
 * ends below a neighbouring pond that would flow in. Above the pad everything is cleared to sky;
 * below, dirt under grass and stone into any cave or water down to a depth that is enough to
 * stand on. About a thousand columns; a fraction of a second, once per plot.
 */
public final class PadBuilder {

    private PadBuilder() {}

    public static int padX(RoboCraftPlugin plugin) { return Math.max(8, plugin.getConfig().getInt("plot.pad-x", 44)); }
    public static int padZ(RoboCraftPlugin plugin) { return Math.max(8, plugin.getConfig().getInt("plot.pad-z", 24)); }

    /** Sea level, the floor below which no pad goes - an ocean plot gets a platform, not a dive. */
    private static final int MIN_FLOOR = 62;
    private static final int CLEAR_ABOVE = 96;
    private static final int FILL_BELOW = 64;

    /**
     * The floor height for a plot, from the land. The middle of the tree-free surface heights
     * sampled across the pad - so a hill is half cut and half filled, and the pad's edges meet
     * the land rather than hanging over it - but never below any water sampled, or a pond
     * beside the pad would flow onto it, and never below sea level. Reads the terrain (loading
     * its chunks); writes nothing.
     */
    public static int survey(PlotManager plots, int index) {
        RoboCraftPlugin plugin = plots.plugin();
        World w = plots.world();
        Location c = plots.plotCornerXZ(index);
        int px = padX(plugin), pz = padZ(plugin);
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        int water = Integer.MIN_VALUE;
        for (int dx = 2; dx < px; dx += 6) {
            for (int dz = 2; dz < pz; dz += 6) {
                int x = c.getBlockX() + dx, z = c.getBlockZ() + dz;
                heights.add(surfaceAt(w, x, z));
                int top = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
                if (w.getBlockAt(x, top, z).getType() == Material.WATER) water = Math.max(water, top + 1);
            }
        }
        java.util.Collections.sort(heights);
        int median = heights.isEmpty() ? MIN_FLOOR : heights.get(heights.size() / 2);
        return Math.max(MIN_FLOOR, Math.max(median, water));
    }

    /**
     * How the pad meets the land: the biggest step down and the biggest step up from the pad's
     * floor to the surface one block outside its edge. For the teacher's survey; a pad with a
     * forty-block drop on one side is a pad to move a plot away from.
     */
    public static String edgeDrop(RoboCraftPlugin plugin, int index) {
        PlotManager plots = plugin.plots();
        World w = plots.world();
        Location c = plots.plotCorner(index);
        int px = padX(plugin), pz = padZ(plugin), floor = c.getBlockY();
        int down = 0, up = 0;
        for (int dx = -1; dx <= px; dx += 3) {
            for (int dz : new int[] { -1, pz }) {
                int s = surfaceAt(w, c.getBlockX() + dx, c.getBlockZ() + dz);
                down = Math.max(down, floor - s); up = Math.max(up, s - floor);
            }
        }
        for (int dz = -1; dz <= pz; dz += 3) {
            for (int dx : new int[] { -1, px }) {
                int s = surfaceAt(w, c.getBlockX() + dx, c.getBlockZ() + dz);
                down = Math.max(down, floor - s); up = Math.max(up, s - floor);
            }
        }
        return "edge -" + down + "/+" + up;
    }

    /** The highest block that is not a tree - a log or leaves would put the whole pad in the canopy. */
    public static int surfaceAt(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        while (y > w.getMinHeight()) {
            Material m = w.getBlockAt(x, y, z).getType();
            if (Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || m == Material.MUSHROOM_STEM
                    || m == Material.BROWN_MUSHROOM_BLOCK || m == Material.RED_MUSHROOM_BLOCK) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    /** Build the pad if it has not been built. Returns true if it built now. */
    public static boolean ensure(RoboCraftPlugin plugin, int index) {
        PlotManager plots = plugin.plots();
        if (plots.fixedGround()) return false;          // flat mode: the world is the pad
        if (plots.isPadBuilt(index)) return false;
        build(plugin, index);
        plots.setPadBuilt(index);
        return true;
    }

    /** Carve the pad at the plot's recorded floor. Idempotent; safe to run again on a damaged pad. */
    public static void build(RoboCraftPlugin plugin, int index) {
        PlotManager plots = plugin.plots();
        World w = plots.world();
        Location c = plots.plotCorner(index);
        int floor = c.getBlockY();
        int px = padX(plugin), pz = padZ(plugin);
        int top = Math.min(w.getMaxHeight() - 1, floor + CLEAR_ABOVE);
        int bottom = Math.max(w.getMinHeight(), floor - FILL_BELOW);

        for (int dx = 0; dx < px; dx++) {
            for (int dz = 0; dz < pz; dz++) {
                int x = c.getBlockX() + dx, z = c.getBlockZ() + dz;
                for (int y = top; y > floor; y--) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                }
                w.getBlockAt(x, floor, z).setType(Material.GRASS_BLOCK, false);
                for (int y = floor - 1; y >= floor - 3 && y >= bottom; y--) {
                    w.getBlockAt(x, y, z).setType(Material.DIRT, false);
                }
                for (int y = floor - 4; y >= bottom; y--) {
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m.isAir() || m == Material.WATER || m == Material.LAVA || m == Material.KELP
                            || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS
                            || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m)) {
                        b.setType(Material.STONE, false);
                    }
                }
            }
        }
        rim(w, c, floor, px, pz);
    }

    /** A step this high or more off the pad's edge gets a fence. A hilltop pad is a cliff otherwise. */
    private static final int RIM_DROP = 4;

    /**
     * Fence the pad's edge wherever the land outside falls away by {@link #RIM_DROP} or more.
     * Only there: a pad that meets the land level has no rail, so the workshop opens onto the
     * plot rather than being boxed in.
     */
    private static void rim(World w, Location c, int floor, int px, int pz) {
        for (int dx = 0; dx < px; dx++) {
            rimAt(w, c.getBlockX() + dx, c.getBlockZ(), c.getBlockX() + dx, c.getBlockZ() - 1, floor);
            rimAt(w, c.getBlockX() + dx, c.getBlockZ() + pz - 1, c.getBlockX() + dx, c.getBlockZ() + pz, floor);
        }
        for (int dz = 0; dz < pz; dz++) {
            rimAt(w, c.getBlockX(), c.getBlockZ() + dz, c.getBlockX() - 1, c.getBlockZ() + dz, floor);
            rimAt(w, c.getBlockX() + px - 1, c.getBlockZ() + dz, c.getBlockX() + px, c.getBlockZ() + dz, floor);
        }
    }

    private static void rimAt(World w, int x, int z, int outX, int outZ, int floor) {
        int outside = surfaceAt(w, outX, outZ);
        if (floor - outside >= RIM_DROP) {
            Block b = w.getBlockAt(x, floor + 1, z);
            if (b.getType().isAir()) b.setType(Material.OAK_FENCE, false);
        }
    }

    /** Fence posts on the pad's edge - how much of it needed a rail. */
    public static int rimCount(RoboCraftPlugin plugin, int index) {
        PlotManager plots = plugin.plots();
        World w = plots.world();
        Location c = plots.plotCorner(index);
        int px = padX(plugin), pz = padZ(plugin), y = c.getBlockY() + 1, n = 0;
        for (int dx = 0; dx < px; dx++) {
            if (w.getBlockAt(c.getBlockX() + dx, y, c.getBlockZ()).getType() == Material.OAK_FENCE) n++;
            if (w.getBlockAt(c.getBlockX() + dx, y, c.getBlockZ() + pz - 1).getType() == Material.OAK_FENCE) n++;
        }
        for (int dz = 1; dz < pz - 1; dz++) {
            if (w.getBlockAt(c.getBlockX(), y, c.getBlockZ() + dz).getType() == Material.OAK_FENCE) n++;
            if (w.getBlockAt(c.getBlockX() + px - 1, y, c.getBlockZ() + dz).getType() == Material.OAK_FENCE) n++;
        }
        return n;
    }

    /** Blocks above and below the pad floor that count as "the workshop", not "the ground". */
    private static final int PAD_UP = 14, PAD_DOWN = 3;

    /**
     * Is this block part of the carved workshop pad - its floor, its fence, the air the board
     * and shelf stand in? A visitor may dig the land on someone else's plot but not this
     * (PlotProtection), so the test has to be geometry rather than a block type: the grass of a
     * pad and the grass of the hill beside it are the same block.
     */
    public static boolean onPad(RoboCraftPlugin plugin, int plotIndex, Location loc) {
        if (plugin.plots().fixedGround()) return false;   // flat mode has no pad to speak of
        Location c = plugin.plots().plotCorner(plotIndex);
        int dx = loc.getBlockX() - c.getBlockX(), dz = loc.getBlockZ() - c.getBlockZ();
        if (dx < 0 || dx >= padX(plugin) || dz < 0 || dz >= padZ(plugin)) return false;
        int dy = loc.getBlockY() - c.getBlockY();
        return dy >= -PAD_DOWN && dy <= PAD_UP;
    }

    /**
     * Is every fixture inside the pad? Pure geometry, for the validator and the self-test: a
     * shelf that reaches past the pad's edge would hang over a hillside.
     */
    public static String fixturesOutsidePad(RoboCraftPlugin plugin) {
        int px = padX(plugin), pz = padZ(plugin);
        Location corner = plugin.plots().plotCornerXZ(0);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < plugin.parts().size(); i++) {
            check(out, "board tile " + i, plugin.board().tileLocation(0, i), corner, px, pz);
        }
        for (int i = 0; i < plugin.missions().registry().size(); i++) {
            check(out, "trophy " + i, plugin.trophies().slotLocation(0, i), corner, px, pz);
        }
        check(out, "charging pad", plugin.kiosk().chargerLocation(0), corner, px, pz);
        check(out, "arrival spot", plugin.board().arrivalSpot(0), corner, px, pz);
        return out.toString();
    }

    private static void check(StringBuilder out, String what, Location l, Location corner, int px, int pz) {
        int dx = l.getBlockX() - corner.getBlockX(), dz = l.getBlockZ() - corner.getBlockZ();
        if (dx < 0 || dx >= px || dz < 0 || dz >= pz) {
            out.append(what).append(" at +").append(dx).append(",+").append(dz).append(" is outside the ")
               .append(px).append("x").append(pz).append(" pad; ");
        }
    }

    /** How flat a built pad actually is: columns whose floor is grass with air above, out of all. */
    public static String flatness(RoboCraftPlugin plugin, int index) {
        PlotManager plots = plugin.plots();
        World w = plots.world();
        Location c = plots.plotCorner(index);
        int px = padX(plugin), pz = padZ(plugin), ok = 0;
        for (int dx = 0; dx < px; dx++) {
            for (int dz = 0; dz < pz; dz++) {
                int x = c.getBlockX() + dx, z = c.getBlockZ() + dz, y = c.getBlockY();
                Material above = w.getBlockAt(x, y + 1, z).getType();
                if (w.getBlockAt(x, y, z).getType() == Material.GRASS_BLOCK
                        && (above.isAir() || above == Material.OAK_FENCE)
                        && w.getBlockAt(x, y + 2, z).getType().isAir()) ok++;
            }
        }
        return ok + "/" + (px * pz);
    }
}
