package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * The player-facing "how to play" text. Shown once on first join and any time via
 * {@code /chemcraft guide}. Kept in one place so the welcome flow and the command stay in sync.
 */
public final class Guide {
    private Guide() {}

    /** A short on-screen title shown when a player first arrives. */
    public static void welcome(Player player) {
        player.showTitle(Title.title(
                Component.text("ChemCraft", NamedTextColor.AQUA),
                Component.text("בנו את הטבלה המחזורית, אטום אחר אטום", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
    }

    /** Print the step-by-step gameplay loop into chat. */
    public static void send(ChemCraftPlugin plugin, Player player) {
        line(player, "==== ChemCraft - איך משחקים ====", NamedTextColor.AQUA);
        line(player, "המטרה: להאיר כל משבצת בקיר הטבלה המחזורית שלכם.", NamedTextColor.WHITE);
        line(player, "1. הפיקו אטומים בעמדות שליד הקיר - לחיצה ימנית על בלוק עמדה:", NamedTextColor.YELLOW);
        line(player, "   בלוק נחושת = ניפוי | קרח כחול = אוויר | כבשן היתוך = מתיך", NamedTextColor.GRAY);
        line(player, "   פנס ים = אלקטרוליזה | כבשן = כור", NamedTextColor.GRAY);
        line(player, "2. הניחו בלוקי אטום בעולם. אטומים שנוגעים יוצרים קשרים.", NamedTextColor.YELLOW);
        line(player, "3. קשרים: כיפוף (Shift) + לחיצה ימנית על פאת אטום כדי להחליף בודד/כפול/משולש.", NamedTextColor.YELLOW);
        line(player, "   ספקו לכל אטום את מספר הקשרים שהוא רוצה - ובניתם מולקולה!", NamedTextColor.GRAY);
        line(player, "4. סדרו 8 אטומים זהים בקובייה 2x2x2 כדי לגבש חומר (למשל יהלום).", NamedTextColor.YELLOW);
        line(player, "5. הגיבו מולקולות יחד בכבשן (כור) כדי ליצור תרכובות חדשות.", NamedTextColor.YELLOW);
        line(player, "טיפ: בערכת הפתיחה שלכם יש חומרי גלם - התיכו ופרקו אותם לאטומים.", NamedTextColor.GREEN);
        line(player, "פקודות: /cc guide | /cc kit | /cc tp", NamedTextColor.DARK_AQUA);
        sendProgress(plugin, player);
    }

    /** A one-line "you've found X of Y elements" progress nudge. */
    public static void sendProgress(ChemCraftPlugin plugin, Player player) {
        int found = plugin.store().getDiscovered(player.getUniqueId()).size();
        int total = plugin.registry().all().size();
        player.sendMessage(Component.text("יסודות שהתגלו: " + found + " / " + total, NamedTextColor.AQUA));
    }

    private static void line(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }
}
