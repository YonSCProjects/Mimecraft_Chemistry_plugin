package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.program.Operand;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The program, as a chest GUI: one rule per row, read left to right like a sentence.
 *
 * <p>Authored entirely by clicking, which is the whole point - no typing means no syntax errors and
 * no English barrier, and both of those matter enormously for eleven-year-olds working in Hebrew.
 * Every token in a rule (S1, A1, {@code <}, 7) is Latin, so a row reads identically in any client.
 *
 * <p>Row layout: [#] [source] [op] [value] [->] [target] [verb] [arg] [x]
 */
public class ProgramMenu implements InventoryHolder {

    public static final int SLOT_ADD     = 45;
    public static final int SLOT_RUN     = 47;
    public static final int SLOT_STOP    = 48;
    public static final int SLOT_INFO    = 50;
    /** The mission button: the bench, one click from the rules, with nothing to type. */
    public static final int SLOT_MISSION = 51;
    public static final int SLOT_HELP    = 53;

    private final String robotKey;
    private Inventory inventory;

    public ProgramMenu(String robotKey) { this.robotKey = robotKey; }

    public String robotKey() { return robotKey; }

    /** Create the window and paint it, without showing it to anyone. Lets the self-test render
     *  the rule table headless - otherwise the GUI would be the one subsystem never executed. */
    public Inventory build(RoboCraftPlugin plugin) {
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("תוכנית הרובוט", NamedTextColor.DARK_AQUA));
        render(plugin);
        return inventory;
    }

    public Inventory open(RoboCraftPlugin plugin, Player player) {
        build(plugin);
        player.openInventory(inventory);
        return inventory;
    }

    /**
     * Redraw into the inventory that is already open.
     *
     * <p>Every click edits the program and needs the table redrawn. Calling openInventory again
     * from inside an InventoryClickEvent is the obvious way to do that and it is wrong: reopening
     * a window while the client is still processing a click in it desyncs the two, and the symptom
     * is ghost items and clicks that land on the wrong cell. Repainting the existing inventory has
     * neither problem.
     */
    public void render(RoboCraftPlugin plugin) {
        if (inventory == null) return;
        Robot robot = plugin.robots().get(robotKey);
        int max = plugin.getConfig().getInt("program.max-rules", 5);
        List<Rule> rules = (robot == null) ? List.of() : robot.program().rules();

        for (int i = 0; i < max; i++) {
            if (i < rules.size()) renderRule(plugin, i, rules.get(i));
            else renderEmptyRow(i);
        }
        renderControls(plugin, robot);
    }

    // ------------------------------------------------------- the readable copy

    /**
     * The program as plain text lines, e.g. {@code "1. IF S1 < 7 THEN A1 ON"}.
     *
     * <p>A chest GUI renders an item's name only while the mouse is over it. So the rule table -
     * whose founding idea is that a rule reads left to right like a sentence - is in practice eight
     * anonymous items until you hover them one at a time. The first person ever to open it said
     * exactly that: "I see all sorts of inventory stuff but not the text". Every self-test around
     * this GUI passed; none of them asked whether a row could be read.
     *
     * <p>Chat is the one surface that draws real text over an open container, so the sentence goes
     * there. The window title would be the other candidate and is arguably what it is for, but
     * {@code InventoryView.setTitle} re-sends the open-window packet - precisely what {@link
     * #render} exists to avoid, because doing that mid-click desyncs the client into ghost items
     * and clicks that land on the wrong cell.
     */
    public static List<String> programLines(List<Rule> rules) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) out.add((i + 1) + ". " + rules.get(i).text());
        return out;
    }

    /** The whole program, for when it changes shape: opening the menu, adding or deleting a rule. */
    public static void echoProgram(RoboCraftPlugin plugin, Player player, String robotKey) {
        Robot robot = plugin.robots().get(robotKey);
        List<Rule> rules = (robot == null) ? List.of() : robot.program().rules();
        if (rules.isEmpty()) {
            player.sendMessage(Component.text("התוכנית ריקה - לחצו על האמרלד כדי להוסיף כלל.",
                    NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("==== התוכנית ====", NamedTextColor.DARK_AQUA));
        for (String line : programLines(rules)) {
            player.sendMessage(Component.text(line, NamedTextColor.WHITE));
        }
    }

    /** One rule, for a single cell edit - so the sentence visibly changes as you click. */
    public static void echoRule(Player player, int index, Rule rule) {
        player.sendMessage(Component.text((index + 1) + ". " + rule.text(), NamedTextColor.YELLOW));
    }

    // ------------------------------------------------------------------ rows

    private void renderRule(RoboCraftPlugin plugin, int index, Rule rule) {
        int base = index * 9;
        put(base, icon(Material.PAPER, "כלל " + (index + 1), NamedTextColor.WHITE,
                rule.text(), "כללים רצים מלמעלה למטה, בכל סיבוב"));

        if (rule.always()) {
            put(base + 1, icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "ALWAYS", NamedTextColor.AQUA,
                    "תמיד - בלי תנאי", "לחיצה: החליפו למקור אחר"));
            put(base + 2, filler());
            put(base + 3, filler());
        } else {
            put(base + 1, icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, rule.source(), NamedTextColor.AQUA,
                    "המקור שנבדק", "לחיצה: הבא | ימנית: הקודם"));
            put(base + 2, icon(Material.COMPARATOR, rule.op().symbol(), NamedTextColor.WHITE,
                    "ההשוואה", "לחיצה: הבאה"));
            put(base + 3, operandIcon(rule.rhs(), "הערך להשוואה"));
        }

        put(base + 4, icon(Material.ARROW, "THEN", NamedTextColor.GRAY, "אז...", ""));
        put(base + 5, icon(Material.ORANGE_STAINED_GLASS_PANE,
                rule.target().isEmpty() ? "-" : rule.target(), NamedTextColor.GOLD,
                "המפעיל או הזיכרון שמשתנה", "לחיצה: הבא | ימנית: הקודם"));
        put(base + 6, icon(Material.LEVER, rule.verb().name(), NamedTextColor.WHITE,
                "הפעולה", "לחיצה: הבאה"));
        if (rule.verb().needsArg()) put(base + 7, operandIcon(rule.arg(), "הערך לפעולה"));
        else put(base + 7, filler());

        put(base + 8, icon(Material.BARRIER, "מחיקה", NamedTextColor.RED, "מוחק את הכלל הזה", ""));
    }

    private void renderEmptyRow(int index) {
        int base = index * 9;
        for (int i = 0; i < 9; i++) put(base + i, filler());
    }

    private void renderControls(RoboCraftPlugin plugin, Robot robot) {
        for (int i = 45; i < 54; i++) put(i, filler());

        int max = plugin.getConfig().getInt("program.max-rules", 5);
        int used = (robot == null) ? 0 : robot.program().size();
        put(SLOT_ADD, icon(Material.EMERALD, "כלל חדש", NamedTextColor.GREEN,
                used + " / " + max + " כללים", "לחיצה מוסיפה שורה"));

        boolean running = robot != null && robot.running();
        put(SLOT_RUN, icon(running ? Material.LIME_CONCRETE : Material.LIME_CONCRETE_POWDER,
                "הפעלה", NamedTextColor.GREEN,
                running ? "הרובוט פועל" : "מפעיל את הלולאה", ""));
        put(SLOT_STOP, icon(Material.RED_CONCRETE, "עצירה", NamedTextColor.RED,
                "עוצר את הלולאה", "פלטים נשארים במצבם - עצירה היא לא איפוס"));

        String energy = (robot == null) ? "-" : String.valueOf(robot.energy());
        put(SLOT_INFO, icon(Material.REDSTONE, "אנרגיה: " + energy, NamedTextColor.GOLD,
                (robot != null && robot.halt() != null) ? robot.halt() : "כל מפעיל דולק שורף חשמל", ""));

        put(SLOT_HELP, icon(Material.BOOK, "איך זה עובד", NamedTextColor.AQUA,
                "כללים רצים מלמעלה למטה בכל סיבוב",
                "כלל מאוחר גובר על מוקדם | פלט זוכר את מצבו"));

        // The loop closes here: write rules, press the lectern, watch the bench. Until this
        // button the only way onto the bench was a typed command, and the students who avoided
        // the missions were avoiding exactly that.
        com.agurim.robocraft.mission.Mission mission = (robot == null || robot.owner() == null)
                ? null : plugin.missions().focusOrNext(robot.owner());
        if (mission == null) {
            put(SLOT_MISSION, icon(Material.LECTERN, "כל המשימות הושלמו", NamedTextColor.GREEN,
                    "בנו מנגנון משלכם", ""));
        } else {
            String label = plugin.missions().registry().label(mission);
            put(SLOT_MISSION, icon(Material.LECTERN, "המשימה: " + label + ". " + mission.name(), NamedTextColor.YELLOW,
                    "לחיצה: הרובוט עולה על הבוחן",
                    "ימנית: הכרטיס - מה בונים ומה נבדק"));
        }
    }

    // ------------------------------------------------------------------ cells

    private ItemStack operandIcon(Operand operand, String what) {
        Material mat = operand.constant() ? Material.GOLD_NUGGET : Material.LIGHT_BLUE_DYE;
        return icon(mat, operand.label(), NamedTextColor.YELLOW, what,
                "לחיצה: ±1 | Shift: ±10 | גלגלת: מספר/חיישן");
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(Material mat, String name, NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            if (l != null && !l.isEmpty()) {
                lines.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private void put(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    // ------------------------------------------------------- port vocabulary

    /** Everything a condition may read: ALWAYS, the robot's sensor ports, memory, TIME. */
    public static List<String> sources(RoboCraftPlugin plugin, String robotKey) {
        List<String> out = new ArrayList<>();
        out.add(Rule.ALWAYS);
        out.addAll(ports(plugin, robotKey, true));
        for (int i = 1; i <= plugin.getConfig().getInt("program.memory-slots", 4); i++) out.add("M" + i);
        out.add("TIME");
        return out;
    }

    /** Everything an action may write: the robot's actuator ports, and memory. */
    public static List<String> targets(RoboCraftPlugin plugin, String robotKey) {
        List<String> out = new ArrayList<>(ports(plugin, robotKey, false));
        for (int i = 1; i <= plugin.getConfig().getInt("program.memory-slots", 4); i++) out.add("M" + i);
        return out;
    }

    /** Everything an operand may reference (no ALWAYS - it is not a value). */
    public static List<String> valueSources(RoboCraftPlugin plugin, String robotKey) {
        List<String> out = new ArrayList<>(ports(plugin, robotKey, true));
        for (int i = 1; i <= plugin.getConfig().getInt("program.memory-slots", 4); i++) out.add("M" + i);
        out.add("TIME");
        return out;
    }

    private static List<String> ports(RoboCraftPlugin plugin, String robotKey, boolean sensors) {
        List<String> out = new ArrayList<>();
        for (Placed p : plugin.placements().partsOf(robotKey).values()) {
            Part part = plugin.parts().get(p.partId());
            if (part == null || p.port().isEmpty()) continue;
            if (sensors ? part.isSensor() : part.isActuator()) out.add(p.port());
        }
        out.sort(String::compareTo);
        return out;
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
