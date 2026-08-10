package com.planeguardian.assets.generation.topology;

import java.util.Objects;

public record ProtoLoop(
        LoopId id, FaceId faceId, VertexId vertexId, EdgeId edgeId,
        CornerAttributes attributes) {
    public ProtoLoop {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(faceId, "faceId");
        Objects.requireNonNull(vertexId, "vertexId");
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(attributes, "attributes");
    }
}
