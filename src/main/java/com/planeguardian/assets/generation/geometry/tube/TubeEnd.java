package com.planeguardian.assets.generation.geometry.tube;

import com.planeguardian.assets.generation.curves.CurveFrame;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.List;
import java.util.Objects;

/** Stable open ring and local frame consumed by caps, collars, or junctions. */
public record TubeEnd(List<VertexId> ringVertices, CurveFrame frame, double nominalRadius) {
    public TubeEnd {
        ringVertices = List.copyOf(ringVertices);
        Objects.requireNonNull(frame, "frame");
        if (!Double.isFinite(nominalRadius) || nominalRadius <= 0) {
            throw new IllegalArgumentException("End radius must be finite and positive");
        }
    }
}
