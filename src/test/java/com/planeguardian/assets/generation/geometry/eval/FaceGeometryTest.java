package com.planeguardian.assets.generation.geometry.eval;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.triangulation.ProtoMeshTriangulator;
import com.planeguardian.assets.generation.triangulation.TriangulatedMesh;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceGeometryTest {

    @Test
    void generatesAValidMesh() {
        FaceGeometry face = new FaceGeometry();
        ProtoMeshSnapshot mesh = face.generate();

        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        assertEquals("face", face.id());
        assertTrue(mesh.faces().size() > 0);
    }

    @Test
    void triangulatesCleanly() {
        FaceGeometry face = new FaceGeometry();
        ProtoMeshSnapshot mesh = face.generate();
        TriangulatedMesh triangulated = ProtoMeshTriangulator.triangulate(mesh);

        assertTrue(triangulated.triangleCount() > 0);
    }

    @Test
    void authoredCurvePolylinesAreNonEmptyAndMirrored() {
        FaceGeometry face = new FaceGeometry();

        List<List<Vector3>> polylines = face.authoredCurvePolylines();

        assertFalse(polylines.isEmpty());
        // Every authored curve on a mirrored skeleton is emitted alongside its mirror image.
        assertEquals(0, polylines.size() % 2);
        for (List<Vector3> polyline : polylines) {
            assertTrue(polyline.size() >= 2, "every polyline must have at least a start and end point");
        }
    }
}
