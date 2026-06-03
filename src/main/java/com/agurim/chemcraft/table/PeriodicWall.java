package com.agurim.chemcraft.table;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.Element;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

/** Builds and updates a per-plot physical periodic table out of glass tiles + floating labels. */
public class PeriodicWall {

    private static final int MAX_PERIOD = 6; // our element set tops out at period 6 (Au)
    private final ChemCraftPlugin plugin;

    public PeriodicWall(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private World world() { return plugin.plots().world(); }

    /** World location of the tile block for an element on a given plot's wall. */
    public Location tileLocation(int plotIndex, Element e) {
        Location corner = plugin.plots().plotCorner(plotIndex);
        int ox = plugin.getConfig().getInt("wall.offset-x", 8);
        int oy = plugin.getConfig().getInt("wall.offset-y", 65);
        int oz = plugin.getConfig().getInt("wall.offset-z", 4);
        int sp = plugin.getConfig().getInt("wall.tile-spacing", 2);
        int x = corner.getBlockX() + ox + (e.group() - 1) * sp;
        int y = oy + (MAX_PERIOD - e.period()) * sp;     // period 1 sits at the top
        int z = corner.getBlockZ() + oz;
        return new Location(world(), x, y, z);
    }

    private Material familyMaterial(String family) {
        return switch (family) {
            case "noble"           -> Material.PURPLE_STAINED_GLASS;
            case "alkali"          -> Material.RED_STAINED_GLASS;
            case "alkaline_earth"  -> Material.ORANGE_STAINED_GLASS;
            case "metalloid"       -> Material.YELLOW_STAINED_GLASS;
            case "halogen"         -> Material.LIGHT_BLUE_STAINED_GLASS;
            case "post_transition" -> Material.PINK_STAINED_GLASS;
            case "transition"      -> Material.BROWN_STAINED_GLASS;
            default                -> Material.LIME_STAINED_GLASS; // nonmetal
        };
    }

    /** (Re)build every tile as an unlit "ghost". */
    public void build(int plotIndex) {
        for (Element e : plugin.registry().all()) {
            Location loc = tileLocation(plotIndex, e);
            world().getBlockAt(loc).setType(Material.GRAY_STAINED_GLASS);
            spawnLabel(e, loc, false);
        }
    }

    /** Light a single element's tile: family colour + bright label. */
    public void lightUp(int plotIndex, String symbol) {
        Element e = plugin.registry().get(symbol);
        if (e == null) return;
        Location loc = tileLocation(plotIndex, e);
        world().getBlockAt(loc).setType(familyMaterial(e.family()));
        spawnLabel(e, loc, true);
    }

    private Location labelAnchor(Location tile) {
        // hover just off the north (-z) face, where players read the wall from
        return tile.clone().add(0.5, 0.5, -0.3);
    }

    private void spawnLabel(Element e, Location tile, boolean lit) {
        removeLabel(e, tile);
        world().spawn(labelAnchor(tile), TextDisplay.class, td -> {
            td.text(Component.text(e.symbol(), lit ? NamedTextColor.WHITE : NamedTextColor.GRAY)
                    .append(Component.text("\n" + e.number(), NamedTextColor.GRAY)));
            td.setBillboard(Display.Billboard.CENTER);          // always faces the reader
            td.setBrightness(new Display.Brightness(lit ? 15 : 4, lit ? 15 : 4));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.tileKey(), PersistentDataType.STRING, e.symbol());
        });
    }

    private void removeLabel(Element e, Location tile) {
        for (Entity ent : world().getNearbyEntities(labelAnchor(tile), 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String sym = td.getPersistentDataContainer().get(plugin.tileKey(), PersistentDataType.STRING);
                if (e.symbol().equals(sym)) td.remove();
            }
        }
    }

    /** True if this block is one of the plot's wall tiles (so we can protect it). */
    public boolean isWallTile(int plotIndex, Location loc) {
        for (Element e : plugin.registry().all()) {
            Location t = tileLocation(plotIndex, e);
            if (t.getBlockX() == loc.getBlockX()
                    && t.getBlockY() == loc.getBlockY()
                    && t.getBlockZ() == loc.getBlockZ()) return true;
        }
        return false;
    }
}
