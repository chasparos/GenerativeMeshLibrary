package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.EdgeId;
import com.planeguardian.assets.generation.topology.EdgeUse;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.LoopId;
import com.planeguardian.assets.generation.topology.ProtoEdge;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoLoop;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.ProtoVertex;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One level of (generalised, arbitrary-n-gon) Catmull-Clark subdivision, applied in place to a
 * {@link ProtoMeshBuilder}. This is the "control cage" half of the plan described on
 * {@code TopologyGenerator}'s class Javadoc: rather than asking a single flat Coons patch or a
 * single-pole fan to carry all of a region's curvature by itself, every quad is refined into four
 * smaller quads and every vertex (including fan-fill phantom poles) is relaxed toward its smooth
 * limit-surface position. Two properties this relies on and preserves:
 *
 * <ul>
 *   <li><b>Vertex identity and valence are preserved.</b> Every original vertex keeps its
 *       {@link VertexId} (repositioned via {@link ProtoMeshBuilder#moveVertex}, not replaced), and
 *       ends up with exactly as many incident edges as it started with (one per original incident
 *       edge, now split at its midpoint) — so a caller tracking a specific pole's identity or
 *       valence (for example {@code TopologyGenerator}'s post-mirror valence verification) keeps
 *       working unmodified after subdivision.</li>
 *   <li><b>Boundary/crease edges stay boundary/crease edges.</b> Any edge used by exactly one face
 *       (an authored hole boundary, or a symmetry-seam edge left unfilled on this half) is treated
 *       as a 1-D crease: its edge point is a plain midpoint (not blended with a face point), and a
 *       vertex with exactly two such incident edges is smoothed only along that crease (the
 *       classic boundary rule {@code (6*P + m1 + m2) / 8}), never pulled sideways into the interior
 *       mesh. Since every authored on-axis pole's seam-adjacent neighbours are themselves on-axis,
 *       this keeps every crease vertex exactly on the symmetry plane, so subdivided half-meshes
 *       still weld correctly once mirrored.</li>
 * </ul>
 *
 * <p>A vertex with an anomalous crease-edge count (zero handled by the general interior rule;
 * one or three-or-more is an unexpected open/branching corner) is left at its original position
 * rather than guessing — this is the standard "corner" fallback and avoids ever displacing a
 * vertex whose neighbourhood doesn't match either clean case.</p>
 */
public final class CatmullClarkSubdivisionOperation {
    private CatmullClarkSubdivisionOperation() {
    }

    /** Applies {@code levels} successive subdivision passes (a no-op for {@code levels == 0}). */
    public static void subdivide(ProtoMeshBuilder builder, int levels) {
        if (levels < 0) throw new IllegalArgumentException("levels must be non-negative, got " + levels);
        for (int level = 0; level < levels; level++) {
            subdivide(builder);
        }
    }

    /** Applies exactly one subdivision pass in place. */
    public static void subdivide(ProtoMeshBuilder builder) {
        ProtoMeshSnapshot source = builder.snapshot();

        Map<FaceId, Vector3> facePointPosition = computeFacePoints(source);
        Map<EdgeId, Vector3> edgeMidpoint = new TreeMap<>();
        Map<EdgeId, Vector3> edgePointPosition = computeEdgePoints(source, facePointPosition, edgeMidpoint);
        Map<VertexId, List<EdgeId>> incidentEdges = incidentEdgesByVertex(source);
        Map<VertexId, Set<FaceId>> incidentFaces = incidentFacesByVertex(source);
        Map<VertexId, Vector3> newVertexPosition =
                computeVertexPoints(source, facePointPosition, edgeMidpoint, incidentEdges, incidentFaces);

        Map<FaceId, VertexId> facePointVertex = new TreeMap<>();
        facePointPosition.forEach((faceId, position) -> facePointVertex.put(faceId, builder.addVertex(position)));
        Map<EdgeId, VertexId> edgePointVertex = new TreeMap<>();
        edgePointPosition.forEach((edgeId, position) -> edgePointVertex.put(edgeId, builder.addVertex(position)));

        for (ProtoFace face : source.faces().values()) {
            addRefinedFaces(builder, source, face, facePointVertex, edgePointVertex);
        }
        for (FaceId faceId : source.faces().keySet()) {
            builder.removeFace(faceId);
        }
        newVertexPosition.forEach(builder::moveVertex);
    }

    private static Map<FaceId, Vector3> computeFacePoints(ProtoMeshSnapshot source) {
        Map<FaceId, Vector3> facePoints = new TreeMap<>();
        for (ProtoFace face : source.faces().values()) {
            List<Vector3> corners = face.loops().stream()
                    .map(source.loops()::get)
                    .map(loop -> source.vertices().get(loop.vertexId()).position())
                    .toList();
            facePoints.put(face.id(), average(corners));
        }
        return facePoints;
    }

    private static Map<EdgeId, Vector3> computeEdgePoints(
            ProtoMeshSnapshot source, Map<FaceId, Vector3> facePointPosition, Map<EdgeId, Vector3> edgeMidpointOut) {
        Map<EdgeId, Vector3> edgePoints = new TreeMap<>();
        for (ProtoEdge edge : source.edges().values()) {
            Vector3 a = source.vertices().get(edge.vertexA()).position();
            Vector3 b = source.vertices().get(edge.vertexB()).position();
            Vector3 midpoint = VectorMath.scale(VectorMath.add(a, b), 0.5);
            edgeMidpointOut.put(edge.id(), midpoint);
            if (edge.isBoundary()) {
                edgePoints.put(edge.id(), midpoint);
            } else {
                List<Vector3> adjacentFacePoints = edge.uses().stream()
                        .map(EdgeUse::faceId)
                        .distinct()
                        .map(facePointPosition::get)
                        .toList();
                Vector3 facePointSum = Vector3.ZERO;
                for (Vector3 facePoint : adjacentFacePoints) facePointSum = VectorMath.add(facePointSum, facePoint);
                Vector3 sum = VectorMath.add(VectorMath.add(a, b), facePointSum);
                edgePoints.put(edge.id(), VectorMath.scale(sum, 0.25));
            }
        }
        return edgePoints;
    }

    private static Map<VertexId, List<EdgeId>> incidentEdgesByVertex(ProtoMeshSnapshot source) {
        Map<VertexId, List<EdgeId>> incident = new TreeMap<>();
        for (ProtoEdge edge : source.edges().values()) {
            incident.computeIfAbsent(edge.vertexA(), ignored -> new ArrayList<>()).add(edge.id());
            incident.computeIfAbsent(edge.vertexB(), ignored -> new ArrayList<>()).add(edge.id());
        }
        return incident;
    }

    private static Map<VertexId, Set<FaceId>> incidentFacesByVertex(ProtoMeshSnapshot source) {
        Map<VertexId, Set<FaceId>> incident = new TreeMap<>();
        for (ProtoFace face : source.faces().values()) {
            for (LoopId loopId : face.loops()) {
                VertexId vertexId = source.loops().get(loopId).vertexId();
                incident.computeIfAbsent(vertexId, ignored -> new TreeSet<>()).add(face.id());
            }
        }
        return incident;
    }

    private static Map<VertexId, Vector3> computeVertexPoints(
            ProtoMeshSnapshot source,
            Map<FaceId, Vector3> facePointPosition,
            Map<EdgeId, Vector3> edgeMidpoint,
            Map<VertexId, List<EdgeId>> incidentEdges,
            Map<VertexId, Set<FaceId>> incidentFaces) {
        Map<VertexId, Vector3> result = new TreeMap<>();
        for (ProtoVertex vertex : source.vertices().values()) {
            List<EdgeId> edges = incidentEdges.getOrDefault(vertex.id(), List.of());
            List<EdgeId> boundaryEdges = edges.stream().filter(id -> source.edges().get(id).isBoundary()).toList();
            Vector3 position = vertex.position();
            if (edges.isEmpty()) {
                result.put(vertex.id(), position); // isolated vertex: nothing to smooth against
            } else if (boundaryEdges.size() == 2) {
                Vector3 m1 = edgeMidpoint.get(boundaryEdges.get(0));
                Vector3 m2 = edgeMidpoint.get(boundaryEdges.get(1));
                Vector3 sum = VectorMath.add(VectorMath.scale(position, 6), VectorMath.add(m1, m2));
                result.put(vertex.id(), VectorMath.scale(sum, 1.0 / 8.0));
            } else if (!boundaryEdges.isEmpty()) {
                result.put(vertex.id(), position); // anomalous open/branching corner: leave fixed
            } else {
                int n = edges.size();
                Set<FaceId> faces = incidentFaces.getOrDefault(vertex.id(), Set.of());
                Vector3 faceAverage = average(faces.stream().map(facePointPosition::get).toList());
                Vector3 edgeAverage = average(edges.stream().map(edgeMidpoint::get).toList());
                Vector3 weighted = VectorMath.add(
                        VectorMath.add(faceAverage, VectorMath.scale(edgeAverage, 2)),
                        VectorMath.scale(position, n - 3));
                result.put(vertex.id(), VectorMath.scale(weighted, 1.0 / n));
            }
        }
        return result;
    }

    /** Splits one original n-gon face into n new quads: {@code [corner, nextEdgePoint, facePoint, prevEdgePoint]}. */
    private static void addRefinedFaces(
            ProtoMeshBuilder builder,
            ProtoMeshSnapshot source,
            ProtoFace face,
            Map<FaceId, VertexId> facePointVertex,
            Map<EdgeId, VertexId> edgePointVertex) {
        List<ProtoLoop> loops = face.loops().stream().map(source.loops()::get).toList();
        int n = loops.size();
        VertexId facePoint = facePointVertex.get(face.id());
        for (int index = 0; index < n; index++) {
            int previous = (index - 1 + n) % n;
            VertexId corner = loops.get(index).vertexId();
            VertexId nextEdgePoint = edgePointVertex.get(loops.get(index).edgeId());
            VertexId previousEdgePoint = edgePointVertex.get(loops.get(previous).edgeId());
            builder.addFace(
                    List.of(corner, nextEdgePoint, facePoint, previousEdgePoint),
                    java.util.Collections.nCopies(4, CornerAttributes.EMPTY),
                    face.semanticGroups());
        }
    }

    private static Vector3 average(List<Vector3> values) {
        if (values.isEmpty()) return Vector3.ZERO;
        Vector3 sum = Vector3.ZERO;
        for (Vector3 value : values) sum = VectorMath.add(sum, value);
        return VectorMath.scale(sum, 1.0 / values.size());
    }
}
