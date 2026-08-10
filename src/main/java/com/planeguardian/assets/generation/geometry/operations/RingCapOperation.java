package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.geometry.tube.TubeEnd;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.Vector2;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RingCapOperation {
    private RingCapOperation() {
    }

    /** Caps a tube end; outwardAlongFrameTangent selects winding. */
    public static FaceId cap(
            ProtoMeshBuilder builder, TubeEnd end,
            boolean outwardAlongFrameTangent, Set<String> semanticGroups) {
        List<VertexId> ring = end.ringVertices();
        List<VertexId> ordered = new ArrayList<>(ring);
        List<CornerAttributes> attributes = new ArrayList<>(ring.size());
        for (int index = 0; index < ring.size(); index++) attributes.add(capCorner(index, ring.size()));
        if (!outwardAlongFrameTangent) {
            java.util.Collections.reverse(ordered);
            java.util.Collections.reverse(attributes);
        }
        return builder.addFace(ordered, attributes, semanticGroups);
    }

    private static CornerAttributes capCorner(int index, int count) {
        double angle = index * StrictMath.PI * 2 / count;
        return new CornerAttributes(Optional.of(new Vector2(
                0.5 + StrictMath.cos(angle) * 0.5,
                0.5 + StrictMath.sin(angle) * 0.5)), Optional.empty(), Map.of());
    }
}
