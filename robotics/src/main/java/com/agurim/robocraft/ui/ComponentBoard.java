package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.part.Part;
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
import java.util.UUID;

/**
 * The Component Board: a per-plot ghost -&gt; lit wall, one tile per part.
 *
 * <p>ChemCraft's periodic table, transplanted. It answers "what can I build with?" and "what am I
 * working towards?" in one glance, and an unlocked tile shows the part's real block, so the wall
 * doubles as a legend for what you are looking at when you find one in the world.
 */
public class ComponentBoard {

    private final RoboCraftPlugin plugin;

    public ComponentBoard(RoboCraftPlugin plugin) { this.plugin = plugin; }

    private World world() { return plugin.plots().world(); }

    private int perRow()  { return Math.max(1, plugin.getConfig().getInt("board.per-row", 6)); }
    private int spacing() { return Math.max(1, plugin.getConfig().getInt("board.tile-spacing", 2)); }

    private List<Part> parts() { return new ArrayList<>(plugin.parts().all()); }

    /** World location of tile i on a plot's board. Index 0 sits top-left. */
    public Location tileLocation(int plotIndex, int i) {
        Location corner = plugin.plots().plotCorner(plotIndex);
        int ox = plugin.getConfig().getInt("board.offset-x", 8);
        int oy = plugin.getConfig().getInt("board.offset-y", 65);
        int oz = plugin.getConfig().getInt("board.offset-z", 4);
        int rows = (int) Math.ceil(parts().size() / (double) perRow());
        int col = i % perRow();
        int row = i / perRow();
        return new Location(world(),
                corner.getBlockX() + ox + col * spacing(),
                oy + (rows - 1 - row) * spacing(),
                corner.getBlockZ() + oz);
    }

    /**
     * Where a student should arrive: a few blocks out from the middle of their board, facing it.
     *
     * <p>The plot centre is the obvious spot and it is the wrong one. A plot is 96 blocks across
     * and the board sits 8 from the corner, so arriving at the centre drops a first-timer in an
     * empty field some forty blocks from the only thing on their plot - while the welcome text
     * tells them the wall is in front of them.
     */
    public Location arrivalSpot(int plotIndex) {
        Location corner = plugin.plots().plotCorner(plotIndex);
        int ox = plugin.getConfig().getInt("board.offset-x", 8);
        int oz = plugin.getConfig().getInt("board.offset-z", 4);
        int columns = Math.min(perRow(), Math.max(1, plugin.parts().size()));

        Location spot = new Location(world(),
                corner.getBlockX() + ox + (columns - 1) * spacing() / 2.0 + 0.5,
                corner.getBlockY() + 1,
                corner.getBlockZ() + oz + 6.5);
        spot.setYaw(180f);      // look back along -z, at the board
        spot.setPitch(0f);
        return spot;
    }

    /** (Re)build every tile for this plot, lit according to what the owner has unlocked. */
    public void build(int plotIndex, UUID owner) {
        List<Part> parts = parts();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            boolean unlocked = owner != null && plugin.store().isUnlocked(owner, part.id());
            Location loc = tileLocation(plotIndex, i);
            world().getBlockAt(loc).setType(unlocked ? part.block() : Material.GRAY_STAINED_GLASS);
            spawnLabel(part, loc, unlocked);
        }
    }

    /** The part whose tile occupies this block on the given plot's board, or null. */
    public Part partAt(int plotIndex, Location loc) {
        List<Part> parts = parts();
        for (int i = 0; i < parts.size(); i++) {
            Location t = tileLocation(plotIndex, i);
            if (t.getBlockX() == loc.getBlockX()
                    && t.getBlockY() == loc.getBlockY()
                    && t.getBlockZ() == loc.getBlockZ()) return parts.get(i);
        }
        return null;
    }

    public boolean isBoardTile(int plotIndex, Location loc) {
        return partAt(plotIndex, loc) != null;
    }

    private Location labelAnchor(Location tile) { return tile.clone().add(0.5, 0.5, 1.3); }

    /**
     * Wrap width for a tile label, in pixels, derived from the tile pitch so the two cannot drift
     * apart. A text display renders at 40 pixels to the block, so a spacing of 2 gives 80 pixels
     * of room; the margin keeps neighbouring labels from touching.
     *
     * <p>Without this a label ran as wide as it liked - the default is 200 pixels, five blocks -
     * on a grid pitched at two. Found by playtesting: the first person to stand at a board could
     * not read it, because every tile was written over by the two beside it.
     */
    private int labelWidth() { return Math.max(40, spacing() * 40 - 12); }

    private void spawnLabel(Part part, Location tile, boolean unlocked) {
        // Board updates can arrive while the plot chunk is unloaded. getNearbyEntities cannot see
        // unloaded entities, so force the chunk's entities in first - Chunk#getEntities() is the
        // call that sync-loads entity data, Chunk#load() alone is not enough.
        labelAnchor(tile).getChunk().getEntities();
        removeLabel(part, tile);

        final String locked = unlocked ? null : unlockedBy(part);
        world().spawn(labelAnchor(tile), TextDisplay.class, td -> {
            // Name only, plus what unlocks a locked tile. The range and the hint used to be
            // here too, which made every label far wider than the tile it belonged to - and
            // they were never needed here: PartItems already puts range, capacity, drain and
            // hint on the item's own tooltip, where there is room and no neighbour to collide
            // with. A board answers "what have I got, what am I working towards" at a glance;
            // the detail belongs on the part itself.
            Component text = Component.text(part.name(), unlocked ? NamedTextColor.WHITE : NamedTextColor.GRAY);
            if (!unlocked) {
                text = text.append(Component.text("\nנעול", NamedTextColor.DARK_GRAY));
                if (locked != null) {
                    text = text.append(Component.text("\nמשימה: " + locked, NamedTextColor.YELLOW));
                }
            }
            td.text(text);
            td.setLineWidth(labelWidth());
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(unlocked ? 15 : 4, unlocked ? 15 : 4));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.tileKey(), PersistentDataType.STRING, part.id());
        });
    }

    /** Name of the mission that hands out this part, for the locked-tile hint. */
    private String unlockedBy(Part part) {
        for (Mission m : plugin.missions().registry().all()) {
            if (m.reward().contains(part.id())) return m.name();
        }
        return null;
    }

    private void removeLabel(Part part, Location tile) {
        for (Entity ent : world().getNearbyEntities(labelAnchor(tile), 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String id = td.getPersistentDataContainer().get(plugin.tileKey(), PersistentDataType.STRING);
                if (part.id().equals(id)) td.remove();
            }
        }
    }
}
