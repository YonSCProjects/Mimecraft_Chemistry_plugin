package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.plot.BuildLog;
import com.agurim.robocraft.plot.PadBuilder;
import com.agurim.robocraft.ui.MissionCard;
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
 * <p>So there are three kinds of place:
 * <ul>
 *   <li><b>Your plot:</b> anything, except taking the workshop furniture apart.</li>
 *   <li><b>A classmate's plot:</b> dig the land; never a part, a placed block, or their pad; no
 *       building, so nobody is walled in or towered over.</li>
 *   <li><b>Open land</b> - the gaps and everything outside the grid ({@link
 *       com.agurim.robocraft.plot.Commons}, {@code /rc wild}): build anything, dig anything, but
 *       never take apart a block somebody else placed.</li>
 * </ul>
 * {@link BuildLog} is what tells work from land, and whose work it is.
 *
 * <p>NORMAL priority, so the listeners that must run after protection can use HIGH +
 * ignoreCancelled. Failing is deliberately undramatic - a thunk and a barrier flash so the wall
 * feels solid, and nothing is ever damaged, dropped, or lost. ChemCraft learned that a dramatic
 * punishment turns griefing into a toy for this age group.
 */
public class PlotProtection implements Listener {

    private final RoboCraftPlugin plugin;

    public PlotProtection(RoboCraftPlugin plugin) { this.plugin = plugin; }

    private boolean digNatural()   { return plugin.getConfig().getBoolean("plot-shield.dig-natural", true); }
    private boolean visitorBuild() { return plugin.getConfig().getBoolean("plot-shield.visitor-build", false); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Player player = event.getPlayer();
        int plot = plugin.plots().plotIndexAt(loc);
        int mine = own(player);

        if (plot >= 0 && plot != mine && !player.isOp()) {
            // Someone else's plot. The land is fair game; their work is not.
            if (!digNatural()) {
                event.setCancelled(true);
                deny(player, loc, Component.text("זו הסדנה של מישהו אחר.", NamedTextColor.RED), true);
                return;
            }
            String whose = theirWork(mine, plot, loc);
            if (whose != null) {
                event.setCancelled(true);
                deny(player, loc, Component.text(whose, NamedTextColor.RED), false);
                return;
            }
            plugin.builds().remove(loc);   // land, and now it is gone
            return;
        }

        if (plot >= 0) {
            // Your own plot: the workshop furniture is still furniture.
            if (furniture(plot, loc)) {
                event.setCancelled(true);
                deny(player, loc, Component.text("זה חלק מהסדנה - אי אפשר לפרק אותו.", NamedTextColor.RED), false);
                return;
            }
        } else if (!player.isOp()) {
            // Open land: build and dig freely, but a block somebody else put there is theirs.
            Integer by = plugin.builds().owner(loc);
            if (by != null && by != BuildLog.UNKNOWN && by != mine) {
                event.setCancelled(true);
                deny(player, loc, Component.text("מישהו אחר בנה את זה. בנו לידו.", NamedTextColor.RED), false);
                return;
            }
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
            // The moment a student learns they cannot build here is the moment to show where
            // they can - both places, one click each.
            deny(player, loc, Component.text(digNatural()
                    ? "בחלקה של מישהו אחר אפשר לחפור - לא לבנות."
                    : "זו הסדנה של מישהו אחר.", NamedTextColor.RED), true);
            return;
        }
        // Remember it and whose it is, so nobody else can take it apart.
        plugin.builds().add(loc, own(player));
    }

    private int own(Player player) {
        return plugin.store().getOrAssignPlotIndex(player.getUniqueId());
    }

    /**
     * Why a visitor may not break this block on a classmate's plot, or null if it is only the
     * ground - or something the visitor placed themselves, which only happens when a teacher has
     * opened visitor building.
     */
    private String theirWork(int mine, int plot, Location loc) {
        if (plugin.placements().get(loc) != null) return "זה רכיב של מישהו אחר.";
        Integer by = plugin.builds().owner(loc);
        if (by != null && by != mine)              return "מישהו בנה את זה. את הקרקע אפשר לחפור.";
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

    private void deny(Player player, Location loc, Component message, boolean showWays) {
        if (showWays) {
            message = message.append(Component.text("  "))
                    .append(MissionCard.button("שטח פתוח", "/rc wild", "מקום שאינו חלקה של אף אחד - בונים שם חופשי", NamedTextColor.GREEN))
                    .append(Component.text(" "))
                    .append(MissionCard.button("לחלקה שלי", "/rc tp", "חזרה לחלקה ולסדנה", NamedTextColor.AQUA));
        }
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
        if (plugin.getConfig().getBoolean("plot-shield.particles", true)) {
            player.spawnParticle(Particle.BLOCK_MARKER, loc.clone().add(0.5, 0.5, 0.5), 1,
                    org.bukkit.Material.BARRIER.createBlockData());
        }
    }
}
