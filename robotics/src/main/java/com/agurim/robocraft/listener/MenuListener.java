package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.program.RuleEdit;
import com.agurim.robocraft.robot.Robot;
import com.agurim.robocraft.ui.ProgramMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

/**
 * Editing the rule table by clicking: event plumbing and the messages.
 *
 * <p>What a click means lives in {@link RuleEdit}, which is Bukkit-free and therefore testable.
 *
 * <p>One interaction model per cell, so there is nothing to memorise: token cells cycle
 * (left = next, right = previous), number cells step (left/right = 1, shift = 10), and the middle
 * click swaps a number cell for a port. Everything is cancelled - this is a control panel, not an
 * inventory, and nothing in it may be picked up.
 */
public class MenuListener implements Listener {

    private final RoboCraftPlugin plugin;

    public MenuListener(RoboCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ProgramMenu) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ProgramMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        Robot robot = plugin.robots().get(menu.robotKey());
        if (robot == null) return;

        if (slot >= 45) {
            controls(player, menu, robot, slot, event.getClick().isRightClick());
            return;
        }

        int index = slot / 9;
        int field = slot % 9;
        Rule rule = robot.program().get(index);
        if (rule == null) return;

        if (field == RuleEdit.DELETE) {
            robot.program().remove(index);
        } else {
            if (!RuleEdit.editable(rule, field)) return;
            List<String> targets = ProgramMenu.targets(plugin, menu.robotKey());
            if (field == RuleEdit.TARGET && targets.isEmpty()) {
                player.sendMessage(Component.text(
                        "אין מפעילים מחוברים לבקר. הניחו נורה או שער בקרבת הבקר.", NamedTextColor.RED));
                return;
            }
            robot.program().set(index, RuleEdit.apply(rule, field, click(event.getClick()),
                    ProgramMenu.sources(plugin, menu.robotKey()),
                    targets,
                    ProgramMenu.valueSources(plugin, menu.robotKey())));
        }

        plugin.robots().save();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
        menu.render(plugin);
        player.updateInventory();

        // The cells only say what they mean on hover, so echo the sentence where it can be read
        // without one. Deleting changes the numbering of every rule below, so that reprints the lot.
        if (field == RuleEdit.DELETE) {
            ProgramMenu.echoProgram(plugin, player, menu.robotKey());
        } else {
            Rule edited = robot.program().get(index);
            if (edited != null) ProgramMenu.echoRule(player, index, edited);
        }
    }

    /** Reduce a Bukkit click to the three things that change what it means. */
    private RuleEdit.Click click(ClickType type) {
        boolean alternate = type == ClickType.MIDDLE || type == ClickType.DROP;
        return new RuleEdit.Click(type.isRightClick(), type.isShiftClick(), alternate);
    }

    // ------------------------------------------------------------- controls

    private void controls(Player player, ProgramMenu menu, Robot robot, int slot, boolean right) {
        switch (slot) {
            case ProgramMenu.SLOT_MISSION -> {
                // Chat is drawn behind an open chest GUI, so close first or nothing is seen.
                player.closeInventory();
                com.agurim.robocraft.mission.Mission mission = plugin.missions().focusOrNext(player.getUniqueId());
                if (mission == null) {
                    player.sendMessage(Component.text("כל המשימות הושלמו! בנו מנגנון משלכם.", NamedTextColor.GREEN));
                    return;
                }
                if (right) {
                    com.agurim.robocraft.ui.MissionCard.send(plugin, player, mission);
                    return;
                }
                String why = plugin.missions().start(player, robot, mission);
                if (why != null) {
                    player.sendMessage(Component.text(why, NamedTextColor.RED));
                    com.agurim.robocraft.ui.MissionCard.send(plugin, player, mission);
                }
                return;
            }
            case ProgramMenu.SLOT_ADD -> {
                int max = plugin.getConfig().getInt("program.max-rules", 5);
                if (robot.program().size() >= max) {
                    player.sendMessage(Component.text(
                            "הגעתם ל-" + max + " כללים. זה המקום לחשוב איך לעשות את זה בפחות.",
                            NamedTextColor.YELLOW));
                    return;
                }
                robot.program().add(RuleEdit.starter(
                        ProgramMenu.sources(plugin, menu.robotKey()),
                        ProgramMenu.targets(plugin, menu.robotKey())));
                plugin.robots().save();
                ProgramMenu.echoProgram(plugin, player, menu.robotKey());
            }
            case ProgramMenu.SLOT_RUN -> {
                String why = plugin.engine().whyNotReady(robot);
                if (why != null) {
                    player.sendMessage(Component.text("אי אפשר להפעיל: " + why, NamedTextColor.RED));
                    return;
                }
                robot.start(plugin.engine().now());
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
                player.sendMessage(Component.text("הרובוט פועל.", NamedTextColor.GREEN));
            }
            case ProgramMenu.SLOT_STOP -> {
                robot.stop("עצור");
                plugin.missions().abort(robot.key());
                player.sendMessage(Component.text(
                        "הרובוט נעצר. הפלטים נשארו במצבם - עצירה היא לא איפוס.", NamedTextColor.YELLOW));
            }
            case ProgramMenu.SLOT_HELP -> {
                player.closeInventory();
                com.agurim.robocraft.ui.Guide.send(plugin, player);
                return;
            }
            default -> { return; }
        }
        menu.render(plugin);
        player.updateInventory();
    }
}
