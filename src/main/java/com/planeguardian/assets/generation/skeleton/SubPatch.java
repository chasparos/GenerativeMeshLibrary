package com.planeguardian.assets.generation.skeleton;

import java.util.List;
import java.util.Objects;

/**
 * A closed loop of {@link GuideCurve}s traced by {@link TopologicalSkeleton},
 * bounding one fillable region of the mesh.
 *
 * <p>Every skeleton curve participates in exactly two traced loops (one per
 * traversal direction), so every traced loop is treated as a legitimate,
 * fillable sub-patch. This generator therefore targets closed, cage-like
 * skeletons (for example one half of a creature body) rather than flat, open
 * 2D layouts with an unbounded "outer" region — see
 * {@link TopologicalSkeleton#tracePatches()} for the full rationale.</p>
 */
public record SubPatch(List<Side> sides) {

    public SubPatch {
        Objects.requireNonNull(sides, "sides");
        sides = List.copyOf(sides);
        if (sides.size() < 2) {
            throw new IllegalArgumentException("A sub-patch needs at least two bounding sides");
        }
    }

    public int sideCount() {
        return sides.size();
    }

    /** Sum of the boundary segment densities, used by the Step 1 parity check. */
    public int boundarySegmentSum() {
        return sides.stream().mapToInt(Side::densitySegmentCount).sum();
    }

    /** One directed traversal of a {@link GuideCurve} around a {@link SubPatch} boundary. */
    public record Side(String curveId, String fromPoleId, String toPoleId, boolean reversed, int densitySegmentCount) {
        public Side {
            if (curveId == null || curveId.isBlank()) {
                throw new IllegalArgumentException("Side curveId must not be blank");
            }
            if (fromPoleId == null || fromPoleId.isBlank() || toPoleId == null || toPoleId.isBlank()) {
                throw new IllegalArgumentException("Side pole ids must not be blank");
            }
            if (densitySegmentCount < 1) {
                throw new IllegalArgumentException("Side densitySegmentCount must be at least 1");
            }
        }
    }
}
