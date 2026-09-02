package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.Objects;

/**
 * An explicit, user-authored vertex of the topological skeleton.
 *
 * <p>A pole is the only kind of point in the generated mesh permitted to have
 * a valence other than four. {@code requestedValence} is the valence the
 * generated mesh must exhibit at this point once generation (and, for
 * symmetry-plane poles, mirroring) is complete.</p>
 *
 * <p>When {@code isOnSymmetryPlane} is {@code true} the pole is treated as a
 * {@link BoundaryConstraint} during half-mesh generation: its half-mesh
 * (pre-mirror) valence is only a fraction of {@code requestedValence}, and the
 * remainder is contributed by the mirrored copy once vertices are welded
 * across the symmetry plane. See {@link TopologicalSkeleton} for the exact
 * valence bookkeeping rules.</p>
 */
public record Pole(String id, Vector3 position, int requestedValence, boolean isOnSymmetryPlane) {

    public Pole {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pole id must not be blank");
        }
        Objects.requireNonNull(position, "position");
        if (requestedValence < 2) {
            throw new IllegalArgumentException("Pole requestedValence must be at least 2, got " + requestedValence);
        }
    }
}
