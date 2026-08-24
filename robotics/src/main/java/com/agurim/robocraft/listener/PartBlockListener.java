package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartItems;
import com.agurim.robocraft.part.PartLabels;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Placing and breaking parts.
 *
 * <p>HIGH + ignoreCancelled so plot protection (NORMAL) has already had its say - the same ordering
 * rule ChemCraft uses for anything that must run after protection.
 *
 * <p>Attaching on placement is the teaching moment: the part announces the port it just got, and a
 * port is a pin number. That is the vocabulary the program is written in.
 */
public class PartBlockListener implements Listener {

    private final RoboCraftPlugin plugin;

    public PartBlockListener(RoboCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String partId = PartItems.idOf(plugin, event.getItemInHand());
        if (partId == null) return;
        Part part = plugin.parts().get(partId);
        if (part == null) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        String key = PartStore.key(loc);

        if (part.isController()) {
            plugin.placements().put(loc, new Placed(partId, player.getFacing(), "", ""));
            Robot robot = plugin.robots().getOrCreate(key, player.getUniqueId());
            robot.owner(player.getUniqueId());
            plugin.robots().save();
            PartLabels.refresh(plugin, loc);
            player.sendMessage(Component.text("בקר הונח. לחיצה ימנית עליו פותחת את התוכנית.",
                    NamedTextColor.GREEN));
            adoptOrphans(player, key, loc);
            return;
        }

        String controller = nearestController(loc);
        if (controller == null) {
            place(loc, partId, player, "", "");
            player.sendMessage(Component.text("אין בקר בטווח "
                    + plugin.getConfig().getInt("robot.attach-radius", 4)
                    + " בלוקים - הרכיב לא מחובר.", NamedTextColor.RED));
            return;
        }

        int max = plugin.getConfig().getInt("robot.max-parts", 16);
        if (plugin.placements().partsOf(controller).size() >= max) {
            place(loc, partId, player, "", "");
            player.sendMessage(Component.text("הבקר הזה מלא (" + max + " רכיבים).", NamedTextColor.RED));
            return;
        }

        boolean ported = part.isSensor() || part.isActuator();
        String port = ported ? plugin.placements().nextPort(controller, part.isSensor()) : "";
        place(loc, partId, player, controller, port);

        // A freshly attached battery arrives charged. Making a student hunt for the charging pad
        // before their first robot can move would be a puzzle about menus, not about robotics.
        if (part.isBattery()) {
            Robot robot = plugin.robots().get(controller);
            if (robot != null) {
                robot.energy(plugin.batteryCapacity(controller));
                plugin.robots().save();
            }
        }
        refreshAt(controller);

        player.sendMessage(ported
                ? Component.text(part.name() + " חובר לבקר בשם " + port
                    + " - זה השם שמשתמשים בו בתוכנית.", NamedTextColor.GREEN)
                : Component.text(part.name() + " חובר לבקר.", NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Placed placed = plugin.placements().get(loc);
        if (placed == null) return;
        Part part = plugin.parts().get(placed.partId());
        if (part == null) return;

        String key = PartStore.key(loc);
        String controller = placed.robot();
        PartLabels.remove(plugin, loc, part.id());
        plugin.placements().remove(loc);

        if (part.isController()) {
            // Everything attached becomes an orphan rather than vanishing: the student's build
            // stays standing, it just stops being a robot until a controller comes back.
            Map<String, Placed> orphans = new LinkedHashMap<>(plugin.placements().partsOf(key));
            plugin.placements().detachAll(key);
            plugin.robots().remove(key);
            plugin.missions().abort(key);
            orphans.keySet().forEach(this::refreshAt);
            event.getPlayer().sendMessage(Component.text(
                    "הבקר הוסר. הרכיבים נשארו במקומם אבל אינם מחוברים.", NamedTextColor.YELLOW));
        } else if (placed.attached()) {
            refreshAt(controller);
        }

        // Hand the part back rather than the vanilla block, so a build can be taken apart safely.
        event.setDropItems(false);
        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), PartItems.of(plugin, part, 1));
    }

    private void place(Location loc, String partId, Player player, String robot, String port) {
        plugin.placements().put(loc, new Placed(partId, player.getFacing(), robot, port));
        PartLabels.refresh(plugin, loc);
    }

    /** Parts already standing in range with no controller get adopted when one is placed. */
    private void adoptOrphans(Player player, String controllerKey, Location controllerLoc) {
        int radius = plugin.getConfig().getInt("robot.attach-radius", 4);
        int adopted = 0;
        for (Map.Entry<String, Placed> e : new LinkedHashMap<>(plugin.placements().partsOf("")).entrySet()) {
            Location loc = PartStore.fromKey(e.getKey());
            if (loc == null || !loc.getWorld().equals(controllerLoc.getWorld())) continue;
            if (loc.distance(controllerLoc) > radius) continue;
            Part part = plugin.parts().get(e.getValue().partId());
            if (part == null || part.isController()) continue;

            boolean ported = part.isSensor() || part.isActuator();
            String port = ported ? plugin.placements().nextPort(controllerKey, part.isSensor()) : "";
            plugin.placements().put(loc, e.getValue().withRobot(controllerKey, port));
            PartLabels.refresh(plugin, loc);
            adopted++;
        }
        if (adopted > 0) {
            player.sendMessage(Component.text(adopted + " רכיבים שהיו בסביבה התחברו לבקר.",
                    NamedTextColor.GREEN));
        }
    }

    /** Nearest controller within attach-radius, or null. */
    private String nearestController(Location loc) {
        int radius = plugin.getConfig().getInt("robot.attach-radius", 4);
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (String key : plugin.placements().controllers()) {
            Location c = PartStore.fromKey(key);
            if (c == null || !c.getWorld().equals(loc.getWorld())) continue;
            double d = c.distance(loc);
            if (d <= radius && d < bestDist) { bestDist = d; best = key; }
        }
        return best;
    }

    private void refreshAt(String key) {
        if (key == null || key.isEmpty()) return;
        Location loc = PartStore.fromKey(key);
        if (loc != null && loc.isChunkLoaded()) PartLabels.refresh(plugin, loc);
    }
}
