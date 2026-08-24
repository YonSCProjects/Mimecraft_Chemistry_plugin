package com.agurim.robocraft.program;

/**
 * The right-hand side of a comparison, or the argument of an action: either a literal number or a
 * reference to another value (a sensor port, a memory slot, TIME).
 *
 * <p>Allowing a source here is what makes edge detection possible - {@code M2 SET S1} remembers
 * this tick's reading so the next tick can compare against it - and it costs almost nothing.
 */
public record Operand(boolean constant, int value, String source) {

    public static Operand of(int v)        { return new Operand(true, v, ""); }
    public static Operand ref(String port) { return new Operand(false, 0, port); }

    public String label() { return constant ? String.valueOf(value) : source; }

    public String encode() { return constant ? "C" + value : "P" + source; }

    public static Operand decode(String s) {
        if (s == null || s.length() < 1) return of(0);
        if (s.charAt(0) == 'P') return ref(s.substring(1));
        try { return of(Integer.parseInt(s.substring(1))); }
        catch (NumberFormatException e) { return of(0); }
    }
}
