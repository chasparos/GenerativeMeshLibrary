package com.planeguardian.assets.generation.topology;

public record VertexId(long value) implements Comparable<VertexId> {
    public VertexId {
        if (value < 0) throw new IllegalArgumentException("Vertex ID must be non-negative");
    }

    @Override public int compareTo(VertexId other) { return Long.compare(value, other.value); }
}
