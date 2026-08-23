package com.agurim.chemcraft.element;

/** A single element loaded from elements.yml. */
public record Element(
        String symbol, String name, int number,
        int group, int period, String family, int valence,
        String region, String raw, String extraction, String fact
) {}
