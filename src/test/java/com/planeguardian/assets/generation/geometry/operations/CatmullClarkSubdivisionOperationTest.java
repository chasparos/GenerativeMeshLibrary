package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.VertexId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatmullClarkSubdivisionOperationTest {

    @Test
    void oneLevelQuadruplesFacesOnAFlatGridAndPreservesVertexCount() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId[][] grid = new VertexId[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                grid[i][j] = builder.addVertex(new Vector3(i, 0, j));
            }
        }
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                builder.addFace(List.of(grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]));
            }
        }
        assertEquals(4, builder.snapshot().faces().size());

        CatmullClarkSubdivisionOperation.subdivide(builder);
        ProtoMeshSnapshot subdivided = builder.snapshot();

        assertTrue(subdivided.isValid(), () -> "issues: " + subdivided.issues());
        assertEquals(16, subdivided.faces().size());
        // 9 original + 4 face points + 12 edge points = 25.
        assertEquals(25, subdivided.vertices().size());
        // The original grid's flat interior point must stay fixed (only fan-hub/crease vertices move).
        assertEquals(new Vector3(1, 0, 1), subdivided.vertices().get(grid[1][1]).position());
    }

    @Test
    void interiorFanHubRelaxesTowardItsNeighboursInsteadOfStayingASharpCone() {
        // A 6-sided pyramid apex, elevated well above the ring plane, surrounded by 6 quads
        // (mirroring how TopologyGenerator's pole-fan fill can leave a valence-n hub proud of its
        // neighbours) should move toward the ring average after one subdivision pass, not stay put.
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId apex = builder.addVertex(new Vector3(0, 5, 0));
        VertexId[] ring = new VertexId[6];
        VertexId[] mid = new VertexId[6];
        for (int k = 0; k < 6; k++) {
            double angle = 2 * Math.PI * k / 6;
            ring[k] = builder.addVertex(new Vector3(Math.cos(angle), 0, Math.sin(angle)));
        }
        for (int k = 0; k < 6; k++) {
            int next = (k + 1) % 6;
            Vector3 a = builder.requireVertex(ring[k]).position();
            Vector3 b = builder.requireVertex(ring[next]).position();
            mid[k] = builder.addVertex(new Vector3((a.x() + b.x()) / 2, 0, (a.z() + b.z()) / 2));
        }
        for (int k = 0; k < 6; k++) {
            int previous = (k - 1 + 6) % 6;
            builder.addFace(List.of(apex, mid[previous], ring[k], mid[k]));
        }
        assertEquals(6, builder.snapshot().faces().size());
        assertEquals(5.0, builder.requireVertex(apex).position().y());

        CatmullClarkSubdivisionOperation.subdivide(builder);

        assertTrue(builder.snapshot().isValid(), () -> "issues: " + builder.snapshot().issues());
        assertTrue(builder.requireVertex(apex).position().y() < 5.0,
                "the apex should relax toward its neighbours rather than staying a sharp cone tip");
    }
}
