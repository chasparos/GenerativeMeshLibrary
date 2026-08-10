package com.planeguardian.assets.generation.topology;

public record EdgeId(long value) implements Comparable<EdgeId> {
    public EdgeId {
        if (value < 0) throw new IllegalArgumentException("Edge ID must be non-negative");
    }

    @Override public int compareTo(EdgeId other) { return Long.compare(value, other.value); }
}
