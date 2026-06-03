package com.agurim.chemcraft.molecule;

import java.util.Map;

public record Molecule(String id, String name, String display, Map<String, Integer> atoms, String fact, String reward) {}
