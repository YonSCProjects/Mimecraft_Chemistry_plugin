package com.agurim.robocraft.program;

/**
 * One line of the program: {@code IF <source> <op> <rhs> THEN <target> <verb> [arg]}.
 *
 * <p>Source {@code ALWAYS} makes the rule unconditional (op and rhs are ignored). Everything in a
 * rule is a Latin token - S1, A1, {@code <}, 7 - so the row reads the same in a Hebrew client.
 *
 * <p>{@code IF}, not {@code WHEN}, and the difference is the whole point of the course. The prize
 * here is transfer (docs/DESIGN.md 1): a student who later opens an Arduino sketch, a micro:bit or
 * Scratch should recognise what they already wrote. Every one of those spells the keyword
 * {@code if}. {@code WHEN} reads a shade more naturally in isolation and buys nothing later, so
 * the rule row now says the word they are going to meet again.
 */
public record Rule(String source, Op op, Operand rhs, String target, Verb verb, Operand arg) {

    public static final String ALWAYS = "ALWAYS";

    public boolean always() { return ALWAYS.equals(source); }

    public static Rule blank() {
        return new Rule(ALWAYS, Op.LT, Operand.of(0), "", Verb.ON, Operand.of(0));
    }

    public Rule withSource(String s) { return new Rule(s, op, rhs, target, verb, arg); }
    public Rule withOp(Op o)         { return new Rule(source, o, rhs, target, verb, arg); }
    public Rule withRhs(Operand o)   { return new Rule(source, op, o, target, verb, arg); }
    public Rule withTarget(String t) { return new Rule(source, op, rhs, t, verb, arg); }
    public Rule withVerb(Verb v)     { return new Rule(source, op, rhs, target, v, arg); }
    public Rule withArg(Operand o)   { return new Rule(source, op, rhs, target, verb, o); }

    /** The rule as the student reads it, e.g. "IF S1 < 7 THEN A1 ON". */
    public String text() {
        String head = always() ? ALWAYS : "IF " + source + " " + op.symbol() + " " + rhs.label();
        String tail = target.isEmpty() ? "-" : target + " " + verb
                + (verb.needsArg() ? " " + arg.label() : "");
        return head + " THEN " + tail;
    }

    /**
     * The inverse of {@link #text()}: {@code "IF S1 < 7 THEN A1 ON"} or
     * {@code "ALWAYS THEN M2 SET S1"} back into a rule, or null if the line does not parse.
     *
     * <p>This is how a mission's {@code solution:} is written in missions.yml - in the exact
     * words a student reads off the rule table, so the content file and the hint and the screen
     * all say the same thing. Strict on purpose: a verb that takes no argument refuses one, a
     * verb that needs one refuses to go without, so a typo in content is a log line at enable
     * rather than a rule that quietly does something else.
     */
    public static Rule parse(String line) {
        if (line == null) return null;
        String[] t = line.trim().toUpperCase(java.util.Locale.ROOT).split("\\s+");
        int i = 0;
        String src; Op o; Operand r;
        if (t.length >= 1 && t[0].equals(ALWAYS)) {
            src = ALWAYS; o = Op.LT; r = Operand.of(0); i = 1;
        } else if (t.length >= 4 && t[0].equals("IF")) {
            src = t[1];
            o = null;
            for (Op candidate : Op.values()) if (candidate.symbol().equals(t[2])) o = candidate;
            if (o == null) return null;
            r = operand(t[3]);
            i = 4;
        } else {
            return null;
        }
        if (i >= t.length || !t[i].equals("THEN")) return null;
        i++;
        if (i + 1 >= t.length) return null;
        String target = t[i];
        Verb v;
        try { v = Verb.valueOf(t[i + 1]); } catch (IllegalArgumentException e) { return null; }
        i += 2;
        Operand a = Operand.of(0);
        if (v.needsArg()) {
            if (i >= t.length) return null;
            a = operand(t[i++]);
        }
        if (i != t.length) return null;
        return new Rule(src, o, r, target, v, a);
    }

    private static Operand operand(String token) {
        if (token.equals("?")) return Operand.blank();
        if (token.matches("-?\\d+")) return Operand.of(Integer.parseInt(token));
        return Operand.ref(token);
    }

    /** Does this rule still carry a {@code ?} where a number should be? Guided mode's unfinished state. */
    public boolean hasBlank() {
        return (!always() && rhs.isBlank()) || (verb.needsArg() && arg.isBlank());
    }

    public String encode() {
        return String.join("|", source, op.name(), rhs.encode(), target, verb.name(), arg.encode());
    }

    public static Rule decode(String s) {
        String[] p = s.split("\\|", -1);
        if (p.length != 6) return blank();
        Op o;
        try { o = Op.valueOf(p[1]); } catch (IllegalArgumentException e) { o = Op.LT; }
        Verb v;
        try { v = Verb.valueOf(p[4]); } catch (IllegalArgumentException e) { v = Verb.ON; }
        return new Rule(p[0], o, Operand.decode(p[2]), p[3], v, Operand.decode(p[5]));
    }
}
