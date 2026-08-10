package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnequalRingBridgeOperationTest {
    @Test
    void eightToTwelveUsesTwoEvenlyDistributedWedgeQuads() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        List<VertexId> eight = ring(builder, 8, 1, 0);
        List<VertexId> twelve = ring(builder, 12, 1.4, 0.4);

        var result = UnequalRingBridgeOperation.bridge(
                builder, eight, twelve, StandardLoopTransition.EIGHT_TO_TWELVE, Set.of("transition"));
        var mesh = builder.snapshot();

        assertTrue(result.increasing());
        assertEquals(8, result.regularFaces().size());
        assertEquals(2, result.wedgeFaces().size());
        assertEquals(10, mesh.faces().size());
        assertTrue(mesh.faces().values().stream().allMatch(face -> face.loops().size() == 4));
        assertEquals(20, mesh.boundaryEdges().size());
        assertTrue(mesh.isValid());
    }

    @Test
    void reverseEightToSixteenRetainsQuadWindingAndFourWedges() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        List<VertexId> sixteen = ring(builder, 16, 1.5, 0);
        List<VertexId> eight = ring(builder, 8, 1, 0.5);

        var result = UnequalRingBridgeOperation.bridge(
                builder, sixteen, eight, StandardLoopTransition.EIGHT_TO_SIXTEEN, Set.of());
        var mesh = builder.snapshot();

        assertTrue(!result.increasing());
        assertEquals(8, result.regularFaces().size());
        assertEquals(4, result.wedgeFaces().size());
        assertEquals(12, mesh.faces().size());
        assertEquals(24, mesh.boundaryEdges().size());
        assertTrue(mesh.isValid());
    }

    @Test
    void catalogRejectsUnreviewedRingSizes() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        List<VertexId> eight = ring(builder, 8, 1, 0);
        List<VertexId> ten = ring(builder, 10, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> UnequalRingBridgeOperation.bridge(
                builder, eight, ten, StandardLoopTransition.EIGHT_TO_TWELVE, Set.of()));
        assertEquals(0, builder.snapshot().faces().size());
    }

    private static List<VertexId> ring(ProtoMeshBuilder builder, int count, double radius, double z) {
        List<VertexId> ring = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double angle = index * StrictMath.PI * 2 / count;
            ring.add(builder.addVertex(new Vector3(
                    StrictMath.cos(angle) * radius, StrictMath.sin(angle) * radius, z)));
        }
        return List.copyOf(ring);
    }
}
