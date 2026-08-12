package com.planeguardian.assets.generation.topology;

public record Vector2(double x, double y) {
    public Vector2 {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Vector components must be finite");
        }
    }
}
