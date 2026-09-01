package com.planeguardian.assets.generation.geometry.eval;

/**
 * Validated parameters for {@link TubeGeometry}: a straight tube of circular
 * cross-section, centred on the origin and extruded along +Y.
 */
public record TubeGeometryParameters(
        double radius,
        double length,
        int radialSegments,
        int lengthSegments,
        boolean capped) {

    public static final TubeGeometryParameters DEFAULT =
            new TubeGeometryParameters(0.5, 2.0, 16, 4, true);

    public TubeGeometryParameters {
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("radius must be finite and positive");
        }
        if (!Double.isFinite(length) || length <= 0) {
            throw new IllegalArgumentException("length must be finite and positive");
        }
        if (radialSegments < 3) {
            throw new IllegalArgumentException("radialSegments must be at least three");
        }
        if (lengthSegments < 1) {
            throw new IllegalArgumentException("lengthSegments must be at least one");
        }
    }
}
