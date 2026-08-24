package com.agurim.robocraft.program;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of rules. Rules run top to bottom every tick - literally {@code loop()} with a
 * chain of ifs - and later rules win, which is what makes ordering a thing the student can feel.
 */
public class Program {

    private final List<Rule> rules = new ArrayList<>();

    public List<Rule> rules()   { return rules; }
    public int size()           { return rules.size(); }
    public boolean isEmpty()    { return rules.isEmpty(); }

    public Rule get(int i)      { return (i >= 0 && i < rules.size()) ? rules.get(i) : null; }
    public void set(int i, Rule r) { if (i >= 0 && i < rules.size()) rules.set(i, r); }
    public void add(Rule r)     { rules.add(r); }
    public void remove(int i)   { if (i >= 0 && i < rules.size()) rules.remove(i); }

    public List<String> encode() {
        List<String> out = new ArrayList<>();
        for (Rule r : rules) out.add(r.encode());
        return out;
    }

    public static Program decode(List<String> lines) {
        Program p = new Program();
        if (lines != null) for (String line : lines) p.add(Rule.decode(line));
        return p;
    }
}
