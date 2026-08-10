package com.planeguardian.assets.generation.topology;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/** Mutable construction surface. Consumers receive immutable snapshots only. */
public final class ProtoMeshBuilder {
    private static final double AREA_EPSILON_SQUARED = 1.0e-18;
    private static final double PLANARITY_TOLERANCE_METRES = 1.0e-6;
    private final NavigableMap<VertexId, ProtoVertex> vertices = new TreeMap<>();
    private final NavigableMap<EdgeId, MutableEdge> edges = new TreeMap<>();
    private final NavigableMap<LoopId, ProtoLoop> loops = new TreeMap<>();
    private final NavigableMap<FaceId, ProtoFace> faces = new TreeMap<>();
    private final Map<EdgeKey, MutableEdge> edgesByEndpoints = new HashMap<>();
    private long nextVertexId;
    private long nextEdgeId;
    private long nextLoopId;
    private long nextFaceId;

    public ProtoMeshBuilder() {
    }

    /** Starts an additive edit while preserving every identity in the source snapshot. */
    public static ProtoMeshBuilder copyOf(ProtoMeshSnapshot source) {
        java.util.Objects.requireNonNull(source, "source");
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        builder.vertices.putAll(source.vertices());
        builder.loops.putAll(source.loops());
        builder.faces.putAll(source.faces());
        source.edges().values().forEach(edge -> {
            MutableEdge copy = new MutableEdge(edge.id(), edge.vertexA(), edge.vertexB());
            copy.uses.addAll(edge.uses());
            builder.edges.put(edge.id(), copy);
            builder.edgesByEndpoints.put(new EdgeKey(edge.vertexA(), edge.vertexB()), copy);
        });
        builder.nextVertexId = nextId(source.vertices().isEmpty() ? -1 : source.vertices().lastKey().value());
        builder.nextEdgeId = nextId(source.edges().isEmpty() ? -1 : source.edges().lastKey().value());
        builder.nextLoopId = nextId(source.loops().isEmpty() ? -1 : source.loops().lastKey().value());
        builder.nextFaceId = nextId(source.faces().isEmpty() ? -1 : source.faces().lastKey().value());
        return builder;
    }

    private static long nextId(long lastId) {
        if (lastId == Long.MAX_VALUE) throw new IllegalArgumentException("Topology ID space is exhausted");
        return lastId + 1;
    }

    public VertexId addVertex(Vector3 position) {
        VertexId id = new VertexId(nextVertexId++);
        vertices.put(id, new ProtoVertex(id, position));
        return id;
    }

    public ProtoVertex requireVertex(VertexId id) {
        ProtoVertex vertex = vertices.get(id);
        if (vertex == null) throw new IllegalArgumentException("Unknown vertex: " + id);
        return vertex;
    }

    public ProtoFace requireFace(FaceId id) {
        ProtoFace face = faces.get(id);
        if (face == null) throw new IllegalArgumentException("Unknown face: " + id);
        return face;
    }

    public ProtoLoop requireLoop(LoopId id) {
        ProtoLoop loop = loops.get(id);
        if (loop == null) throw new IllegalArgumentException("Unknown loop: " + id);
        return loop;
    }

    public ProtoEdge requireEdge(EdgeId id) {
        MutableEdge edge = edges.get(id);
        if (edge == null) throw new IllegalArgumentException("Unknown edge: " + id);
        return edge.freeze();
    }

    /** Removes an isolated vertex. Its identity remains retired. */
    public ProtoVertex removeVertex(VertexId id) {
        ProtoVertex vertex = requireVertex(id);
        boolean used = loops.values().stream().anyMatch(loop -> loop.vertexId().equals(id));
        if (used) throw new IllegalStateException("Cannot remove a vertex still used by a face: " + id);
        vertices.remove(id);
        return vertex;
    }

