package com.planeguardian.assets.generation.adapters.jme;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.EdgeId;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoEdge;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.ProtoVertex;
import com.planeguardian.assets.generation.topology.Vector2;
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

    private static final double DEGENERATE_EPSILON = 1.0e-12;

    private JmeMeshAdapter() {
    }

    /**
     * Builds a triangle mesh carrying the full authored render-vertex gamut:
     * position, normal, UV, tangent (with handedness in {@code w}), and
     * vertex color. Each triangle owns its own three vertices (no sharing
     * across faces) so per-corner seams (UV/normal splits) are exact
     * regardless of polygon shape, and each triangle can be traced back to
     * the {@link FaceId} that produced it via the returned {@link FaceTriangleMap}.
     *
     * <p>Where a corner has no authored {@link CornerAttributes#normal()},
     * the flat per-triangle face normal is used instead, so meshes that
     * never author normals keep their previous flat-shaded appearance.
     * Tangents are derived from UV derivatives when not authored, falling
     * back to an arbitrary basis for degenerate/unset UVs.</p>
     */
    public static TriangleMeshResult toTriangleMesh(TriangulatedMesh triangulated) {
        Objects.requireNonNull(triangulated, "triangulated");
        List<TriangleVertex> vertices = triangulated.vertices();
        int[] indices = triangulated.indices();
        int triangleCount = triangulated.triangleCount();

        FloatBuffer positions = BufferUtils.createFloatBuffer(triangleCount * 3 * 3);
        FloatBuffer normals = BufferUtils.createFloatBuffer(triangleCount * 3 * 3);
        FloatBuffer texCoords = BufferUtils.createFloatBuffer(triangleCount * 3 * 2);
        FloatBuffer tangents = BufferUtils.createFloatBuffer(triangleCount * 3 * 4);
        FloatBuffer colors = BufferUtils.createFloatBuffer(triangleCount * 3 * 4);
        boolean anyColorAuthored = vertices.stream().anyMatch(v -> v.attributes().color().isPresent());
        List<FaceId> faceIdPerTriangle = new ArrayList<>(triangleCount);

        for (int triangle = 0; triangle < triangleCount; triangle++) {
            TriangleVertex a = vertices.get(indices[triangle * 3]);
            TriangleVertex b = vertices.get(indices[triangle * 3 + 1]);
            TriangleVertex c = vertices.get(indices[triangle * 3 + 2]);
            Vector3f pa = toVector3f(a.position());
            Vector3f pb = toVector3f(b.position());
            Vector3f pc = toVector3f(c.position());
            Vector3f faceNormal = pb.subtract(pa).cross(pc.subtract(pa));
            if (faceNormal.lengthSquared() > 1.0e-20f) {
                faceNormal.normalizeLocal();
            }
            positions.put(pa.x).put(pa.y).put(pa.z);
            positions.put(pb.x).put(pb.y).put(pb.z);
            positions.put(pc.x).put(pc.y).put(pc.z);

            TriangleVertex[] corners = {a, b, c};
            Vector3f[] cornerNormals = new Vector3f[3];
            Vector2f[] cornerUvs = new Vector2f[3];
            for (int i = 0; i < 3; i++) {
                CornerAttributes attributes = corners[i].attributes();
                Vector3f normal = attributes.normal().map(JmeMeshAdapter::toVector3f).orElse(faceNormal);
                cornerNormals[i] = normal;
                normals.put(normal.x).put(normal.y).put(normal.z);
                Vector2f uv = attributes.textureCoordinate().map(JmeMeshAdapter::toVector2f).orElse(new Vector2f(0f, 0f));
                cornerUvs[i] = uv;
                texCoords.put(uv.x).put(uv.y);
                float[] rgba = attributes.color().map(v -> new float[] {(float) v.x(), (float) v.y(), (float) v.z(), 1f})
                        .orElse(new float[] {1f, 1f, 1f, 1f});
                colors.put(rgba[0]).put(rgba[1]).put(rgba[2]).put(rgba[3]);
            }

            Vector4f[] cornerTangents = computeTriangleTangents(pa, pb, pc, cornerUvs, cornerNormals, corners);
            for (Vector4f tangent : cornerTangents) {
                tangents.put(tangent.x).put(tangent.y).put(tangent.z).put(tangent.w);
            }

            faceIdPerTriangle.add(a.sourceFaceId());
        }

        int[] triangleIndices = new int[triangleCount * 3];
        for (int i = 0; i < triangleIndices.length; i++) triangleIndices[i] = i;

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texCoords);
        mesh.setBuffer(VertexBuffer.Type.Tangent, 4, tangents);
        if (anyColorAuthored) {
            mesh.setBuffer(VertexBuffer.Type.Color, 4, colors);
        }
        mesh.setBuffer(VertexBuffer.Type.Index, 3, triangleIndices);
        mesh.updateBound();
        mesh.updateCounts();
        return new TriangleMeshResult(mesh, List.copyOf(faceIdPerTriangle));
    }

    /**
     * Derives a tangent (xyz) plus handedness ({@code w}, +1/-1) for each of a
     * triangle's three corners. Prefers an authored {@link CornerAttributes#tangent()}
     * (re-orthogonalized against the corner normal via Gram-Schmidt), otherwise
     * derives the tangent from UV derivatives across the triangle, falling back
     * to an arbitrary basis perpendicular to the normal when the UVs are
     * degenerate (absent, identical, or collinear).
     */
    private static Vector4f[] computeTriangleTangents(
            Vector3f pa, Vector3f pb, Vector3f pc,
            Vector2f[] uv, Vector3f[] normal, TriangleVertex[] corners) {
        Vector3f edge1 = pb.subtract(pa);
        Vector3f edge2 = pc.subtract(pa);
        float deltaU1 = uv[1].x - uv[0].x;
        float deltaV1 = uv[1].y - uv[0].y;
        float deltaU2 = uv[2].x - uv[0].x;
        float deltaV2 = uv[2].y - uv[0].y;
        double determinant = deltaU1 * (double) deltaV2 - deltaU2 * (double) deltaV1;

        Vector3f uvTangent;
        Vector3f uvBitangent;
        if (StrictMath.abs(determinant) > DEGENERATE_EPSILON) {
            float inverseDet = (float) (1.0 / determinant);
            uvTangent = edge1.mult(deltaV2).subtractLocal(edge2.mult(deltaV1)).multLocal(inverseDet);
            uvBitangent = edge2.mult(deltaU1).subtractLocal(edge1.mult(deltaU2)).multLocal(inverseDet);
        } else {
            uvTangent = null;
            uvBitangent = null;
        }

        Vector4f[] result = new Vector4f[3];
        for (int i = 0; i < 3; i++) {
            Vector3f n = normal[i];
            CornerAttributes attributes = corners[i].attributes();
            Vector3f rawTangent = attributes.tangent().map(JmeMeshAdapter::toVector3f)
                    .orElse(uvTangent != null ? uvTangent : arbitraryPerpendicular(n));
            Vector3f rawBitangent = attributes.bitangent().map(JmeMeshAdapter::toVector3f)
                    .orElse(uvBitangent != null ? uvBitangent : n.cross(rawTangent));

            // Gram-Schmidt orthogonalize the tangent against the (possibly smoothed) normal.
            Vector3f tangent = rawTangent.subtract(n.mult(n.dot(rawTangent)));
            if (tangent.lengthSquared() <= 1.0e-20f) {
                tangent = arbitraryPerpendicular(n);
            } else {
                tangent.normalizeLocal();
            }
            float handedness = n.cross(tangent).dot(rawBitangent) < 0f ? -1f : 1f;
            result[i] = new Vector4f(tangent.x, tangent.y, tangent.z, handedness);
        }
        return result;
    }

    /** Any unit vector perpendicular to {@code normal}; used when a real tangent basis cannot be derived. */
    private static Vector3f arbitraryPerpendicular(Vector3f normal) {
        Vector3f reference = StrictMath.abs(normal.y) < 0.99f ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        Vector3f perpendicular = reference.cross(normal);
        if (perpendicular.lengthSquared() <= 1.0e-20f) {
            perpendicular = Vector3f.UNIT_X.cross(normal);
        }
        return perpendicular.normalizeLocal();
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

    /**
     * Builds a {@code Mesh.Mode.Lines} mesh from an arbitrary set of polylines (e.g. sampled
     * {@link com.planeguardian.assets.generation.skeleton.GuideCurve} paths), for a viewer
     * overlay distinct from the generated mesh's own edges. Each polyline is emitted as a
     * disconnected strip of line segments.
     */
    public static Mesh toPolylineMesh(List<List<Vector3>> polylines) {
        Objects.requireNonNull(polylines, "polylines");
        int segmentCount = 0;
        for (List<Vector3> polyline : polylines) {
            segmentCount += Math.max(0, polyline.size() - 1);
        }
        FloatBuffer positions = BufferUtils.createFloatBuffer(segmentCount * 2 * 3);
        int[] lineIndices = new int[segmentCount * 2];
        int cursor = 0;
        for (List<Vector3> polyline : polylines) {
            for (int index = 0; index + 1 < polyline.size(); index++) {
                Vector3f a = toVector3f(polyline.get(index));
                Vector3f b = toVector3f(polyline.get(index + 1));
                positions.put(a.x).put(a.y).put(a.z);
                positions.put(b.x).put(b.y).put(b.z);
                lineIndices[cursor] = cursor;
                lineIndices[cursor + 1] = cursor + 1;
                cursor += 2;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Index, 2, lineIndices);
        mesh.updateBound();
        mesh.updateCounts();
        return mesh;
    }

    /**
     * Builds a {@code Mesh.Mode.Lines} normals-overlay mesh: one short segment per
     * triangle corner, from the vertex position along its (authored or flat-face)
     * normal, scaled to {@code length}. Useful to visually verify normal direction
     * and winding.
     */
    public static Mesh toNormalOverlayMesh(TriangulatedMesh triangulated, float length) {
        Objects.requireNonNull(triangulated, "triangulated");
        List<TriangleVertex> vertices = triangulated.vertices();
        int[] indices = triangulated.indices();
        int triangleCount = triangulated.triangleCount();

        FloatBuffer positions = BufferUtils.createFloatBuffer(triangleCount * 3 * 2 * 3);
        int[] lineIndices = new int[triangleCount * 3 * 2];
        int vertexCursor = 0;
        int indexCursor = 0;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            TriangleVertex a = vertices.get(indices[triangle * 3]);
            TriangleVertex b = vertices.get(indices[triangle * 3 + 1]);
            TriangleVertex c = vertices.get(indices[triangle * 3 + 2]);
            Vector3f pa = toVector3f(a.position());
            Vector3f pb = toVector3f(b.position());
            Vector3f pc = toVector3f(c.position());
            Vector3f faceNormal = pb.subtract(pa).cross(pc.subtract(pa));
            if (faceNormal.lengthSquared() > 1.0e-20f) {
                faceNormal.normalizeLocal();
            }
            TriangleVertex[] corners = {a, b, c};
            Vector3f[] cornerPositions = {pa, pb, pc};
            for (int i = 0; i < 3; i++) {
                Vector3f normal = corners[i].attributes().normal().map(JmeMeshAdapter::toVector3f).orElse(faceNormal);
                Vector3f origin = cornerPositions[i];
                Vector3f tip = origin.add(normal.mult(length));
                positions.put(origin.x).put(origin.y).put(origin.z);
                positions.put(tip.x).put(tip.y).put(tip.z);
                lineIndices[indexCursor] = vertexCursor;
                lineIndices[indexCursor + 1] = vertexCursor + 1;
                vertexCursor += 2;
                indexCursor += 2;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Index, 2, lineIndices);
        mesh.updateBound();
        mesh.updateCounts();
        return mesh;
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

    public static Vector2f toVector2f(Vector2 value) {
        return new Vector2f((float) value.x(), (float) value.y());
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
