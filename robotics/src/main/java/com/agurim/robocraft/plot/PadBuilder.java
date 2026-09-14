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
    private static final int CLEAR_ABOVE = 48;
    private static final int FILL_BELOW = 24;

    /**
     * The floor height for a plot, from the land: the highest tree-free surface across the pad,
     * never below sea level. Reads the terrain (loading its chunks); writes nothing.
     */
    public static int survey(PlotManager plots, int index) {
        RoboCraftPlugin plugin = plots.plugin();
        World w = plots.world();
        Location c = plots.plotCornerXZ(index);
        int px = padX(plugin), pz = padZ(plugin);
        int best = MIN_FLOOR;
        for (int dx = 2; dx < px; dx += 6) {
            for (int dz = 2; dz < pz; dz += 6) {
                best = Math.max(best, surfaceAt(w, c.getBlockX() + dx, c.getBlockZ() + dz));
            }
        }
        return best;
    }

    /** The highest block that is not a tree - a log or leaves would put the whole pad in the canopy. */
    private static int surfaceAt(World w, int x, int z) {
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
                if (w.getBlockAt(x, y, z).getType() == Material.GRASS_BLOCK
                        && w.getBlockAt(x, y + 1, z).getType().isAir()
                        && w.getBlockAt(x, y + 2, z).getType().isAir()) ok++;
            }
        }
        return ok + "/" + (px * pz);
    }
}
