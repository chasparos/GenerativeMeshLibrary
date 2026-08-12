package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.math.VectorMath;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoLoop;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.List;

/** Welds a coincident, non-adjacent vertex onto a retained vertex without remeshing faces. */
public final class VertexWeldOperation {
    private VertexWeldOperation() {
    }

    public static WeldResult weldCoincident(
            ProtoMeshBuilder builder, VertexId retained, VertexId retired, double tolerance) {
        if (retained.equals(retired)) throw new IllegalArgumentException("Weld vertices must be distinct");
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            throw new IllegalArgumentException("Weld tolerance must be finite and non-negative");
        }
        double distanceSquared = VectorMath.lengthSquared(VectorMath.subtract(
                builder.requireVertex(retained).position(), builder.requireVertex(retired).position()));
        if (distanceSquared > tolerance * tolerance) {
            throw new IllegalArgumentException("Vertices are outside the weld tolerance");
        }

        List<FaceReplacement> replacements = builder.snapshot().faces().values().stream()
                .filter(face -> face.loops().stream().map(builder::requireLoop)
                        .anyMatch(loop -> loop.vertexId().equals(retired)))
                .map(face -> replacement(builder, face, retained, retired))
                .toList();
        if (replacements.isEmpty()) throw new IllegalArgumentException("Retired vertex has no face uses");

        replacements.forEach(replacement -> builder.removeFace(replacement.source().id()));
        builder.removeVertex(retired);
        List<FaceId> replacementFaces = replacements.stream().map(replacement -> builder.addFace(
                replacement.vertices(), replacement.attributes(), replacement.source().semanticGroups())).toList();
        return new WeldResult(retained, retired,
                replacements.stream().map(replacement -> replacement.source().id()).toList(), replacementFaces);
    }

    private static FaceReplacement replacement(
            ProtoMeshBuilder builder, ProtoFace face, VertexId retained, VertexId retired) {
        List<ProtoLoop> loops = face.loops().stream().map(builder::requireLoop).toList();
        List<VertexId> vertices = loops.stream()
                .map(loop -> loop.vertexId().equals(retired) ? retained : loop.vertexId()).toList();
        if (vertices.stream().distinct().count() != vertices.size()) {
            throw new IllegalArgumentException("Weld would collapse or repeat a vertex in face " + face.id());
        }
        return new FaceReplacement(face, vertices, loops.stream().map(ProtoLoop::attributes).toList());
    }

    private record FaceReplacement(ProtoFace source, List<VertexId> vertices,
                                   List<com.planeguardian.assets.generation.topology.CornerAttributes> attributes) {
    }

    public record WeldResult(VertexId retainedVertex, VertexId retiredVertex,
                             List<FaceId> retiredFaces, List<FaceId> replacementFaces) {
        public WeldResult {
            retiredFaces = List.copyOf(retiredFaces);
            replacementFaces = List.copyOf(replacementFaces);
        }
    }
}
