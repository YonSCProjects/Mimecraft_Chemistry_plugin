package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Attachment;
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

/**
 * Placing and breaking parts: event plumbing and the messages, nothing else.
 *
 * <p>The decisions live in {@link Attachment}, which is reachable by the self-test; this class
 * cannot be, because it needs a real player holding a real item.
 *
 * <p>HIGH + ignoreCancelled so plot protection (NORMAL) has already had its say - the same
 * ordering rule ChemCraft uses for anything that must run after protection.
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

        Attachment.Result result = Attachment.place(plugin, loc, part, player.getFacing());
        PartLabels.refresh(plugin, loc);

        switch (result.outcome()) {
            case IS_CONTROLLER -> {
                Robot robot = plugin.robots().getOrCreate(result.controllerKey(), player.getUniqueId());
                robot.owner(player.getUniqueId());
                plugin.robots().save();
                player.sendMessage(Component.text("בקר הונח. לחיצה ימנית עליו פותחת את התוכנית.",
                        NamedTextColor.GREEN));

                java.util.Set<String> adopted = Attachment.adoptOrphans(plugin, result.controllerKey(), loc);
                adopted.forEach(this::refreshAt);
                if (!adopted.isEmpty()) {
                    player.sendMessage(Component.text(
                            adopted.size() + " רכיבים שהיו בסביבה התחברו לבקר.", NamedTextColor.GREEN));
                }
                PartLabels.refresh(plugin, loc);
            }
            case NO_CONTROLLER -> player.sendMessage(Component.text("אין בקר בטווח "
                    + plugin.getConfig().getInt("robot.attach-radius", 4)
                    + " בלוקים - הרכיב לא מחובר.", NamedTextColor.RED));
            case FULL -> player.sendMessage(Component.text("הבקר הזה מלא ("
                    + plugin.getConfig().getInt("robot.max-parts", 16) + " רכיבים).", NamedTextColor.RED));
            case ATTACHED -> {
                // A freshly attached battery arrives charged. Making a student hunt for the
                // charging pad before their first robot can move would be a puzzle about menus,
                // not about robotics.
                if (part.isBattery()) {
                    Robot robot = plugin.robots().get(result.controllerKey());
                    if (robot != null) {
                        robot.energy(plugin.batteryCapacity(result.controllerKey()));
                        plugin.robots().save();
                    }
                }
                refreshAt(result.controllerKey());
                player.sendMessage(result.port().isEmpty()
                        ? Component.text(part.name() + " חובר לבקר.", NamedTextColor.GREEN)
                        : Component.text(part.name() + " חובר לבקר בשם " + result.port()
                            + " - זה השם שמשתמשים בו בתוכנית.", NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Placed placed = plugin.placements().get(loc);
        if (placed == null) return;
        Part part = plugin.parts().get(placed.partId());
        if (part == null) return;

        PartLabels.remove(plugin, loc, part.id());
        Attachment.remove(plugin, loc, part).forEach(this::refreshAt);

        if (part.isController()) {
            event.getPlayer().sendMessage(Component.text(
                    "הבקר הוסר. הרכיבים נשארו במקומם אבל אינם מחוברים.", NamedTextColor.YELLOW));
        }

        // Hand the part back rather than the vanilla block, so a build can be taken apart safely.
        event.setDropItems(false);
        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), PartItems.of(plugin, part, 1));
    }

    private void refreshAt(String key) {
        if (key == null || key.isEmpty()) return;
        Location loc = PartStore.fromKey(key);
        if (loc != null && loc.isChunkLoaded()) PartLabels.refresh(plugin, loc);
    }
}
