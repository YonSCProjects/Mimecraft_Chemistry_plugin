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
        // The teacher's hand on the room. From the console too: a teacher's laptop may be
        // driving the server over RCON while the teacher is not in the game at all.
        if (args.length > 0 && (args[0].equalsIgnoreCase("pause")
                || args[0].equalsIgnoreCase("resume") || args[0].equalsIgnoreCase("say"))) {
            if (sender instanceof Player player && !player.hasPermission("robocraft.admin")) {
                denied(player);
            } else {
                classroom(sender, args);
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
            case "missions" -> listMissions(player, args);
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
    /**
     * The mission list.
     *
     * <p>{@code /rc missions} and {@code /rc mission} differ by one letter and do entirely
     * different things - one lists, one runs a bench test. The first person to use the list read
     * it, thought "mission #1", and typed {@code /rc missions #1}; the argument was silently
     * dropped, nothing ran, and they believed they had completed it until the trophy stayed grey.
     * So an argument here is never ignored: it is what they meant to run.
     */
    private void listMissions(Player player, String[] args) {
        if (args.length >= 2) { runMission(player, args); return; }

        player.sendMessage(Component.text("==== משימות ====", NamedTextColor.AQUA));
        List<Mission> required = plugin.missions().registry().required();
        for (int i = 0; i < required.size(); i++) line(player, required.get(i), String.valueOf(i + 1));

        List<Mission> warmUps = plugin.missions().registry().warmUps();
        if (!warmUps.isEmpty()) {
            player.sendMessage(Component.text("---- חימום (לא חובה) ----", NamedTextColor.DARK_AQUA));
            player.sendMessage(Component.text(
                    "דברים שאפשר לבנות גם באבן אדומה. הם כאן כדי להתרגל לכלים.", NamedTextColor.GRAY));
            for (int i = 0; i < warmUps.size(); i++) line(player, warmUps.get(i), WARM_UP_PREFIX + (i + 1));
        }

        List<Mission> bonus = plugin.missions().registry().bonus();
        if (!bonus.isEmpty()) {
            boolean ladderDone = plugin.missions().registry().requiredDone(
                    plugin.store().completedMissions(player.getUniqueId()))
                    >= plugin.missions().registry().requiredCount();
            player.sendMessage(Component.text("---- בונוס (אחרי הסולם) ----", NamedTextColor.DARK_GREEN));
            player.sendMessage(Component.text(ladderDone
                    ? "עבודות לרכיבים שהסולם פתח. אין פרס - רק מה שהרובוט עושה."
                    : "נפתחות אחרי שהסולם גמור. אפשר לנסות כבר עכשיו, אבל הרכיבים עוד נעולים.",
                    NamedTextColor.GRAY));
            for (int i = 0; i < bonus.size(); i++) line(player, bonus.get(i), BONUS_PREFIX + (i + 1));
        }
    }

    /**
     * Warm-ups are numbered W1..Wn and bonus jobs B1..Bn, not carried on from the required ladder.
     *
     * <p>The first draft numbered the required five 1-5 and then continued 6-8 into the warm-ups,
     * which meant night_light was "6" in the list while the status bar called the very same
     * mission "חימום 1/3" - and it is {@code order: 1} in missions.yml on top of that. Three names
     * for one thing. The bar counts three separate tracks, so the list counts the same three.
     */
    private static final String WARM_UP_PREFIX = "W";
    private static final String BONUS_PREFIX   = "B";

    private void line(Player player, Mission m, String label) {
        boolean done = plugin.store().isMissionDone(player.getUniqueId(), m.id());
        player.sendMessage(Component.text(
                (done ? "✔ " : label + ". ") + m.name() + " - " + m.brief(),
                done ? NamedTextColor.GREEN : NamedTextColor.WHITE));
        if (!done) {
            // The command that RUNS it, spelled out next to the label they can see.
            player.sendMessage(Component.text("   /rc mission " + label + "   (" + m.id() + ")",
                    NamedTextColor.GRAY));
        }
    }

    /**
     * Run a mission, by list number or by id.
     *
     * <p>Numbers because that is what a student reads off the list and what they try first. Ids
     * because they are stable and are what the docs and the mission's own messages quote.
     */
    private void runMission(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/rc mission <מספר> - הרשימה: /rc missions",
                    NamedTextColor.GRAY));
            return;
        }
        Mission mission = resolveMission(args[1]);
        if (mission == null) {
            player.sendMessage(Component.text("אין משימה כזו: " + args[1]
                    + " - הרשימה: /rc missions", NamedTextColor.RED));
            return;
        }
        Robot robot = myRobot(player);
        if (robot == null) return;

        String why = plugin.missions().start(player, robot, mission);
        if (why != null) player.sendMessage(Component.text(why, NamedTextColor.RED));
    }

    /**
     * A mission from whatever the student typed: "3", "#3", "W1", "ח1", "B2", "ב2", or an id.
     *
     * <p>The "#" is stripped rather than rejected because that is literally what the first person
     * typed, copying the numbering off the list. Both the Latin letter and the Hebrew one are
     * accepted for each track: the list prints W/B so the token is typeable on any keyboard, but a
     * student reading "חימום 1/3" or "בונוס 2/7" off the status bar will reasonably reach for the
     * Hebrew letter, and their keyboard is already in Hebrew.
     */
    private Mission resolveMission(String token) {
        var r = plugin.missions().registry();
        return resolve(token, r.required(), r.warmUps(), r.bonus());
    }

    /** Bukkit-free so the self-test can drive every form a student might type. */
    public static Mission resolve(String token, List<Mission> required, List<Mission> warmUps,
                                  List<Mission> bonus) {
        if (token == null || token.isEmpty()) return null;
        String t = token.startsWith("#") ? token.substring(1) : token;
        if (t.isEmpty()) return null;

        for (Mission m : required) if (m.id().equals(t)) return m;
        for (Mission m : warmUps)  if (m.id().equals(t)) return m;
        for (Mission m : bonus)    if (m.id().equals(t)) return m;

        char c = t.charAt(0);
        boolean warmUp = t.length() > 1 && (c == 'W' || c == 'w' || c == 'ח');
        boolean extra  = t.length() > 1 && (c == 'B' || c == 'b' || c == 'ב');
        List<Mission> track = warmUp ? warmUps : extra ? bonus : required;
        String digits = (warmUp || extra) ? t.substring(1) : t;

        try {
            int n = Integer.parseInt(digits);
            if (n >= 1 && n <= track.size()) return track.get(n - 1);
        } catch (NumberFormatException ignored) {
            // not a number; fall through to "no such mission"
        }
        return null;
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
        // A budget mission starts the robot nearly flat on purpose. A refill mid-run would let an
        // always-on lamp pass "still running", which is the one thing the rung exists to catch.
        if (plugin.missions().isRunning(robot.key())) {
            player.sendMessage(Component.text("הרובוט באמצע הרצת ניסוי - הטעינה תחכה לסיום.",
                    NamedTextColor.YELLOW));
            return;
        }
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
        plugin.store().setLayout(player.getUniqueId(),
                plugin.parts().size(), plugin.missions().registry().all().size());
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

    // ------------------------------------------------------------ classroom

    /**
     * {@code pause [player] [text]}, {@code resume [player]}, {@code say [player] <text>}.
     *
     * <p>The second word is a player if one of that name is online, or {@code all}; anything
     * else is the start of the text and the target is the room. That is the one ambiguity here -
     * a message whose first word is a student's name - and it is accepted, because the
     * alternative is making a teacher type {@code all} every time.
     */
    private void classroom(CommandSender sender, String[] args) {
        String verb = args[0].toLowerCase();
        Player one = null;
        int textFrom = 1;
        if (args.length >= 2) {
            Player p = com.agurim.robocraft.ui.Whisper.find(args[1]);
            if (p != null) { one = p; textFrom = 2; }
            else if (args[1].equalsIgnoreCase("all") || args[1].equals("כולם")) textFrom = 2;
        }
        String text = args.length > textFrom
                ? String.join(" ", java.util.Arrays.copyOfRange(args, textFrom, args.length)) : null;

        switch (verb) {
            case "pause" -> {
                if (one != null) {
                    plugin.pause().pause(one, text);
                    reply(sender, "הפסקה: " + one.getName(), "paused " + one.getName());
                } else {
                    int n = plugin.pause().pauseAll(text);
                    reply(sender, "הפסקה לכל הכיתה (" + n + " מחוברים). /rc resume משחרר.",
                            "paused the room: " + n + " online. /rc resume lifts it.");
                }
            }
            case "resume" -> {
                if (one != null) {
                    boolean was = plugin.pause().resume(one);
                    reply(sender, was ? "שוחרר: " + one.getName() : one.getName() + " לא היה בהפסקה.",
                            was ? "resumed " + one.getName() : one.getName() + " was not paused");
                } else {
                    int n = plugin.pause().resumeAll();
                    reply(sender, "ההפסקה הסתיימה (" + n + " שוחררו).", "resumed the room: " + n + " released");
                }
            }
            case "say" -> {
                if (text == null || text.isBlank()) {
                    reply(sender, "שימוש: /rc say [שם] <טקסט>", "usage: /rc say [player] <text>");
                    return;
                }
                java.util.Collection<? extends Player> targets =
                        one != null ? List.of(one) : plugin.getServer().getOnlinePlayers();
                int n = plugin.pause().announce(targets, text);
                reply(sender, "נשלח ל-" + n + ".", "shown to " + n);
            }
            default -> { }
        }
    }

    /** Hebrew for a player, English for a console whose terminal will not draw Hebrew anyway. */
    private void reply(CommandSender sender, String hebrew, String english) {
        if (sender instanceof Player p) p.sendMessage(Component.text(hebrew, NamedTextColor.GRAY));
        else sender.sendMessage(english);
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
                                    "whisper", "pause", "resume", "say")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("pause")
                || args[0].equalsIgnoreCase("resume") || args[0].equalsIgnoreCase("say"))) {
            if ("all".startsWith(args[1].toLowerCase())) out.add("all");
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
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
