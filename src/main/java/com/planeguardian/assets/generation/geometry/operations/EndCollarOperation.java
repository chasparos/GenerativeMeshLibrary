package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.curves.CurveFrame;
import com.planeguardian.assets.generation.geometry.tube.TubeEnd;
import com.planeguardian.assets.generation.math.VectorMath;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Extends the forward end with a scaled circular ring and quad collar. */
public final class EndCollarOperation {
    private EndCollarOperation() {
    }

    public static CollarResult extendForward(
            ProtoMeshBuilder builder, TubeEnd source,
            double length, double radiusScale, Set<String> semanticGroups) {
        if (!Double.isFinite(length) || length <= 0) throw new IllegalArgumentException("Collar length must be positive");
        if (!Double.isFinite(radiusScale) || radiusScale <= 0) throw new IllegalArgumentException("Collar radius scale must be positive");
        CurveFrame frame = source.frame();
        Vector3 center = VectorMath.add(frame.position(), VectorMath.scale(frame.tangent(), length));
        double radius = source.nominalRadius() * radiusScale;
        List<VertexId> newRing = new ArrayList<>(source.ringVertices().size());
        for (int index = 0; index < source.ringVertices().size(); index++) {
            double angle = index * StrictMath.PI * 2 / source.ringVertices().size();
            Vector3 radial = VectorMath.add(
                    VectorMath.scale(frame.normal(), StrictMath.cos(angle)),
                    VectorMath.scale(frame.binormal(), StrictMath.sin(angle)));
            newRing.add(builder.addVertex(VectorMath.add(center, VectorMath.scale(radial, radius))));
        }
        List<FaceId> faces = RingBridgeOperation.bridge(builder, source.ringVertices(), newRing, semanticGroups);
        CurveFrame newFrame = new CurveFrame(frame.arcFraction(), frame.parameter(), center,
                frame.tangent(), frame.normal(), frame.binormal(), frame.rollRadians());
        return new CollarResult(new TubeEnd(newRing, newFrame, radius), faces);
    }

    public record CollarResult(TubeEnd end, List<FaceId> faces) {
        public CollarResult {
            faces = List.copyOf(faces);
        }
    }
}
