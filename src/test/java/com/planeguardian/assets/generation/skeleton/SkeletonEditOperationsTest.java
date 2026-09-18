package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonEditOperationsTest {

    @Test
    void movingAnInteriorPoleReanchorsEveryIncidentCurveAndStillGenerates() {
        SkeletonEditOperations editor = new SkeletonEditOperations(unitCubeSkeleton());
        editor.movePole("P6", new Vector3(2, 2, 2));

        assertEquals(2, editor.pole("P6").position().x());
        // Every curve incident to P6 still references it by id, so generation succeeds unchanged.
        ProtoMeshSnapshot mesh = new TopologyGenerator().generate(editor.toGenerationSkeleton(), 0).mesh();
        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        assertEquals(6, mesh.faces().size());
    }

    @Test
    void onSymmetryPlanePoleIsConstrainedToThePlaneWhenMoved() {
        SkeletonEditOperations editor = new SkeletonEditOperations(mirroredCubeSkeleton());
        // S0 is on the x=0 plane; try to drag it off-axis to x=5.
        editor.movePole("S0", new Vector3(5, -2, -2));

        assertEquals(0.0, editor.pole("S0").position().x(), 1.0e-9, "on-plane pole must stay on x=0");
        assertEquals(-2.0, editor.pole("S0").position().y(), 1.0e-9);
        assertEquals(-2.0, editor.pole("S0").position().z(), 1.0e-9);
    }

    @Test
    void deletingACurveRemovesItAndAdjustsGeneration() {
        SkeletonEditOperations editor = new SkeletonEditOperations(unitCubeSkeleton());
        int before = editor.curves().size();
        editor.deleteCurve("E0");
        assertEquals(before - 1, editor.curves().size());
        assertThrows(IllegalArgumentException.class, () -> editor.curve("E0"));
    }

    @Test
    void splitCurveInsertsANewPoleAndTwoCurvesSharingIt() {
        SkeletonEditOperations editor = new SkeletonEditOperations(unitCubeSkeleton());
        int curvesBefore = editor.curves().size();
        int polesBefore = editor.poles().size();

        String newPoleId = editor.splitCurve("E0", editor.curveMidpoint("E0"));

        assertEquals(polesBefore + 1, editor.poles().size());
        assertEquals(curvesBefore + 1, editor.curves().size());
        // The original half keeps id E0 and now ends at the new pole; a "_split" curve begins there.
        assertEquals(newPoleId, editor.curve("E0").endPoleId());
        assertEquals(newPoleId, editor.curve("E0_split").startPoleId());
        assertEquals(4, editor.pole(newPoleId).requestedValence());
    }

    @Test
    void createCurveConnectsTwoExistingPolesAndRejectsSelfLoops() {
        SkeletonEditOperations editor = new SkeletonEditOperations(unitCubeSkeleton());
        // P0 and P6 are opposite cube corners not directly connected.
        String id = editor.createCurve("P0", "P6", 1);
        assertEquals("P0", editor.curve(id).startPoleId());
        assertEquals("P6", editor.curve(id).endPoleId());
        assertThrows(IllegalArgumentException.class, () -> editor.createCurve("P0", "P0", 1));
    }

    @Test
    void reattachEndpointRewiresACurveToADifferentPole() {
        SkeletonEditOperations editor = new SkeletonEditOperations(unitCubeSkeleton());
        assertEquals("P1", editor.curve("E0").endPoleId());
        editor.reattachEndpoint("E0", false, "P2");
        assertEquals("P2", editor.curve("E0").endPoleId());
        assertNotEquals("P1", editor.curve("E0").endPoleId());
    }

    @Test
    void aPoleWithOneCurveLightsUpAndItsCurveIsExcludedFromGeneration() {
        SkeletonEditOperations editor = new SkeletonEditOperations(unitCubeSkeleton());
        // Add a brand-new dangling pole connected by a single curve to an existing corner.
        editor.addPole("dangling", new Vector3(3, 3, 3), 4, false);
        String danglingCurve = editor.createCurve("P0", "dangling", 1);

        assertTrue(editor.singleCurvePoleIds().contains("dangling"), "the new pole must light up");
        // Generation must still succeed and ignore the dangling curve entirely (cube unchanged).
        TopologicalSkeleton generation = editor.toGenerationSkeleton();
        assertFalse(generation.poles().containsKey("dangling"), "dangling pole excluded from generation");
        ProtoMeshSnapshot mesh = new TopologyGenerator().generate(generation, 0).mesh();
        assertTrue(mesh.isValid(), () -> "issues: " + mesh.issues());
        assertEquals(6, mesh.faces().size());
        assertTrue(generation.curves().stream().noneMatch(c -> c.id().equals(danglingCurve)));
    }

    @Test
    void seamCurveControlPointsAreProjectedFlatOntoThePlane() {
        SkeletonEditOperations editor = new SkeletonEditOperations(mirroredCubeSkeleton());
        // seam0 joins two on-plane poles (S0->S1); give it an off-plane control point.
        editor.setControlPoints("seam0", List.of(new Vector3(7, 0, -1)));
        for (Vector3 controlPoint : editor.curve("seam0").controlPoints()) {
            assertEquals(0.0, controlPoint.x(), 1.0e-9, "seam curve control points must be flattened onto x=0");
        }
    }

    // ---- fixtures --------------------------------------------------------------------------

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
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        List<GuideCurve> curves = new ArrayList<>();
        for (int i = 0; i < edges.length; i++) {
            curves.add(new GuideCurve("E" + i, "P" + edges[i][0], "P" + edges[i][1], List.of(), 1));
        }
        return new TopologicalSkeleton(poles, curves, false, null);
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
}
