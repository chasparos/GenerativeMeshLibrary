package com.planeguardian.assets.generation.adapters.jme;

import com.planeguardian.assets.generation.geometry.eval.TubeGeometry;
import com.planeguardian.assets.generation.geometry.eval.TubeGeometryParameters;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.triangulation.ProtoMeshTriangulator;
import com.planeguardian.assets.generation.triangulation.TriangulatedMesh;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmeMeshAdapterTest {

    @Test
    void triangleMeshMapsEveryTriangleBackToItsSourceFace() {
        TubeGeometry tube = new TubeGeometry(new TubeGeometryParameters(0.5, 2.0, 8, 2, true));
        ProtoMeshSnapshot mesh = tube.generate();
        TriangulatedMesh triangulated = ProtoMeshTriangulator.triangulate(mesh);

        JmeMeshAdapter.TriangleMeshResult result = JmeMeshAdapter.toTriangleMesh(triangulated);

        assertEquals(triangulated.triangleCount(), result.faceIdPerTriangle().size());
        assertEquals(triangulated.triangleCount() * 3, result.mesh().getVertexCount());

        Set<FaceId> facesSeen = new HashSet<>(result.faceIdPerTriangle());
        assertEquals(mesh.faces().size(), facesSeen.size());
        assertTrue(mesh.faces().keySet().containsAll(facesSeen));
    }

    @Test
    void edgeMeshCoversEveryTopologyEdge() {
        TubeGeometry tube = new TubeGeometry();
        ProtoMeshSnapshot mesh = tube.generate();

        JmeMeshAdapter.EdgeMeshResult result = JmeMeshAdapter.toEdgeMesh(mesh);

        assertEquals(mesh.edges().size(), result.edgeIdPerLine().size());
        assertEquals(mesh.edges().size() * 2, result.mesh().getVertexCount());
        assertTrue(mesh.edges().keySet().containsAll(result.edgeIdPerLine()));
    }
}
