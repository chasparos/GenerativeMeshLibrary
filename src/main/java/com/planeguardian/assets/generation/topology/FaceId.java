package com.planeguardian.assets.generation.topology;

public record FaceId(long value) implements Comparable<FaceId> {
    public FaceId {
        if (value < 0) throw new IllegalArgumentException("Face ID must be non-negative");
    }

    @Override public int compareTo(FaceId other) { return Long.compare(value, other.value); }
}
