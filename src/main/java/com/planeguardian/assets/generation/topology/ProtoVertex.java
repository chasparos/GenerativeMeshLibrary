package com.planeguardian.assets.generation.topology;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.Objects;

public record ProtoVertex(VertexId id, Vector3 position) {
    public ProtoVertex {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
    }
}
