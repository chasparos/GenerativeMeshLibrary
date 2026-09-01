package com.planeguardian.assets.generation.geometry.eval;

import com.planeguardian.assets.generation.triangulation.ProtoMeshTriangulator;
import com.planeguardian.assets.generation.triangulation.TriangulatedMesh;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeGeometryTest {

    @Test
    void defaultTubeIsValidAndCapped() {
        TubeGeometry tube = new TubeGeometry();
        ProtoMeshSnapshot mesh = tube.generate();

        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        assertEquals("tube", tube.id());

        TubeGeometryParameters parameters = tube.parameters();
        int wallFaces = parameters.radialSegments() * parameters.lengthSegments();
        int capFaces = 2;
        assertEquals(wallFaces + capFaces, mesh.faces().size());
    }

    @Test
    void uncappedTubeHasOpenBoundaryRings() {
        TubeGeometry tube = new TubeGeometry(new TubeGeometryParameters(1.0, 3.0, 12, 2, false));
        ProtoMeshSnapshot mesh = tube.generate();

        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        assertEquals(12 * 2, mesh.faces().size());
        assertEquals(2 * 12, mesh.boundaryEdges().size());
    }

    @Test
    void tubeTriangulatesCleanly() {
        TubeGeometry tube = new TubeGeometry(TubeGeometryParameters.DEFAULT);
        ProtoMeshSnapshot mesh = tube.generate();
        TriangulatedMesh triangulated = ProtoMeshTriangulator.triangulate(mesh);

        assertTrue(triangulated.triangleCount() > 0);
    }

    @Test
    void rejectsInvalidParameters() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TubeGeometryParameters(-1, 1, 8, 1, true));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TubeGeometryParameters(1, 1, 2, 1, true));
    }
}
