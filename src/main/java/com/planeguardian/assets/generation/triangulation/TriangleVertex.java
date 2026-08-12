package com.planeguardian.assets.generation.triangulation;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.LoopId;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.Objects;

/** Render vertex derived from one face corner, preserving authoring seams. */
public record TriangleVertex(
        VertexId sourceVertexId,
        LoopId sourceLoopId,
        FaceId sourceFaceId,
        Vector3 position,
        CornerAttributes attributes) {
    public TriangleVertex {
        Objects.requireNonNull(sourceVertexId, "sourceVertexId");
        Objects.requireNonNull(sourceLoopId, "sourceLoopId");
        Objects.requireNonNull(sourceFaceId, "sourceFaceId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(attributes, "attributes");
    }
}
