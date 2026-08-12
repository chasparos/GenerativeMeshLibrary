package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Deterministic polygon fill for an ordered boundary ring. */
public final class RingFillOperation {
    private RingFillOperation() {
    }

    public static FaceId fill(
            ProtoMeshBuilder builder, List<VertexId> ring,
            List<CornerAttributes> attributes, boolean reverse,
            Set<String> semanticGroups) {
        List<VertexId> orderedRing = new ArrayList<>(ring);
        List<CornerAttributes> orderedAttributes = new ArrayList<>(attributes);
        if (orderedRing.size() < 3 || orderedRing.size() != orderedAttributes.size()) {
            throw new IllegalArgumentException("Fill requires one corner attribute per ring vertex");
        }
        if (reverse) {
            Collections.reverse(orderedRing);
            Collections.reverse(orderedAttributes);
        }
        return builder.addFace(orderedRing, orderedAttributes, semanticGroups);
    }

    public static FaceId fill(
            ProtoMeshBuilder builder, List<VertexId> ring,
            boolean reverse, Set<String> semanticGroups) {
        return fill(builder, ring, Collections.nCopies(ring.size(), CornerAttributes.EMPTY), reverse, semanticGroups);
    }
}
