package com.agurim.robocraft.sense;

import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.Placed;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns the world into a number - the first half of the loop, and the first idea of the course.
 *
 * <p>When a mission is running, {@code env} carries simulated readings by sensor TYPE and wins over
 * the world. That is the test bench: deterministic, fast, and it does not black out a shared world
 * to test one student's night light (see docs/DESIGN.md 4.4).
 */
public final class SensorReader {

    private SensorReader() {}

    /**
     * Every sensor type this class actually implements. A part whose {@code sensor:} is not in
     * here reads 0 forever and never says why, so startup validation checks against this list -
     * a typo in parts.yml should be a log line, not a silent dead sensor.
     */
    public static final java.util.Set<String> TYPES = java.util.Set.of(
            "light", "distance", "player", "mob", "heat", "color", "rain", "redstone", "random");

    public static int read(Location loc, Placed placed, Part part, Map<String, Integer> env) {
        String type = part.sensor();
        if (env != null && env.containsKey(type)) return env.get(type);

        return switch (type) {
            case "light"    -> light(loc);
            case "distance" -> distance(loc, placed.facing(), part.reach());
            case "player"   -> nearPlayer(loc, part.radius());
            case "mob"      -> nearMob(loc, part.radius());
            case "heat"     -> heat(loc, part.radius());
            case "color"    -> colorBelow(loc);
            case "rain"     -> (loc.getWorld() != null && loc.getWorld().hasStorm()) ? 1 : 0;
            case "redstone" -> loc.getBlock().getBlockPower();
            case "random"   -> ThreadLocalRandom.current().nextInt(100);
            default         -> 0;
        };
    }

    /** Light where the sensor can actually see it: the sensor's own block is opaque, so read above. */
    private static int light(Location loc) {
        return loc.clone().add(0, 1, 0).getBlock().getLightLevel();
    }

    /** Blocks to the first solid block along the facing direction; {@code reach} means "nothing". */
    private static int distance(Location loc, BlockFace facing, int reach) {
        Block b = loc.getBlock();
        for (int i = 1; i <= reach; i++) {
            Block probe = b.getRelative(facing, i);
            if (probe.getType().isSolid()) return i;
        }
        return reach;
    }

    private static int nearPlayer(Location loc, int radius) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(loc) <= (double) radius * radius) return 1;
        }
        return 0;
    }

    /**
     * Any living creature that is not a player. An armour stand is a LivingEntity in Bukkit, and
     * a student could plant one to pin a classmate's animal sensor at 1 forever - so it does not
     * count. The pen's sheep are meant to.
     */
    private static int nearMob(Location loc, int radius) {
        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity && !(e instanceof Player)
                    && !(e instanceof org.bukkit.entity.ArmorStand)) return 1;
        }
        return 0;
    }

    /**
     * A crude thermometer: 50 is neutral, fire and lava push it up, ice and snow push it down.
     * Real values only matter outside the bench - missions inject heat directly.
     */
    private static int heat(Location loc, int radius) {
        int heat = 50;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Material m = loc.clone().add(dx, dy, dz).getBlock().getType();
                    switch (m) {
                        case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, LAVA_CAULDRON -> heat += 6;
                        case CAMPFIRE, SOUL_CAMPFIRE, TORCH, LANTERN           -> heat += 2;
                        case ICE, PACKED_ICE, BLUE_ICE, SNOW_BLOCK, POWDER_SNOW -> heat -= 6;
                        case SNOW                                              -> heat -= 2;
                        default -> { }
                    }
                }
            }
        }
        return Math.max(0, Math.min(100, heat));
    }

    /** Colour index (vanilla dye order) of the block underneath; 16 when it is not a coloured block. */
    private static int colorBelow(Location loc) {
        String name = loc.clone().add(0, -1, 0).getBlock().getType().name();
        for (DyeColor c : DyeColor.values()) {
            if (name.startsWith(c.name() + "_")) return c.ordinal();
        }
        return 16;
    }
}
