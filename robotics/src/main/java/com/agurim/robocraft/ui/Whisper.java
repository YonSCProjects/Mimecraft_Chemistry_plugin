package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plugin-side private messaging, so an external agent (the workshop assistant) can talk to ONE
 * student.
 *
 * <p>Why this exists: on Paper 26.2 the console/RCON commands {@code tell}, {@code tellraw} and
 * {@code msg} execute silently and deliver nothing - no error, no log line, no message. Only
 * {@code say} (which broadcasts to the whole class) and {@code title}/{@code actionbar} work. Since
 * the entire point of an assistant is a PRIVATE answer, delivery has to happen inside the plugin,
 * which messages players fine. ChemCraft found this the hard way; this is the same fix.
 *
 * <p>Channels: chat (default), actionbar (a transient nudge above the hotbar), title (a big
 * centre-screen beat). All are addressed to a single player.
 */
public final class Whisper {

    private Whisper() {}

    /** How the assistant's voice is labelled, so it never reads as a classmate. */
    private static Component prefix(RoboCraftPlugin plugin) {
        return Component.text("[" + plugin.getConfig().getString("assistant.name", "עוזר/ת סדנה") + "] ",
                NamedTextColor.LIGHT_PURPLE);
    }

    /** Send a private chat line. Returns false if the player is not online. */
    public static boolean chat(RoboCraftPlugin plugin, Player target, String text) {
        if (target == null) return false;
        target.sendMessage(prefix(plugin).append(Component.text(text, NamedTextColor.WHITE)));
        if (plugin.getConfig().getBoolean("assistant.sound", true)) {
            target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f);
        }
        return true;
    }

    /** A transient line above the hotbar - good for a nudge that must not spam chat. */
    public static boolean actionbar(RoboCraftPlugin plugin, Player target, String text) {
        if (target == null) return false;
        target.sendActionBar(Component.text(text, NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** A centre-screen title (title + optional subtitle, separated by " | "). */
    public static boolean title(RoboCraftPlugin plugin, Player target, String text) {
        if (target == null) return false;
        String head = text, sub = "";
        int split = text.indexOf(" | ");
        if (split >= 0) { head = text.substring(0, split); sub = text.substring(split + 3); }
        target.showTitle(net.kyori.adventure.title.Title.title(
                Component.text(head, NamedTextColor.LIGHT_PURPLE),
                Component.text(sub, NamedTextColor.WHITE),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(200),
                        java.time.Duration.ofSeconds(4),
                        java.time.Duration.ofMillis(600))));
        return true;
    }

    /** Resolve a target by exact name among ONLINE players (offline-mode safe: no UUID guessing). */
    public static Player find(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /** Deliver on the named channel. Unknown channel falls back to chat. */
    public static boolean send(RoboCraftPlugin plugin, Player target, String channel, String text) {
        return switch (channel == null ? "chat" : channel.toLowerCase()) {
            case "actionbar" -> actionbar(plugin, target, text);
            case "title"     -> title(plugin, target, text);
            default          -> chat(plugin, target, text);
        };
    }
}
