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
        assertEquals(8, skeleton.holeCurveIds().size(),
                "the eye ring (2 curves), mouth opening (3 curves), and back-of-head gap (3 curves) are holes");
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
}
