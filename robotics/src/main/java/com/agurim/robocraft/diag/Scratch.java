package com.agurim.robocraft.diag;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartLabels;
import com.agurim.robocraft.part.Placed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scratch area for {@link SelfTest}: remembers every block it overwrites and every part it
 * registers, so the world can be put back exactly as it was.
 *
 * <p>A diagnostic that leaves litter behind is worse than no diagnostic - this runs on the same
 * server a class is about to use.
 */
final class Scratch {

    private final Map<Location, BlockData> restore = new LinkedHashMap<>();
    private final List<Location> placed = new ArrayList<>();
    private final List<String> robots = new ArrayList<>();

    /** Overwrite a block, remembering what was there. */
    void set(Location loc, Material material) {
        Block block = loc.getBlock();
        restore.putIfAbsent(loc.clone(), block.getBlockData().clone());
        block.setType(material, false);          // false: no physics, these are our blocks
    }

    void clear(Location loc) {
        set(loc, Material.AIR);
    }

    /** Place a part block and register it, so the engine treats it as a real part. */
    void part(RoboCraftPlugin plugin, Location loc, Part part, String robot, String port) {
        set(loc, part.block());
        plugin.placements().put(loc, new Placed(part.id(), BlockFace.NORTH, robot, port));
        PartLabels.refresh(plugin, loc);
        placed.add(loc.clone());
    }

    void robot(String key) { robots.add(key); }

    void restore(RoboCraftPlugin plugin) {
        for (Location loc : placed) {
            Placed p = plugin.placements().get(loc);
            if (p != null) PartLabels.remove(plugin, loc, p.partId());
            plugin.placements().remove(loc);
        }
        for (String key : robots) {
            plugin.placements().detachAll(key);
            plugin.robots().remove(key);
        }
        restore.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
        placed.clear();
        robots.clear();
        restore.clear();
    }
}
