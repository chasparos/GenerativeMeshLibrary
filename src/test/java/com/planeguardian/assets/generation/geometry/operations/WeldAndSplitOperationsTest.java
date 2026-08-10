package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.EdgeId;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.ProtoMeshEditTransaction;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.Vector2;
import com.planeguardian.assets.generation.topology.VertexId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeldAndSplitOperationsTest {
    @Test
    void internalEdgeSplitTurnsTwoTrianglesIntoTwoQuads() {
        SplitFixture fixture = splitFixture();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(fixture.mesh());
        EdgeSplitOperation.SplitResult result = transaction.apply(builder ->
                EdgeSplitOperation.split(builder, fixture.diagonal(), 0.25));

        ProtoMeshSnapshot edited = transaction.commit();

        assertTrue(fixture.mesh().edges().containsKey(fixture.diagonal()));
        assertFalse(edited.edges().containsKey(fixture.diagonal()));
        assertEquals(5, edited.vertices().size());
        assertEquals(2, edited.faces().size());
        assertEquals(4, edited.boundaryEdges().size());
        assertTrue(edited.faces().values().stream().allMatch(face -> face.loops().size() == 4));
        assertEquals(new Vector3(0.5, 0.5, 0), edited.vertices().get(result.splitVertex()).position());
        assertTrue(result.replacementFaces().stream().allMatch(id -> id.value() > 1));
        assertTrue(edited.isValid());
    }

    @Test
    void edgeSplitInterpolatesPerCornerUvAndScalarData() {
        SplitFixture fixture = splitFixture();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(fixture.mesh());
        EdgeSplitOperation.SplitResult result = transaction.apply(builder ->
                EdgeSplitOperation.split(builder, fixture.diagonal(), 0.25));
        ProtoMeshSnapshot edited = transaction.commit();

        var splitLoops = edited.loops().values().stream()
                .filter(loop -> loop.vertexId().equals(result.splitVertex())).toList();
        assertEquals(2, splitLoops.size());
        assertTrue(splitLoops.stream().allMatch(loop -> loop.attributes().textureCoordinate().isPresent()));
        assertTrue(splitLoops.stream().allMatch(loop -> loop.attributes().scalarLayers().containsKey("weight")));
    }

    @Test
    void coincidentWeldRewritesUsesAndRetiresVertex() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId retained = builder.addVertex(Vector3.ZERO);
        VertexId retired = builder.addVertex(Vector3.ZERO);
        VertexId a = builder.addVertex(new Vector3(1, 0, 0));
        VertexId b = builder.addVertex(new Vector3(0, 1, 0));
        VertexId c = builder.addVertex(new Vector3(-1, 0, 0));
        VertexId d = builder.addVertex(new Vector3(0, -1, 0));
        builder.addFace(List.of(retained, a, b));
        FaceId rewritten = builder.addFace(List.of(retired, c, d));
        ProtoMeshSnapshot source = builder.snapshot();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(source);

        VertexWeldOperation.WeldResult result = transaction.apply(draft ->
                VertexWeldOperation.weldCoincident(draft, retained, retired, 0));
        ProtoMeshSnapshot edited = transaction.commit();

        assertTrue(source.vertices().containsKey(retired));
        assertFalse(edited.vertices().containsKey(retired));
        assertFalse(edited.faces().containsKey(rewritten));
        assertEquals(5, edited.vertices().size());
        assertEquals(2, edited.faces().size());
        assertEquals(1, result.replacementFaces().size());
        assertTrue(edited.isValid());
    }

    @Test
    void weldRejectsFaceCollapseBeforeChangingDraft() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId retained = builder.addVertex(Vector3.ZERO);
        VertexId retired = builder.addVertex(new Vector3(0.01, 0, 0));
        VertexId third = builder.addVertex(new Vector3(0, 1, 0));
        builder.addFace(List.of(retained, retired, third));
        ProtoMeshSnapshot source = builder.snapshot();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(source);

        assertThrows(IllegalArgumentException.class, () -> transaction.apply(draft ->
                VertexWeldOperation.weldCoincident(draft, retained, retired, 0.02)));
        assertEquals(source.vertices().keySet(), transaction.preview().vertices().keySet());
        assertEquals(source.faces().keySet(), transaction.preview().faces().keySet());
    }

    private static SplitFixture splitFixture() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId v0 = builder.addVertex(new Vector3(0, 0, 0));
        VertexId v1 = builder.addVertex(new Vector3(2, 0, 0));
        VertexId v2 = builder.addVertex(new Vector3(2, 2, 0));
        VertexId v3 = builder.addVertex(new Vector3(0, 2, 0));
        builder.addFace(List.of(v0, v1, v2), attributes(0, 0, 1, 0, 1, 1), Set.of("surface"));
        builder.addFace(List.of(v0, v2, v3), attributes(0, 0, 1, 1, 0, 1), Set.of("surface"));
        ProtoMeshSnapshot mesh = builder.snapshot();
        EdgeId diagonal = mesh.edges().values().stream()
                .filter(edge -> edge.uses().size() == 2).findFirst().orElseThrow().id();
        return new SplitFixture(mesh, diagonal);
    }

    private static List<CornerAttributes> attributes(double... coordinates) {
        return java.util.stream.IntStream.range(0, coordinates.length / 2)
                .mapToObj(index -> new CornerAttributes(
                        Optional.of(new Vector2(coordinates[index * 2], coordinates[index * 2 + 1])),
                        Optional.of(new Vector3(0, 0, 1)), Map.of("weight", (double) index)))
                .toList();
    }

    private record SplitFixture(ProtoMeshSnapshot mesh, EdgeId diagonal) {
    }
}