    /** Removes one face and its loops; newly orphaned edges are retired. */
    public ProtoFace removeFace(FaceId id) {
        ProtoFace face = requireFace(id);
        faces.remove(id);
        for (LoopId loopId : face.loops()) {
            ProtoLoop loop = loops.remove(loopId);
            MutableEdge edge = edges.get(loop.edgeId());
            edge.uses.removeIf(use -> use.loopId().equals(loopId));
            if (edge.uses.isEmpty()) {
                edges.remove(edge.id);
                edgesByEndpoints.remove(new EdgeKey(edge.a, edge.b));
            }
        }
        return face;
    }

    public FaceId addFace(List<VertexId> vertexIds) {
        return addFace(vertexIds, java.util.Collections.nCopies(vertexIds.size(), CornerAttributes.EMPTY), Set.of());
    }

    public FaceId addFace(
            List<VertexId> vertexIds,
            List<CornerAttributes> cornerAttributes,
            Set<String> semanticGroups) {
        List<VertexId> orderedVertices = List.copyOf(vertexIds);
        List<CornerAttributes> orderedAttributes = List.copyOf(cornerAttributes);
        if (orderedVertices.size() < 3) throw new IllegalArgumentException("A polygon needs at least three vertices");
        if (orderedVertices.size() != orderedAttributes.size()) throw new IllegalArgumentException("One attribute set is required per corner");
        if (new HashSet<>(orderedVertices).size() != orderedVertices.size()) throw new IllegalArgumentException("A face cannot repeat a vertex");
        orderedVertices.forEach(id -> {
            if (!vertices.containsKey(id)) throw new IllegalArgumentException("Unknown vertex: " + id);
        });

        FaceId faceId = new FaceId(nextFaceId++);
        List<LoopId> faceLoops = new ArrayList<>(orderedVertices.size());
        for (int index = 0; index < orderedVertices.size(); index++) {
            VertexId from = orderedVertices.get(index);
            VertexId to = orderedVertices.get((index + 1) % orderedVertices.size());
            EdgeKey key = new EdgeKey(from, to);
            MutableEdge edge = edgesByEndpoints.computeIfAbsent(key, ignored -> {
                MutableEdge created = new MutableEdge(new EdgeId(nextEdgeId++), key.a, key.b);
                edges.put(created.id, created);
                return created;
            });
            LoopId loopId = new LoopId(nextLoopId++);
            faceLoops.add(loopId);
            loops.put(loopId, new ProtoLoop(loopId, faceId, from, edge.id, orderedAttributes.get(index)));
            edge.uses.add(new EdgeUse(loopId, faceId, from, to));
        }
        faces.put(faceId, new ProtoFace(faceId, faceLoops, semanticGroups));
        return faceId;
    }

    public ProtoMeshSnapshot snapshot() {
        NavigableMap<EdgeId, ProtoEdge> frozenEdges = new TreeMap<>();
        edges.forEach((id, edge) -> frozenEdges.put(id, edge.freeze()));
        return new ProtoMeshSnapshot(vertices, frozenEdges, loops, faces, validate(frozenEdges));
    }

