package com.planeguardian.assets.generation.triangulation;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtoMeshTriangulatorTest {
    @Test
    void concavePolygonTriangulatesDeterministicallyWithoutLosingCorners() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(new Vector3(0, 0, 0));
        VertexId b = builder.addVertex(new Vector3(2, 0, 0));
        VertexId c = builder.addVertex(new Vector3(1, 0.5, 0));
        VertexId d = builder.addVertex(new Vector3(2, 2, 0));
        VertexId e = builder.addVertex(new Vector3(0, 2, 0));
        builder.addFace(List.of(a, b, c, d, e));

        TriangulatedMesh first = ProtoMeshTriangulator.triangulate(builder.snapshot());
        TriangulatedMesh second = ProtoMeshTriangulator.triangulate(builder.snapshot());

        assertEquals(5, first.vertices().size());
        assertEquals(3, first.triangleCount());
        assertArrayEquals(first.indices(), second.indices());
        assertEquals(5, first.vertices().stream().map(TriangleVertex::sourceLoopId).distinct().count());
    }

    @Test
    void adjacentFacesKeepSeparateRenderVerticesForCornerSeams() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(new Vector3(0, 0, 0));
        VertexId b = builder.addVertex(new Vector3(1, 0, 0));
        VertexId c = builder.addVertex(new Vector3(1, 1, 0));
        VertexId d = builder.addVertex(new Vector3(0, 1, 0));
        builder.addFace(List.of(a, b, c));
        builder.addFace(List.of(a, c, d));

        TriangulatedMesh mesh = ProtoMeshTriangulator.triangulate(builder.snapshot());

        assertEquals(6, mesh.vertices().size());
        assertEquals(2, mesh.triangleCount());
    }
}
