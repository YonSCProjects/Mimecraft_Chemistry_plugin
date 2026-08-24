package com.agurim.robocraft.program;

/** The comparison in a rule's condition. Kept in Latin - it is a symbol, not player-facing prose. */
public enum Op {
    LT("<"), LE("<="), EQ("="), NE("!="), GE(">="), GT(">");

    private final String symbol;

    Op(String symbol) { this.symbol = symbol; }

    public String symbol() { return symbol; }

    public boolean test(int a, int b) {
        return switch (this) {
            case LT -> a <  b;
            case LE -> a <= b;
            case EQ -> a == b;
            case NE -> a != b;
            case GE -> a >= b;
            case GT -> a >  b;
        };
    }

    public Op next() { return values()[(ordinal() + 1) % values().length]; }
}
