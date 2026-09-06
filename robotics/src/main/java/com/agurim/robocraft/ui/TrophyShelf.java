package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A row of trophies above the Component Board - one slot per mission, ghost until earned.
 *
 * <p>This exists because finishing a mission left no trace. The completion message is a title and
 * one chat line, both gone within seconds, and for the three warm-ups nothing else moved at all:
 * they grant no part, so the board did not change, and progress counts the required ladder only,
 * so the status bar did not either. A student could complete their first mission and find the
 * world exactly as they left it. The shelf is the permanent half of the answer - it is still there
 * next lesson, it is visible to anyone visiting the plot, and it costs nothing in the currency the
 * curriculum cares about, because a trophy grants no power at all.
 *
 * <p>Deliberately modelled on {@link ComponentBoard} rather than invented fresh: same ghost-&gt;lit
 * tiles, same {@link TextDisplay} labels, same idempotent rebuild. It also inherits that class's
 * hard-won chunk fix - see {@link #spawnLabel}.
 */
public class TrophyShelf {

    private final RoboCraftPlugin plugin;

    public TrophyShelf(RoboCraftPlugin plugin) { this.plugin = plugin; }

    private World world() { return plugin.plots().world(); }

    private boolean enabled() { return plugin.getConfig().getBoolean("trophies.enabled", true); }

    private int spacing() { return Math.max(1, plugin.getConfig().getInt("trophies.spacing", 2)); }
    private int perRow()  { return Math.max(1, plugin.getConfig().getInt("trophies.per-row", 4)); }

    private List<Mission> missions() { return new ArrayList<>(plugin.missions().registry().all()); }

    /**
     * Where the shelf sits: beside the board, at the same height, not above it.
     *
     * <p>The first draft put it two blocks above the board's top row, and the first person to
     * stand in front of a plot did not see it at all - then, told where to look, said it "looks
     * like part of the wall". Two separate mistakes. It was above eye level, so it was never in
     * shot; and an unearned trophy was the same grey stained glass as a locked board tile, so even
     * once found it read as a fourth row of the parts wall rather than a different object.
     *
     * <p>Everything here is derived from the board's own layout rather than configured absolutely,
     * because a bundled default is only copied to a server that has no {@code config.yml} yet -
     * the class servers run {@code board.offset-y: -60} against a shipped default of 65. Deriving
     * it means the shelf lands correctly beside whatever wall a server actually has.
     */
    private int boardOffsetY()   { return plugin.getConfig().getInt("board.offset-y", 65); }
    private int boardSpacing()   { return Math.max(1, plugin.getConfig().getInt("board.tile-spacing", 2)); }
    private int boardPerRow()    { return Math.max(1, plugin.getConfig().getInt("board.per-row", 6)); }

    /** First column of the shelf: clear of the board's right-hand edge, plus a gap of empty wall. */
    private int startX() {
        return plugin.getConfig().getInt("board.offset-x", 8)
                + boardPerRow() * boardSpacing()
                + Math.max(1, plugin.getConfig().getInt("trophies.gap", 3));
    }

    /** World location of trophy slot i. Index 0 is top-left, reading like the board. */
    public Location slotLocation(int plotIndex, int i) {
        Location corner = plugin.plots().plotCorner(plotIndex);
        int oz = plugin.getConfig().getInt("trophies.offset-z",
                plugin.getConfig().getInt("board.offset-z", 4));
        int rows = Math.max(1, (int) Math.ceil(missions().size() / (double) perRow()));
        int col = i % perRow();
        int row = i / perRow();
        return new Location(world(),
                corner.getBlockX() + startX() + col * spacing(),
                // Bottom row level with the board's bottom row, so the whole shelf is at eye height.
                boardOffsetY() + (rows - 1 - row) * boardSpacing(),
                corner.getBlockZ() + oz);
    }

    /**
     * The block a trophy shows.
     *
     * <p>An empty slot is deliberately NOT the board's grey stained glass. Sharing that block made
     * the shelf invisible as a separate object - the whole point of it is to be a different thing
     * you have earned, so it has to look like one before anything is earned at all.
     */
    public static Material trophyBlock(boolean earned, boolean warmUp) {
        if (!earned) return Material.BLACK_STAINED_GLASS;
        return warmUp ? Material.IRON_BLOCK : Material.GOLD_BLOCK;
    }

    /** (Re)build every trophy slot for this plot, lit according to what the owner has completed. */
    public void build(int plotIndex, UUID owner) {
        if (!enabled()) return;
        Set<String> done = owner == null ? Set.of() : plugin.store().completedMissions(owner);
        List<Mission> missions = missions();
        for (int i = 0; i < missions.size(); i++) {
            Mission m = missions.get(i);
            boolean earned = done.contains(m.id());
            Location loc = slotLocation(plotIndex, i);
            world().getBlockAt(loc).setType(trophyBlock(earned, m.optional()));
            spawnLabel(m, loc, earned);
        }
    }

    /** Light exactly one trophy, for the moment a mission is completed. */
    public void award(int plotIndex, Mission mission) {
        if (!enabled()) return;
        List<Mission> missions = missions();
        for (int i = 0; i < missions.size(); i++) {
            if (!missions.get(i).id().equals(mission.id())) continue;
            Location loc = slotLocation(plotIndex, i);
            world().getBlockAt(loc).setType(trophyBlock(true, mission.optional()));
            spawnLabel(mission, loc, true);
            return;
        }
    }

    private Location labelAnchor(Location slot) { return slot.clone().add(0.5, 0.5, 1.3); }

    /** Same derivation as the board's: keep a label inside the pitch of its own slot. */
    private int labelWidth() { return Math.max(40, spacing() * 40 - 12); }

    private void spawnLabel(Mission mission, Location slot, boolean earned) {
        // Copied deliberately from ComponentBoard: a shelf can be rebuilt while the plot chunk is
        // unloaded, and getNearbyEntities cannot see entities that are not loaded. Chunk#getEntities
        // is the call that sync-loads them; Chunk#load() alone is not enough. Without this the old
        // label is never found, never removed, and every rebuild leaves another copy behind.
        labelAnchor(slot).getChunk().getEntities();
        removeLabel(mission, slot);

        world().spawn(labelAnchor(slot), TextDisplay.class, td -> {
            Component text = Component.text(mission.name(),
                    earned ? NamedTextColor.GOLD : NamedTextColor.GRAY);
            if (!earned) text = text.append(Component.text("\nטרם הושלמה", NamedTextColor.DARK_GRAY));
            td.text(text);
            td.setLineWidth(labelWidth());
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(earned ? 15 : 4, earned ? 15 : 4));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.trophyKey(), PersistentDataType.STRING, mission.id());
        });
    }

    private void removeLabel(Mission mission, Location slot) {
        for (Entity ent : world().getNearbyEntities(labelAnchor(slot), 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String id = td.getPersistentDataContainer().get(plugin.trophyKey(), PersistentDataType.STRING);
                if (mission.id().equals(id)) td.remove();
            }
        }
    }
}
