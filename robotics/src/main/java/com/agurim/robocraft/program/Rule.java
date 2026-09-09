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
