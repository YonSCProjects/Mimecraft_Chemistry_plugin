package com.agurim.chemcraft.element;

import org.bukkit.Material;

/** Maps an element family to the Tier-1 block/glass colour used for its atom and tile. */
public final class Families {
    private Families() {}

    public static Material material(String family) {
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
}
