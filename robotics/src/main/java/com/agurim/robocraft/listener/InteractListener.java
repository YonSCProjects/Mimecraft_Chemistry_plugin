package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartItems;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.robot.Robot;
import com.agurim.robocraft.ui.ProgramMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-clicking things: the controller opens the program, a board tile hands out a part, the
 * charger refills a battery, and any other part reports what it is reading.
 *
 * <p>Interactions with part blocks are always cancelled. Several of the blocks parts are built from
 * have vanilla behaviour that would otherwise fight us - a note block retunes, a trapdoor opens, a
 * daylight detector inverts - and a part must behave like a part, not like the block it borrows.
 */
public class InteractListener implements Listener {

    private final RoboCraftPlugin plugin;

    public InteractListener(RoboCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        Location loc = block.getLocation();

        Placed placed = plugin.placements().get(loc);
        if (placed != null) {
            Part part = plugin.parts().get(placed.partId());
            boolean isController = part != null && part.isController();

            // A controller behaves like a chest: right-click opens it whatever you are holding,
            // and you sneak to build against it. Requiring an empty hand instead was a mistake -
            // a student assembling a robot has parts in hand at exactly the moment the guide tells
            // them to click the controller, so the program would simply never open for them.
            //
            // Every other part stays out of the way of building: a block in hand means build, an
            // empty hand means "tell me what you are reading".
            if (player.isSneaking()) return;
            if (!isController && isPlacing(player)) return;

            event.setCancelled(true);
            onPart(player, loc, placed);
            return;
        }

        if (isCharger(block.getType())) {
            event.setCancelled(true);
            charge(player, loc);
            return;
        }

        int plot = plugin.plots().plotIndexAt(loc);
        if (plot < 0) return;
        Part tile = plugin.board().partAt(plot, loc);
        if (tile == null) return;

        event.setCancelled(true);
        if (plot != plugin.store().getOrAssignPlotIndex(player.getUniqueId())) {
            player.sendMessage(Component.text("זה הלוח של מישהו אחר.", NamedTextColor.GRAY));
            return;
        }
        if (!plugin.store().isUnlocked(player.getUniqueId(), tile.id())) {
            // Name the mission, and make it a click. "Complete the mission that unlocks it" sent
            // a student to a list they then had to search; the tile's own label names the
            // mission, and now so does the message, with the card one click away.
            com.agurim.robocraft.mission.Mission by = plugin.missions().registry().unlocking(tile.id());
            Component msg = Component.text(tile.name() + " עדיין נעול", NamedTextColor.GRAY);
            if (by != null) {
                msg = msg.append(Component.text(" - נפתח במשימה ", NamedTextColor.GRAY))
                        .append(com.agurim.robocraft.ui.MissionCard.openLink(
                                plugin.missions().registry(), by, NamedTextColor.YELLOW))
                        .append(Component.text("  "))
                        .append(com.agurim.robocraft.ui.MissionCard.openButton(by));
            } else {
                msg = msg.append(Component.text(" - השלימו את המשימה שפותחת אותו.", NamedTextColor.GRAY));
            }
            player.sendMessage(msg);
            return;
        }
        PartItems.give(plugin, player, tile, 1);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
        player.sendMessage(Component.text("לקחתם: " + tile.name(), NamedTextColor.GREEN));
    }

    private void onPart(Player player, Location loc, Placed placed) {
        Part part = plugin.parts().get(placed.partId());
        if (part == null) return;

        if (part.isController()) {
            Robot robot = plugin.robots().get(PartStore.key(loc));
            if (robot != null && robot.owner() != null && !robot.owner().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("זה הרובוט של מישהו אחר.", NamedTextColor.GRAY));
                return;
            }
            new ProgramMenu(PartStore.key(loc)).open(plugin, player);
            // The rows are unreadable without hovering each cell, so put the program in chat too,
            // where it renders over the open window.
            ProgramMenu.echoProgram(plugin, player, PartStore.key(loc));
            return;
        }

