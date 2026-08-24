package com.agurim.robocraft.program;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One pass of the rule table. Deliberately free of any Bukkit type, so the semantics students are
 * being taught can be exercised without a server - which is how the mission ladder was checked to
 * be solvable at all.
 *
 * <p>The two rules that define the language:
 * <ul>
 *   <li><b>Top to bottom, later wins.</b> Exactly {@code loop()} with a chain of ifs.</li>
 *   <li><b>Memory writes land immediately</b>, so a later rule sees what an earlier one wrote.
 *       That is what makes edge detection ({@code M2 SET S1} after the comparison) work.</li>
 * </ul>
 *
 * <p>Latching is NOT done here: a port simply absent from the returned commands means "no rule
 * spoke about it this tick", and holding the previous value is the caller's job.
 */
public final class Evaluator {

    private Evaluator() {}

    /** Ports commanded this tick, plus the index of the last rule that fired (-1 if none). */
    public record Result(Map<String, Integer> commands, int lastFired) { }

    public static Result run(Program program, Map<String, Integer> inputs, int[] memory, int timeSeconds) {
        Map<String, Integer> commands = new LinkedHashMap<>();
        int lastFired = -1;

        List<Rule> rules = program.rules();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.target() == null || rule.target().isEmpty()) continue;

            if (!rule.always()) {
                int lhs = value(rule.source(), inputs, memory, timeSeconds);
                int rhs = operand(rule.rhs(), inputs, memory, timeSeconds);
                if (!rule.op().test(lhs, rhs)) continue;
            }
            lastFired = i;

            int arg = operand(rule.arg(), inputs, memory, timeSeconds);
            if (rule.verb().isMemory()) {
                int slot = slot(rule.target());
                if (slot >= 0 && slot < memory.length) {
                    memory[slot] = (rule.verb() == Verb.SET) ? arg : memory[slot] + arg;
                }
            } else {
                switch (rule.verb()) {
                    case ON   -> commands.put(rule.target(), 1);
                    case OFF  -> commands.put(rule.target(), 0);
                    case SHOW -> commands.put(rule.target(), arg);
                    default   -> { }
                }
            }
        }
        return new Result(commands, lastFired);
    }

    /**
     * Would this rule's condition fire right now?
     *
     * <p>Only used for showing a student their program's state. "Which of my rules matched, and
     * which one actually decided the output" is the whole answer to "why did it do that?", and
     * being able to see it is the difference between debugging and guessing.
     */
    public static boolean matches(Rule rule, Map<String, Integer> inputs, int[] memory, int timeSeconds) {
        if (rule.always()) return true;
        int lhs = value(rule.source(), inputs, memory, timeSeconds);
        int rhs = operand(rule.rhs(), inputs, memory, timeSeconds);
        return rule.op().test(lhs, rhs);
    }

    private static int operand(Operand operand, Map<String, Integer> inputs, int[] memory, int timeSeconds) {
        return operand.constant() ? operand.value() : value(operand.source(), inputs, memory, timeSeconds);
    }

    /** A source name is a sensor port (S1), a memory slot (M1), or TIME. */
    public static int value(String source, Map<String, Integer> inputs, int[] memory, int timeSeconds) {
        if (source == null || source.isEmpty()) return 0;
        if ("TIME".equals(source)) return timeSeconds;
        if (source.charAt(0) == 'M') {
            int slot = slot(source);
            return (slot >= 0 && slot < memory.length) ? memory[slot] : 0;
        }
        Integer v = inputs.get(source);
        return (v == null) ? 0 : v;
    }

    private static int slot(String name) {
        try { return Integer.parseInt(name.substring(1)) - 1; }
        catch (RuntimeException e) { return -1; }
    }
}
