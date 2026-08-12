package com.planeguardian.assets.generation.topology;

import com.planeguardian.assets.generation.api.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtoMeshBuilderTest {
    @Test
    void twoQuadsShareOneStableEdgeAndExposeSixBoundaryEdges() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId v0 = builder.addVertex(new Vector3(0, 0, 0));
        VertexId v1 = builder.addVertex(new Vector3(1, 0, 0));
        VertexId v2 = builder.addVertex(new Vector3(1, 1, 0));
        VertexId v3 = builder.addVertex(new Vector3(0, 1, 0));
        VertexId v4 = builder.addVertex(new Vector3(2, 0, 0));
        VertexId v5 = builder.addVertex(new Vector3(2, 1, 0));

        builder.addFace(List.of(v0, v1, v2, v3));
        builder.addFace(List.of(v1, v4, v5, v2));
        ProtoMeshSnapshot mesh = builder.snapshot();

        assertEquals(6, mesh.vertices().size());
        assertEquals(7, mesh.edges().size());
        assertEquals(8, mesh.loops().size());
        assertEquals(2, mesh.faces().size());
        assertEquals(6, mesh.boundaryEdges().size());
        assertTrue(mesh.isValid());
        assertEquals(1, mesh.edges().values().stream().filter(edge -> edge.uses().size() == 2).count());
    }

    @Test
    void perCornerSeamsDoNotDuplicateTheGeometricVertex() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(new Vector3(0, 0, 0));
        VertexId b = builder.addVertex(new Vector3(1, 0, 0));
        VertexId c = builder.addVertex(new Vector3(0, 1, 0));
        CornerAttributes seam = new CornerAttributes(
                Optional.of(new Vector2(0.25, 0.75)), Optional.empty(), Map.of("mask", 0.8));

        FaceId face = builder.addFace(
                List.of(a, b, c), List.of(seam, CornerAttributes.EMPTY, CornerAttributes.EMPTY), Set.of("bark"));
        ProtoMeshSnapshot mesh = builder.snapshot();
        ProtoLoop firstLoop = mesh.loops().get(mesh.faces().get(face).loops().get(0));

        assertEquals(a, firstLoop.vertexId());
        assertEquals(new Vector2(0.25, 0.75), firstLoop.attributes().textureCoordinate().orElseThrow());
        assertEquals(0.8, firstLoop.attributes().scalarLayers().get("mask"));
        assertEquals(Set.of("bark"), mesh.faces().get(face).semanticGroups());
    }

    @Test
    void snapshotIsImmutableAndUnaffectedByLaterBuilderChanges() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(new Vector3(0, 0, 0));
        VertexId b = builder.addVertex(new Vector3(1, 0, 0));
        VertexId c = builder.addVertex(new Vector3(0, 1, 0));
        builder.addFace(List.of(a, b, c));
        ProtoMeshSnapshot before = builder.snapshot();

        builder.addVertex(new Vector3(5, 5, 5));

        assertEquals(3, before.vertices().size());
        assertThrows(UnsupportedOperationException.class, () -> before.vertices().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.faces().firstEntry().getValue().loops().clear());
    }

    @Test
    void validationDetectsWindingAndNonManifoldEdges() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(new Vector3(0, 0, 0));
        VertexId b = builder.addVertex(new Vector3(1, 0, 0));
        VertexId c = builder.addVertex(new Vector3(0, 1, 0));
        VertexId d = builder.addVertex(new Vector3(1, 1, 0));
        VertexId e = builder.addVertex(new Vector3(0.5, -1, 0));
        builder.addFace(List.of(a, b, c));
        builder.addFace(List.of(a, b, d));
        builder.addFace(List.of(b, a, e));

        ProtoMeshSnapshot mesh = builder.snapshot();

        assertFalse(mesh.isValid());
        assertTrue(mesh.issues().stream().anyMatch(issue -> issue.code().equals("non-manifold-edge")));
    }

    @Test
    void invalidFaceInputsAreRejectedBeforeMutation() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(Vector3.ZERO);
        VertexId b = builder.addVertex(Vector3.ONE);
        assertThrows(IllegalArgumentException.class, () -> builder.addFace(List.of(a, b)));
        assertThrows(IllegalArgumentException.class, () -> builder.addFace(List.of(a, b, a)));
        assertEquals(0, builder.snapshot().faces().size());
    }

    @Test
    void validationDetectsCoincidentEdgesAndDegenerateFaces() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(new Vector3(0, 0, 0));
        VertexId duplicateA = builder.addVertex(new Vector3(0, 0, 0));
        VertexId b = builder.addVertex(new Vector3(1, 0, 0));
        builder.addFace(List.of(a, duplicateA, b));

        ProtoMeshSnapshot mesh = builder.snapshot();

        assertFalse(mesh.isValid());
        assertTrue(mesh.issues().stream().anyMatch(issue -> issue.code().equals("zero-length-edge")));
        assertTrue(mesh.issues().stream().anyMatch(issue -> issue.code().equals("degenerate-face")));
    }

    @Test
    void validationDetectsSelfCrossingPolygonBeforeTriangulation() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(new Vector3(0, 0, 0));
        VertexId b = builder.addVertex(new Vector3(2, 2, 0));
        VertexId c = builder.addVertex(new Vector3(0, 2, 0));
        VertexId d = builder.addVertex(new Vector3(2, 0, 0));
        builder.addFace(List.of(a, b, c, d));

        ProtoMeshSnapshot mesh = builder.snapshot();

        assertFalse(mesh.isValid());
        assertTrue(mesh.issues().stream().anyMatch(issue -> issue.code().equals("self-intersecting-face")));
    }
}
