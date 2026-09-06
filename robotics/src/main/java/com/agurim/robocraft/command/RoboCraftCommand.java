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
        // Two things the console may run. selftest is what you want BEFORE a lesson, on a server
        // nobody has joined yet; progress is what you want DURING one, and the console is where
        // its fixed-width columns actually line up.
        if (args.length > 0 && args[0].equalsIgnoreCase("selftest")) {
            selfTest(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("progress")) {
            if (sender instanceof Player player && !player.hasPermission("robocraft.admin")) {
                denied(player);
            } else {
                com.agurim.robocraft.ui.ProgressReport.send(plugin, sender);
            }
            return true;
        }
        // whisper is the delivery channel for the external assistant agent, so it must work from
        // the console: on 26.2 tell/tellraw/msg execute silently over RCON and deliver nothing.
        if (args.length >= 3 && args[0].equalsIgnoreCase("whisper")) {
            sender.sendMessage(whisper(args) ? "sent" : "player not online: " + args[1]);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("questions")) {
            if (sender instanceof Player player && !player.hasPermission("robocraft.admin")) {
                denied(player);
            } else {
                questions(sender, args);
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = (args.length == 0) ? "guide" : args[0].toLowerCase();

        switch (sub) {
            case "guide"    -> Guide.send(plugin, player);
            case "kit"      -> StarterKit.give(plugin, player);
            case "tp"       -> player.teleport(plugin.board().arrivalSpot(
                                    plugin.store().getOrAssignPlotIndex(player.getUniqueId())));
            case "board"    -> rebuildBoard(player);
            case "missions" -> listMissions(player);
            case "mission"  -> runMission(player, args);
            case "run"      -> startRobot(player);
            case "stop"     -> stopRobot(player);
            case "charge"   -> chargeRobot(player);
            case "trace"    -> traceRobot(player);
            case "ask"      -> askQuestion(player, args);
            case "give"     -> give(player, args);
            case "unlock"   -> unlock(player, args);
            case "reset"    -> reset(player);
            case "reload"   -> reload(player);
            default         -> player.sendMessage(Component.text(
                                    "/rc guide | kit | tp | board | missions | mission <id> | run | stop | trace | charge | ask <שאלה>",
                                    NamedTextColor.GRAY));
        }
        return true;
    }

    // ------------------------------------------------------------- students

    /** The required ladder first, warm-ups after - and the warm-ups say plainly that they are optional. */
    private void listMissions(Player player) {
        player.sendMessage(Component.text("==== משימות ====", NamedTextColor.AQUA));
        for (Mission m : plugin.missions().registry().required()) line(player, m);

        List<Mission> warmUps = plugin.missions().registry().warmUps();
        if (!warmUps.isEmpty()) {
            player.sendMessage(Component.text("---- חימום (לא חובה) ----", NamedTextColor.DARK_AQUA));
            player.sendMessage(Component.text(
                    "דברים שאפשר לבנות גם באבן אדומה. הם כאן כדי להתרגל לכלים.", NamedTextColor.GRAY));
            for (Mission m : warmUps) line(player, m);
        }
    }

    private void line(Player player, Mission m) {
        boolean done = plugin.store().isMissionDone(player.getUniqueId(), m.id());
        player.sendMessage(Component.text(
                (done ? "✔ " : "· ") + m.name() + " - " + m.brief(),
                done ? NamedTextColor.GREEN : NamedTextColor.WHITE));
        if (!done) {
            player.sendMessage(Component.text("   מלמד: " + m.teaches() + "   |   /rc mission " + m.id(),
                    NamedTextColor.GRAY));
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

    /** Print the whole state of the loop - the answer to "why did it do that?". */
    private void traceRobot(Player player) {
        Robot robot = myRobot(player);
        if (robot == null) return;
        com.agurim.robocraft.ui.Trace.send(plugin, player, robot);
    }

    /**
     * Rebuild everything the plugin puts on a plot.
     *
     * <p>The trophy shelf belongs here too, not only in {@code JoinListener}: the join path builds
     * it once behind the {@code board-built} flag, so without this a student whose shelf was broken
     * or lost with a world reset would have no way at all to get it back.
     */
    private void rebuildBoard(Player player) {
        int plot = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
        plugin.board().build(plot, player.getUniqueId());
        plugin.trophies().build(plot, player.getUniqueId());
        plugin.kiosk().build(plot);
        player.sendMessage(Component.text("לוח הרכיבים והמדף נבנו מחדש.", NamedTextColor.GREEN));
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

    // ------------------------------------------------------------- assistant

    /**
     * Capture a question with the state needed to answer it. The plugin never answers - see
     * AskService for why the agent lives outside it.
     */
    private void askQuestion(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "שימוש: /rc ask <שאלה>   -   למשל: /rc ask למה הנורה לא נדלקת?", NamedTextColor.RED));
            return;
        }
        long wait = plugin.ask().cooldownRemaining(player.getUniqueId());
        if (wait > 0) {
            player.sendMessage(Component.text("רגע אחד - אפשר לשאול שוב בעוד " + wait + " שניות.",
                    NamedTextColor.GRAY));
            return;
        }
        String question = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (plugin.ask().ask(player, question)) {
            player.sendMessage(Component.text("השאלה נשלחה ל"
                    + plugin.getConfig().getString("assistant.name", "עוזר/ת סדנה")
                    + ". התשובה תגיע לצ'אט.", NamedTextColor.LIGHT_PURPLE));
        } else {
            player.sendMessage(Component.text("לא הצלחתי לשמור את השאלה. קראו למורה.", NamedTextColor.RED));
        }
    }

    /**
     * /rc whisper &lt;player&gt; [chat|actionbar|title] &lt;text...&gt;
     *
     * <p>The channel argument is optional; anything unrecognised is treated as the start of the
     * message, so an agent can just say what it means without knowing the channel names.
     */
    private boolean whisper(String[] args) {
        Player target = com.agurim.robocraft.ui.Whisper.find(args[1]);
        if (target == null) return false;

        String maybeChannel = args[2].toLowerCase();
        boolean named = maybeChannel.equals("chat") || maybeChannel.equals("actionbar")
                || maybeChannel.equals("title");
        String channel = named ? maybeChannel : "chat";
        int from = named ? 3 : 2;
        if (from >= args.length) return false;

        String text = String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
        return com.agurim.robocraft.ui.Whisper.send(plugin, target, channel, text);
    }

    /** What the class is stuck on - a teaching record even when no assistant is running. */
    private void questions(CommandSender sender, String[] args) {
        int n = (args.length >= 2) ? parseInt(args[1], 10) : 10;
        List<String> lines = plugin.ask().recent(n);
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("עוד לא נשאלו שאלות.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("==== " + lines.size() + " שאלות אחרונות ====",
                NamedTextColor.AQUA));
        for (String line : lines) sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------- diagnostic

    /**
     * Build a real robot, drive a known input through it, check a real block moved, put it back.
     *
     * <p>A player runs it next to themselves; the console runs it well away from spawn, so it can
     * be driven by a startup script before anyone joins.
     */
    private void selfTest(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("robocraft.admin")) {
            denied(player);
            return;
        }
        Location origin;
        if (sender instanceof Player player) {
            origin = player.getLocation().getBlock().getLocation().add(2, 0, 0);
        } else {
            org.bukkit.World world = plugin.plots().world();
            Location spawn = world.getSpawnLocation();
            int x = spawn.getBlockX() + 64;
            int z = spawn.getBlockZ() + 64;
            origin = new Location(world, x, world.getHighestBlockYAt(x, z) + 1, z);
        }
        sender.sendMessage(Component.text("selftest @ " + origin.getWorld().getName() + " "
                + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ(),
                NamedTextColor.GRAY));
        com.agurim.robocraft.diag.SelfTest.report(sender,
                com.agurim.robocraft.diag.SelfTest.run(plugin, sender, origin));
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
                                    "run", "stop", "trace", "charge", "ask", "give", "unlock",
                                    "reset", "reload", "selftest", "progress", "questions",
                                    "whisper")) {
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
