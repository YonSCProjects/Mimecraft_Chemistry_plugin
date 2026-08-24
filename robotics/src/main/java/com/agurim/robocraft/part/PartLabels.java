package com.agurim.robocraft.part;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
     * The display entity for each part, and a signature of what it is currently showing.
     *
     * <p>Both are caches - everything in them can be recovered from the world - and both exist for
     * speed. {@link #refresh} runs once per part of every running robot, twice a second: twenty
     * students' robots is eighty calls a tick. Measured at that size, finding the entity with
     * {@code getNearbyEntities} and rebuilding its Component every time cost 35 ms of a 50 ms
     * server tick.
     */
    private static final Map<String, UUID> DISPLAYS = new ConcurrentHashMap<>();
    private static final Map<String, String> SHOWING = new ConcurrentHashMap<>();

    /**
     * Create the label, or update the existing one in place.
     *
     * <p>Never remove-and-respawn: that would churn entities twice a second per part and make the
     * text visibly flicker.
     */
    public static void refresh(RoboCraftPlugin plugin, Location block) {
        Placed placed = plugin.placements().get(block);
        if (placed == null) return;
        Part part = plugin.parts().get(placed.partId());
        if (part == null) return;

        String key = PartStore.key(block);
        Robot robot = placed.attached() ? plugin.robots().get(placed.robot())
                                        : plugin.robots().get(key);

        // Cheap first: if nothing this label shows has changed, stop before building a Component
        // or touching the entity at all. Most parts are unchanged on most ticks.
        String signature = signature(part, placed, robot);
        if (signature.equals(SHOWING.get(key))) return;

        final Component text = describe(plugin, part, placed, robot);
        TextDisplay existing = display(plugin, key, block, part.id());
        if (existing != null) {
            existing.text(text);
            SHOWING.put(key, signature);
            return;
        }
        TextDisplay spawned = block.getWorld().spawn(anchor(block), TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.partKey(), PersistentDataType.STRING, part.id());
        });
        DISPLAYS.put(key, spawned.getUniqueId());
        SHOWING.put(key, signature);
    }

    /**
     * Everything the label shows, as a cheap string built from primitives only.
     *
     * <p>It has to track {@link #describe} exactly. A signature that misses something the label
     * displays leaves that label stale on screen - and a debugging aid showing a stale number is
     * worse than a slow one. That is why both go through {@link #shownEnergy} and {@link #pct}
     * rather than reading energy directly.
     */
    private static String signature(Part part, Placed placed, Robot robot) {
        if (!placed.attached() && !part.isController()) return "detached";
        if (robot == null) return "no-robot";
        if (part.isController()) {
            return "c|" + robot.running() + "|" + robot.halt() + "|" + robot.lastFired()
                    + "|" + shownEnergy(robot.energy());
        }
        if (part.isBattery()) return "b|" + pct(robot.energy(), part.capacity());
        if (part.isSolar())   return "s";
        Integer value = part.isSensor() ? robot.inputs().get(placed.port())
                                        : robot.outputs().get(placed.port());
        return placed.port() + "=" + value;
    }

    /** The cached display if it is still alive, otherwise one spatial search to re-find it. */
    private static TextDisplay display(RoboCraftPlugin plugin, String key, Location block, String partId) {
        UUID id = DISPLAYS.get(key);
        if (id != null) {
            Entity cached = Bukkit.getEntity(id);
            if (cached instanceof TextDisplay td && td.isValid()) return td;
            DISPLAYS.remove(key);
        }
        TextDisplay found = find(plugin, block, partId);
        if (found != null) DISPLAYS.put(key, found.getUniqueId());
        return found;
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
            int energy = (robot != null) ? robot.energy() : 0;
            return head.append(Component.text("\n" + pct(energy, part.capacity()) + "%",
                    energyColor(energy, part.capacity())));
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
        return out.append(Component.text("\n⚡ " + shownEnergy(robot.energy()), NamedTextColor.GOLD));
    }

    /**
     * Charge to the nearest 5%, and raw energy to the nearest 50.
     *
     * <p>Both roundings exist for the same two reasons. A figure ticking down every half second is
     * unreadable on a floating label - and it also meant the battery and controller labels counted
     * as "changed" on literally every tick, which defeated the point of the signature check. The
     * exact number is still available in the program menu and in {@code /rc trace}, which is where
     * a student reading numbers actually is.
     */
    private static int pct(int energy, int capacity) {
        if (capacity <= 0) return 0;
        return Math.round(energy * 20f / capacity) * 5;
    }

    private static int shownEnergy(int energy) {
        return Math.round(energy / 50f) * 50;
    }

    private static NamedTextColor energyColor(int energy, int capacity) {
        if (capacity <= 0) return NamedTextColor.GRAY;
        double f = (double) energy / capacity;
        return (f > 0.5) ? NamedTextColor.GREEN : (f > 0.2) ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    public static void remove(RoboCraftPlugin plugin, Location block, String partId) {
        String key = PartStore.key(block);
        DISPLAYS.remove(key);
        SHOWING.remove(key);
        for (Entity ent : block.getWorld().getNearbyEntities(anchor(block), 0.6, 0.8, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.partKey(), PersistentDataType.STRING);
                if (partId.equals(tag)) td.remove();
            }
        }
    }
}
