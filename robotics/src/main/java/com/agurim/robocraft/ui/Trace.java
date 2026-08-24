package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.program.Evaluator;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Why did it do that?" - the question every student asks, and the one a wall of redstone can
 * never answer.
 *
 * <p>Prints the whole state of the loop at this instant: what each sensor is reading, what is in
 * memory, which rules match right now, <em>which rule actually decided each output</em>, and where
 * the outputs ended up. The last of those is the important one - it makes "later rules win"
 * something a student can see happening rather than a sentence in a guide they skipped.
 */
public final class Trace {

    private Trace() {}

    public static void send(RoboCraftPlugin plugin, CommandSender sender, Robot robot) {
        int seconds = robot.seconds(plugin.engine().now());
        Map<String, Placed> parts = plugin.placements().partsOf(robot.key());

        sender.sendMessage(Component.text("==== מצב הרובוט ====", NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
                (robot.running() ? "פועל" : (robot.halt() != null ? robot.halt() : "עצור"))
                        + "   ⚡ " + robot.energy()
                        + (robot.running() ? "   זמן " + seconds + "s" : ""),
                robot.running() ? NamedTextColor.GREEN : NamedTextColor.GRAY));

        // ---- sensors: the numbers the program is actually looking at ----
        if (robot.inputs().isEmpty()) {
            sender.sendMessage(Component.text("חיישנים: אין", NamedTextColor.DARK_GRAY));
        } else {
            robot.inputs().forEach((port, value) -> sender.sendMessage(
                    Component.text("  " + port + " = " + value, NamedTextColor.AQUA)
                            .append(Component.text("   " + nameOf(plugin, parts, port), NamedTextColor.DARK_GRAY))));
        }

        // ---- memory ----
        StringBuilder mem = new StringBuilder();
        for (int i = 0; i < robot.memory().length; i++) {
            if (i > 0) mem.append("   ");
            mem.append("M").append(i + 1).append(" = ").append(robot.mem(i));
        }
        sender.sendMessage(Component.text("זיכרון:  " + mem, NamedTextColor.GRAY));

        // ---- rules: which match, and which one won its target ----
        List<Rule> rules = robot.program().rules();
        if (rules.isEmpty()) {
            sender.sendMessage(Component.text("אין כללים. לחצו על הבקר.", NamedTextColor.RED));
            return;
        }

        // Later rules win, so the last matching rule for a target is the one that decided it.
        Map<String, Integer> decidedBy = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.target().isEmpty()) continue;
            if (Evaluator.matches(rule, robot.inputs(), robot.memory(), seconds)) {
                decidedBy.put(rule.target(), i);
            }
        }

        sender.sendMessage(Component.text("כללים:", NamedTextColor.AQUA));
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            boolean match = !rule.target().isEmpty()
                    && Evaluator.matches(rule, robot.inputs(), robot.memory(), seconds);
            boolean won = match && Integer.valueOf(i).equals(decidedBy.get(rule.target()));

            String mark = won ? " ← זה שקבע" : (match ? "  (מתקיים)" : "");
            NamedTextColor colour = won ? NamedTextColor.GREEN
                    : match ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY;
            sender.sendMessage(Component.text("  " + (i + 1) + ". " + rule.text() + mark, colour));
        }

        // A rule that matches but did not decide is the single most confusing thing in the model,
        // so name it explicitly rather than leaving them to infer it from the colours.
        boolean overridden = false;
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.target().isEmpty()) continue;
            if (Evaluator.matches(rule, robot.inputs(), robot.memory(), seconds)
                    && !Integer.valueOf(i).equals(decidedBy.get(rule.target()))) {
                overridden = true;
                break;
            }
        }
        if (overridden) {
            sender.sendMessage(Component.text(
                    "שימו לב: יותר מכלל אחד מתקיים על אותו פלט - הכלל המאוחר גובר.",
                    NamedTextColor.GOLD));
        }

        // ---- outputs ----
        if (robot.outputs().isEmpty()) {
            sender.sendMessage(Component.text("מפעילים: אין", NamedTextColor.DARK_GRAY));
        } else {
            StringBuilder out = new StringBuilder();
            robot.outputs().forEach((port, value) -> {
                if (out.length() > 0) out.append("   ");
                out.append(port).append(" = ").append(isDisplay(plugin, parts, port)
                        ? String.valueOf(value) : (value != 0 ? "ON" : "OFF"));
            });
            sender.sendMessage(Component.text("מפעילים: " + out, NamedTextColor.GOLD));
        }
    }

    private static String nameOf(RoboCraftPlugin plugin, Map<String, Placed> parts, String port) {
        for (Placed p : parts.values()) {
            if (port.equals(p.port())) {
                Part part = plugin.parts().get(p.partId());
                if (part != null) return part.name();
            }
        }
        return "";
    }

    private static boolean isDisplay(RoboCraftPlugin plugin, Map<String, Placed> parts, String port) {
        for (Placed p : parts.values()) {
            if (port.equals(p.port())) {
                Part part = plugin.parts().get(p.partId());
                return part != null && "display".equals(part.actuator());
            }
        }
        return false;
    }
}
