package com.agurim.robocraft.part;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * The floating label over a part.
 *
 * <p>This is the debugger. Redstone's problem is that the state of the machine is invisible - you
 * infer it from wire layout. Here every sensor shows what it is reading right now and every
 * actuator shows whether it is on, so a student debugs by <em>looking</em>, which is what a real
 * engineer does with a multimeter.
 *
 * <p>Shows the reading, never the answer: "S1 = 4" teaches; "it is dark, turn the lamp on" would
 * steal the discovery.
 */
public final class PartLabels {

    private PartLabels() {}

    private static Location anchor(Location block) { return block.clone().add(0.5, 1.1, 0.5); }

    /**
     * Create the label, or update the existing one in place.
     *
     * <p>The engine calls this every tick for every part of every running robot, so it must not
     * remove-and-respawn: that would churn entities twice a second per part and make the text
     * visibly flicker. Setting {@code text()} on the live entity is free.
     */
    public static void refresh(RoboCraftPlugin plugin, Location block) {
        Placed placed = plugin.placements().get(block);
        if (placed == null) return;
        Part part = plugin.parts().get(placed.partId());
        if (part == null) return;

        Robot robot = placed.attached() ? plugin.robots().get(placed.robot())
                                        : plugin.robots().get(PartStore.key(block));
        final Component text = describe(plugin, part, placed, robot);

        TextDisplay existing = find(plugin, block, part.id());
        if (existing != null) {
            existing.text(text);
            return;
        }
        block.getWorld().spawn(anchor(block), TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.partKey(), PersistentDataType.STRING, part.id());
        });
    }

    private static TextDisplay find(RoboCraftPlugin plugin, Location block, String partId) {
        for (Entity ent : block.getWorld().getNearbyEntities(anchor(block), 0.6, 0.8, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.partKey(), PersistentDataType.STRING);
                if (partId.equals(tag)) return td;
            }
        }
        return null;
    }

    private static Component describe(RoboCraftPlugin plugin, Part part, Placed placed, Robot robot) {
        if (part.isController()) return controller(plugin, part, robot);

        Component head = Component.text(
                (placed.port().isEmpty() ? "" : placed.port() + "  ") + part.name(),
                placed.attached() ? NamedTextColor.WHITE : NamedTextColor.GRAY);

        if (!placed.attached()) {
            return head.append(Component.text("\nלא מחובר לבקר", NamedTextColor.RED));
        }
        if (part.isBattery()) {
            int e = (robot != null) ? robot.energy() : 0;
            return head.append(Component.text("\n" + e + " / " + part.capacity(), energyColor(e, part.capacity())));
        }
        if (part.isSolar()) {
            return head.append(Component.text("\nטוען", NamedTextColor.YELLOW));
        }
        if (part.isSensor()) {
            Integer v = (robot != null) ? robot.inputs().get(placed.port()) : null;
            return head.append(Component.text("\n" + (v == null ? "-" : v.toString()), NamedTextColor.AQUA));
        }
        // actuator
        Integer v = (robot != null) ? robot.outputs().get(placed.port()) : null;
        if ("display".equals(part.actuator())) {
            return head.append(Component.text("\n" + (v == null ? "-" : v.toString()), NamedTextColor.GOLD));
        }
        boolean on = v != null && v != 0;
        return head.append(Component.text("\n" + (on ? "ON" : "OFF"),
                on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
    }

    private static Component controller(RoboCraftPlugin plugin, Part part, Robot robot) {
        Component head = Component.text(part.name(), NamedTextColor.AQUA);
        if (robot == null) return head.append(Component.text("\nלחיצה ימנית", NamedTextColor.GRAY));

        String state = robot.running() ? "פועל" : (robot.halt() != null ? robot.halt() : "עצור");
        NamedTextColor stateColor = robot.running() ? NamedTextColor.GREEN
                : (robot.halt() != null ? NamedTextColor.RED : NamedTextColor.GRAY);

        Component out = head.append(Component.text("\n" + state, stateColor));
        if (robot.running() && robot.lastFired() >= 0) {
            out = out.append(Component.text("\nכלל " + (robot.lastFired() + 1), NamedTextColor.YELLOW));
        }
        return out.append(Component.text("\n⚡ " + robot.energy(), NamedTextColor.GOLD));
    }

    private static NamedTextColor energyColor(int energy, int capacity) {
        if (capacity <= 0) return NamedTextColor.GRAY;
        double f = (double) energy / capacity;
        return (f > 0.5) ? NamedTextColor.GREEN : (f > 0.2) ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    public static void remove(RoboCraftPlugin plugin, Location block, String partId) {
        for (Entity ent : block.getWorld().getNearbyEntities(anchor(block), 0.6, 0.8, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.partKey(), PersistentDataType.STRING);
                if (partId.equals(tag)) td.remove();
            }
        }
    }
}
