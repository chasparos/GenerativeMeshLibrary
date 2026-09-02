package com.planeguardian.assets.generation.skeleton;

/**
 * Reports the half-mesh valence bookkeeping for a {@link Pole} marked
 * {@link Pole#isOnSymmetryPlane()}.
 *
 * <p>A symmetry-plane pole's incident curves split into two kinds:</p>
 * <ul>
 *   <li><b>seam curves</b> connect it to another symmetry-plane pole; the
 *       curve lies on the plane and is shared, unduplicated, with the
 *       mirrored copy (it maps to itself under reflection), so it
 *       contributes exactly one edge to the pole's final valence.</li>
 *   <li><b>free curves</b> connect it to an interior pole; they get a
 *       genuinely distinct mirrored counterpart, so each contributes two
 *       edges to the pole's final valence once welded.</li>
 * </ul>
 *
 * <p>This yields {@code requestedValence == seamCurveCount + 2 * freeCurveCount},
 * which {@link TopologicalSkeleton#validate()} enforces and which
 * {@link TopologyGenerator} verifies by construction after mirroring and
 * welding.</p>
 */
public record BoundaryConstraint(String poleId, int seamCurveCount, int freeCurveCount, int requestedValence) {

    public BoundaryConstraint {
        if (poleId == null || poleId.isBlank()) {
            throw new IllegalArgumentException("BoundaryConstraint poleId must not be blank");
        }
        if (seamCurveCount < 0 || freeCurveCount < 0) {
            throw new IllegalArgumentException("BoundaryConstraint curve counts must not be negative");
        }
    }

    /** The half-mesh (pre-mirror) valence this pole must exhibit while generating the half-mesh. */
    public int halfMeshValence() {
        return seamCurveCount + freeCurveCount;
    }
}
