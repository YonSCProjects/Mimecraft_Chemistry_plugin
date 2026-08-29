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
            player.sendMessage(Component.text(tile.name() + " עדיין נעול - השלימו את המשימה שפותחת אותו.",
                    NamedTextColor.GRAY));
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

    /** Top up every robot the clicker owns within range. Charging is deliberately manual: an
     *  energy budget you can refill without thinking is not a budget. */
    private void charge(Player player, Location loc) {
        int radius = plugin.getConfig().getInt("power.charger-radius", 6);
        int rate = plugin.getConfig().getInt("power.charger-rate", 60);
        int charged = 0;

        for (String key : plugin.placements().controllers()) {
            Location c = PartStore.fromKey(key);
            if (c == null || !c.getWorld().equals(loc.getWorld()) || c.distance(loc) > radius) continue;
            Robot robot = plugin.robots().get(key);
            if (robot == null || robot.owner() == null || !robot.owner().equals(player.getUniqueId())) continue;

            int capacity = 0;
            for (Placed p : plugin.placements().partsOf(key).values()) {
                Part part = plugin.parts().get(p.partId());
                if (part != null && part.isBattery()) capacity += part.capacity();
            }
            if (capacity <= 0) continue;
            robot.energy(Math.min(capacity, robot.energy() + rate * 20));
            charged++;
        }
        plugin.robots().save();

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        player.sendMessage(charged > 0
                ? Component.text("נטענו " + charged + " רובוטים.", NamedTextColor.GOLD)
                : Component.text("אין רובוט שלכם עם סוללה בטווח.", NamedTextColor.GRAY));
    }
}
