package com.planeguardian.assets.generation.api;

/** Engine-neutral vector in glTF coordinates and metres. */
public record Vector3(double x, double y, double z) {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 ONE = new Vector3(1, 1, 1);

    public Vector3 {
        requireFinite(x);
        requireFinite(y);
        requireFinite(z);
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Vector components must be finite");
        }
    }
}
