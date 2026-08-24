package com.agurim.robocraft.command;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.StarterKit;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartItems;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.robot.Robot;
import com.agurim.robocraft.ui.Guide;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Admin and student commands. Everything a student needs is also reachable in the world. */
public class RoboCraftCommand implements CommandExecutor, TabCompleter {

    private final RoboCraftPlugin plugin;

    public RoboCraftCommand(RoboCraftPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = (args.length == 0) ? "guide" : args[0].toLowerCase();

        switch (sub) {
            case "guide"    -> Guide.send(plugin, player);
            case "kit"      -> StarterKit.give(plugin, player);
            case "tp"       -> plugin.plots().teleportToPlot(player,
                                    plugin.store().getOrAssignPlotIndex(player.getUniqueId()));
            case "board"    -> rebuildBoard(player);
            case "missions" -> listMissions(player);
            case "mission"  -> runMission(player, args);
            case "run"      -> startRobot(player);
            case "stop"     -> stopRobot(player);
            case "charge"   -> chargeRobot(player);
            case "give"     -> give(player, args);
            case "unlock"   -> unlock(player, args);
            case "reset"    -> reset(player);
            case "reload"   -> reload(player);
            default         -> player.sendMessage(Component.text(
                                    "/rc guide | kit | tp | board | missions | mission <id> | run | stop | charge",
                                    NamedTextColor.GRAY));
        }
        return true;
    }

    // ------------------------------------------------------------- students

    private void listMissions(Player player) {
        player.sendMessage(Component.text("==== משימות ====", NamedTextColor.AQUA));
        for (Mission m : plugin.missions().registry().all()) {
            boolean done = plugin.store().isMissionDone(player.getUniqueId(), m.id());
            player.sendMessage(Component.text(
                    (done ? "✔ " : "· ") + m.name() + " - " + m.brief(),
                    done ? NamedTextColor.GREEN : NamedTextColor.WHITE));
            if (!done) {
                player.sendMessage(Component.text("   מלמד: " + m.teaches() + "   |   /rc mission " + m.id(),
                        NamedTextColor.GRAY));
            }
        }
    }

    private void runMission(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/rc mission <id> - הרשימה: /rc missions", NamedTextColor.GRAY));
            return;
        }
        Mission mission = plugin.missions().registry().byId(args[1]);
        if (mission == null) {
            player.sendMessage(Component.text("אין משימה כזו.", NamedTextColor.RED));
            return;
        }
        Robot robot = myRobot(player);
        if (robot == null) return;

        String why = plugin.missions().start(player, robot, mission);
        if (why != null) player.sendMessage(Component.text(why, NamedTextColor.RED));
    }

    private void startRobot(Player player) {
        Robot robot = myRobot(player);
        if (robot == null) return;
        String why = plugin.engine().whyNotReady(robot);
        if (why != null) {
            player.sendMessage(Component.text("אי אפשר להפעיל: " + why, NamedTextColor.RED));
            return;
        }
        robot.start(plugin.engine().now());
        player.sendMessage(Component.text("הרובוט פועל.", NamedTextColor.GREEN));
    }

    private void stopRobot(Player player) {
        Robot robot = myRobot(player);
        if (robot == null) return;
        robot.stop("עצור");
        plugin.missions().abort(robot.key());
        player.sendMessage(Component.text("הרובוט נעצר.", NamedTextColor.YELLOW));
    }

    private void chargeRobot(Player player) {
        Robot robot = myRobot(player);
        if (robot == null) return;
        int capacity = plugin.batteryCapacity(robot.key());
        if (capacity <= 0) {
            player.sendMessage(Component.text("אין סוללה מחוברת לרובוט.", NamedTextColor.RED));
            return;
        }
        robot.energy(capacity);
        plugin.robots().save();
        player.sendMessage(Component.text("הסוללה מלאה: " + capacity, NamedTextColor.GOLD));
    }

    private void rebuildBoard(Player player) {
        int plot = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
        plugin.board().build(plot, player.getUniqueId());
        plugin.kiosk().build(plot);
        player.sendMessage(Component.text("לוח הרכיבים נבנה מחדש.", NamedTextColor.GREEN));
    }

    /** The player's robot: the only one they own, or the nearest if they have several. */
    private Robot myRobot(Player player) {
        List<Robot> mine = new ArrayList<>();
        for (String key : plugin.placements().controllers()) {
            Robot r = plugin.robots().get(key);
            if (r != null && player.getUniqueId().equals(r.owner())) mine.add(r);
        }
        if (mine.isEmpty()) {
            player.sendMessage(Component.text("אין לכם בקר. הניחו אחד כדי להתחיל.", NamedTextColor.RED));
            return null;
        }
        Robot best = null;
        double bestDist = Double.MAX_VALUE;
        for (Robot r : mine) {
            Location loc = PartStore.fromKey(r.key());
            if (loc == null || !loc.getWorld().equals(player.getWorld())) continue;
            double d = loc.distance(player.getLocation());
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return (best != null) ? best : mine.get(0);
    }

    // ---------------------------------------------------------------- admin

    private void give(Player player, String[] args) {
        if (!player.hasPermission("robocraft.admin")) { denied(player); return; }
        if (args.length < 2) { player.sendMessage(Component.text("/rc give <part> [n]")); return; }
        Part part = plugin.parts().get(args[1]);
        if (part == null) {
            player.sendMessage(Component.text("אין רכיב כזה: " + args[1], NamedTextColor.RED));
            return;
        }
        int n = (args.length >= 3) ? parseInt(args[2], 1) : 1;
        PartItems.give(plugin, player, part, n);
    }

    private void unlock(Player player, String[] args) {
        if (!player.hasPermission("robocraft.admin")) { denied(player); return; }
        if (args.length < 2) { player.sendMessage(Component.text("/rc unlock <part|all>")); return; }
        if ("all".equalsIgnoreCase(args[1])) {
            for (Part p : plugin.parts().all()) plugin.store().unlock(player.getUniqueId(), p.id());
        } else if (plugin.parts().has(args[1])) {
            plugin.store().unlock(player.getUniqueId(), args[1]);
        } else {
            player.sendMessage(Component.text("אין רכיב כזה.", NamedTextColor.RED));
            return;
        }
        rebuildBoard(player);
    }

    private void reset(Player player) {
        if (!player.hasPermission("robocraft.admin")) { denied(player); return; }
        plugin.store().reset(player.getUniqueId());
        rebuildBoard(player);
        plugin.statusBar().update(player);
        player.sendMessage(Component.text("ההתקדמות אופסה.", NamedTextColor.YELLOW));
    }

    private void reload(Player player) {
        if (!player.hasPermission("robocraft.admin")) { denied(player); return; }
        plugin.reloadConfig();
        player.sendMessage(Component.text("config.yml נטען מחדש. תוכן (parts/missions) דורש הפעלה מחדש.",
                NamedTextColor.YELLOW));
    }

    private void denied(Player player) {
        player.sendMessage(Component.text("אין לכם הרשאה לפקודה הזו.", NamedTextColor.RED));
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("guide", "kit", "tp", "board", "missions", "mission",
                                    "run", "stop", "charge", "give", "unlock", "reset", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mission")) {
            for (Mission m : plugin.missions().registry().all()) {
                if (m.id().startsWith(args[1])) out.add(m.id());
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock"))) {
            for (Part p : plugin.parts().all()) {
                if (p.id().startsWith(args[1])) out.add(p.id());
            }
        }
        return out;
    }
}
