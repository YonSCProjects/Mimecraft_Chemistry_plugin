package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The player-facing "how to play". Shown once on first join and any time via {@code /rc guide}.
 *
 * <p>First join gets three lines, not eighteen: Minecraft's unfocused chat shows about ten, and a
 * wall of text at a spot where none of the mechanics can be performed teaches nothing. The rest of
 * the teaching lives where it is usable - the status bar, the board tiles, and the part labels.
 * (ChemCraft learned this the hard way.)
 *
 * <p>That lesson was written here and then not applied to {@link #send}, which sent seventeen
 * lines. The first player to run {@code /rc guide} saw it begin at item 6, because everything
 * above had already scrolled away - and the two indented continuation lines, mixing a Hebrew
 * sentence with a Latin rule, arrived scrambled. Hence {@link #CHAT_LINES} and
 * {@link #CHAT_WIDTH}, and a self-test that holds this file to them.
 */
public final class Guide {

    private Guide() {}

    /**
     * What Minecraft's chat will actually show, and the budget every message here is written to.
     *
     * <p>Unfocused chat displays ten lines. A line longer than the chat is wide does not get
     * clipped, it <em>wraps</em> - so one over-long line costs two of the ten and silently pushes
     * the top of the guide out of view. The real wrap is near 53 characters; 48 leaves room for a
     * mission name to be substituted in without tipping over.
     */
    public static final int CHAT_LINES = 10;
    public static final int CHAT_WIDTH = 48;

    /** One line of the guide: the text, and the colour it is sent in. */
    private record Line(String text, NamedTextColor color) {}

    /**
     * The guide body, as data so the self-test can measure it.
     *
     * <p>Eight lines, because {@link #sendProgress} adds two and the budget is ten. Every step is
     * one line and the numbering starts at 1 and is unbroken: a continuation line indented under
     * its parent reads as a separate, unnumbered instruction, which is what made the old version
     * look garbled.
     *
     * <p>What is not here is deliberate. Energy, ports and live readings are all shown by the
     * part labels and the status bar, in front of the student, at the moment they matter. This
     * text only has to get someone as far as their first rule.
     */
    private static final List<Line> BODY = List.of(
            new Line("==== RoboCraft - איך משחקים ====", NamedTextColor.AQUA),
            new Line("1. הניחו בקר, ולידו סוללה, חיישן ומפעיל.", NamedTextColor.YELLOW),
            new Line("2. כל רכיב מקבל שם: חיישן S1, מפעיל A1.", NamedTextColor.YELLOW),
            new Line("3. לחיצה ימנית = תוכנית. Shift+לחיצה = בנייה.", NamedTextColor.YELLOW),
            // A chest GUI shows an item's name only on hover, so a rule row is eight anonymous
            // items until you mouse over one. Hovering the paper shows the whole rule at once -
            // the only way to read a rule, and the first person to open the table never found it.
            // It has to be taught HERE: any hint inside the window is itself hover-only, and chat
            // is drawn behind the window while it is open, so nothing said there can be read.
            new Line("4. ריחוף על הדף בתחילת השורה מראה את הכלל.", NamedTextColor.GOLD),
            // Merged to buy that line back: the budget is eight and it was already full.
            new Line("5. כלל בכל שורה. המאוחר גובר. פלט זוכר מצב.", NamedTextColor.GOLD),
            new Line("6. התוויות מראות מה כל חיישן קורא עכשיו.", NamedTextColor.YELLOW),
            new Line("פקודות: /rc missions | /rc trace | /rc ask", NamedTextColor.DARK_AQUA));

    /** The guide body as plain text, for the self-test to measure. */
    public static List<String> bodyText() {
        List<String> out = new ArrayList<>();
        for (Line l : BODY) out.add(l.text());
        return out;
    }

    public static void welcome(Player player) {
        player.showTitle(Title.title(
                Component.text("RoboCraft", NamedTextColor.AQUA),
                Component.text("חשים, מחליטים, פועלים", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
    }

    public static void firstJoin(RoboCraftPlugin plugin, Player player) {
        line(player, "ברוכים הבאים ל-RoboCraft! זו הסדנה שלכם, והקיר שלפניכם הוא מלאי הרכיבים.", NamedTextColor.GREEN);
        line(player, "הניחו בקר, סוללה, חיישן ומפעיל קרוב זה לזה - ואז לחצו על הבקר וכתבו כלל.", NamedTextColor.YELLOW);
        // The first mission is a click, not a command. A twelve-year-old who has just landed does
        // not type "/rc missions"; they click the thing that says "first mission".
        Mission first = plugin.missions().registry().nextSuggested(
                plugin.store().completedMissions(player.getUniqueId()));
        Component line = Component.text("המשימה הראשונה: ", NamedTextColor.DARK_AQUA);
        line = first != null
                ? line.append(MissionCard.openLink(plugin.missions().registry(), first, NamedTextColor.YELLOW))
                      .append(Component.text("  ")).append(MissionCard.openButton(first))
                : line.append(MissionCard.button("רשימה", "/rc missions", "כל המשימות", NamedTextColor.YELLOW));
        player.sendMessage(line);
        line(player, "המדריך: /rc guide  |  שאלה: /rc ask", NamedTextColor.DARK_AQUA);
    }

    public static void send(RoboCraftPlugin plugin, Player player) {
        for (Line l : BODY) line(player, l.text(), l.color());
        // No brief here: briefs run to sixty characters, which wraps and costs a line the guide
        // has not got. /rc missions is where a brief has room.
        sendProgress(plugin, player, false);
    }

    public static void sendProgress(RoboCraftPlugin plugin, Player player) {
        sendProgress(plugin, player, true);
    }

    public static void sendProgress(RoboCraftPlugin plugin, Player player, boolean withBrief) {
        var registry = plugin.missions().registry();
        var completed = plugin.store().completedMissions(player.getUniqueId());
        int done = registry.requiredDone(completed);
        int total = registry.requiredCount();
        int parts = plugin.store().unlocked(player.getUniqueId()).size();

        // Warm-ups are only worth a segment while they are what the student is actually doing.
        // Shown always, "משימות: 0 / 5" is the only number a beginner ever sees, and it reads as
        // no progress at all when they have in fact just finished something.
        String warm = "";
        if (done == 0 && registry.warmUpCount() > 0 && registry.warmUpsDone(completed) > 0) {
            warm = "  ·  חימום: " + registry.warmUpsDone(completed) + "/" + registry.warmUpCount();
        }
        player.sendMessage(Component.text(
                "משימות: " + done + " / " + total + warm
                        + "  ·  רכיבים: " + parts + " / " + plugin.parts().size(),
                NamedTextColor.AQUA));

        Mission next = registry.nextSuggested(completed);
        if (next != null) {
            // The name is a click that opens the card; the button says so for anyone who would
            // not think to click a name.
            player.sendMessage(Component.text("הבאה בתור: ", NamedTextColor.YELLOW)
                    .append(MissionCard.openLink(registry, next, NamedTextColor.YELLOW))
                    .append(Component.text("  "))
                    .append(MissionCard.openButton(next)));
            if (withBrief) {
                for (String l : com.agurim.robocraft.classroom.BigText.lines(next.brief(), CHAT_WIDTH)) {
                    player.sendMessage(Component.text(l, NamedTextColor.WHITE));
                }
            }
        }
    }

    private static void line(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }
}
