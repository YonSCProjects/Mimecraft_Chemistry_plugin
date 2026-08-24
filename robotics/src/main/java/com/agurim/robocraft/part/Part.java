package com.agurim.robocraft.part;

import org.bukkit.Material;

/** One buildable part, loaded from parts.yml. */
public record Part(
        String id, String name, String kind, Material block,
        String sensor, String actuator,
        int capacity, int drain, int reach, int radius,
        String range, String hint, boolean unlockedByDefault) {

    public boolean isController() { return "controller".equals(kind); }
    public boolean isBattery()    { return "battery".equals(kind); }
    public boolean isSolar()      { return "solar".equals(kind); }
    public boolean isSensor()     { return "sensor".equals(kind); }
    public boolean isActuator()   { return "actuator".equals(kind); }
}
