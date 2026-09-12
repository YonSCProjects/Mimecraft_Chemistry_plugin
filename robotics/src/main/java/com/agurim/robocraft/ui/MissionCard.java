package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.classroom.BigText;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.mission.MissionRegistry;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartRegistry;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One mission as a card a student can act on, and the overview that leads to it.
 *
 * <p>The complaint this answers (Yon, 2026-09-12): "the instructions of the missions must be
 * clear; if things are not simple and clear the students prefer to play and avoid the missions."
 * What a student met until now was a wall - sixteen missions, two lines each, in a chat that
 * shows ten - and a command to type for every step. A card is the same shape for every mission:
 *
 * <pre>
 *   ▶ 4. נורה שלא מרצדת                      what it is, and its number on the ladder
 *   הנורה מאירה את החיישן שלה - תקנו...      the goal, one line
 *   בונים: ✔ בקר · ✔ סוללה · ○ נורה           what to build, ticked against the robot they have
 *   ✓ אור 14: נורה כבויה                      what the bench will check - the spec, before the run
 *   ✓ לילה, והנורה מאירה את החיישן: ...
 *   [▶ הרצה]  [רמז]  [כל המשימות]              buttons; nothing to type
 * </pre>
 *
 * <p>Ten lines at most, so the whole card is on screen at once; the self-test and the content
 * validator both hold every mission to that. The check rows are derived from the injected readings
 * so they cannot drift from the bench, and authored ({@code check:}) only where a reading does not
 * tell the story - a deadline, a count, a direction.
 *
 * <p>Every reference to a mission anywhere - the overview, the join line, the pass line, the
 * failure's retry, a locked board tile - is a click that opens this card or runs this bench.
 */
public final class MissionCard {

    public static final int MAX_LINES = 10;
    public static final int MAX_CHECKS = 5;
    public static final int WIDTH = Guide.CHAT_WIDTH;
    /** Situation text on an authored check row: short, so the outcome still fits beside it. */
    public static final int CHECK_MAX = 30;

    private MissionCard() {}

    // ============================================================ pure text

    /** Every line of the card, wrapped to the chat width. Bukkit-free; the self-test reads this. */
    public static List<String> plainLines(Mission m, String label, PartRegistry parts,
                                          Set<String> attached, Set<String> unlocked, boolean done) {
        List<String> out = new ArrayList<>();
        out.add(header(m, label, done));
        out.addAll(BigText.lines(m.brief(), WIDTH));
        out.addAll(partsLines(m, parts, attached, unlocked));
        List<String> rows = checkRows(m, parts);
        int shown = Math.min(rows.size(), MAX_CHECKS);
        for (int i = 0; i < shown; i++) out.addAll(BigText.lines("✓ " + rows.get(i), WIDTH));
        if (rows.size() > shown) out.add("  ...ועוד " + (rows.size() - shown));
        out.add("[▶ הרצה]  [רמז]  [כל המשימות]");
        return out;
    }

    public static String header(Mission m, String label, boolean done) {
        String tier = m.warmUp() ? " (חימום)" : m.bonus() ? " (בונוס)" : "";
        return (done ? "✔ " : "▶ ") + label + ". " + m.name() + tier + (done ? " - הושלמה" : "");
    }

    /**
     * "בונים: ✔ בקר · ✔ סוללה · ○ חיישן אור · ✖ חיישן חום (נעול)" - the needed parts, ticked
     * against what is on the student's robot, and honest about a part they cannot draw yet.
     */
    public static String partsRow(Mission m, PartRegistry parts, Set<String> attached, Set<String> unlocked) {
        return String.join(" ", partsLines(m, parts, attached, unlocked));
    }

