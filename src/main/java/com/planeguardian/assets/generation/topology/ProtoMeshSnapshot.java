package com.planeguardian.assets.generation.topology;

import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Immutable, ID-ordered topology snapshot safe to hand to generators and adapters. */
public final class ProtoMeshSnapshot {
    private final NavigableMap<VertexId, ProtoVertex> vertices;
    private final NavigableMap<EdgeId, ProtoEdge> edges;
    private final NavigableMap<LoopId, ProtoLoop> loops;
    private final NavigableMap<FaceId, ProtoFace> faces;
    private final List<TopologyIssue> issues;

    ProtoMeshSnapshot(
            NavigableMap<VertexId, ProtoVertex> vertices,
            NavigableMap<EdgeId, ProtoEdge> edges,
            NavigableMap<LoopId, ProtoLoop> loops,
            NavigableMap<FaceId, ProtoFace> faces,
            List<TopologyIssue> issues) {
        this.vertices = immutableCopy(vertices);
        this.edges = immutableCopy(edges);
        this.loops = immutableCopy(loops);
        this.faces = immutableCopy(faces);
        this.issues = List.copyOf(issues);
    }

    private static <K, V> NavigableMap<K, V> immutableCopy(NavigableMap<K, V> source) {
        return Collections.unmodifiableNavigableMap(new TreeMap<>(source));
    }

    public NavigableMap<VertexId, ProtoVertex> vertices() { return vertices; }
    public NavigableMap<EdgeId, ProtoEdge> edges() { return edges; }
    public NavigableMap<LoopId, ProtoLoop> loops() { return loops; }
    public NavigableMap<FaceId, ProtoFace> faces() { return faces; }
    public List<TopologyIssue> issues() { return issues; }
    public List<ProtoEdge> boundaryEdges() { return edges.values().stream().filter(ProtoEdge::isBoundary).toList(); }
    public boolean isValid() { return issues.stream().noneMatch(issue -> issue.severity() == TopologyIssue.Severity.ERROR); }
}
