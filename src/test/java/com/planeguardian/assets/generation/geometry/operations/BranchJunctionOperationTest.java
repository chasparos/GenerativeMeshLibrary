package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.ProtoMeshEditTransaction;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.VertexId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchJunctionOperationTest {
    @Test
    void parentQuadTransitionsToEightVertexChildUsingOnlyQuads() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        List<VertexId> parent = List.of(
                builder.addVertex(new Vector3(-1, -1, 0)),
                builder.addVertex(new Vector3(1, -1, 0)),
                builder.addVertex(new Vector3(1, 1, 0)),
                builder.addVertex(new Vector3(-1, 1, 0)));
        FaceId parentFace = builder.addFace(parent, java.util.Collections.nCopies(4,
                com.planeguardian.assets.generation.topology.CornerAttributes.EMPTY), Set.of("parent"));
        List<VertexId> child = ring(builder, 0.65, 2);
        ProtoMeshSnapshot source = builder.snapshot();
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(source);

        BranchJunctionOperation.BranchJunctionResult junction = transaction.apply(draft ->
                BranchJunctionOperation.create(draft, parentFace, child, 0.25,
                        new Vector3(0, 0, 0.5), Set.of("junction")));
        ProtoMeshSnapshot edited = transaction.commit();

        assertTrue(source.faces().containsKey(parentFace));
        assertFalse(edited.faces().containsKey(parentFace));
        assertFalse(edited.faces().containsKey(junction.retiredCollarCap()));
        assertEquals(6, junction.transition().allFaces().size());
        assertEquals(2, junction.transition().wedgeFaces().size());
        assertEquals(14, edited.faces().size());
        assertEquals(12, edited.boundaryEdges().size());
        assertTrue(edited.faces().values().stream().allMatch(face -> face.loops().size() == 4));
        assertTrue(junction.transition().allFaces().stream()
                .map(edited.faces()::get).allMatch(face -> face.semanticGroups().contains("junction")));
        assertTrue(edited.isValid());
    }

    @Test
    void rejectsTwistedChildRingPhase() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        List<VertexId> parent = List.of(
                builder.addVertex(new Vector3(-1, -1, 0)), builder.addVertex(new Vector3(1, -1, 0)),
                builder.addVertex(new Vector3(1, 1, 0)), builder.addVertex(new Vector3(-1, 1, 0)));
        FaceId parentFace = builder.addFace(parent);
        List<VertexId> twisted = new ArrayList<>(ring(builder, 0.65, 2));
        java.util.Collections.rotate(twisted, 1);
        ProtoMeshEditTransaction transaction = ProtoMeshEditTransaction.begin(builder.snapshot());

        assertThrows(IllegalArgumentException.class, () -> transaction.apply(draft ->
                BranchJunctionOperation.create(draft, parentFace, twisted, 0.25,
                        new Vector3(0, 0, 0.5), Set.of())));
    }

    private static List<VertexId> ring(ProtoMeshBuilder builder, double radius, double z) {
        List<VertexId> result = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            // Phase L0 with the first square corner; the reviewed pattern then
            // distributes its two extraordinary quads on opposite sides.
            double angle = -3 * StrictMath.PI / 4 + 2 * StrictMath.PI * index / 8;
            result.add(builder.addVertex(new Vector3(
                    radius * StrictMath.cos(angle), radius * StrictMath.sin(angle), z)));
        }
        return List.copyOf(result);
    }
}
