package com.agurim.robocraft.mission;

import java.util.Set;

/**
 * A Minecraft power a mission pays in - {@code perks:} in missions.yml (docs/DESIGN.md 4.12).
 *
 * <p>Written as {@code speed}, {@code speed:2}, {@code pet:wolf}, {@code wild}. Perks, not gates:
 * a pass grants one for keeps, the first real bench check lends it for a few minutes, and a
 * student who cannot crack a mission loses nothing they had. {@code wild} is the one soft gate -
 * the open land opens after the first warm-up - and the only perk that is not a power.
 *
 * <p>Bukkit-free; {@link com.agurim.robocraft.perk.PerkService} is the half that touches a player.
 */
public record Perk(Kind kind, String arg, int level) {

    public enum Kind {
        WILD("wild"), SPEED("speed"), HASTE("haste"), JUMP("jump"), NIGHT_VISION("night-vision"),
        FLY_PLOT("fly-plot"), FLY("fly"), ELYTRA("elytra"), PET("pet");

        public final String id;

        Kind(String id) { this.id = id; }

        static Kind byId(String id) {
            for (Kind k : values()) if (k.id.equals(id)) return k;
            return null;
        }

        public boolean potion() { return this == SPEED || this == HASTE || this == JUMP || this == NIGHT_VISION; }
        public boolean flight() { return this == FLY_PLOT || this == FLY; }

        /** Can it be lent for ten minutes? A gate, an item and an animal cannot be taken back. */
        public boolean tastable() { return potion() || flight(); }
    }

    public static final Set<String> PETS = Set.of("wolf", "cat", "parrot", "horse");

    /** {@code speed}, {@code speed:2} (level 1-3), {@code pet:wolf}; null for anything else. */
    public static Perk parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        String id = s, arg = "";
        int colon = s.indexOf(':');
        if (colon >= 0) { id = s.substring(0, colon); arg = s.substring(colon + 1); }
        Kind kind = Kind.byId(id);
        if (kind == null) return null;
        int level = 1;
        if (kind == Kind.PET) {
            if (!PETS.contains(arg)) return null;
        } else if (!arg.isEmpty()) {
            if (!kind.potion()) return null;
            try { level = Integer.parseInt(arg); } catch (NumberFormatException e) { return null; }
            if (level < 1 || level > 3) return null;
            arg = "";
        }
        return new Perk(kind, arg, level);
    }

    /** The id a held set is keyed by: {@code speed}, {@code speed:2}, {@code pet:wolf}. */
    public String key() {
        if (kind == Kind.PET) return kind.id + ":" + arg;
        return level > 1 ? kind.id + ":" + level : kind.id;
    }

    public boolean tastable() { return kind.tastable(); }

    public String hebrew() {
        String roman = level == 2 ? " II" : level == 3 ? " III" : "";
        return switch (kind) {
            case WILD         -> "השטח הפתוח";
            case SPEED        -> "מהירות" + roman;
            case HASTE        -> "חפירה מהירה" + roman;
            case JUMP         -> "קפיצה גבוהה" + roman;
            case NIGHT_VISION -> "ראיית לילה";
            case FLY_PLOT     -> "תעופה בחלקה שלכם";
            case FLY          -> "תעופה בכל מקום";
            case ELYTRA       -> "כנפי רחיפה";
            case PET          -> petName();
        };
    }

    /** "הכלב" - the animal, with the article, for a name tag and a message. */
    public String petName() {
        return switch (arg) {
            case "cat"    -> "החתול";
            case "parrot" -> "התוכי";
            case "horse"  -> "הסוס";
            default       -> "הכלב";
        };
    }
}
