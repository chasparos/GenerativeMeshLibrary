package com.planeguardian.assets.generation.topology;

import java.util.List;
import java.util.Objects;

public record ProtoEdge(EdgeId id, VertexId vertexA, VertexId vertexB, List<EdgeUse> uses) {
    public ProtoEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vertexA, "vertexA");
        Objects.requireNonNull(vertexB, "vertexB");
        uses = List.copyOf(uses);
        if (vertexA.compareTo(vertexB) >= 0) {
            throw new IllegalArgumentException("Edge endpoints must be distinct and canonically ordered");
        }
    }

    public boolean isBoundary() { return uses.size() == 1; }
    public boolean isManifold() { return uses.size() <= 2; }
}
