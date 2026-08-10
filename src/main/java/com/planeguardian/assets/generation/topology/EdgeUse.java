package com.planeguardian.assets.generation.topology;

import java.util.Objects;

/** Directed use of an undirected edge by one face loop. */
public record EdgeUse(LoopId loopId, FaceId faceId, VertexId from, VertexId to) {
    public EdgeUse {
        Objects.requireNonNull(loopId, "loopId");
        Objects.requireNonNull(faceId, "faceId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
