package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Quad bridge for equally sampled, correspondingly ordered rings. */
public final class RingBridgeOperation {
    private RingBridgeOperation() {
    }

    public static List<FaceId> bridge(
            ProtoMeshBuilder builder,
            List<VertexId> first,
            List<VertexId> second,
            Set<String> semanticGroups) {
        List<VertexId> ringA = List.copyOf(first);
        List<VertexId> ringB = List.copyOf(second);
        if (ringA.size() < 3 || ringA.size() != ringB.size()) {
            throw new IllegalArgumentException("Bridge rings must have the same size of at least three");
        }
        List<FaceId> faces = new ArrayList<>(ringA.size());
        for (int index = 0; index < ringA.size(); index++) {
            int next = (index + 1) % ringA.size();
            faces.add(builder.addFace(List.of(
                    ringA.get(index), ringA.get(next), ringB.get(next), ringB.get(index)),
                    java.util.Collections.nCopies(4, CornerAttributes.EMPTY), semanticGroups));
        }
        return List.copyOf(faces);
    }
}
