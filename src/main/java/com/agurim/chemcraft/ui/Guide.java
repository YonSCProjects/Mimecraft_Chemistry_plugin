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
                Component.text("Build the periodic table, atom by atom", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
    }

    /** Print the step-by-step gameplay loop into chat. */
    public static void send(ChemCraftPlugin plugin, Player player) {
        line(player, "==== ChemCraft - how to play ====", NamedTextColor.AQUA);
        line(player, "Goal: light up every tile on your periodic table wall.", NamedTextColor.WHITE);
        line(player, "1. EXTRACT atoms at the stations next to your wall - right-click a station block:", NamedTextColor.YELLOW);
        line(player, "   Copper Block = Panning | Blue Ice = Air | Blast Furnace = Smelter", NamedTextColor.GRAY);
        line(player, "   Sea Lantern = Electrolysis | Furnace = Reactor", NamedTextColor.GRAY);
        line(player, "2. PLACE atom blocks in the world. Atoms that touch form bonds.", NamedTextColor.YELLOW);
        line(player, "3. BOND: sneak + right-click an atom's face to cycle single/double/triple.", NamedTextColor.YELLOW);
        line(player, "   Satisfy every atom's wanted bonds and you build a molecule!", NamedTextColor.GRAY);
        line(player, "4. STACK 8 of the same atom into a 2x2x2 cube to crystallize a material (e.g. diamond).", NamedTextColor.YELLOW);
        line(player, "5. REACT molecules together at the Furnace to make new compounds.", NamedTextColor.YELLOW);
        line(player, "Tip: your starter kit holds raw materials - smelt and split them into atoms.", NamedTextColor.GREEN);
        line(player, "Commands: /cc guide | /cc kit | /cc tp", NamedTextColor.DARK_AQUA);
        sendProgress(plugin, player);
    }

    /** A one-line "you've found X of Y elements" progress nudge. */
    public static void sendProgress(ChemCraftPlugin plugin, Player player) {
        int found = plugin.store().getDiscovered(player.getUniqueId()).size();
        int total = plugin.registry().all().size();
        player.sendMessage(Component.text("Elements discovered: " + found + " / " + total, NamedTextColor.AQUA));
    }

    private static void line(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }
}
