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
        return bridge(builder, first, second,
                java.util.Collections.nCopies(first.size(), CornerAttributes.EMPTY),
                java.util.Collections.nCopies(second.size(), CornerAttributes.EMPTY),
                semanticGroups);
    }

    /**
     * Bridges two rings, attaching per-vertex authored attributes (for example smooth
     * radial normals and length/angle UVs on a tube wall) to each ring's corners.
     * {@code firstAttributes} and {@code secondAttributes} must align positionally
     * with {@code first} and {@code second}.
     */
    public static List<FaceId> bridge(
            ProtoMeshBuilder builder,
            List<VertexId> first,
            List<VertexId> second,
            List<CornerAttributes> firstAttributes,
            List<CornerAttributes> secondAttributes,
            Set<String> semanticGroups) {
        List<VertexId> ringA = List.copyOf(first);
        List<VertexId> ringB = List.copyOf(second);
        if (ringA.size() < 3 || ringA.size() != ringB.size()) {
            throw new IllegalArgumentException("Bridge rings must have the same size of at least three");
        }
        if (firstAttributes.size() != ringA.size() || secondAttributes.size() != ringB.size()) {
            throw new IllegalArgumentException("Attribute lists must match their ring's vertex count");
        }
        List<FaceId> faces = new ArrayList<>(ringA.size());
        for (int index = 0; index < ringA.size(); index++) {
            int next = (index + 1) % ringA.size();
            faces.add(builder.addFace(
                    List.of(ringA.get(index), ringA.get(next), ringB.get(next), ringB.get(index)),
                    List.of(firstAttributes.get(index), firstAttributes.get(next),
                            secondAttributes.get(next), secondAttributes.get(index)),
                    semanticGroups));
        }
        return List.copyOf(faces);
    }
}
