package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.List;
import java.util.Set;

/** Reviewed quad-first parent-face collar terminating in an eight-vertex child boundary. */
public final class BranchJunctionOperation {
    private BranchJunctionOperation() {
    }

    public static BranchJunctionResult create(
            ProtoMeshBuilder builder,
            FaceId parentFace,
            List<VertexId> childRing,
            double insetFraction,
            Vector3 collarOffset,
            Set<String> transitionGroups) {
        if (childRing.size() != 8) throw new IllegalArgumentException("The initial child junction requires an eight-vertex ring");
        BranchCollarOperation.BranchCollarResult collar = BranchCollarOperation.create(
                builder, parentFace, insetFraction, collarOffset);
        requireAlignedPhase(builder, collar.extrusion().capRing(), childRing);
        FaceId retiredCollarCap = collar.extrusion().capFace();
        builder.removeFace(retiredCollarCap);
        UnequalRingBridgeOperation.TransitionResult transition = UnequalRingBridgeOperation.bridge(
                builder, collar.extrusion().capRing(), childRing,
                StandardLoopTransition.FOUR_TO_EIGHT, transitionGroups);
        return new BranchJunctionResult(collar, retiredCollarCap, childRing, transition);
    }

    private static void requireAlignedPhase(
            ProtoMeshBuilder builder, List<VertexId> collarRing, List<VertexId> childRing) {
        double suppliedCost = phaseCost(builder, collarRing, childRing, 0);
        double bestCost = suppliedCost;
        for (int offset = 1; offset < childRing.size(); offset++) {
            bestCost = StrictMath.min(bestCost, phaseCost(builder, collarRing, childRing, offset));
        }
        if (suppliedCost > bestCost + 1.0e-12) {
            throw new IllegalArgumentException("Child ring phase is not aligned with the collar ring");
        }
    }

    private static double phaseCost(
            ProtoMeshBuilder builder, List<VertexId> collarRing, List<VertexId> childRing, int offset) {
        double cost = 0;
        for (int index = 0; index < collarRing.size(); index++) {
            Vector3 collar = builder.requireVertex(collarRing.get(index)).position();
            Vector3 child = builder.requireVertex(childRing.get((index * 2 + offset) % childRing.size())).position();
            double dx = collar.x() - child.x();
            double dy = collar.y() - child.y();
            double dz = collar.z() - child.z();
            cost += dx * dx + dy * dy + dz * dz;
        }
        return cost;
    }

    public record BranchJunctionResult(
            BranchCollarOperation.BranchCollarResult collar,
            FaceId retiredCollarCap,
            List<VertexId> childRing,
            UnequalRingBridgeOperation.TransitionResult transition) {
        public BranchJunctionResult {
            childRing = List.copyOf(childRing);
        }
    }
}
