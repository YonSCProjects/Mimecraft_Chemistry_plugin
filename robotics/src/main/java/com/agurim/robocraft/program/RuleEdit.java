package com.agurim.robocraft.program;

import java.util.List;

/**
 * What a click on one cell of the rule table does.
 *
 * <p>Bukkit-free, like {@link Evaluator}, so every field and click combination can be exercised
 * without a player - and this is the code where a bug is worst. The click handler decodes a click
 * from its slot number alone, so an error here does not crash: it silently edits a different part
 * of the rule than the one the student clicked, in a tool whose entire premise is that clicking is
 * unambiguous.
 *
 * <p>Field numbers are the column within a rule's row:
 * {@code 0:#  1:source  2:op  3:value  4:->  5:target  6:verb  7:arg  8:delete}.
 */
public final class RuleEdit {

    private RuleEdit() {}

    /** Highest literal a value cell will hold. Above a sensor's range there is nothing to say. */
    public static final int MAX_CONST = 255;

    public static final int SOURCE = 1, OP = 2, RHS = 3, TARGET = 5, VERB = 6, ARG = 7, DELETE = 8;

    /** A click, reduced to the three things that change what it means. */
    public record Click(boolean right, boolean shift, boolean alternate) {
        public static final Click LEFT      = new Click(false, false, false);
        public static final Click RIGHT     = new Click(true,  false, false);
        public static final Click SHIFT     = new Click(false, true,  false);
        public static final Click SHIFT_RT  = new Click(true,  true,  false);
        public static final Click ALTERNATE = new Click(false, false, true);
    }

    /**
     * The rule a student gets when they add a row.
     *
     * <p>Not a blank. A blank ({@code ALWAYS THEN A1 ON}) is a thing they have to assemble from
     * nothing before it does anything recognisable, and the first rule is exactly where that costs
     * most. This hands them a complete, readable rule built from their own first sensor and first
     * actuator - so the row already reads like the sentence they are trying to write, and the work
     * is changing a number rather than constructing a form.
     *
     * <p>It deliberately stops at one rule. The second rule - the one that switches the output back
     * off - is the whole lesson of the first mission, and handing that over would be handing over
     * the answer.
     */
    public static Rule starter(List<String> sources, List<String> targets) {
        String sensor = firstWith(sources, 'S');
        String target = firstWith(targets, 'A');
        if (target == null) target = targets.isEmpty() ? "" : targets.get(0);
        if (sensor == null) return Rule.blank().withTarget(target);
        return new Rule(sensor, Op.LT, Operand.of(7), target, Verb.ON, Operand.of(0));
    }

    private static String firstWith(List<String> options, char prefix) {
        if (options == null) return null;
        for (String s : options) if (!s.isEmpty() && s.charAt(0) == prefix) return s;
        return null;
    }

    /**
     * Is this cell one the student can actually see and change?
     *
     * <p>An ALWAYS rule hides its comparison; a verb with no argument hides its value. Those slots
     * hold blanks, and clicking a blank must do nothing rather than edit a field off-screen.
     */
    public static boolean editable(Rule rule, int field) {
        return switch (field) {
            case OP, RHS -> !rule.always();
            case ARG     -> rule.verb().needsArg();
            case SOURCE, TARGET, VERB, DELETE -> true;
            default      -> false;
        };
    }

    /**
     * Apply a click. Returns the edited rule, or the rule unchanged when the cell is not editable
     * or there is nothing to cycle to. {@link #DELETE} is handled by the caller, not here.
     */
    public static Rule apply(Rule rule, int field, Click click,
                             List<String> sources, List<String> targets, List<String> valueSources) {
        if (!editable(rule, field)) return rule;

        return switch (field) {
            case SOURCE -> rule.withSource(cycle(sources, rule.source(), click));
            case OP     -> rule.withOp(rule.op().next());
            case RHS    -> rule.withRhs(operand(rule.rhs(), click, valueSources));
            case VERB   -> rule.withVerb(rule.verb().next(isMemory(rule.target())));
            case ARG    -> rule.withArg(operand(rule.arg(), click, valueSources));
            case TARGET -> retarget(rule, cycle(targets, rule.target(), click));
            default     -> rule;
        };
    }

    /**
     * Changing the target can make the verb illegal - ON means nothing to a memory slot and SET
     * means nothing to a lamp - so normalise it in the same step. Otherwise the student is left
     * holding a rule that cannot run and no cell looks wrong.
     */
    private static Rule retarget(Rule rule, String target) {
        if (target == null || target.equals(rule.target())) return rule.withTarget(target);
        Rule out = rule.withTarget(target);
        if (isMemory(target) && !out.verb().isMemory())      return out.withVerb(Verb.SET);
        if (!isMemory(target) && out.verb().isMemory())      return out.withVerb(Verb.ON);
        return out;
    }

    /** A value cell steps by 1, or 10 with shift; the alternate click swaps number for port. */
    private static Operand operand(Operand operand, Click click, List<String> valueSources) {
        if (click.alternate()) {
            if (operand.constant()) {
                return valueSources.isEmpty() ? operand : Operand.ref(valueSources.get(0));
            }
            return Operand.of(0);
        }
        if (!operand.constant()) return Operand.ref(cycle(valueSources, operand.source(), click));

        int step = click.shift() ? 10 : 1;
        int value = operand.value() + (click.right() ? -step : step);
        return Operand.of(Math.max(0, Math.min(MAX_CONST, value)));
    }

    /** Left goes forward, right goes back, and both wrap. An unknown current value starts at 0. */
    public static String cycle(List<String> options, String current, Click click) {
        if (options == null || options.isEmpty()) return current;
        int i = options.indexOf(current);
        if (i < 0) return options.get(0);
        int next = click.right() ? (i - 1 + options.size()) % options.size()
                                 : (i + 1) % options.size();
        return options.get(next);
    }

    public static boolean isMemory(String target) {
        return target != null && target.length() >= 2 && target.charAt(0) == 'M'
                && Character.isDigit(target.charAt(1));
    }
}
