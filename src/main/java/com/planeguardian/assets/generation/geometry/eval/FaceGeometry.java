package com.planeguardian.assets.generation.geometry.eval;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.skeleton.GuideCurve;
import com.planeguardian.assets.generation.skeleton.GuideCurveSampler;
import com.planeguardian.assets.generation.skeleton.HumanFaceSkeleton;
import com.planeguardian.assets.generation.skeleton.Pole;
import com.planeguardian.assets.generation.skeleton.TopologicalSkeleton;
import com.planeguardian.assets.generation.skeleton.TopologyGenerator;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SUTGeometryInterface} implementation that generates the {@link HumanFaceSkeleton}
 * half-face template through the {@link TopologyGenerator} pipeline. In addition to the
 * generated mesh, exposes the authored {@link GuideCurve} network as sampled polylines
 * (mirrored to match the generated full-face mesh) so a viewer can render them as a
 * distinguishable overlay on top of the mesh they were used to build.
 */
public final class FaceGeometry implements SUTGeometryInterface {

    /**
     * Segment count used to resample every authored curve purely for the overlay display,
     * far higher than any curve's mesh-generation {@code densitySegmentCount} so the overlay
     * reads as a smooth spline and the generated topology underneath it is easier to make out.
     */
    private static final int OVERLAY_DISPLAY_SEGMENT_COUNT = 64;

    /**
     * A single authored {@link GuideCurve}, sampled into a world-space polyline for display,
     * paired with the curve's id so a viewer can label it (see {@code FaceGeometry#authoredNamedCurves()}).
     * {@code id} carries a {@code " (mirror)"} suffix for the reflected copy of a mirrored curve.
     */
    public record NamedCurve(String id, List<Vector3> polyline) {
    }

    @Override
    public String id() {
        return "face";
    }

    @Override
    public String displayName() {
        return "Human Face";
    }

    @Override
    public ProtoMeshSnapshot generate() {
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();
        return new TopologyGenerator().generate(skeleton).mesh();
    }

    /**
     * Samples every authored {@link GuideCurve} in the face skeleton into a polyline of
     * world-space points, mirroring each one across the symmetry plane when the skeleton is
     * mirrored so the overlay lines up with the full (mirrored + welded) generated mesh.
     *
     * @return one polyline per authored curve (mirrored curves appended after the originals).
     */
    public List<List<Vector3>> authoredCurvePolylines() {
        List<List<Vector3>> polylines = new ArrayList<>();
        for (NamedCurve namedCurve : authoredNamedCurves()) {
            polylines.add(namedCurve.polyline());
        }
        return List.copyOf(polylines);
    }

    /**
     * Same sampling as {@link #authoredCurvePolylines()}, but paired with each curve's authored
     * id so a viewer (for example a "face inspector" mode) can render a text label alongside
     * every curve instead of an anonymous line.
     *
     * @return one {@link NamedCurve} per authored curve (mirrored curves appended after the
     *     originals, with a {@code " (mirror)"}-suffixed id).
     */
    public List<NamedCurve> authoredNamedCurves() {
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();
        List<NamedCurve> namedCurves = new ArrayList<>();
        for (GuideCurve curve : skeleton.curves()) {
            Pole start = skeleton.pole(curve.startPoleId());
            Pole end = skeleton.pole(curve.endPoleId());
            List<Vector3> samples =
                    GuideCurveSampler.sampleForDisplay(curve, start.position(), end.position(), null, OVERLAY_DISPLAY_SEGMENT_COUNT);
            namedCurves.add(new NamedCurve(curve.id(), samples));
            if (skeleton.isMirrored()) {
                List<Vector3> mirrored = new ArrayList<>(samples.size());
                for (Vector3 point : samples) {
                    mirrored.add(skeleton.symmetryPlane().reflect(point));
                }
                namedCurves.add(new NamedCurve(curve.id() + " (mirror)", mirrored));
            }
        }
        return List.copyOf(namedCurves);
    }
}
