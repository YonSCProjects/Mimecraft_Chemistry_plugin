package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.plot.PadBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * What belongs to a student, and what is just the ground.
 *
 * <p>The first rule was "you may only touch your own plot", and on a flat world that cost
 * nothing: there was nothing on anyone else's plot but their robot. Real terrain changed it.
 * Tuval's class, 2026-09-15, were upset that they could not dig on a classmate's land - and
 * they were right to be. Digging is most of what this age group wants from Minecraft, and a
 * hill nobody made is not anybody's property.
 *
 * <p>So a visitor may now <b>break the land</b> on another student's plot, and may not touch
 * <b>anyone's work</b>: a RoboCraft part, a block a person placed ({@link
 * com.agurim.robocraft.plot.BuildLog}), or the workshop pad with its board, shelf and charging
 * pad. Building on someone else's plot stays closed, so a plot cannot be walled in or towered
 * over; a hole a visitor digs is the owner's to fill, on their own plot, where they may do
 * anything. Both halves are config (`plot-shield.dig-natural`, `plot-shield.visitor-build`).
 *
 * <p>NORMAL priority, so the listeners that must run after protection can use HIGH +
 * ignoreCancelled. Failing is deliberately undramatic - a thunk and a barrier flash so the wall
 * feels solid, and nothing is ever damaged, dropped, or lost. ChemCraft learned that a dramatic
 * punishment turns griefing into a toy for this age group.
 */
public class PlotProtection implements Listener {

    private final RoboCraftPlugin plugin;

    public PlotProtection(RoboCraftPlugin plugin) { this.plugin = plugin; }

    private boolean digNatural()  { return plugin.getConfig().getBoolean("plot-shield.dig-natural", true); }
    private boolean visitorBuild() { return plugin.getConfig().getBoolean("plot-shield.visitor-build", false); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Player player = event.getPlayer();
        int plot = plugin.plots().plotIndexAt(loc);

        if (plot >= 0 && plot != own(player) && !player.isOp()) {
            // Someone else's plot. The land is fair game; their work is not.
            if (!digNatural()) {
                event.setCancelled(true);
                deny(player, loc, "זו הסדנה של מישהו אחר. /rc tp מחזיר אתכם לשלכם.");
                return;
            }
            String whose = theirWork(plot, loc);
            if (whose != null) {
                event.setCancelled(true);
                deny(player, loc, whose);
                return;
            }
            plugin.builds().remove(loc);   // land, and now it is gone
            return;
        }

        // Your own plot, or outside the grid: the workshop furniture is still furniture.
        if (plot >= 0 && furniture(plot, loc)) {
            event.setCancelled(true);
            deny(player, loc, "זה חלק מהסדנה - אי אפשר לפרק אותו.");
            return;
        }
        plugin.builds().remove(loc);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        Player player = event.getPlayer();
        int plot = plugin.plots().plotIndexAt(loc);

        if (plot >= 0 && plot != own(player) && !player.isOp() && !visitorBuild()) {
            event.setCancelled(true);
            deny(player, loc, digNatural()
                    ? "בחלקה של מישהו אחר אפשר לחפור - אבל לא לבנות."
                    : "זו הסדנה של מישהו אחר. /rc tp מחזיר אתכם לשלכם.");
            return;
        }
        // Remember it, so nobody else can take it apart - and so the land stays telling the truth.
        plugin.builds().add(loc);
    }

    private int own(Player player) {
        return plugin.store().getOrAssignPlotIndex(player.getUniqueId());
    }

    /**
     * Why a visitor may not break this block, or null if it is only the ground.
     *
     * <p>Three kinds of "somebody's work", in the order a student meets them: a part of a robot,
     * a block a person placed, and the workshop pad the plugin carved and furnished.
     */
    private String theirWork(int plot, Location loc) {
        if (plugin.placements().get(loc) != null) return "זה רכיב של מישהו אחר.";
        if (plugin.builds().contains(loc))        return "מישהו בנה את זה. את הקרקע אפשר לחפור.";
        if (furniture(plot, loc) || PadBuilder.onPad(plugin, plot, loc)) {
            return "זו הסדנה של מישהו אחר. את הקרקע מסביב אפשר לחפור.";
        }
        return null;
    }

    /** The board, the trophy shelf and the charging pad: furniture on anybody's plot, owner included. */
    private boolean furniture(int plot, Location loc) {
        return plugin.board().isBoardTile(plot, loc)
                || plugin.trophies().isTrophySlot(plot, loc)
                || plugin.kiosk().isKioskBlock(plot, loc);
    }

    private void deny(Player player, Location loc, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
        if (plugin.getConfig().getBoolean("plot-shield.particles", true)) {
            player.spawnParticle(Particle.BLOCK_MARKER, loc.clone().add(0.5, 0.5, 0.5), 1,
                    org.bukkit.Material.BARRIER.createBlockData());
        }
    }
}
