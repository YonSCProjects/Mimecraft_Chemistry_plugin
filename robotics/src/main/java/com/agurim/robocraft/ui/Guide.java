package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * The player-facing "how to play". Shown once on first join and any time via {@code /rc guide}.
 *
 * <p>First join gets three lines, not eighteen: Minecraft's unfocused chat shows about ten, and a
 * wall of text at a spot where none of the mechanics can be performed teaches nothing. The rest of
 * the teaching lives where it is usable - the status bar, the board tiles, and the part labels.
 * (ChemCraft learned this the hard way.)
 */
public final class Guide {

    private Guide() {}

    public static void welcome(Player player) {
        player.showTitle(Title.title(
                Component.text("RoboCraft", NamedTextColor.AQUA),
                Component.text("חשים, מחליטים, פועלים", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
    }

    public static void firstJoin(RoboCraftPlugin plugin, Player player) {
        line(player, "ברוכים הבאים ל-RoboCraft! זו הסדנה שלכם, והקיר שלפניכם הוא מלאי הרכיבים.", NamedTextColor.GREEN);
        line(player, "הניחו בקר, סוללה, חיישן ומפעיל קרוב זה לזה - ואז לחצו על הבקר וכתבו כלל.", NamedTextColor.YELLOW);
        line(player, "המשימה הראשונה: /rc missions | המדריך המלא: /rc guide", NamedTextColor.DARK_AQUA);
    }

    public static void send(RoboCraftPlugin plugin, Player player) {
        line(player, "==== RoboCraft - איך משחקים ====", NamedTextColor.AQUA);
        line(player, "כל מנגנון רובוטי הוא לולאה: חיישן נותן מספר, התוכנית מחליטה, המפעיל פועל.", NamedTextColor.WHITE);
        line(player, "1. הניחו בקר. כל רכיב שתניחו לידו מתחבר אליו ומקבל שם: S1, S2 לחיישנים, A1 למפעילים.", NamedTextColor.YELLOW);
        line(player, "2. חייבת להיות סוללה. בלי אנרגיה הרובוט לא רץ.", NamedTextColor.YELLOW);
        line(player, "3. לחיצה ימנית על הבקר פותחת את התוכנית - שורה אחת לכל כלל.", NamedTextColor.YELLOW);
        line(player, "   WHEN S1 < 7 THEN A1 ON  =  כשהאור קטן מ-7, הדליקו את A1.", NamedTextColor.GRAY);
        line(player, "4. הכללים רצים מלמעלה למטה בכל סיבוב. כלל מאוחר גובר על מוקדם.", NamedTextColor.YELLOW);
        line(player, "5. פלט זוכר את מצבו! נורה שנדלקה לא תיכבה לבד - צריך כלל שמכבה אותה.", NamedTextColor.GOLD);
        line(player, "6. התוויות מעל הרכיבים מראות מה כל חיישן קורא עכשיו - שם מנפים באגים.", NamedTextColor.YELLOW);
        line(player, "7. /rc missions - רשימת המשימות. הרצה בודקת את הרובוט ואומרת מה לא עבד.", NamedTextColor.YELLOW);
        line(player, "8. תקועים? /rc trace מראה מה כל חיישן קורא ואיזה כלל קבע כל פלט.", NamedTextColor.GOLD);
        line(player, "פקודות: /rc guide | /rc kit | /rc tp | /rc missions | /rc trace | /rc charge", NamedTextColor.DARK_AQUA);
        sendProgress(plugin, player);
    }

    public static void sendProgress(RoboCraftPlugin plugin, Player player) {
        int done = plugin.missions().registry()
                .requiredDone(plugin.store().completedMissions(player.getUniqueId()));
        int total = plugin.missions().registry().requiredCount();
        int parts = plugin.store().unlocked(player.getUniqueId()).size();
        player.sendMessage(Component.text(
                "משימות: " + done + " / " + total + "  ·  רכיבים: " + parts + " / " + plugin.parts().size(),
                NamedTextColor.AQUA));

        Mission next = plugin.missions().registry().nextFor(plugin.store().completedMissions(player.getUniqueId()));
        if (next != null) {
            player.sendMessage(Component.text("הבאה בתור: " + next.name() + " - " + next.brief(),
                    NamedTextColor.YELLOW));
        }
    }

    private static void line(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }
}
