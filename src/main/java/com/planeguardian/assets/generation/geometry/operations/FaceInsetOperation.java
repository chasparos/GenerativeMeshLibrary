package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoLoop;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.List;

/** Centroid-directed planar inset that replaces one face with a quad border and inner face. */
public final class FaceInsetOperation {
    private FaceInsetOperation() {
    }

    public static InsetResult inset(ProtoMeshBuilder builder, FaceId sourceFaceId, double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction >= 1) {
            throw new IllegalArgumentException("Inset fraction must be in (0, 1)");
        }
        ProtoFace sourceFace = builder.requireFace(sourceFaceId);
        List<ProtoLoop> sourceLoops = sourceFace.loops().stream().map(builder::requireLoop).toList();
        List<VertexId> outerRing = sourceLoops.stream().map(ProtoLoop::vertexId).toList();
        Vector3 centroid = centroid(builder, outerRing);
        List<VertexId> insetRing = new ArrayList<>(outerRing.size());
        for (VertexId vertexId : outerRing) {
            Vector3 position = builder.requireVertex(vertexId).position();
            insetRing.add(builder.addVertex(VectorMath.add(
                    VectorMath.scale(position, 1 - fraction), VectorMath.scale(centroid, fraction))));
        }

        builder.removeFace(sourceFaceId);
        List<FaceId> borderFaces = new ArrayList<>(outerRing.size());
        for (int index = 0; index < outerRing.size(); index++) {
            int next = (index + 1) % outerRing.size();
            CornerAttributes currentAttributes = sourceLoops.get(index).attributes();
            CornerAttributes nextAttributes = sourceLoops.get(next).attributes();
            borderFaces.add(builder.addFace(List.of(
                            outerRing.get(index), outerRing.get(next), insetRing.get(next), insetRing.get(index)),
                    List.of(currentAttributes, nextAttributes, nextAttributes, currentAttributes),
                    sourceFace.semanticGroups()));
        }
        List<CornerAttributes> insetAttributes = sourceLoops.stream().map(ProtoLoop::attributes).toList();
        FaceId insetFace = RingFillOperation.fill(
                builder, insetRing, insetAttributes, false, sourceFace.semanticGroups());
        return new InsetResult(sourceFaceId, outerRing, insetRing, borderFaces, insetFace);
    }

    private static Vector3 centroid(ProtoMeshBuilder builder, List<VertexId> ring) {
        Vector3 sum = Vector3.ZERO;
        for (VertexId vertexId : ring) sum = VectorMath.add(sum, builder.requireVertex(vertexId).position());
        return VectorMath.scale(sum, 1.0 / ring.size());
    }

    public record InsetResult(
            FaceId retiredFace,
            List<VertexId> outerRing,
            List<VertexId> insetRing,
            List<FaceId> borderFaces,
            FaceId insetFace) {
        public InsetResult {
            outerRing = List.copyOf(outerRing);
            insetRing = List.copyOf(insetRing);
            borderFaces = List.copyOf(borderFaces);
        }
    }
}
