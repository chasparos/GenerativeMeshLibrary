package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resamples a {@link GuideCurve} into evenly (arc-length) spaced points.
 *
 * <p>The returned list always has {@code densitySegmentCount + 1} entries,
 * starting exactly at {@code startPosition} and ending exactly at
 * {@code endPosition}; any authored {@link GuideCurve#controlPoints()} only
 * influence the shape of the path between those fixed endpoints.</p>
 */
public final class GuideCurveSampler {

    private GuideCurveSampler() {
    }

    /**
     * Samples {@code curve} between the two supplied pole positions.
     *
     * @param projectionPlane if non-null, every sampled point (including the
     *                         supplied endpoints) is projected onto this plane.
     *                         This is used to force "seam curves" between two
     *                         symmetry-plane poles to lie exactly on the plane,
     *                         regardless of authored control-point precision.
     */
    public static List<Vector3> sample(GuideCurve curve, Vector3 startPosition, Vector3 endPosition, Plane projectionPlane) {
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(startPosition, "startPosition");
        Objects.requireNonNull(endPosition, "endPosition");

        List<Vector3> polyline = new ArrayList<>(curve.controlPoints().size() + 2);
        polyline.add(startPosition);
        polyline.addAll(curve.controlPoints());
        polyline.add(endPosition);

        List<Double> cumulativeLength = new ArrayList<>(polyline.size());
        cumulativeLength.add(0.0);
        for (int index = 1; index < polyline.size(); index++) {
            double segmentLength = VectorMath.distance(polyline.get(index - 1), polyline.get(index));
            cumulativeLength.add(cumulativeLength.get(index - 1) + segmentLength);
        }
        double totalLength = cumulativeLength.get(cumulativeLength.size() - 1);

        int segments = curve.densitySegmentCount();
        List<Vector3> samples = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double targetLength = totalLength * ((double) i / segments);
            Vector3 point = pointAtLength(polyline, cumulativeLength, targetLength, totalLength);
            samples.add(projectionPlane == null ? point : projectionPlane.project(point));
        }
        // Guard against floating-point drift: reproduce the exact authored endpoints.
        samples.set(0, projectionPlane == null ? startPosition : projectionPlane.project(startPosition));
        samples.set(segments, projectionPlane == null ? endPosition : projectionPlane.project(endPosition));
        return List.copyOf(samples);
    }

    private static Vector3 pointAtLength(List<Vector3> polyline, List<Double> cumulativeLength, double targetLength, double totalLength) {
        if (totalLength <= 0.0) {
            return polyline.get(0);
        }
        for (int index = 1; index < cumulativeLength.size(); index++) {
            if (targetLength <= cumulativeLength.get(index) || index == cumulativeLength.size() - 1) {
                double segmentStart = cumulativeLength.get(index - 1);
                double segmentEnd = cumulativeLength.get(index);
                double segmentLength = segmentEnd - segmentStart;
                double t = segmentLength <= 0.0 ? 0.0 : VectorMath.clamp((targetLength - segmentStart) / segmentLength, 0.0, 1.0);
                Vector3 from = polyline.get(index - 1);
                Vector3 to = polyline.get(index);
                return VectorMath.add(from, VectorMath.scale(VectorMath.subtract(to, from), t));
            }
        }
        return polyline.get(polyline.size() - 1);
    }
}
