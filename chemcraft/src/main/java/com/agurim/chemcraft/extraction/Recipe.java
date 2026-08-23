package com.agurim.chemcraft.extraction;

import java.util.Map;

/**
 * A single extraction process at a station.
 * inputs keys are vanilla Material names OR "atom:<SYMBOL>" for atom inputs.
 * outputs keys are element symbols.
 */
public record Recipe(
        String id, String station, String icon, String label,
        Map<String, Integer> inputs, Map<String, Integer> outputs
) {}
