package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resamples a {@link GuideCurve} into evenly (arc-length) spaced points along a smooth
 * Catmull-Rom spline that interpolates the curve's endpoints and authored control points
 * (rather than the sharp-cornered straight polyline through them).
 *
 * <p>The returned list always has {@code densitySegmentCount + 1} entries,
 * starting exactly at {@code startPosition} and ending exactly at
 * {@code endPosition}; any authored {@link GuideCurve#controlPoints()} only
 * influence the shape of the path between those fixed endpoints.</p>
 */
public final class GuideCurveSampler {

    /**
     * Number of fine subdivisions evaluated along the smooth spline for every authored
     * control-polyline segment, used to build the arc-length lookup table that both
     * {@link #sample} and {@link #sampleForDisplay} resample from. High enough that the
     * arc-length parametrization and the spline's true curvature are visually indistinguishable.
     */
    private static final int SPLINE_SUBDIVISIONS_PER_SEGMENT = 24;

    private GuideCurveSampler() {
    }

    /**
     * Samples {@code curve} between the two supplied pole positions, at the curve's own
     * {@link GuideCurve#densitySegmentCount()} (the resolution actually used to build the
     * generated mesh).
     *
     * @param projectionPlane if non-null, every sampled point (including the
     *                         supplied endpoints) is projected onto this plane.
     *                         This is used to force "seam curves" between two
     *                         symmetry-plane poles to lie exactly on the plane,
     *                         regardless of authored control-point precision.
     */
    public static List<Vector3> sample(GuideCurve curve, Vector3 startPosition, Vector3 endPosition, Plane projectionPlane) {
        Objects.requireNonNull(curve, "curve");
        return sample(curve, startPosition, endPosition, projectionPlane, curve.densitySegmentCount());
    }

    /**
     * Samples {@code curve} at an arbitrary, caller-supplied segment count, independent of
     * {@link GuideCurve#densitySegmentCount()}. Intended for visualization overlays that want
     * a much smoother rendering of the authored spline than the (topology-constrained) mesh
     * resolution allows, without affecting mesh generation in any way.
     */
    public static List<Vector3> sampleForDisplay(
            GuideCurve curve, Vector3 startPosition, Vector3 endPosition, Plane projectionPlane, int displaySegmentCount) {
        Objects.requireNonNull(curve, "curve");
        if (displaySegmentCount < 1) {
            throw new IllegalArgumentException("displaySegmentCount must be at least 1, got " + displaySegmentCount);
        }
        return sample(curve, startPosition, endPosition, projectionPlane, displaySegmentCount);
    }

    private static List<Vector3> sample(
            GuideCurve curve, Vector3 startPosition, Vector3 endPosition, Plane projectionPlane, int segments) {
        Objects.requireNonNull(startPosition, "startPosition");
        Objects.requireNonNull(endPosition, "endPosition");

        List<Vector3> controlPolyline = new ArrayList<>(curve.controlPoints().size() + 2);
        controlPolyline.add(startPosition);
        controlPolyline.addAll(curve.controlPoints());
        controlPolyline.add(endPosition);

        List<Vector3> splinePoints = evaluateCatmullRomSpline(controlPolyline);

        List<Double> cumulativeLength = new ArrayList<>(splinePoints.size());
        cumulativeLength.add(0.0);
        for (int index = 1; index < splinePoints.size(); index++) {
            double segmentLength = VectorMath.distance(splinePoints.get(index - 1), splinePoints.get(index));
            cumulativeLength.add(cumulativeLength.get(index - 1) + segmentLength);
        }
        double totalLength = cumulativeLength.get(cumulativeLength.size() - 1);

        List<Vector3> samples = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double targetLength = totalLength * ((double) i / segments);
            Vector3 point = pointAtLength(splinePoints, cumulativeLength, targetLength, totalLength);
            samples.add(projectionPlane == null ? point : projectionPlane.project(point));
        }
        // Guard against floating-point drift: reproduce the exact authored endpoints.
        samples.set(0, projectionPlane == null ? startPosition : projectionPlane.project(startPosition));
        samples.set(segments, projectionPlane == null ? endPosition : projectionPlane.project(endPosition));
        return List.copyOf(samples);
    }

    /**
     * Evaluates a uniform Catmull-Rom spline through {@code controlPolyline} (which always
     * includes both curve endpoints), returning a dense polyline of fine points that hugs the
     * true smooth curve. If {@code controlPolyline} has only two points (no authored control
     * points), the spline degenerates exactly to the straight line between them.
     *
     * <p>Missing "virtual" points needed to evaluate the tangent at each end are synthesized by
     * reflecting the nearest interior point through the endpoint, the standard way to give a
     * Catmull-Rom chain sensible end tangents without clamping the curve to a straight run-in.</p>
     */
    private static List<Vector3> evaluateCatmullRomSpline(List<Vector3> controlPolyline) {
        int pointCount = controlPolyline.size();
        List<Vector3> finePoints = new ArrayList<>();
        finePoints.add(controlPolyline.get(0));
        for (int segment = 0; segment < pointCount - 1; segment++) {
            Vector3 p0 = controlPolyline.get(Math.max(0, segment - 1));
            Vector3 p1 = controlPolyline.get(segment);
            Vector3 p2 = controlPolyline.get(segment + 1);
            Vector3 p3 = controlPolyline.get(Math.min(pointCount - 1, segment + 2));
            if (segment == 0) {
                p0 = VectorMath.subtract(p1, VectorMath.subtract(p2, p1));
            }
            if (segment == pointCount - 2) {
                p3 = VectorMath.add(p2, VectorMath.subtract(p2, p1));
            }
            for (int step = 1; step <= SPLINE_SUBDIVISIONS_PER_SEGMENT; step++) {
                double t = (double) step / SPLINE_SUBDIVISIONS_PER_SEGMENT;
                finePoints.add(catmullRomPoint(p0, p1, p2, p3, t));
            }
        }
        return finePoints;
    }

    private static Vector3 catmullRomPoint(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        Vector3 term1 = VectorMath.scale(p1, 2.0);
        Vector3 term2 = VectorMath.scale(VectorMath.subtract(p2, p0), t);
        Vector3 term3 = VectorMath.scale(
                VectorMath.add(VectorMath.subtract(VectorMath.scale(p0, 2.0), VectorMath.scale(p1, 5.0)),
                        VectorMath.subtract(VectorMath.scale(p2, 4.0), p3)),
                t2);
        Vector3 term4 = VectorMath.scale(
                VectorMath.add(VectorMath.subtract(VectorMath.scale(p1, 3.0), VectorMath.scale(p2, 3.0)),
                        VectorMath.subtract(p3, p0)),
                t3);
        return VectorMath.scale(VectorMath.add(VectorMath.add(term1, term2), VectorMath.add(term3, term4)), 0.5);
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