    private List<TopologyIssue> validate(NavigableMap<EdgeId, ProtoEdge> frozenEdges) {
        List<TopologyIssue> issues = new ArrayList<>();
        Set<VertexId> usedVertices = new HashSet<>();
        loops.values().forEach(loop -> usedVertices.add(loop.vertexId()));
        vertices.keySet().stream().filter(id -> !usedVertices.contains(id)).forEach(id ->
                issues.add(new TopologyIssue(TopologyIssue.Severity.WARNING, "isolated-vertex", "Vertex " + id.value() + " is unused")));
        frozenEdges.values().forEach(edge -> {
            Vector3 positionA = vertices.get(edge.vertexA()).position();
            Vector3 positionB = vertices.get(edge.vertexB()).position();
            double dx = positionA.x() - positionB.x();
            double dy = positionA.y() - positionB.y();
            double dz = positionA.z() - positionB.z();
            if ((dx * dx) + (dy * dy) + (dz * dz) <= AREA_EPSILON_SQUARED) {
                issues.add(new TopologyIssue(TopologyIssue.Severity.ERROR, "zero-length-edge", "Edge " + edge.id().value() + " has coincident endpoints"));
            }
            if (!edge.isManifold()) {
                issues.add(new TopologyIssue(TopologyIssue.Severity.ERROR, "non-manifold-edge", "Edge " + edge.id().value() + " has " + edge.uses().size() + " face uses"));
            } else if (edge.uses().size() == 2) {
                EdgeUse first = edge.uses().get(0);
                EdgeUse second = edge.uses().get(1);
                if (first.from().equals(second.from()) && first.to().equals(second.to())) {
                    issues.add(new TopologyIssue(TopologyIssue.Severity.ERROR, "inconsistent-winding", "Faces use edge " + edge.id().value() + " in the same direction"));
                }
            }
        });
        faces.values().forEach(face -> validateFaceGeometry(face, issues));
        return List.copyOf(issues);
    }

    private void validateFaceGeometry(ProtoFace face, List<TopologyIssue> issues) {
        List<Vector3> positions = face.loops().stream()
                .map(loops::get)
                .map(loop -> vertices.get(loop.vertexId()).position())
                .toList();
        Vector3 normal = newellNormal(positions);
        double normalLengthSquared = normal.x() * normal.x() + normal.y() * normal.y() + normal.z() * normal.z();
        if (hasSelfIntersection(positions, normal)) {
            issues.add(new TopologyIssue(TopologyIssue.Severity.ERROR, "self-intersecting-face", "Face " + face.id().value() + " crosses itself"));
        }
        if (normalLengthSquared <= AREA_EPSILON_SQUARED) {
            issues.add(new TopologyIssue(TopologyIssue.Severity.ERROR, "degenerate-face", "Face " + face.id().value() + " has no stable area"));
            return;
        }
        double inverseLength = 1.0 / StrictMath.sqrt(normalLengthSquared);
        Vector3 origin = positions.get(0);
        double maximumDistance = positions.stream().mapToDouble(position -> StrictMath.abs(
                (position.x() - origin.x()) * normal.x() * inverseLength
                        + (position.y() - origin.y()) * normal.y() * inverseLength
                        + (position.z() - origin.z()) * normal.z() * inverseLength)).max().orElse(0);
        if (maximumDistance > PLANARITY_TOLERANCE_METRES) {
            issues.add(new TopologyIssue(TopologyIssue.Severity.WARNING, "non-planar-face", "Face " + face.id().value() + " deviates " + maximumDistance + " metres from its plane"));
        }
    }

