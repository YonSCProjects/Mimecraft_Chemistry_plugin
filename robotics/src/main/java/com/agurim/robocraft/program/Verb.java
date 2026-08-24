package com.agurim.robocraft.program;

/**
 * What a rule does when it fires.
 *
 * <p>ON/OFF/SHOW drive an actuator; SET/ADD write a memory slot. ADD is what turns memory into a
 * counter, and counters are what make timing and sequencing possible - a lot of expressive power
 * for one extra verb.
 */
public enum Verb {
    ON, OFF, SHOW, SET, ADD;

    public boolean needsArg()    { return this == SHOW || this == SET || this == ADD; }
    public boolean isMemory()    { return this == SET || this == ADD; }

    /** Cycle within the verbs legal for this target kind, so the GUI can never build nonsense. */
    public Verb next(boolean memoryTarget) {
        if (memoryTarget) return (this == SET) ? ADD : SET;
        return switch (this) {
            case ON   -> OFF;
            case OFF  -> SHOW;
            default   -> ON;
        };
    }
}
