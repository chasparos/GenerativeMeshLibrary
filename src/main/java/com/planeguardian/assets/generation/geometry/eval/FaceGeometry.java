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
        TopologicalSkeleton skeleton = HumanFaceSkeleton.build();
        List<List<Vector3>> polylines = new ArrayList<>();
        for (GuideCurve curve : skeleton.curves()) {
            Pole start = skeleton.pole(curve.startPoleId());
            Pole end = skeleton.pole(curve.endPoleId());
            List<Vector3> samples = GuideCurveSampler.sample(curve, start.position(), end.position(), null);
            polylines.add(samples);
            if (skeleton.isMirrored()) {
                List<Vector3> mirrored = new ArrayList<>(samples.size());
                for (Vector3 point : samples) {
                    mirrored.add(skeleton.symmetryPlane().reflect(point));
                }
                polylines.add(mirrored);
            }
        }
        return List.copyOf(polylines);
    }
}
