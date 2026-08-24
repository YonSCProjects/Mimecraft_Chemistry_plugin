package com.agurim.robocraft.plot;

import com.agurim.robocraft.RoboCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * The bit of furniture every plot gets so a student can start with no op setup: a charging pad
 * next to the Component Board. Same idea as ChemCraft's station kiosk - join, and the world
 * already contains everything you need to take the first step.
 */
public class WorkshopKiosk {

    private final RoboCraftPlugin plugin;

    public WorkshopKiosk(RoboCraftPlugin plugin) { this.plugin = plugin; }

    private World world() { return plugin.plots().world(); }

    public Location chargerLocation(int plotIndex) {
        Location corner = plugin.plots().plotCorner(plotIndex);
        return new Location(world(),
                corner.getBlockX() + plugin.getConfig().getInt("kiosk.offset-x", 8),
                corner.getBlockY() + plugin.getConfig().getInt("kiosk.offset-y", 1),
                corner.getBlockZ() + plugin.getConfig().getInt("kiosk.offset-z", 1));
    }

    public void build(int plotIndex) {
        Material charger = Material.matchMaterial(
                plugin.getConfig().getString("power.charger-block", "WAXED_CHISELED_COPPER"));
        if (charger == null) return;

        Location loc = chargerLocation(plotIndex);
        world().getBlockAt(loc).setType(charger);
        spawnLabel(loc);
    }

    public boolean isKioskBlock(int plotIndex, Location loc) {
        Location c = chargerLocation(plotIndex);
        return c.getBlockX() == loc.getBlockX()
                && c.getBlockY() == loc.getBlockY()
                && c.getBlockZ() == loc.getBlockZ();
    }

    private void spawnLabel(Location block) {
        Location anchor = block.clone().add(0.5, 1.4, 0.5);
        removeLabel(anchor);
        world().spawn(anchor, TextDisplay.class, td -> {
            td.text(Component.text("עמדת טעינה", NamedTextColor.GOLD)
                    .append(Component.text("\nלחיצה ימנית טוענת רובוטים בסביבה", NamedTextColor.GRAY)));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.kioskKey(), PersistentDataType.STRING, "charger");
        });
    }

    private void removeLabel(Location anchor) {
        for (Entity ent : world().getNearbyEntities(anchor, 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td
                    && td.getPersistentDataContainer().has(plugin.kioskKey(), PersistentDataType.STRING)) {
                td.remove();
            }
        }
    }
}
