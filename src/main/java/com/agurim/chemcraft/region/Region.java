package com.agurim.chemcraft.region;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * A shared themed gathering zone, loaded from regions.yml. Ids match the region: values in
 * elements.yml. An X/Z box (all heights, same model as plots) holding gather nodes and stations.
 */
public record Region(
        String id, String name, int x, int z, int size,
        Material floor, List<String> stations, Map<Material, Gather> gather
) {

    /** A gatherable node: break the source block -> drop items, regrow after regen seconds. */
    public record Gather(Material drop, int count, int regenSeconds, Material placeholder) {}

    public boolean contains(int bx, int bz) {
        return bx >= x && bx < x + size && bz >= z && bz < z + size;
    }
}