    /**
     * The parts row, wrapped at item boundaries so a line never ends on a dangling separator -
     * five parts run past the chat width, and word-wrap would leave "· " hanging at the edge.
     */
    public static List<String> partsLines(Mission m, PartRegistry parts, Set<String> attached, Set<String> unlocked) {
        List<String> items = new ArrayList<>();
        for (String id : m.needs()) {
            Part p = parts.get(id);
            String name = p == null ? id : p.name();
            if (attached.contains(id))      items.add("✔ " + name);
            else if (!unlocked.contains(id)) items.add("✖ " + name + " (נעול)");
            else                             items.add("○ " + name);
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder("בונים: ");
        boolean first = true;
        for (String item : items) {
            String sep = first ? "" : " · ";
            if (!first && line.length() + sep.length() + item.length() > WIDTH) {
                lines.add(line.toString());
                line = new StringBuilder("   ");
                sep = "";
            }
            line.append(sep).append(item);
            first = false;
        }
        lines.add(line.toString());
        return lines;
    }

    /**
     * One row per bench check: "situation: expected outcome".
     *
     * <p>The situation is the injected readings at that point of the run ("אור 14", "מישהו קרוב,
     * גשם"), or the step's authored {@code check} where readings do not say it. Two expects in a
     * row with no wait and no new readings between them are one situation, so they share a row.
     */
    public static List<String> checkRows(Mission m, PartRegistry parts) {
        List<String> rows = new ArrayList<>();
        Map<String, Integer> env = new LinkedHashMap<>();
        int lastExpect = -2;
        List<Mission.Step> steps = m.steps();
        for (int i = 0; i < steps.size(); i++) {
            Mission.Step step = steps.get(i);
            if (step.hasEnv()) env.putAll(step.env());
            if (!step.hasExpect()) continue;
            String outcome = outcome(step.expect(), parts);
            boolean mergeable = !step.hasCheck() && !step.hasEnv() && i == lastExpect + 1 && !rows.isEmpty();
            if (mergeable) {
                rows.set(rows.size() - 1, rows.get(rows.size() - 1) + ", " + outcome);
            } else {
                String situation = step.hasCheck() ? step.check() : readings(env);
                rows.add(situation.isEmpty() ? outcome : situation + ": " + outcome);
            }
            lastExpect = i;
        }
        return rows;
    }

    /** "אור 14, גשם" - the readings a student would see on their sensor labels at that moment. */
    public static String readings(Map<String, Integer> env) {
        List<String> parts = new ArrayList<>();
        env.forEach((type, v) -> parts.add(reading(type, v)));
        return String.join(", ", parts);
    }

    public static String reading(String type, int v) {
        return switch (type) {
            case "light"    -> "אור " + v;
            case "heat"     -> "חום " + v;
            case "distance" -> "מרחק " + v;
            case "color"    -> "צבע " + v;
            case "redstone" -> "אות " + v;
            case "player"   -> v > 0 ? "מישהו קרוב" : "אין אף אחד";
            case "rain"     -> v > 0 ? "גשם" : "יבש";
            case "mob"      -> v > 0 ? "חיה בחוץ" : "אין חיה";
            default         -> type + " " + v;
        };
    }

    /** "נורה דולקת, הצג 2, בלי ריצוד" - what the bench wants, in the words the parts are called. */
    public static String outcome(Map<String, String> expect, PartRegistry parts) {
        List<String> out = new ArrayList<>();
        boolean steady = false;
        for (Map.Entry<String, String> e : expect.entrySet()) {
            String key = e.getKey(), want = e.getValue().toLowerCase();
            if (key.equals("steady")) { steady = true; continue; }
            if (key.equals("running")) { out.add(want.equals("true") ? "הרובוט עדיין פועל" : "הרובוט נעצר"); continue; }
            if (key.length() >= 2 && key.charAt(0) == 'M' && Character.isDigit(key.charAt(1))) {
                out.add(key + " = " + e.getValue());
                continue;
            }
            String name = actuatorName(parts, key);
            if (want.matches("-?\\d+"))                      out.add(name + " " + want);
            else if (want.equals("on") || want.equals("true")) out.add(name + " " + state(key, true));
            else                                               out.add(name + " " + state(key, false));
        }
        if (steady) out.add("בלי ריצוד");
        return String.join(", ", out);
    }

    /** The on/off words agree in gender with the part's Hebrew name. */
    private static String state(String actuatorType, boolean on) {
        return switch (actuatorType) {
            case "lamp", "marker" -> on ? "דולקת" : "כבויה";
            case "buzzer"         -> on ? "מצלצל" : "שקט";
            case "gate"           -> on ? "פתוח" : "סגור";
            default               -> on ? "פועל" : "כבוי";
        };
    }

    private static String actuatorName(PartRegistry parts, String type) {
        for (Part p : parts.all()) if (p.isActuator() && type.equals(p.actuator())) return p.name();
        return type;
    }

    // ============================================================= delivery

    /** A clickable "[text]". Clicking runs the command as the player. */
    public static Component button(String text, String command, String hover, NamedTextColor color) {
        return Component.text("[" + text + "]", color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    /** Clickable text without brackets - a mission name that opens its card. */
    public static Component link(String text, String command, String hover, NamedTextColor color) {
        return Component.text(text, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    public static Component openLink(MissionRegistry reg, Mission m, NamedTextColor color) {
        return link(reg.label(m) + ". " + m.name(), "/rc missions " + m.id(), "לחצו - מה בונים ומה נבדק", color);
    }

    public static Component openButton(Mission m) {
        return button("פתיחה", "/rc missions " + m.id(), "מה בונים ומה הבוחן בודק", NamedTextColor.YELLOW);
    }

    public static Component runButton(Mission m, String text) {
        return button(text, "/rc mission " + m.id(), "הרובוט עולה על הבוחן", NamedTextColor.GREEN);
    }

    /** The card. Also makes this the mission the rule table's button will run. */
    public static void send(RoboCraftPlugin plugin, Player player, Mission m) {
        UUID id = player.getUniqueId();
        MissionRegistry reg = plugin.missions().registry();
        boolean done = plugin.store().isMissionDone(id, m.id());
        Set<String> attached = attached(plugin, id);
        Set<String> unlocked = plugin.store().unlocked(id);
        plugin.missions().focus(id, m.id());

        NamedTextColor head = done ? NamedTextColor.GREEN : m.warmUp() ? NamedTextColor.DARK_AQUA
                : m.bonus() ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        player.sendMessage(Component.text(header(m, reg.label(m), done), head));
        for (String l : BigText.lines(m.brief(), WIDTH)) player.sendMessage(Component.text(l, NamedTextColor.WHITE));
        for (String l : partsLines(m, plugin.parts(), attached, unlocked)) {
            player.sendMessage(Component.text(l, NamedTextColor.GRAY));
        }
        List<String> rows = checkRows(m, plugin.parts());
        int shown = Math.min(rows.size(), MAX_CHECKS);
        for (int i = 0; i < shown; i++) {
            for (String l : BigText.lines("✓ " + rows.get(i), WIDTH)) player.sendMessage(Component.text(l, NamedTextColor.YELLOW));
        }
        if (rows.size() > shown) player.sendMessage(Component.text("  ...ועוד " + (rows.size() - shown), NamedTextColor.GRAY));

        Component buttons = runButton(m, done ? "▶ הרצה שוב" : "▶ הרצה")
                .append(Component.text("  "))
                .append(button("רמז", "/rc hint " + m.id(), "עזרה קטנה - לא הפתרון", NamedTextColor.GOLD))
                .append(Component.text("  "))
                .append(button("כל המשימות", "/rc missions", "הרשימה", NamedTextColor.GRAY));
        player.sendMessage(buttons);
    }

    /**
     * The overview: three one-line tracks with a mark per mission, then the next mission and
     * its buttons. Eight lines for a fresh student; the wall it replaces was thirty-seven.
     */
    public static void overview(RoboCraftPlugin plugin, Player player) {
        UUID id = player.getUniqueId();
        MissionRegistry reg = plugin.missions().registry();
        Set<String> done = plugin.store().completedMissions(id);
        Mission next = reg.nextSuggested(done);
        boolean ladderDone = reg.requiredDone(done) >= reg.requiredCount();

        player.sendMessage(Component.text("==== משימות ====", NamedTextColor.AQUA));
        strip(player, reg, "חימום: ", reg.warmUps(), done, next, NamedTextColor.DARK_AQUA);
        strip(player, reg, "הסולם: ", reg.required(), done, next, NamedTextColor.WHITE);
        strip(player, reg, "בונוס: ", reg.bonus(), done, next, ladderDone ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY);
        player.sendMessage(Component.text("✔ הושלמה · ● הבאה · ○ עוד לא. לחיצה על שם פותחת את הכרטיס.", NamedTextColor.GRAY));

        if (next == null) {
            player.sendMessage(Component.text("כל המשימות הושלמו! בנו מנגנון משלכם.", NamedTextColor.GREEN));
            return;
        }
        player.sendMessage(Component.text("הבאה בתור: ", NamedTextColor.YELLOW)
                .append(openLink(reg, next, NamedTextColor.YELLOW)));
        for (String l : BigText.lines(next.brief(), WIDTH)) player.sendMessage(Component.text(l, NamedTextColor.WHITE));
        player.sendMessage(runButton(next, "▶ הרצה")
                .append(Component.text("  "))
                .append(button("הכרטיס: מה בונים ומה נבדק", "/rc missions " + next.id(), "פותח את הכרטיס", NamedTextColor.YELLOW)));
    }

    private static void strip(Player player, MissionRegistry reg, String title, List<Mission> track,
                              Set<String> done, Mission next, NamedTextColor colour) {
        if (track.isEmpty()) return;
        Component line = Component.text(title, colour);
        for (int i = 0; i < track.size(); i++) {
            Mission m = track.get(i);
            boolean d = done.contains(m.id());
            boolean isNext = next != null && next.id().equals(m.id());
            String mark = d ? "✔ " : isNext ? "● " : "○ ";
            NamedTextColor c = d ? NamedTextColor.GREEN : isNext ? NamedTextColor.YELLOW : colour;
            if (i > 0) line = line.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
            line = line.append(link(mark + reg.label(m) + " " + m.name(), "/rc missions " + m.id(),
                    "לחצו - מה בונים ומה נבדק", c));
        }
        player.sendMessage(line);
    }

    /** Every part id on any robot this student owns, plus the controller itself. */
    public static Set<String> attached(RoboCraftPlugin plugin, UUID owner) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : plugin.placements().controllers()) {
            Robot r = plugin.robots().get(key);
            if (r == null || !owner.equals(r.owner())) continue;
            Placed self = plugin.placements().byKey(key);
            if (self != null) out.add(self.partId());
            for (Placed p : plugin.placements().partsOf(key).values()) out.add(p.partId());
        }
        return out;
    }
}
