package com.planeguardian.assets.generation.geometry.eval;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.curves.CurveFrame;
import com.planeguardian.assets.generation.geometry.operations.RingBridgeOperation;
import com.planeguardian.assets.generation.geometry.operations.RingCapOperation;
import com.planeguardian.assets.generation.geometry.tube.TubeEnd;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.Vector2;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Basic {@link SUTGeometryInterface} implementation: a straight, capped tube
 * built entirely from this library's shared topology toolkit (ring bridging
 * for the side wall, ring capping for the ends). Useful as a minimal, always
 * valid mesh to exercise the geometry tools and the evaluation viewer.
 */
public final class TubeGeometry implements SUTGeometryInterface {
    private final TubeGeometryParameters parameters;

    public TubeGeometry() {
        this(TubeGeometryParameters.DEFAULT);
    }

    public TubeGeometry(TubeGeometryParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    @Override
    public String id() {
        return "tube";
    }

    @Override
    public String displayName() {
        return "Tube";
    }

    public TubeGeometryParameters parameters() {
        return parameters;
    }

    @Override
    public ProtoMeshSnapshot generate() {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        int radialSegments = parameters.radialSegments();
        int lengthSegments = parameters.lengthSegments();
        double radius = parameters.radius();
        double halfLength = parameters.length() / 2.0;

        List<List<VertexId>> rings = new ArrayList<>(lengthSegments + 1);
        List<List<CornerAttributes>> ringAttributes = new ArrayList<>(lengthSegments + 1);
        for (int ring = 0; ring <= lengthSegments; ring++) {
            double y = -halfLength + (parameters.length() * ring / lengthSegments);
            rings.add(buildRing(builder, y, radius, radialSegments));
            double vCoordinate = (double) ring / lengthSegments;
            ringAttributes.add(buildRingAttributes(radialSegments, vCoordinate));
        }

        Set<String> wallGroup = Set.of("tube-wall");
        for (int ring = 0; ring < lengthSegments; ring++) {
            RingBridgeOperation.bridge(builder, rings.get(ring), rings.get(ring + 1),
                    ringAttributes.get(ring), ringAttributes.get(ring + 1), wallGroup);
        }

        if (parameters.capped()) {
            TubeEnd startEnd = new TubeEnd(rings.get(0), straightFrame(-halfLength, 0.0, 0.0), radius);
            TubeEnd endEnd = new TubeEnd(rings.get(lengthSegments), straightFrame(halfLength, 1.0, 1.0), radius);
            RingCapOperation.cap(builder, startEnd, false, Set.of("tube-start-cap"));
            RingCapOperation.cap(builder, endEnd, true, Set.of("tube-end-cap"));
        }

        return builder.snapshot();
    }

    private static List<VertexId> buildRing(ProtoMeshBuilder builder, double y, double radius, int radialSegments) {
        List<VertexId> ring = new ArrayList<>(radialSegments);
        for (int index = 0; index < radialSegments; index++) {
            double angle = (2.0 * Math.PI * index) / radialSegments;
            double x = radius * StrictMath.cos(angle);
            double z = radius * StrictMath.sin(angle);
            ring.add(builder.addVertex(new Vector3(x, y, z)));
        }
        return ring;
    }

    /**
     * Smooth per-vertex wall attributes for one ring: a radial normal (so the
     * cylindrical wall shades smoothly instead of as flat facets), a
     * circumferential tangent, a longitudinal bitangent, and a UV where u
     * wraps once around the tube and v runs along its length.
     */
    private static List<CornerAttributes> buildRingAttributes(int radialSegments, double vCoordinate) {
        List<CornerAttributes> attributes = new ArrayList<>(radialSegments);
        for (int index = 0; index < radialSegments; index++) {
            double angle = (2.0 * Math.PI * index) / radialSegments;
            Vector3 normal = new Vector3(StrictMath.cos(angle), 0, StrictMath.sin(angle));
            Vector3 tangent = new Vector3(-StrictMath.sin(angle), 0, StrictMath.cos(angle));
            Vector3 bitangent = new Vector3(0, 1, 0);
            Vector2 uv = new Vector2((double) index / radialSegments, vCoordinate);
            attributes.add(new CornerAttributes(
                    Optional.of(uv), Optional.of(normal), Optional.of(tangent), Optional.of(bitangent),
                    Optional.empty(), java.util.Map.of()));
        }
        return attributes;
    }

    /** A straight, un-twisted frame along +Y at the given height; used only to satisfy {@link TubeEnd}. */
    private static CurveFrame straightFrame(double y, double arcFraction, double parameter) {
        return new CurveFrame(
                arcFraction,
                parameter,
                new Vector3(0, y, 0),
                new Vector3(0, 1, 0),
                new Vector3(1, 0, 0),
                new Vector3(0, 0, -1),
                0.0);
    }
}
