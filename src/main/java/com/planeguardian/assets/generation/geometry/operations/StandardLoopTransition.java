package com.planeguardian.assets.generation.geometry.operations;

/** Reviewed quad-only loop transition patterns. */
public enum StandardLoopTransition {
    FOUR_TO_EIGHT(4, 8),
    EIGHT_TO_TWELVE(8, 12),
    EIGHT_TO_SIXTEEN(8, 16);

    private final int smallerRingSize;
    private final int largerRingSize;

    StandardLoopTransition(int smallerRingSize, int largerRingSize) {
        this.smallerRingSize = smallerRingSize;
        this.largerRingSize = largerRingSize;
    }

    public int smallerRingSize() { return smallerRingSize; }
    public int largerRingSize() { return largerRingSize; }
    public int wedgeCount() { return (largerRingSize - smallerRingSize) / 2; }
    public int faceCount() { return smallerRingSize + wedgeCount(); }
}
