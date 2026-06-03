package com.agurim.chemcraft.reaction;

import java.util.Map;

/** inputs/outputs keys: a molecule id, or "atom:<SYMBOL>" for an element atom. */
public record Reaction(String id, String label, Map<String, Integer> inputs,
                       Map<String, Integer> outputs, String effect, String fact) {}
