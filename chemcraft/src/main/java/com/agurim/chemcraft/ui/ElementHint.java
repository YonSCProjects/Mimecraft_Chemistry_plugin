package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.Element;
import com.agurim.chemcraft.extraction.Recipe;
import com.agurim.chemcraft.region.Region;

/**
 * "Where does this element come from?" - the answer the game already knew and never said.
 *
 * Derived from extraction.yml, NOT from Element.extraction(): those values are smelt/burn/dig/
 * captured, three of which are not station ids at all. The recipe carries a real station id
 * (-> a Hebrew station-info name) and an already-Hebrew label, so this needs zero new content.
 * Element.raw() is deliberately unused too - it is English by design.
 */
public final class ElementHint {
    private ElementHint() {}

    /** The recipe that produces this element, or null if nothing does (a content bug). */
    public static Recipe recipeFor(ChemCraftPlugin plugin, String symbol) {
        for (Recipe r : plugin.extraction().all()) {
            if (r.outputs().containsKey(symbol)) return r;
        }
        return null;
    }

    /** Hebrew display name of a station method, e.g. "מתיך". */
    public static String stationName(ChemCraftPlugin plugin, String method) {
        return plugin.getConfig().getString("station-info." + method + ".name", method);
    }

    /**
     * Which region to send them to: the element's own thematic home if that region actually
     * hosts the needed station, otherwise the first region that does (defensive - keeps the
     * hint truthful if content drifts).
     */
    public static String regionName(ChemCraftPlugin plugin, Element e, Recipe r) {
        Region home = plugin.regions().byId(e.region());
        if (home != null && (r == null || home.stations().contains(r.station()))) return home.name();
        if (r != null) {
            for (Region reg : plugin.regions().all().values()) {
                if (reg.stations().contains(r.station())) return reg.name();
            }
        }
        return (home != null) ? home.name() : e.region();
    }

    /** "במכרה, במתיך" - where and at which station. Empty string if regions are not in use. */
    public static String where(ChemCraftPlugin plugin, Element e) {
        Recipe r = recipeFor(plugin, e.symbol());
        if (plugin.regions().isEmpty()) {
            return (r != null) ? "ב" + stationName(plugin, r.station()) : "";
        }
        String reg = regionName(plugin, e, r);
        if (r == null) return "ב" + reg;
        return "ב" + reg + ", ב" + stationName(plugin, r.station());
    }

    /** "ברזל - במכרה" - the compact form for the mission bar. */
    public static String shortWhere(ChemCraftPlugin plugin, Element e) {
        Recipe r = recipeFor(plugin, e.symbol());
        String reg = plugin.regions().isEmpty()
                ? (r != null ? stationName(plugin, r.station()) : "")
                : regionName(plugin, e, r);
        return reg.isEmpty() ? e.name() : e.name() + " - ב" + reg;
    }

    /** The recipe's own Hebrew label, quoted, or "" if unknown. */
    public static String recipeLabel(ChemCraftPlugin plugin, String symbol) {
        Recipe r = recipeFor(plugin, symbol);
        return (r == null) ? "" : r.label();
    }
}