    private static Vector3 newellNormal(List<Vector3> positions) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (int index = 0; index < positions.size(); index++) {
            Vector3 current = positions.get(index);
            Vector3 next = positions.get((index + 1) % positions.size());
            x += (current.y() - next.y()) * (current.z() + next.z());
            y += (current.z() - next.z()) * (current.x() + next.x());
            z += (current.x() - next.x()) * (current.y() + next.y());
        }
        return new Vector3(x, y, z);
    }

    private static boolean hasSelfIntersection(List<Vector3> positions, Vector3 normal) {
        List<ProjectedPoint> points = project(positions, normal);
        int edgeCount = points.size();
        for (int first = 0; first < edgeCount; first++) {
            int firstNext = (first + 1) % edgeCount;
            for (int second = first + 1; second < edgeCount; second++) {
                int secondNext = (second + 1) % edgeCount;
                if (first == second || firstNext == second || secondNext == first) continue;
                if (segmentsIntersect(points.get(first), points.get(firstNext), points.get(second), points.get(secondNext))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<ProjectedPoint> project(List<Vector3> positions, Vector3 normal) {
        double absX = StrictMath.abs(normal.x());
        double absY = StrictMath.abs(normal.y());
        double absZ = StrictMath.abs(normal.z());
        ProjectionAxis dropped;
        if (absX + absY + absZ > 1.0e-15) {
            dropped = absX >= absY && absX >= absZ ? ProjectionAxis.X
                    : absY >= absZ ? ProjectionAxis.Y : ProjectionAxis.Z;
        } else {
            double minX = positions.stream().mapToDouble(Vector3::x).min().orElse(0);
            double maxX = positions.stream().mapToDouble(Vector3::x).max().orElse(0);
            double minY = positions.stream().mapToDouble(Vector3::y).min().orElse(0);
            double maxY = positions.stream().mapToDouble(Vector3::y).max().orElse(0);
            double minZ = positions.stream().mapToDouble(Vector3::z).min().orElse(0);
            double maxZ = positions.stream().mapToDouble(Vector3::z).max().orElse(0);
            double yz = (maxY - minY) * (maxZ - minZ);
            double xz = (maxX - minX) * (maxZ - minZ);
            double xy = (maxX - minX) * (maxY - minY);
            dropped = yz >= xz && yz >= xy ? ProjectionAxis.X
                    : xz >= xy ? ProjectionAxis.Y : ProjectionAxis.Z;
        }
        ProjectionAxis axis = dropped;
        return positions.stream().map(position -> switch (axis) {
            case X -> new ProjectedPoint(position.y(), position.z());
            case Y -> new ProjectedPoint(position.x(), position.z());
            case Z -> new ProjectedPoint(position.x(), position.y());
        }).toList();
    }

    private static boolean segmentsIntersect(ProjectedPoint a, ProjectedPoint b, ProjectedPoint c, ProjectedPoint d) {
        double abC = orientation(a, b, c);
        double abD = orientation(a, b, d);
        double cdA = orientation(c, d, a);
        double cdB = orientation(c, d, b);
        if (((abC > 0 && abD < 0) || (abC < 0 && abD > 0))
                && ((cdA > 0 && cdB < 0) || (cdA < 0 && cdB > 0))) return true;
        double epsilon = 1.0e-12;
        return StrictMath.abs(abC) <= epsilon && onSegment(a, b, c, epsilon)
                || StrictMath.abs(abD) <= epsilon && onSegment(a, b, d, epsilon)
                || StrictMath.abs(cdA) <= epsilon && onSegment(c, d, a, epsilon)
                || StrictMath.abs(cdB) <= epsilon && onSegment(c, d, b, epsilon);
    }

    private static double orientation(ProjectedPoint a, ProjectedPoint b, ProjectedPoint c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static boolean onSegment(ProjectedPoint a, ProjectedPoint b, ProjectedPoint point, double epsilon) {
        return point.x >= StrictMath.min(a.x, b.x) - epsilon
                && point.x <= StrictMath.max(a.x, b.x) + epsilon
                && point.y >= StrictMath.min(a.y, b.y) - epsilon
                && point.y <= StrictMath.max(a.y, b.y) + epsilon;
    }

    private enum ProjectionAxis { X, Y, Z }
    private record ProjectedPoint(double x, double y) { }

    private record EdgeKey(VertexId a, VertexId b) {
        EdgeKey {
            if (a.equals(b)) throw new IllegalArgumentException("An edge requires two vertices");
            if (a.compareTo(b) > 0) {
                VertexId swap = a;
                a = b;
                b = swap;
            }
        }
    }

    private static final class MutableEdge {
        private final EdgeId id;
        private final VertexId a;
        private final VertexId b;
        private final List<EdgeUse> uses = new ArrayList<>();

        private MutableEdge(EdgeId id, VertexId a, VertexId b) {
            this.id = id;
            this.a = a;
            this.b = b;
        }

        private ProtoEdge freeze() { return new ProtoEdge(id, a, b, uses); }
    }
}
