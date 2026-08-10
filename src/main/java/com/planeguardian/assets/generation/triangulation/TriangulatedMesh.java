package com.planeguardian.assets.generation.triangulation;

import java.util.List;

public final class TriangulatedMesh {
    private final List<TriangleVertex> vertices;
    private final int[] indices;

    public TriangulatedMesh(List<TriangleVertex> vertices, int[] indices) {
        this.vertices = List.copyOf(vertices);
        this.indices = indices.clone();
        if (indices.length % 3 != 0) throw new IllegalArgumentException("Triangle indices must be grouped by three");
        for (int index : indices) {
            if (index < 0 || index >= vertices.size()) throw new IllegalArgumentException("Triangle index is out of range");
        }
    }

    public List<TriangleVertex> vertices() { return vertices; }
    public int[] indices() { return indices.clone(); }
    public int triangleCount() { return indices.length / 3; }
}
