package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.EdgeId;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoEdge;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoLoop;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.List;

/** Splits one manifold edge and rewrites each incident polygon without implicit remeshing. */
public final class EdgeSplitOperation {
    private EdgeSplitOperation() {
    }

    public static SplitResult split(ProtoMeshBuilder builder, EdgeId edgeId, double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction >= 1) {
            throw new IllegalArgumentException("Split fraction must be in (0, 1)");
        }
        ProtoEdge edge = builder.requireEdge(edgeId);
        Vector3 from = builder.requireVertex(edge.vertexA()).position();
        Vector3 to = builder.requireVertex(edge.vertexB()).position();
        VertexId splitVertex = builder.addVertex(VectorMath.add(
                VectorMath.scale(from, 1 - fraction), VectorMath.scale(to, fraction)));

        List<FaceReplacement> replacements = edge.uses().stream().map(use -> {
            ProtoFace face = builder.requireFace(use.faceId());
            List<ProtoLoop> loops = face.loops().stream().map(builder::requireLoop).toList();
            List<VertexId> vertices = new ArrayList<>();
            List<CornerAttributes> attributes = new ArrayList<>();
            for (int index = 0; index < loops.size(); index++) {
                ProtoLoop current = loops.get(index);
                ProtoLoop next = loops.get((index + 1) % loops.size());
                vertices.add(current.vertexId());
                attributes.add(current.attributes());
                if (current.vertexId().equals(use.from()) && next.vertexId().equals(use.to())) {
                    double directedFraction = use.from().equals(edge.vertexA()) ? fraction : 1 - fraction;
                    vertices.add(splitVertex);
                    attributes.add(CornerAttributeInterpolation.interpolate(
                            current.attributes(), next.attributes(), directedFraction));
                }
            }
            return new FaceReplacement(face, vertices, attributes);
        }).toList();

        replacements.forEach(replacement -> builder.removeFace(replacement.face().id()));
        List<FaceId> replacementFaces = replacements.stream().map(replacement -> builder.addFace(
                replacement.vertices(), replacement.attributes(), replacement.face().semanticGroups())).toList();
        return new SplitResult(edgeId, splitVertex,
                replacements.stream().map(replacement -> replacement.face().id()).toList(), replacementFaces);
    }

    private record FaceReplacement(ProtoFace face, List<VertexId> vertices, List<CornerAttributes> attributes) {
    }

    public record SplitResult(EdgeId retiredEdge, VertexId splitVertex,
                              List<FaceId> retiredFaces, List<FaceId> replacementFaces) {
        public SplitResult {
            retiredFaces = List.copyOf(retiredFaces);
            replacementFaces = List.copyOf(replacementFaces);
        }
    }
}
