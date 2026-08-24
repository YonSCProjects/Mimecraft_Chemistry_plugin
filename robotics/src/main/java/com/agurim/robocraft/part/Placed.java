package com.agurim.robocraft.part;

import org.bukkit.block.BlockFace;

/**
 * A part block standing in the world.
 *
 * <p>{@code facing} is the horizontal direction the player was looking when they placed it - which
 * is what a distance sensor looks along. {@code robot} is the location key of the controller this
 * part belongs to (empty for a controller itself, or for a part that found no controller in range).
 * {@code port} is the pin name the program refers to: S1, S2... for sensors, A1, A2... for actuators.
 */
public record Placed(String partId, BlockFace facing, String robot, String port) {

    public boolean attached() { return robot != null && !robot.isEmpty(); }

    public Placed withRobot(String robotKey, String port) {
        return new Placed(partId, facing, robotKey, port);
    }
}
