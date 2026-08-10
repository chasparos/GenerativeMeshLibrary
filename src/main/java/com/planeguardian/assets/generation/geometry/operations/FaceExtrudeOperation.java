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

/** Replaces one face with translated cap vertices and a quad side strip. */
public final class FaceExtrudeOperation {
    private FaceExtrudeOperation() {
    }

    public static ExtrudeResult extrude(ProtoMeshBuilder builder, FaceId sourceFaceId, Vector3 offset) {
        if (VectorMath.lengthSquared(offset) <= 1.0e-24) {
            throw new IllegalArgumentException("Extrusion offset must be non-zero");
        }
        ProtoFace sourceFace = builder.requireFace(sourceFaceId);
        List<ProtoLoop> sourceLoops = sourceFace.loops().stream().map(builder::requireLoop).toList();
        List<VertexId> baseRing = sourceLoops.stream().map(ProtoLoop::vertexId).toList();
        List<VertexId> capRing = new ArrayList<>(baseRing.size());
        for (VertexId vertexId : baseRing) {
            capRing.add(builder.addVertex(VectorMath.add(builder.requireVertex(vertexId).position(), offset)));
        }
        builder.removeFace(sourceFaceId);
        List<FaceId> sideFaces = new ArrayList<>(baseRing.size());
        for (int index = 0; index < baseRing.size(); index++) {
            int next = (index + 1) % baseRing.size();
            CornerAttributes current = sourceLoops.get(index).attributes();
            CornerAttributes nextAttributes = sourceLoops.get(next).attributes();
            sideFaces.add(builder.addFace(List.of(
                            baseRing.get(index), baseRing.get(next), capRing.get(next), capRing.get(index)),
                    List.of(current, nextAttributes, nextAttributes, current), sourceFace.semanticGroups()));
        }
        FaceId capFace = RingFillOperation.fill(builder, capRing,
                sourceLoops.stream().map(ProtoLoop::attributes).toList(), false, sourceFace.semanticGroups());
        return new ExtrudeResult(sourceFaceId, baseRing, capRing, sideFaces, capFace);
    }

    public record ExtrudeResult(
            FaceId retiredFace,
            List<VertexId> baseRing,
            List<VertexId> capRing,
            List<FaceId> sideFaces,
            FaceId capFace) {
        public ExtrudeResult {
            baseRing = List.copyOf(baseRing);
            capRing = List.copyOf(capRing);
            sideFaces = List.copyOf(sideFaces);
        }
    }
}
