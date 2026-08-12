package com.planeguardian.assets.generation.topology;

/** Stable identity of one directed face corner (half-edge loop). */
public record LoopId(long value) implements Comparable<LoopId> {
    public LoopId {
        if (value < 0) throw new IllegalArgumentException("Loop ID must be non-negative");
    }

    @Override public int compareTo(LoopId other) { return Long.compare(value, other.value); }
}
