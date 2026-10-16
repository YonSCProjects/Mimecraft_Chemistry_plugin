package com.agurim.robocraft.ui;

import com.agurim.robocraft.program.Op;
import com.agurim.robocraft.program.Operand;
import com.agurim.robocraft.program.Rule;

import java.util.Map;

/**
 * A rule read aloud, in Hebrew, with the parts called by their names:
 * {@code IF S1 < 7 THEN A1 ON} becomes "אם חיישן אור (S1) קטן מ-7 — נורה (A1): ON".
 *
 * <p>This is what the demo says as it writes each rule, so a student watching sees the sentence
 * and the row at the same time. The tokens stay Latin - S1, A1, ON - because that is the
 * vocabulary the table is written in and the words they will meet again on an Arduino; only the
 * glue around them is Hebrew. The verbs are left as tokens on purpose: "נדלקת" would have to agree
 * in gender with every actuator name, and "נורה (A1): ON" reads fine without it.
 *
 * <p>Bukkit-free, so the self-test can hold every form to a readable sentence.
 */
public final class RuleWords {

    private RuleWords() {}

    /** {@code names} maps a port (S1, A1) to the part's Hebrew name; anything else is shown as-is. */
    public static String hebrew(Rule rule, Map<String, String> names) {
        String head = rule.always() ? "תמיד"
                : "אם " + name(rule.source(), names) + " " + op(rule.op()) + value(rule.rhs(), names);
        String tail = name(rule.target(), names) + ": " + rule.verb().name()
                + (rule.verb().needsArg() ? " " + value(rule.arg(), names) : "");
        return head + " — " + tail;
    }

    private static String op(Op op) {
        return switch (op) {
            case LT -> "קטן מ-";
            case LE -> "עד ";
            case EQ -> "שווה ל-";
            case NE -> "שונה מ-";
            case GE -> "לפחות ";
            case GT -> "גדול מ-";
        };
    }

    private static String value(Operand o, Map<String, String> names) {
        if (o.isBlank()) return "?";
        return o.constant() ? String.valueOf(o.value()) : name(o.source(), names);
    }

    /** "חיישן אור (S1)" for a port with a name; "הזמן (TIME)"; a memory slot or anything unnamed as-is. */
    public static String name(String port, Map<String, String> names) {
        if (port == null || port.isEmpty()) return "-";
        if ("TIME".equals(port)) return "הזמן (TIME)";
        String n = names.get(port);
        return n == null ? port : n + " (" + port + ")";
    }
}
