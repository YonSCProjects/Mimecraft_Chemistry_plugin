package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.classroom.BigText;
import com.agurim.robocraft.mission.BenchSim;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.mission.MissionRegistry;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartItems;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.program.Operand;
import com.agurim.robocraft.program.Program;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A mission one step at a time: the next action only, ticked off as it is done.
 *
 * <p>The card (§4.9) shows everything at once - ten lines - and Yon's students still preferred
 * to play (docs/DESIGN.md 4.12). This is the front door now: "put a controller on the pad",
 * "a battery beside it", "right-click the controller", a choice of how the rules get written,
 * then the rules, then the run. Nothing is stored for it: every step is <em>read off the
 * world</em> - which parts are on the robot, whether the table has been opened, how many rules
 * there are, whether a {@code ?} is still waiting for a number - so a student who logs back in
 * is told exactly where they are, with nothing to reconstruct.
 *
 * <p>The three paths. <b>Solo</b>: write the rules yourself. <b>Guided</b>: the solution's rules
 * appear in the table with each threshold replaced by {@code ?}, and the steps say which to fill.
 * <b>Watch</b>: {@link com.agurim.robocraft.mission.Demo} writes the solution one rule at a time
 * and runs the bench. All three get the mission's prize; the path is recorded at the pass.
 *
 * <p>{@link #plan} is Bukkit-free, so the self-test can walk a student through every state.
 */
public final class Walkthrough {

    public enum Path {
        SOLO, GUIDED, WATCH;

        public static Path parse(String s) {
            if (s == null) return null;
            return switch (s.toLowerCase()) {
                case "solo", "alone", "לבד"          -> SOLO;
                case "guided", "help", "עזרה"        -> GUIDED;
                case "watch", "show", "demo", "תראו" -> WATCH;
                default -> null;
            };
        }

        public String key() { return name().toLowerCase(); }

        /** How the pass line says it. */
        public String passed() {
            return switch (this) {
                case SOLO   -> "פתרתם לבד";
                case GUIDED -> "פתרתם עם עזרה";
                case WATCH  -> "צפיתם בפתרון";
            };
        }
    }

    public enum Kind { BUILD, OPEN, CHOOSE, WRITE, FILL, WATCH, RUN, DONE }

    /** One line of the walkthrough. {@code partId} for a build step, {@code rule} for a fill step. */
    public record Step(Kind kind, String text, boolean done, String partId, int rule) {
        static Step of(Kind kind, String text, boolean done) { return new Step(kind, text, done, "", -1); }
    }

    /** Everything a plan is computed from. {@code attached}/{@code inHand} are part id -> count. */
    public record State(Map<String, Integer> attached, Map<String, Integer> inHand, Set<String> unlocked,
                        boolean opened, Path path, List<Rule> rules, boolean demoDone, boolean passed) {}

    /** The content the plan names: a part's Hebrew name, the mission that unlocks it, which ids are controllers. */
    public record Names(Function<String, String> part, Function<String, String> unlockedBy, Set<String> controllers) {}

    public static final String TEXT_OPEN   = "לחיצה ימנית על הבקר פותחת את טבלת הכללים.";
    public static final String TEXT_CHOOSE = "איך נכתוב את הכללים? בחרו:";
    public static final String TEXT_WRITE  = "כתבו כללים בטבלה. האמרלד מוסיף שורה.";
    public static final String TEXT_WATCH  = "צפו: הרובוט כותב את הכללים ועולה על הבוחן.";
    public static final String TEXT_RUN    = "לחצו על הדוכן בטבלה - הרובוט עולה על הבוחן.";
    public static final String TEXT_DONE   = "המשימה הושלמה!";

    // ================================================================ pure

    /** The whole walkthrough for this mission in this state; {@link #current} is the one to show. */
    public static List<Step> plan(Mission m, State s, Names names) {
        List<Step> out = new ArrayList<>();

        // Build: one step per part, in the order the mission lists them, a second step for a
        // part listed twice (the solar station's second panel). Ticked against the robot.
        Map<String, Integer> needed = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>(m.needs());
        ids.addAll(m.solutionParts());
        for (String id : ids) {
            int n = needed.merge(id, 1, Integer::sum);
            boolean done = s.attached().getOrDefault(id, 0) >= n;
            boolean held = s.inHand().getOrDefault(id, 0) > 0;
            String name = names.part().apply(id);
            String text;
            if (!done && !s.unlocked().contains(id)) {
                String by = names.unlockedBy().apply(id);
                text = name + " עדיין נעול" + (by == null ? "." : " - נפתח במשימה: " + by);
            } else if (names.controllers().contains(id)) {
                text = held ? "שימו את הבקר על המשטח." : "קחו בקר מהלוח ושימו אותו על המשטח.";
            } else {
                String again = n > 1 ? " עוד " : " ";
                text = held ? "שימו" + again + name + " ליד הבקר."
                            : "קחו" + again + name + " מהלוח ושימו ליד הבקר.";
            }
            out.add(new Step(Kind.BUILD, text, done, id, -1));
        }

        // Rules exist or a path was chosen: the table has been opened, whatever the session says.
        boolean opened = s.opened() || !s.rules().isEmpty() || s.path() != null;
        out.add(Step.of(Kind.OPEN, TEXT_OPEN, opened));

        // The path, read off the program when the session has forgotten it: a ? means guided,
        // rules of their own mean solo.
        Path path = s.path();
        if (path == null && hasBlank(s.rules())) path = Path.GUIDED;
        else if (path == null && !s.rules().isEmpty()) path = Path.SOLO;
        out.add(Step.of(Kind.CHOOSE, TEXT_CHOOSE, path != null));

        if (path == Path.GUIDED) {
            for (int i = 0; i < s.rules().size(); i++) {
                Rule r = s.rules().get(i);
                if (r.hasBlank()) {
                    out.add(new Step(Kind.FILL, "כלל " + (i + 1) + ": " + r.text() + "  -  מלאו מספר במקום ה-?",
                            false, "", i));
                }
            }
        } else if (path == Path.WATCH) {
            out.add(Step.of(Kind.WATCH, TEXT_WATCH, s.demoDone()));
        } else {
            out.add(Step.of(Kind.WRITE, TEXT_WRITE, !s.rules().isEmpty()));
        }

        out.add(Step.of(Kind.RUN, TEXT_RUN, s.passed()));
        out.add(Step.of(Kind.DONE, TEXT_DONE, s.passed()));
        return out;
    }

    /** The first step not yet done - or the last, when everything is. */
    public static Step current(List<Step> plan) {
        for (Step s : plan) if (!s.done()) return s;
        return plan.get(plan.size() - 1);
    }

    /** The last step done before the current one, or null - the "✔" line above the "▶". */
    public static Step previous(List<Step> plan) {
        Step prev = null;
        for (Step s : plan) {
            if (!s.done()) return prev;
            prev = s;
        }
        return null;
    }

    private static boolean hasBlank(List<Rule> rules) {
        for (Rule r : rules) if (r.hasBlank()) return true;
        return false;
    }

    /**
     * The rule with its threshold taken out - what guided mode hands over. The first number that
     * is not 0 or 1 goes: {@code IF S1 < 7} loses the 7, {@code M1 ADD 3} loses the 3, while
     * {@code IF S1 = 1} (a presence sensor) and {@code ALWAYS THEN A2 SHOW S1} are given whole.
     * Zero and one are states, not thresholds; the thresholds are the student's problem.
     */
    public static Rule blanked(Rule r) {
        if (!r.always() && r.rhs().constant() && !r.rhs().isBlank() && r.rhs().value() > 1) {
            return r.withRhs(Operand.blank());
        }
        if (r.verb().needsArg() && r.arg().constant() && !r.arg().isBlank() && r.arg().value() > 1) {
            return r.withArg(Operand.blank());
        }
        return r;
    }

    /**
     * A solution written against the reference robot (S1 = the first sensor in {@code needs}),
     * moved onto a student's robot whose light sensor may well be S2. {@code ports} maps the
     * reference port to the student's; anything not in the map (M1, TIME) is left alone.
     */
    public static Program remap(Program solution, Map<String, String> ports, boolean blanks) {
        Program out = new Program();
        for (Rule r : solution.rules()) {
            Rule m = new Rule(
                    ports.getOrDefault(r.source(), r.source()), r.op(), remap(r.rhs(), ports),
                    ports.getOrDefault(r.target(), r.target()), r.verb(), remap(r.arg(), ports));
            out.add(blanks ? blanked(m) : m);
        }
        return out;
    }

    private static Operand remap(Operand o, Map<String, String> ports) {
        if (o.constant()) return o;
        return Operand.ref(ports.getOrDefault(o.source(), o.source()));
    }

    // ============================================================= session

    private final RoboCraftPlugin plugin;
    private final Map<UUID, Map<String, Path>> paths = new HashMap<>();
    private final Set<UUID> opened = new HashSet<>();
    private final Map<UUID, Set<String>> demosDone = new HashMap<>();
    private final Map<UUID, String> lastShown = new HashMap<>();

    public Walkthrough(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public Path pathOf(UUID id, Mission m) {
        Map<String, Path> mine = paths.get(id);
        return mine == null ? null : mine.get(m.id());
    }

    public void demoDone(UUID id, Mission m) {
        demosDone.computeIfAbsent(id, k -> new HashSet<>()).add(m.id());
    }

    /** The table was opened: the one step the world cannot tell us. Then say what is next. */
    public void opened(Player player) {
        opened.add(player.getUniqueId());
        nudge(player);
    }

    /** Passed: forget the session's bits for this mission, so a re-run starts clean. */
    public void finished(UUID id, Mission m) {
        Map<String, Path> mine = paths.get(id);
        if (mine != null) mine.remove(m.id());
        Set<String> demos = demosDone.get(id);
        if (demos != null) demos.remove(m.id());
        lastShown.remove(id);
    }

    public void forget(UUID id) {
        paths.remove(id);
        opened.remove(id);
        demosDone.remove(id);
        lastShown.remove(id);
    }

    // --------------------------------------------------------------- state

    /** The student's robot: the only one they own, or the nearest. Null, quietly, if none. */
    public static Robot robotOf(RoboCraftPlugin plugin, Player player) {
        Robot best = null;
        double bestDist = Double.MAX_VALUE;
        for (String key : plugin.placements().controllers()) {
            Robot r = plugin.robots().get(key);
            if (r == null || !player.getUniqueId().equals(r.owner())) continue;
            Location loc = PartStore.fromKey(r.key());
            double d = (loc == null || !loc.getWorld().equals(player.getWorld()))
                    ? Double.MAX_VALUE / 2 : loc.distance(player.getLocation());
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return best;
    }

    public State state(Player player, Mission m) {
        UUID id = player.getUniqueId();
        Robot robot = robotOf(plugin, player);
        Map<String, Integer> attached = new LinkedHashMap<>();
        List<Rule> rules = List.of();
        if (robot != null) {
            Placed self = plugin.placements().byKey(robot.key());
            if (self != null) attached.merge(self.partId(), 1, Integer::sum);
            for (Placed p : plugin.placements().partsOf(robot.key()).values()) attached.merge(p.partId(), 1, Integer::sum);
            rules = robot.program().rules();
        }
        Map<String, Integer> inHand = new LinkedHashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String partId = PartItems.idOf(plugin, item);
            if (partId != null) inHand.merge(partId, item.getAmount(), Integer::sum);
        }
        Set<String> demos = demosDone.getOrDefault(id, Set.of());
        return new State(attached, inHand, plugin.store().unlocked(id), opened.contains(id),
                pathOf(id, m), rules, demos.contains(m.id()), plugin.store().isMissionDone(id, m.id()));
    }

    public Names names() {
        MissionRegistry reg = plugin.missions().registry();
        Set<String> controllers = new HashSet<>();
        for (Part p : plugin.parts().all()) if (p.isController()) controllers.add(p.id());
        return new Names(
                id -> { Part p = plugin.parts().get(id); return p == null ? id : p.name(); },
                id -> { Mission by = reg.unlocking(id); return by == null ? null : by.name(); },
                controllers);
    }

    /**
     * Reference port -> this robot's port, matched by part id in port order, or null with the
     * first missing part's name when the robot lacks one. What a demo and guided mode write with.
     */
    public Map<String, String> portMap(Mission m, Robot robot, StringBuilder missing) {
        Map<String, BenchSim.PartSpec> reference = BenchSim.ports(
                plugin.missions().registry().referenceParts(m, plugin.parts()));
        List<Placed> mine = new ArrayList<>(plugin.placements().partsOf(robot.key()).values());
        mine.sort((a, b) -> a.port().compareTo(b.port()));
        Set<String> used = new HashSet<>();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, BenchSim.PartSpec> e : reference.entrySet()) {
            String found = null;
            for (Placed p : mine) {
                if (p.port().isEmpty() || used.contains(p.port()) || !p.partId().equals(e.getValue().id())) continue;
                found = p.port();
                break;
            }
            if (found == null) {
                Part part = plugin.parts().get(e.getValue().id());
                if (missing != null) missing.append(part == null ? e.getValue().id() : part.name());
                return null;
            }
            used.add(found);
            out.put(e.getKey(), found);
        }
        return out;
    }

    /** Port -> part name for a robot, for reading a rule aloud. */
    public Map<String, String> portNames(Robot robot) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Placed p : plugin.placements().partsOf(robot.key()).values()) {
            Part part = plugin.parts().get(p.partId());
            if (part != null && !p.port().isEmpty()) out.put(p.port(), part.name());
        }
        return out;
    }

    // -------------------------------------------------------------- choose

    /** The student picked a path. Guided writes the blanked solution; watch starts the demo. */
    public void choose(Player player, Mission m, Path path) {
        UUID id = player.getUniqueId();
        plugin.missions().focus(id, m.id());
        Robot robot = robotOf(plugin, player);
        if (robot == null) {
            player.sendMessage(Component.text("אין לכם בקר. הניחו אחד כדי להתחיל.", NamedTextColor.RED));
            return;
        }
        Program solution = plugin.missions().registry().solution(m);
        if (path != Path.SOLO && (solution == null || !plugin.missions().registry().solutionVerified(m))) {
            player.sendMessage(Component.text("למשימה הזו אין פתרון מוכן - כתבו את הכללים בעצמכם.", NamedTextColor.YELLOW));
            path = Path.SOLO;
        }
        if (path != Path.SOLO) {
            StringBuilder missing = new StringBuilder();
            if (portMap(m, robot, missing) == null) {
                player.sendMessage(Component.text("חסר לרובוט: " + missing, NamedTextColor.RED));
                nudge(player);
                return;
            }
        }
        paths.computeIfAbsent(id, k -> new HashMap<>()).put(m.id(), path);

        switch (path) {
            case GUIDED -> {
                boolean had = !robot.program().isEmpty();
                robot.program(remap(solution, portMap(m, robot, null), true));
                plugin.robots().save();
                player.sendMessage(Component.text(had ? "הכללים הקודמים הוחלפו. " : "",
                        NamedTextColor.GRAY).append(Component.text(
                        "הכללים בטבלה. במקום כל ? - מספר שלכם.", NamedTextColor.YELLOW)));
                ProgramMenu.echoProgram(plugin, player, robot.key());
                lastShown.remove(id);
                nudge(player);
            }
            case WATCH -> {
                String why = plugin.demo().start(player, robot, m);
                if (why != null) player.sendMessage(Component.text(why, NamedTextColor.RED));
            }
            default -> {
                player.sendMessage(Component.text("בהצלחה. הרמז כאן אם צריך:  ", NamedTextColor.GRAY)
                        .append(MissionCard.button("רמז", "/rc hint " + m.id(), "עזרה קטנה - לא הפתרון", NamedTextColor.GOLD)));
                lastShown.remove(id);
                nudge(player);
            }
        }
    }

    // ------------------------------------------------------------- display

    /** The front door: header, goal, the step just done, the step to do, and its buttons. */
    public void send(Player player, Mission m) {
        UUID id = player.getUniqueId();
        plugin.missions().focus(id, m.id());
        MissionRegistry reg = plugin.missions().registry();
        boolean done = plugin.store().isMissionDone(id, m.id());
        NamedTextColor head = done ? NamedTextColor.GREEN : m.warmUp() ? NamedTextColor.DARK_AQUA
                : m.bonus() ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        player.sendMessage(Component.text(MissionCard.header(m, reg.label(m), done), head));
        for (String l : BigText.lines(m.brief(), Guide.CHAT_WIDTH)) player.sendMessage(Component.text(l, NamedTextColor.WHITE));
        // The prize, where the goal is - the thing that puts a mission ahead of free play.
        List<com.agurim.robocraft.mission.Perk> perks = reg.perksOf(m);
        if (!perks.isEmpty()) {
            player.sendMessage(Component.text((done ? "הפרס שלכם: " : "פרס: ")
                    + com.agurim.robocraft.perk.PerkService.names(perks), NamedTextColor.GOLD));
        }
        steps(player, m, true);
    }

    /**
     * After something changed - a part placed, the table opened, a cell clicked - say what is
     * next, once. The same step is never repeated, so a student clicking through a rule's cells
     * is not told "fill in the number" eight times.
     */
    public void nudge(Player player) {
        Mission m = plugin.missions().focusOrNext(player.getUniqueId());
        if (m == null) return;
        steps(player, m, false);
    }

    private void steps(Player player, Mission m, boolean always) {
        UUID id = player.getUniqueId();
        List<Step> plan = plan(m, state(player, m), names());
        Step now = current(plan);
        String key = m.id() + "#" + plan.indexOf(now) + "#" + now.text();
        if (!always && key.equals(lastShown.get(id))) return;
        lastShown.put(id, key);

        if (now.kind() == Kind.DONE) {
            if (!always) return;      // the pass line already said it
            player.sendMessage(Component.text("✔ " + TEXT_DONE + "  ", NamedTextColor.GREEN)
                    .append(MissionCard.runButton(m, "▶ הרצה שוב")));
            return;
        }
        Step prev = previous(plan);
        if (prev != null) player.sendMessage(Component.text("✔ " + prev.text(), NamedTextColor.DARK_GRAY));
        List<String> lines = BigText.lines("▶ " + now.text(), Guide.CHAT_WIDTH);
        for (String l : lines) player.sendMessage(Component.text(l, NamedTextColor.YELLOW));
        player.sendMessage(buttons(m, now));
    }

    private Component buttons(Mission m, Step now) {
        Component card = MissionCard.button("הכרטיס", "/rc card " + m.id(), "מה בונים ומה הבוחן בודק", NamedTextColor.GRAY);
        Component hint = MissionCard.button("רמז", "/rc hint " + m.id(), "עזרה קטנה - לא הפתרון", NamedTextColor.GOLD);
        String sp = "  ";
        return switch (now.kind()) {
            case CHOOSE -> MissionCard.button("אני כותב/ת", "/rc path solo " + m.id(), "הכללים שלכם, מההתחלה", NamedTextColor.GREEN)
                    .append(Component.text(sp))
                    .append(MissionCard.button("עם עזרה", "/rc path guided " + m.id(), "הכללים מוכנים - המספרים שלכם", NamedTextColor.YELLOW))
                    .append(Component.text(sp))
                    .append(MissionCard.button("תראו לי", "/rc path watch " + m.id(), "הרובוט כותב את הפתרון ומריץ", NamedTextColor.AQUA))
                    .append(Component.text(sp)).append(card);
            case WRITE, FILL -> hint.append(Component.text(sp)).append(card);
            case RUN -> MissionCard.runButton(m, "▶ הרצה").append(Component.text(sp)).append(hint)
                    .append(Component.text(sp)).append(card);
            default -> card.append(Component.text(sp))
                    .append(MissionCard.button("כל המשימות", "/rc missions", "הרשימה", NamedTextColor.GRAY));
        };
    }

    /** "▶ the next thing to do" for the join message, or null when the mission is done. */
    public Component currentLine(Player player, Mission m) {
        Step now = current(plan(m, state(player, m), names()));
        if (now.kind() == Kind.DONE) return null;
        return Component.text("▶ " + now.text(), NamedTextColor.YELLOW);
    }
}
