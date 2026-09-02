package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A half-face {@link TopologicalSkeleton} demonstrating the Pole/GuideCurve authoring
 * language on a recognisable, anatomically-inspired template: a symmetric human face,
 * built as one half (mirrored across {@code x=0}) with two open-boundary "ring" holes
 * (the eye socket and mouth opening) and a handful of interior/on-axis poles routing
 * the surrounding forehead, nose, cheek, and jaw regions.
 *
 * <h2>Design notes and simplifications</h2>
 * <p>This template follows the spirit of a production edge-flow blueprint (loops around
 * the eye/mouth, poles acting as the "reversing gears" between them) but simplifies a
 * few points to fit within this library's current quadrangulation capabilities (4-sided
 * transfinite-interpolation patches, and single-center-pole fans for other polygon
 * counts):</p>
 * <ul>
 *   <li>The Orbital Ring (eyelid margin) and Oral Ring (lip margin) are each modelled as
 *       a literal open-boundary "bigon" — two {@link GuideCurve}s sharing the same pair
 *       of endpoint poles — and marked via {@link TopologicalSkeleton#holeCurveIds()} so
 *       {@link TopologyGenerator} leaves them unfilled (a genuine hole), matching the
 *       blueprint's description of these as closed loops with no interior poles.</li>
 *   <li>The full blueprint's Nasal Ring, Nasolabial pole, Jaw pole, and Zygomatic Arch
 *       are collapsed into a leaner off-axis pole set (Inner Eye-Nose, Cheek, Mouth
 *       Corner) connected by direct curves. Every larger region the richer graph traced
 *       out turned out irregular and non-planar/non-convex (up to 12-sided), which the
 *       single-center-pole fan fill cannot reliably handle (it requires a genuinely
 *       convex, roughly planar boundary). This leaner graph traces out only small, local
 *       triangles and pentagons around the two holes and the seam, which fan-fill
 *       correctly.</li>
 *   <li>Every non-hole, non-seam region the curve network traces out (the temple, the
 *       nose/mouth mask, the jaw, the back-of-head/temple closure, and the small brow and
 *       under-eye hollows) is filled with a single-center-pole fan anchored on a small
 *       "phantom" {@link Pole} of matching valence placed inside its boundary and left
 *       unreferenced by any curve (see {@link TopologyGenerator#fillPoleFanPatch}). Every
 *       {@link GuideCurve} is authored at {@code densitySegmentCount == 2}, which
 *       satisfies the fan fill's density requirement everywhere at once and keeps every
 *       patch boundary sum trivially even.</li>
 * </ul>
 */
public final class HumanFaceSkeleton {

    private HumanFaceSkeleton() {
    }

    /** Builds the half-face skeleton described in the class Javadoc. */
    public static TopologicalSkeleton build() {
        Map<String, Pole> poles = new LinkedHashMap<>();

        // Poles are placed roughly on the surface of a head-shaped ellipsoid (centred at
        // (0, 0.55, 0), radii 0.24 lateral / 0.55 vertical / 0.55 front-to-back -- twice as wide
        // as the earlier, much-too-thin 0.12 lateral radius that made the mirrored mesh read as a
        // narrow blade instead of a face) so that the vector from the skeleton's centroid to each
        // pole approximates its true local outward surface normal, which is what
        // TopologicalSkeleton#tracePatches() relies on to sort each pole's curves into a correct
        // rotation order.

        // On-axis (symmetry-plane) poles, ordered top-to-bottom down the centreline.
        addPole(poles, "crown", 0, 1.0725, -0.055, 4, true);
        addPole(poles, "glabella", 0, 0.7425, 0.517, 6, true);
        addPole(poles, "philtrum", 0, 0.5225, 0.5445, 2, true);
        addPole(poles, "lipCenter", 0, 0.4675, 0.5445, 6, true);
        addPole(poles, "mentalCleft", 0, 0.2475, 0.4565, 2, true);
        addPole(poles, "neckBase", 0, 0.0825, -0.11, 4, true);

        // Off-axis interior poles.
        addPole(poles, "innerEyeNose", 0.036, 0.715, 0.495, 4, false);
        addPole(poles, "cheek", 0.132, 0.6875, 0.4125, 6, false);
        addPole(poles, "mouthCorner", 0.108, 0.44, 0.4675, 4, false);

        // Phantom fan-center poles: unreferenced by any curve, existing only to anchor
        // TopologyGenerator's single-center-pole fan fill of the small triangular/pentagonal
        // patches traced above, at each patch's approximate centre and matching side count.
        addPole(poles, "templeFan", 0.066, 0.841, 0.295, 3, false);
        addPole(poles, "noseMouthFan", 0.052, 0.597, 0.511, 5, false);
        addPole(poles, "jawFan", 0.066, 0.280, 0.410, 5, false);
        addPole(poles, "backJawFan", 0.066, 0.615, 0.081, 3, false);
        addPole(poles, "browFan", 0.0252, 0.7301, 0.4980, 3, false);
        addPole(poles, "maskFan", 0.1008, 0.5184, 0.4634, 3, false);

        List<GuideCurve> curves = List.of(
                // Centreline seam curves, closing the centreline into a loop from crown to neck.
                curve("forehead", "crown", "glabella"),
                curve("noseDorsum", "glabella", "philtrum"),
                curve("upperLipSeam", "philtrum", "lipCenter"),
                curve("lowerLipSeam", "lipCenter", "mentalCleft"),
                curve("throat", "mentalCleft", "neckBase"),
                curve("backOfHead", "neckBase", "crown"),

                // Forehead / eye region. The eye ring's two curves are given distinct bulges
                // (control points off the straight line) so they are genuinely distinguishable
                // during rotation-order sorting instead of exactly coincident directions, which
                // is required for tracePatches() to isolate them as their own small open-boundary
                // loop rather than merging into a neighbouring patch.
                curve("browRidge", "glabella", "cheek"),
                curve("noseBridge", "glabella", "innerEyeNose"),
                curveVia("eyeUpperLoop", "innerEyeNose", "cheek", 0.2, 0.8, 0.55),
                curveVia("eyeUnderLoop", "innerEyeNose", "cheek", 0.084, 0.68125, 0.43375),

                // Nose / cheek mask: directly links the eye-nose and mouth-corner poles so the
                // mask region between the two hole rings stays a small, local triangle instead
                // of wrapping around the whole lower face.
                curve("maskLink", "innerEyeNose", "mouthCorner"),
                curve("cheekToMouth", "cheek", "mouthCorner"),

                // Outer (temple/jaw) silhouette: closes the cheek pole back onto the centreline
                // chain directly, avoiding a separate jaw pole and the large irregular region
                // that produced when the outer boundary looped all the way around the head.
                curve("cheekToCrown", "cheek", "crown"),
                curve("cheekToNeck", "cheek", "neckBase"),

                // Mouth opening: same bulge trick as the eye ring, above/below the straight line.
                curveVia("upperLip", "lipCenter", "mouthCorner", 0.054, 0.48375, 0.506),
                curveVia("lowerLip", "mouthCorner", "lipCenter", 0.054, 0.42375, 0.506));

        Set<String> holeCurveIds = Set.of("eyeUpperLoop", "eyeUnderLoop", "upperLip", "lowerLip");
        Plane symmetryPlane = new Plane(Vector3.ZERO, new Vector3(1, 0, 0));

        return new TopologicalSkeleton(poles, curves, true, symmetryPlane, holeCurveIds);
    }

    private static void addPole(
            Map<String, Pole> poles, String id, double x, double y, double z, int requestedValence, boolean onSymmetryPlane) {
        poles.put(id, new Pole(id, new Vector3(x, y, z), requestedValence, onSymmetryPlane));
    }

    private static GuideCurve curve(String id, String startPoleId, String endPoleId) {
        return new GuideCurve(id, startPoleId, endPoleId, List.of(), 2);
    }

    private static GuideCurve curveVia(String id, String startPoleId, String endPoleId, double x, double y, double z) {
        return new GuideCurve(id, startPoleId, endPoleId, List.of(new Vector3(x, y, z)), 2);
    }
}

