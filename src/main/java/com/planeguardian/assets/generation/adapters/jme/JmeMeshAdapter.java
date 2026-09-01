package com.planeguardian.assets.generation.adapters.jme;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.EdgeId;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoEdge;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.ProtoVertex;
import com.planeguardian.assets.generation.topology.VertexId;
import com.planeguardian.assets.generation.triangulation.TriangleVertex;
import com.planeguardian.assets.generation.triangulation.TriangulatedMesh;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts this library's engine-agnostic mesh representation into jME3
 * {@link Mesh} instances for the evaluation viewer. Both this library's
 * {@link Vector3} coordinates and jME3's world space are Y-up, right-handed,
 * metre-scaled, so no axis conversion is required.
 */
public final class JmeMeshAdapter {

    private JmeMeshAdapter() {
    }

    /**
     * Builds a flat-shaded triangle mesh. Each triangle owns its own three
     * vertices (no sharing across faces) so per-face flat normals are exact
     * regardless of polygon shape, and each triangle can be traced back to
     * the {@link FaceId} that produced it via the returned {@link FaceTriangleMap}.
     */
    public static TriangleMeshResult toTriangleMesh(TriangulatedMesh triangulated) {
        Objects.requireNonNull(triangulated, "triangulated");
        List<TriangleVertex> vertices = triangulated.vertices();
        int[] indices = triangulated.indices();
        int triangleCount = triangulated.triangleCount();

        FloatBuffer positions = BufferUtils.createFloatBuffer(triangleCount * 3 * 3);
        FloatBuffer normals = BufferUtils.createFloatBuffer(triangleCount * 3 * 3);
        List<FaceId> faceIdPerTriangle = new ArrayList<>(triangleCount);

        for (int triangle = 0; triangle < triangleCount; triangle++) {
            TriangleVertex a = vertices.get(indices[triangle * 3]);
            TriangleVertex b = vertices.get(indices[triangle * 3 + 1]);
            TriangleVertex c = vertices.get(indices[triangle * 3 + 2]);
            Vector3f pa = toVector3f(a.position());
            Vector3f pb = toVector3f(b.position());
            Vector3f pc = toVector3f(c.position());
            Vector3f normal = pb.subtract(pa).cross(pc.subtract(pa));
            if (normal.lengthSquared() > 1.0e-20f) {
                normal.normalizeLocal();
            }
            positions.put(pa.x).put(pa.y).put(pa.z);
            positions.put(pb.x).put(pb.y).put(pb.z);
            positions.put(pc.x).put(pc.y).put(pc.z);
            for (int corner = 0; corner < 3; corner++) {
                normals.put(normal.x).put(normal.y).put(normal.z);
            }
            faceIdPerTriangle.add(a.sourceFaceId());
        }

        int[] triangleIndices = new int[triangleCount * 3];
        for (int i = 0; i < triangleIndices.length; i++) triangleIndices[i] = i;

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, triangleIndices);
        mesh.updateBound();
        mesh.updateCounts();
        return new TriangleMeshResult(mesh, List.copyOf(faceIdPerTriangle));
    }

    /** Builds a {@code Mesh.Mode.Lines} mesh from every edge in the snapshot, for the "edge mesh" overlay. */
    public static EdgeMeshResult toEdgeMesh(ProtoMeshSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<ProtoEdge> edges = List.copyOf(snapshot.edges().values());
        FloatBuffer positions = BufferUtils.createFloatBuffer(edges.size() * 2 * 3);
        int[] lineIndices = new int[edges.size() * 2];
        List<EdgeId> edgeIdPerLine = new ArrayList<>(edges.size());
        int cursor = 0;
        for (ProtoEdge edge : edges) {
            ProtoVertex a = snapshot.vertices().get(edge.vertexA());
            ProtoVertex b = snapshot.vertices().get(edge.vertexB());
            Vector3f pa = toVector3f(a.position());
            Vector3f pb = toVector3f(b.position());
            positions.put(pa.x).put(pa.y).put(pa.z);
            positions.put(pb.x).put(pb.y).put(pb.z);
            lineIndices[cursor] = cursor;
            lineIndices[cursor + 1] = cursor + 1;
            cursor += 2;
            edgeIdPerLine.add(edge.id());
        }
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Index, 2, lineIndices);
        mesh.updateBound();
        mesh.updateCounts();
        return new EdgeMeshResult(mesh, List.copyOf(edgeIdPerLine));
    }

    /** Two endpoint positions for a single edge, used to build a highlight/selection overlay. */
    public static Vector3f[] edgeEndpoints(ProtoMeshSnapshot snapshot, EdgeId edgeId) {
        ProtoEdge edge = snapshot.edges().get(edgeId);
        if (edge == null) throw new IllegalArgumentException("Unknown edge: " + edgeId);
        VertexId vertexA = edge.vertexA();
        VertexId vertexB = edge.vertexB();
        return new Vector3f[] {
                toVector3f(snapshot.vertices().get(vertexA).position()),
                toVector3f(snapshot.vertices().get(vertexB).position())
        };
    }

    public static Vector3f toVector3f(Vector3 value) {
        return new Vector3f((float) value.x(), (float) value.y(), (float) value.z());
    }

    /** Triangle mesh plus, per generated triangle index, the source {@link FaceId} it was triangulated from. */
    public record TriangleMeshResult(Mesh mesh, List<FaceId> faceIdPerTriangle) {
        public FaceId faceOf(int triangleIndex) {
            return faceIdPerTriangle.get(triangleIndex);
        }
    }

    /** Line mesh plus, per generated line segment index, the source {@link EdgeId} it was drawn from. */
    public record EdgeMeshResult(Mesh mesh, List<EdgeId> edgeIdPerLine) {
        public EdgeId edgeOf(int lineIndex) {
            return edgeIdPerLine.get(lineIndex);
        }
    }
}
