package com.agurim.robocraft.robot;

import com.agurim.robocraft.program.Program;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One robot: a controller block, whatever is attached to it, and the state of the loop.
 *
 * <p>Memory is deliberately NOT persisted. A real controller loses its RAM when the power goes,
 * and a student who parks a counter in M1 should see it start from zero next session - that is
 * the honest behaviour and it is worth teaching.
 */
public class Robot {

    private final String key;               // location key of the controller block
    private UUID owner;
    private int energy;
    private boolean running;
    private Program program = new Program();

    private final int[] memory;
    private final Map<String, Integer> inputs  = new LinkedHashMap<>();  // S1 -> last reading
    private final Map<String, Integer> outputs = new LinkedHashMap<>();  // A1 -> current state
    private long startedTick;
    private int lastFired = -1;
    private String halt;

    public Robot(String key, UUID owner, int memorySlots) {
        this.key = key;
        this.owner = owner;
        this.memory = new int[Math.max(1, memorySlots)];
    }

    public String key()                 { return key; }
    public UUID owner()                 { return owner; }
    public void owner(UUID o)           { this.owner = o; }
    public int energy()                 { return energy; }
    public void energy(int e)           { this.energy = Math.max(0, e); }
    public boolean running()            { return running; }
    public Program program()            { return program; }
    public void program(Program p)      { this.program = p; }
    public int[] memory()               { return memory; }
    public Map<String, Integer> inputs()  { return inputs; }
    public Map<String, Integer> outputs() { return outputs; }
    public int lastFired()              { return lastFired; }
    public void lastFired(int i)        { this.lastFired = i; }
    public String halt()                { return halt; }
    public void halt(String reason)     { this.halt = reason; }
    public long startedTick()           { return startedTick; }

    public void start(long nowTick) {
        this.running = true;
        this.halt = null;
        this.startedTick = nowTick;
        java.util.Arrays.fill(memory, 0);
        lastFired = -1;
    }

    /** Stop the loop. Outputs are left exactly where they were - a stopped robot is not a reset one. */
    public void stop(String reason) {
        this.running = false;
        this.halt = reason;
    }

    /** Seconds since the program started - the TIME source. */
    public int seconds(long nowTick) {
        return (int) Math.max(0, (nowTick - startedTick) / 20L);
    }

    public int mem(int slot) {
        return (slot >= 0 && slot < memory.length) ? memory[slot] : 0;
    }

    public void mem(int slot, int value) {
        if (slot >= 0 && slot < memory.length) memory[slot] = value;
    }
}
