package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.ProtoMeshEditTransaction;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.VertexId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceEditOperationsTest {
    @Test
    void insetReplacesFaceWithQuadBorderAndPreservesSource() {
        Fixture fixture = quad();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(fixture.mesh());
        FaceInsetOperation.InsetResult result = transaction.apply(builder ->
                FaceInsetOperation.inset(builder, fixture.face(), 0.25));

        ProtoMeshSnapshot edited = transaction.commit();

        assertTrue(fixture.mesh().faces().containsKey(fixture.face()));
        assertFalse(edited.faces().containsKey(fixture.face()));
        assertEquals(8, edited.vertices().size());
        assertEquals(5, edited.faces().size());
        assertEquals(4, edited.boundaryEdges().size());
        assertEquals(4, result.borderFaces().size());
        assertTrue(result.borderFaces().stream().allMatch(id -> id.value() > fixture.face().value()));
        assertTrue(result.insetFace().value() > fixture.face().value());
        assertEquals(new Vector3(0.25, 0.25, 0),
                edited.vertices().get(result.insetRing().get(0)).position());
        assertTrue(edited.isValid());
    }

    @Test
    void extrusionBuildsQuadSidesAndTranslatedCap() {
        Fixture fixture = quad();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(fixture.mesh());
        FaceExtrudeOperation.ExtrudeResult result = transaction.apply(builder ->
                FaceExtrudeOperation.extrude(builder, fixture.face(), new Vector3(0, 0, 3)));

        ProtoMeshSnapshot edited = transaction.commit();

        assertEquals(8, edited.vertices().size());
        assertEquals(5, edited.faces().size());
        assertEquals(4, result.sideFaces().size());
        assertTrue(result.capRing().stream()
                .allMatch(id -> edited.vertices().get(id).position().z() == 3));
        assertTrue(edited.faces().values().stream()
                .allMatch(face -> face.semanticGroups().contains("host")));
        assertTrue(edited.isValid());
    }

    @Test
    void branchCollarIsAReusableInsetExtrusionComposition() {
        Fixture fixture = quad();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(fixture.mesh());
        BranchCollarOperation.BranchCollarResult collar = transaction.apply(builder ->
                BranchCollarOperation.create(builder, fixture.face(), 0.2, new Vector3(0, 0, 0.75)));

        ProtoMeshSnapshot edited = transaction.commit();

        assertEquals(12, edited.vertices().size());
        assertEquals(9, edited.faces().size());
        assertEquals(collar.inset().insetRing(), collar.extrusion().baseRing());
        assertEquals(4, edited.boundaryEdges().size());
        assertTrue(edited.isValid());
    }

    @Test
    void removedTopologyIdentitiesAreNotReused() {
        Fixture fixture = quad();
        ProtoMeshBuilder builder = ProtoMeshBuilder.copyOf(fixture.mesh());
        long highestOriginalEdge = fixture.mesh().edges().lastKey().value();
        builder.removeFace(fixture.face());
        FaceId replacement = builder.addFace(fixture.ring());
        ProtoMeshSnapshot rebuilt = builder.snapshot();

        assertTrue(replacement.value() > fixture.face().value());
        assertTrue(rebuilt.edges().firstKey().value() > highestOriginalEdge);
        assertTrue(rebuilt.isValid());
    }

    private static Fixture quad() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        List<VertexId> ring = List.of(
                builder.addVertex(new Vector3(0, 0, 0)),
                builder.addVertex(new Vector3(2, 0, 0)),
                builder.addVertex(new Vector3(2, 2, 0)),
                builder.addVertex(new Vector3(0, 2, 0)));
        FaceId face = builder.addFace(ring, java.util.Collections.nCopies(4,
                com.planeguardian.assets.generation.topology.CornerAttributes.EMPTY), Set.of("host"));
        return new Fixture(builder.snapshot(), face, ring);
    }

    private record Fixture(ProtoMeshSnapshot mesh, FaceId face, List<VertexId> ring) {
    }
}
