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
        assertEquals(4, skeleton.holeCurveIds().size(),
                "the eye ring and mouth ring are each authored as a two-curve hole bigon");
    }

    @Test
    void eyeAndMouthOpeningsTraceAsIsolatedHolePatches() {
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();

        long holePatchCount = skeleton.tracePatches().stream().filter(skeleton::isHolePatch).count();

        assertEquals(2, holePatchCount, "expected exactly one hole patch each for the eye ring and mouth ring");
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
