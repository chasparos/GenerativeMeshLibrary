package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanFaceSkeletonTest {

    @Test
    void buildProducesAValidatedMirroredSkeleton() {
        // Pole/GuideCurve validation (valence/parity invariants) runs in the constructor,
        // so simply building the skeleton is itself an assertion that it is well-formed.
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();

        assertTrue(skeleton.isMirrored());
        assertEquals(9, skeleton.holeCurveIds().size(),
                "the eye ring (2 curves), mouth opening (3 curves), and back-of-head gap "
                        + "(4 curves, now that cheekToCrown is split by the temple pole) are holes");
    }

    @Test
    void eyeMouthAndBackOfHeadOpeningsTraceAsIsolatedHolePatches() {
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();

        long holePatchCount = skeleton.tracePatches().stream().filter(skeleton::isHolePatch).count();

        assertEquals(3, holePatchCount,
                "expected exactly one hole patch each for the eye ring, mouth opening, and back-of-head gap");
    }

    @Test
    void generatesAValidMirroredMesh() {
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();

        GenerationResult result = new TopologyGenerator().generate(skeleton);
        ProtoMeshSnapshot mesh = result.mesh();

        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        assertTrue(mesh.faces().size() > 0);
        assertTrue(mesh.vertices().size() > 0);
    }

    @Test
    void mouthAndEyeHolesRingInsetWithoutRaisingTheirRawControlCage() {
        // Regression test for the on-axis mouth seam bug: upperLipMid/lowerLipMid's boundary
        // loop crosses the mirror plane twice (once at each pole) via the unfillable, on-axis
        // mouthSeam curve, so a naive ring-inset of the whole loop produced a border face lying
        // entirely on the mirror plane; mirroring it then duplicated every one of its edges and
        // failed TopologyGenerator's post-weld valence check. subdivisionLevels=0 isolates the
        // ring-inset collar itself from the separately tested Catmull-Clark smoothing pass.
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();
        GenerationResult result = new TopologyGenerator().generate(skeleton, 0);

        assertTrue(result.mesh().isValid(), () -> "issues: " + result.mesh().issues());
    }
}
