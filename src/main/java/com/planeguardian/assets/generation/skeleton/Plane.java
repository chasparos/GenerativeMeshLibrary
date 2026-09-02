package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;

import java.util.Objects;

/**
 * An infinite plane described by a point on the plane and a unit normal.
 *
 * <p>Used as the symmetry plane of a {@link TopologicalSkeleton}: poles marked
 * {@link Pole#isOnSymmetryPlane()} must lie on this plane, and mirroring
 * reflects the generated half-mesh across it (see {@link TopologyGenerator}).</p>
 */
public record Plane(Vector3 point, Vector3 normal) {

    private static final double NORMALIZATION_EPSILON = 1.0e-9;

    public Plane {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
        double length = VectorMath.length(normal);
        if (!Double.isFinite(length) || length <= NORMALIZATION_EPSILON) {
            throw new IllegalArgumentException("Plane normal must be a finite, non-zero vector");
        }
        normal = VectorMath.scale(normal, 1.0 / length);
    }

    /** Signed distance from {@code position} to this plane; positive on the side the normal points toward. */
    public double signedDistance(Vector3 position) {
        return VectorMath.dot(VectorMath.subtract(position, point), normal);
    }

    /** Whether {@code position} lies on this plane within {@code tolerance} metres. */
    public boolean contains(Vector3 position, double tolerance) {
        return StrictMath.abs(signedDistance(position)) <= tolerance;
    }

    /** Projects {@code position} onto this plane, discarding its component along the normal. */
    public Vector3 project(Vector3 position) {
        return VectorMath.subtract(position, VectorMath.scale(normal, signedDistance(position)));
    }

    /** Reflects {@code position} across this plane. */
    public Vector3 reflect(Vector3 position) {
        return VectorMath.subtract(position, VectorMath.scale(normal, 2.0 * signedDistance(position)));
    }
}
