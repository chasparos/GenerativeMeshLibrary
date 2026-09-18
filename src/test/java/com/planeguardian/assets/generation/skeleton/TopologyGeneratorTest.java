package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.VertexId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologyGeneratorTest {

    @Test
    void cubeCageGeneratesSixQuadFacesWithValenceThreeAtEveryCorner() {
        TopologicalSkeleton cube = unitCubeSkeleton();
        // subdivisionLevels=0: this test validates the raw control-cage quadrangulation invariants
        // (exact vertex/edge/face counts and per-corner valence), not the (separately tested)
        // Catmull-Clark refinement applied by the default generate(skeleton) overload.
        GenerationResult result = new TopologyGenerator().generate(cube, 0);
        ProtoMeshSnapshot mesh = result.mesh();

        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        assertEquals(8, mesh.vertices().size());
        assertEquals(12, mesh.edges().size());
        assertEquals(6, mesh.faces().size());
        for (VertexId vertexId : mesh.vertices().keySet()) {
            long degree = mesh.edges().values().stream()
                    .filter(edge -> edge.vertexA().equals(vertexId) || edge.vertexB().equals(vertexId))
                    .count();
            assertEquals(3, degree, "every cube corner must have valence 3");
        }
    }

    @Test
    void oddBoundaryParityIsAutoRepairedByIncrementingLowestDensityCurve() {
        // A "lens" of two poles joined by two curves is a 2-sided cage with two faces
        // whose boundary sum is densityA + densityB; start it odd (1 + 2 = 3) and
        // require the repair to bump the lower-density curve (density 1) up to 2.
        Pole a = new Pole("A", new Vector3(-1, 0, 0), 2, false);
        Pole b = new Pole("B", new Vector3(1, 0, 0), 2, false);
        GuideCurve top = new GuideCurve("top", "A", "B", List.of(new Vector3(0, 1, 0)), 1);
        GuideCurve bottom = new GuideCurve("bottom", "A", "B", List.of(new Vector3(0, -1, 0)), 2);
        Map<String, Pole> poles = new LinkedHashMap<>();
        poles.put(a.id(), a);
        poles.put(b.id(), b);
        TopologicalSkeleton skeleton = new TopologicalSkeleton(poles, List.of(top, bottom), false, null);

        TopologyGenerator.ParityRepairResult repair = new TopologyGenerator().repairParity(skeleton);

        assertEquals(1, repair.appliedFixes().size());
        assertEquals(2, repair.skeleton().curve("top").densitySegmentCount());
        for (SubPatch patch : repair.skeleton().tracePatches()) {
            assertEquals(0, patch.boundarySegmentSum() % 2, "every patch boundary sum must be even after repair");
        }
    }

    @Test
    void mirroringWeldsSeamPolesToTheirFullRequestedValence() {
        // Half of a cube, cut through the middle on x=0: 4 symmetry-plane poles (S0..S3) form
        // the open seam square, 4 interior poles (I0..I3) form the far face, and 4 curves
        // connect matching corners. Filling every side face + the far face, but leaving the
        // seam square itself unfilled (it closes naturally on mirroring), reproduces exactly
        // the 6-quad topology of a full cube once mirrored and welded.
        Plane symmetryPlane = new Plane(Vector3.ZERO, new Vector3(1, 0, 0));
        double[][] seamCorners = {{0, -1, -1}, {0, 1, -1}, {0, 1, 1}, {0, -1, 1}};
        double[][] interiorCorners = {{1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, -1, 1}};

        Map<String, Pole> poles = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            // Each symmetry pole has 3 half-mesh edges (2 seam + 1 connecting): seamCount=2,
            // freeCount=1, so requestedValence == seamCount + 2*freeCount == 2 + 2 == 4.
            poles.put("S" + i, new Pole("S" + i, new Vector3(seamCorners[i][0], seamCorners[i][1], seamCorners[i][2]), 4, true));
            // Each interior pole has 3 incident curves (2 far-face + 1 connecting): valence 3.
            poles.put("I" + i, new Pole("I" + i, new Vector3(interiorCorners[i][0], interiorCorners[i][1], interiorCorners[i][2]), 3, false));
        }

        List<GuideCurve> curves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            curves.add(new GuideCurve("seam" + i, "S" + i, "S" + next, List.of(), 1));
            curves.add(new GuideCurve("far" + i, "I" + i, "I" + next, List.of(), 1));
            curves.add(new GuideCurve("connect" + i, "S" + i, "I" + i, List.of(), 1));
        }

        TopologicalSkeleton skeleton = new TopologicalSkeleton(poles, curves, true, symmetryPlane);
        // subdivisionLevels=0: this test validates the raw mirror-and-weld vertex/edge/face counts,
        // not the (separately tested) Catmull-Clark refinement applied by generate(skeleton).
        GenerationResult result = new TopologyGenerator().generate(skeleton, 0);

        assertTrue(result.mesh().isValid(), () -> "issues: " + result.mesh().issues());
        // 4 far (I) + 4 mirrored-far (I') + 4 shared seam (S) vertices; 4 far + 4 mirrored-far
        // + 4 seam + 8 connecting (4 original + 4 mirrored) edges; far face + mirrored far face
        // + 4 side walls x2 (original + mirrored) faces. V - E + F == 2, a closed (genus-0) solid.
        assertEquals(12, result.mesh().vertices().size());
        assertEquals(20, result.mesh().edges().size());
        assertEquals(10, result.mesh().faces().size());
        assertEquals(4, result.boundaryConstraints().get("S0").requestedValence());
        assertEquals(3, result.boundaryConstraints().get("S0").halfMeshValence());
    }

    @Test
    void interiorPoleValenceMustMatchGraphDegree() {
        Pole a = new Pole("A", new Vector3(0, 0, 0), 3, false); // claims valence 3 but only has 2 curves
        Pole b = new Pole("B", new Vector3(1, 0, 0), 2, false);
        Map<String, Pole> poles = new LinkedHashMap<>();
        poles.put(a.id(), a);
        poles.put(b.id(), b);
        GuideCurve curve1 = new GuideCurve("c1", "A", "B", List.of(new Vector3(0, 1, 0)), 1);
        GuideCurve curve2 = new GuideCurve("c2", "A", "B", List.of(new Vector3(0, -1, 0)), 1);

        assertThrows(TopologyParityException.class,
                () -> new TopologicalSkeleton(poles, List.of(curve1, curve2), false, null));
    }

    @Test
    void symmetryPlanePoleOffPlanePositionIsRejected() {
        Plane plane = new Plane(Vector3.ZERO, new Vector3(1, 0, 0));
        Pole offPlane = new Pole("S", new Vector3(0.5, 0, 0), 2, true);
        Pole other = new Pole("I", new Vector3(1, 0, 0), 2, false);
        Map<String, Pole> poles = new LinkedHashMap<>();
        poles.put(offPlane.id(), offPlane);
        poles.put(other.id(), other);
        GuideCurve curve = new GuideCurve("c", "S", "I", List.of(), 1);

        assertThrows(IllegalArgumentException.class,
                () -> new TopologicalSkeleton(poles, List.of(curve), true, plane));
    }

    @Test
    void everyGeneratedFaceNormalPointsOutward() {
        // Regression guard for the winding-direction bug: a closed, star-convex cage (the unit
        // cube) must generate every quad with a consistent CCW (outward-facing) winding, so its
        // Newell normal points away from the mesh centroid at every face. An inward-facing
        // (back-face-culled-to-black) winding — the historical bug — would flip all of these.
        assertEveryFaceNormalPointsOutward(new TopologyGenerator().generate(unitCubeSkeleton(), 0).mesh());
    }

    @Test
    void everyMirroredFaceNormalPointsOutward() {
        // The mirror-and-weld path must also keep a consistent outward winding across both halves:
        // the mirrored copies are reversed to compensate for reflection flipping orientation.
        assertEveryFaceNormalPointsOutward(new TopologyGenerator().generate(mirroredCubeSkeleton(), 0).mesh());
    }

    private static void assertEveryFaceNormalPointsOutward(ProtoMeshSnapshot mesh) {
        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        Vector3 centroid = meshCentroid(mesh);
        for (ProtoFace face : mesh.faces().values()) {
            List<Vector3> loop = new ArrayList<>();
            for (var loopId : face.loops()) {
                loop.add(mesh.vertices().get(mesh.loops().get(loopId).vertexId()).position());
            }
            Vector3 normal = newellNormal(loop);
            Vector3 faceCentroid = averagePosition(loop);
            Vector3 outward = new Vector3(
                    faceCentroid.x() - centroid.x(), faceCentroid.y() - centroid.y(), faceCentroid.z() - centroid.z());
            double dot = normal.x() * outward.x() + normal.y() * outward.y() + normal.z() * outward.z();
            assertTrue(dot > 0, () -> "face " + face.id() + " normal points inward (dot=" + dot + ")");
        }
    }

    private static Vector3 newellNormal(List<Vector3> loop) {
        double nx = 0;
        double ny = 0;
        double nz = 0;
        for (int i = 0; i < loop.size(); i++) {
            Vector3 a = loop.get(i);
            Vector3 b = loop.get((i + 1) % loop.size());
            nx += (a.y() - b.y()) * (a.z() + b.z());
            ny += (a.z() - b.z()) * (a.x() + b.x());
            nz += (a.x() - b.x()) * (a.y() + b.y());
        }
        return new Vector3(nx, ny, nz);
    }

    private static Vector3 averagePosition(List<Vector3> points) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Vector3 p : points) {
            x += p.x();
            y += p.y();
            z += p.z();
        }
        int n = Math.max(1, points.size());
        return new Vector3(x / n, y / n, z / n);
    }

    private static Vector3 meshCentroid(ProtoMeshSnapshot mesh) {
        return averagePosition(mesh.vertices().values().stream().map(v -> v.position()).toList());
    }

    private static TopologicalSkeleton mirroredCubeSkeleton() {
        Plane symmetryPlane = new Plane(Vector3.ZERO, new Vector3(1, 0, 0));
        double[][] seamCorners = {{0, -1, -1}, {0, 1, -1}, {0, 1, 1}, {0, -1, 1}};
        double[][] interiorCorners = {{1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, -1, 1}};
        Map<String, Pole> poles = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            poles.put("S" + i, new Pole("S" + i, new Vector3(seamCorners[i][0], seamCorners[i][1], seamCorners[i][2]), 4, true));
            poles.put("I" + i, new Pole("I" + i, new Vector3(interiorCorners[i][0], interiorCorners[i][1], interiorCorners[i][2]), 3, false));
        }
        List<GuideCurve> curves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            curves.add(new GuideCurve("seam" + i, "S" + i, "S" + next, List.of(), 1));
            curves.add(new GuideCurve("far" + i, "I" + i, "I" + next, List.of(), 1));
            curves.add(new GuideCurve("connect" + i, "S" + i, "I" + i, List.of(), 1));
        }
        return new TopologicalSkeleton(poles, curves, true, symmetryPlane);
    }

    private static TopologicalSkeleton unitCubeSkeleton() {
        double[][] corners = {
                {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
                {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1},
        };
        Map<String, Pole> poles = new LinkedHashMap<>();
        for (int i = 0; i < corners.length; i++) {
            String id = "P" + i;
            poles.put(id, new Pole(id, new Vector3(corners[i][0], corners[i][1], corners[i][2]), 3, false));
        }
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom face
                {4, 5}, {5, 6}, {6, 7}, {7, 4}, // top face
                {0, 4}, {1, 5}, {2, 6}, {3, 7}, // verticals
        };
        List<GuideCurve> curves = new ArrayList<>();
        for (int i = 0; i < edges.length; i++) {
            curves.add(new GuideCurve("E" + i, "P" + edges[i][0], "P" + edges[i][1], List.of(), 1));
        }
        return new TopologicalSkeleton(poles, curves, false, null);
    }
}
