package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.program.Operand;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.program.Verb;
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
 * Editing the rule table by clicking.
 *
 * <p>One interaction model per cell, so there is nothing to memorise: token cells cycle
 * (left = next, right = previous), number cells step (left/right = 1, shift = 10), and the middle
 * click switches a number cell between a literal and a port. Everything is cancelled - this is a
 * control panel, not an inventory.
 */
public class MenuListener implements Listener {

    private static final int MAX_CONST = 255;

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
            controls(player, menu, robot, slot);
            return;
        }

        int index = slot / 9;
        int field = slot % 9;
        Rule rule = robot.program().get(index);
        if (rule == null) return;

        // An ALWAYS rule hides its comparison cells, and a verb with no argument hides its value
        // cell. Those slots hold fillers, so clicking one must do nothing rather than silently
        // edit a field the student cannot see.
        if (rule.always() && (field == 2 || field == 3)) return;
        if (field == 7 && !rule.verb().needsArg()) return;

        ClickType click = event.getClick();
        Rule updated = switch (field) {
            case 1 -> rule.withSource(cycle(ProgramMenu.sources(plugin, menu.robotKey()), rule.source(), click));
            case 2 -> rule.withOp(rule.op().next());
            case 3 -> rule.withRhs(editOperand(menu, rule.rhs(), click));
            case 5 -> retarget(menu, rule, click, player);
            case 6 -> rule.withVerb(rule.verb().next(isMemory(rule.target())));
            case 7 -> rule.verb().needsArg() ? rule.withArg(editOperand(menu, rule.arg(), click)) : rule;
            case 8 -> null;
            default -> rule;
        };

        if (field == 8) robot.program().remove(index);
        else if (updated != null) robot.program().set(index, updated);

        plugin.robots().save();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
        menu.render(plugin);
        player.updateInventory();
    }

    // ---------------------------------------------------------------- cells

    /** Changing the target may make the verb illegal, so normalise it in the same step. */
    private Rule retarget(ProgramMenu menu, Rule rule, ClickType click, Player player) {
        List<String> targets = ProgramMenu.targets(plugin, menu.robotKey());
        if (targets.isEmpty()) {
            player.sendMessage(Component.text(
                    "אין מפעילים מחוברים לבקר. הניחו נורה או שער בקרבת הבקר.", NamedTextColor.RED));
            return rule;
        }
        String target = cycle(targets, rule.target(), click);
        Rule out = rule.withTarget(target);
        if (isMemory(target) && !out.verb().isMemory())      out = out.withVerb(Verb.SET);
        else if (!isMemory(target) && out.verb().isMemory()) out = out.withVerb(Verb.ON);
        return out;
    }

    private Operand editOperand(ProgramMenu menu, Operand operand, ClickType click) {
        List<String> sources = ProgramMenu.valueSources(plugin, menu.robotKey());

        // Middle click switches the cell between "a number" and "another value".
        if (click == ClickType.MIDDLE || click == ClickType.DROP) {
            if (operand.constant()) return sources.isEmpty() ? operand : Operand.ref(sources.get(0));
            return Operand.of(0);
        }
        if (!operand.constant()) {
            return Operand.ref(cycle(sources, operand.source(), click));
        }
        int step = click.isShiftClick() ? 10 : 1;
        int value = operand.value() + (click.isRightClick() ? -step : step);
        return Operand.of(Math.max(0, Math.min(MAX_CONST, value)));
    }

    private String cycle(List<String> options, String current, ClickType click) {
        if (options.isEmpty()) return current;
        int i = options.indexOf(current);
        if (i < 0) return options.get(0);
        int next = click.isRightClick() ? (i - 1 + options.size()) % options.size()
                                       : (i + 1) % options.size();
        return options.get(next);
    }

    private boolean isMemory(String target) {
        return target != null && target.length() >= 2 && target.charAt(0) == 'M'
                && Character.isDigit(target.charAt(1));
    }

    // ------------------------------------------------------------- controls

    private void controls(Player player, ProgramMenu menu, Robot robot, int slot) {
        switch (slot) {
            case ProgramMenu.SLOT_ADD -> {
                int max = plugin.getConfig().getInt("program.max-rules", 5);
                if (robot.program().size() >= max) {
                    player.sendMessage(Component.text(
                            "הגעתם ל-" + max + " כללים. זה המקום לחשוב איך לעשות את זה בפחות.",
                            NamedTextColor.YELLOW));
                    return;
                }
                List<String> targets = ProgramMenu.targets(plugin, menu.robotKey());
                Rule blank = Rule.blank();
                if (!targets.isEmpty()) blank = blank.withTarget(targets.get(0));
                robot.program().add(blank);
                plugin.robots().save();
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
