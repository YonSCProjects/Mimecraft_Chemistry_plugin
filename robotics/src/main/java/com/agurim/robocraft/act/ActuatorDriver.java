package com.agurim.robocraft.act;

import com.agurim.robocraft.part.Part;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;

/**
 * Turns a decision into a change in the world - the second half of the loop.
 *
 * <p>Only called when a value actually changed, except for the continuous ones (marker particles),
 * so a running robot does not spam block updates twice a second forever.
 */
public final class ActuatorDriver {

    private ActuatorDriver() {}

    public static void apply(Location loc, Part part, int value, int previous) {
        Block block = loc.getBlock();
        boolean on = value != 0;

        switch (part.actuator()) {
            case "lamp" -> {
                BlockData data = block.getBlockData();
                if (data instanceof Lightable lightable) {
                    lightable.setLit(on);
                    block.setBlockData(lightable, false);   // false: no physics, this is our block
                }
            }
            case "gate" -> {
                BlockData data = block.getBlockData();
                if (data instanceof Openable openable) {
                    openable.setOpen(on);
                    block.setBlockData(openable, false);
                    loc.getWorld().playSound(loc, on ? Sound.BLOCK_IRON_TRAPDOOR_OPEN
                                                     : Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.0f);
                }
            }
            case "buzzer" -> {
                // Rising edge only. A buzzer that re-fires every tick while the condition holds is
                // unbearable in a classroom of thirty.
                if (on && previous == 0) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
                }
            }
            case "marker" -> {
                if (on) {
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 1.2, 0.5), 6, 0.15, 0.15, 0.15, 0.01);
                }
            }
            default -> { }   // display is drawn by PartLabels, not written into the world
        }
    }

    /** Marker particles are continuous, so the engine must call apply() every tick for them. */
    public static boolean continuous(Part part) {
        return "marker".equals(part.actuator());
    }
}