        if (!placed.attached()) {
            player.sendMessage(Component.text(part.name() + " לא מחובר לשום בקר.", NamedTextColor.RED));
            return;
        }
        Robot robot = plugin.robots().get(placed.robot());
        String value = "-";
        if (robot != null) {
            Integer v = part.isSensor() ? robot.inputs().get(placed.port())
                                        : robot.outputs().get(placed.port());
            if (v != null) value = part.isSensor() ? v.toString() : (v != 0 ? "ON" : "OFF");
        }
        player.sendMessage(Component.text(placed.port() + "  " + part.name() + " = " + value,
                NamedTextColor.AQUA));
    }

    private boolean isPlacing(Player player) {
        Material held = player.getInventory().getItemInMainHand().getType();
        return held.isBlock() && held != Material.AIR;
    }

    private boolean isCharger(Material mat) {
        Material charger = Material.matchMaterial(
                plugin.getConfig().getString("power.charger-block", "WAXED_CHISELED_COPPER"));
        return charger != null && charger == mat;
    }

    /**
     * Top up every robot the clicker owns within range. Charging is deliberately manual: an
     * energy budget you can refill without thinking is not a budget.
     *
     * <p>Saying why nothing happened matters more here than almost anywhere else. The pad sits at
     * one corner of the plot and the game lands a student eleven blocks away, in front of the
     * board, which is exactly where they then build - so with the old radius of 6 a robot built
     * where the game invited you to build could not reach its own charger. The robot simply
     * stopped, and the one object on the plot that looks like it should help did nothing at all
     * when clicked. Now it reports the distance and the reach, the same way a detached part does.
     */
    private void charge(Player player, Location loc) {
        int radius = plugin.getConfig().getInt("power.charger-radius", 14);
        int rate = plugin.getConfig().getInt("power.charger-rate", 60);
        int charged = 0;
        double nearestOutOfReach = -1;
        boolean ownRobotWithoutBattery = false;
        boolean midRun = false;

        for (String key : plugin.placements().controllers()) {
            Location c = PartStore.fromKey(key);
            if (c == null || c.getWorld() == null || !c.getWorld().equals(loc.getWorld())) continue;
            Robot robot = plugin.robots().get(key);
            // Owner first, then distance: to explain a miss we have to measure THEIR robots, and
            // the old order threw away the out-of-range ones before we knew whose they were.
            if (robot == null || robot.owner() == null || !robot.owner().equals(player.getUniqueId())) continue;

            double d = c.distance(loc);
            if (d > radius) {
                if (nearestOutOfReach < 0 || d < nearestOutOfReach) nearestOutOfReach = d;
                continue;
            }
            // Never mid-run: a budget mission starts the robot nearly flat on purpose, and one
            // click here would let an always-on lamp pass "still running".
            if (plugin.missions().isRunning(key)) { midRun = true; continue; }

            int capacity = 0;
            for (Placed p : plugin.placements().partsOf(key).values()) {
                Part part = plugin.parts().get(p.partId());
                if (part != null && part.isBattery()) capacity += part.capacity();
            }
            if (capacity <= 0) { ownRobotWithoutBattery = true; continue; }
            robot.energy(Math.min(capacity, robot.energy() + rate * 20));
            charged++;
        }
        plugin.robots().save();

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        if (charged == 0 && midRun) {
            player.sendMessage(Component.text("הרובוט באמצע הרצת ניסוי - הטעינה תחכה לסיום.",
                    NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(chargeResult(charged, nearestOutOfReach, ownRobotWithoutBattery, radius));
    }

    /**
     * What the pad says. Kept separate and Bukkit-free so the self-test can hold every branch to
     * account - a message nobody can act on is the failure mode this exists to prevent.
     */
    public static Component chargeResult(int charged, double nearestOutOfReach,
                                         boolean ownRobotWithoutBattery, int radius) {
        if (charged > 0) {
            return Component.text("נטענו " + charged + " רובוטים.", NamedTextColor.GOLD);
        }
        if (nearestOutOfReach >= 0) {
            return Component.text(String.format(
                    "הרובוט שלכם רחוק מדי: %.1f - הרציף מגיע עד %d. קרבו את הרובוט לרציף.",
                    nearestOutOfReach, radius), NamedTextColor.RED);
        }
        if (ownRobotWithoutBattery) {
            return Component.text("אין סוללה מחוברת לרובוט. הניחו סוללה ליד הבקר.",
                    NamedTextColor.RED);
        }
        return Component.text("אין לכם רובוט. הניחו בקר, סוללה, חיישן ומפעיל.", NamedTextColor.GRAY);
    }
}
