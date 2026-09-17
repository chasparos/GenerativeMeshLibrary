package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A half-face {@link TopologicalSkeleton} demonstrating the Pole/GuideCurve authoring
 * language on a recognisable, anatomically-inspired template: a symmetric human face,
 * built as one half (mirrored across {@code x=0}) with three open-boundary holes (an
 * eye socket, a single mouth opening, and the unmodelled back of the head) and a
 * handful of interior/on-axis poles routing the forehead, nose, cheek, and jaw regions
 * between them.
 *
 * <h2>Design notes and simplifications</h2>
 * <p>This template follows the spirit of a production edge-flow blueprint (loops around
 * the eye/mouth, poles acting as the "reversing gears" between them) but simplifies a
 * few points to fit within this library's current quadrangulation capabilities (4-sided
 * transfinite-interpolation patches, and single-center-pole fans for other polygon
 * counts):</p>
 * <ul>
 *   <li>Poles are placed on the surface of a head-shaped ellipsoid (centred at
 *       {@code (0, 0.55, 0)}, radii {@code 0.42} lateral / {@code 0.62} vertical /
 *       {@code 0.45} front-to-back) so the vector from the skeleton's centroid to each
 *       pole approximates its true local outward surface normal, which is what
 *       {@link TopologicalSkeleton#tracePatches()} relies on to sort each pole's curves
 *       into a correct rotation order. The lateral radius is deliberately close to
 *       three quarters of the vertical one (rather than under half, as in earlier
 *       iterations of this template) to match a human face's actual width-to-height
 *       proportions instead of reading as a narrow, elongated wedge.</li>
 *   <li>The eye and mouth openings are each modelled as a literal open-boundary "hole"
 *       marked via {@link TopologicalSkeleton#holeCurveIds()} so {@link TopologyGenerator}
 *       leaves them unfilled, matching a production blueprint's description of these as
 *       closed loops with no interior poles. The mouth hole is authored with two distinct
 *       on-axis poles ({@code upperLipMid} and {@code lowerLipMid}, joined directly by a
 *       third hole curve, {@code mouthSeam}) rather than a single shared pole: sharing one
 *       pole between the whole seam chain and both the upper and lower lip curves would
 *       make the mouth trace as two separate hole patches that only touch at that single
 *       pole once mirrored — a pinched "figure eight" instead of one opening. With two
 *       distinct poles joined by their own (also unfilled) seam curve, the half-mouth
 *       traces as its own isolated 3-sided hole, and the mirrored result is a single
 *       4-sided diamond-shaped opening.</li>
 *   <li>The region directly behind the cheek — bounded by {@code cheekToCrown},
 *       {@code backOfHead}, and {@code cheekToNeck} — is <em>also</em> marked as a hole
 *       rather than filled. This template is a face, not a full head model: the curve
 *       graph still has to close into a topologically sphere-like cage for
 *       {@link TopologicalSkeleton#tracePatches()}'s rotation-system algorithm to work at
 *       all (see its class Javadoc), but nothing requires every patch that closure
 *       produces to actually be quadrangulated. Leaving this one open, exactly like the
 *       eye and mouth, means the back of the head is simply never generated, instead of
 *       being forced into a small, awkwardly fanned cap.</li>
 *   <li>The full blueprint's Nasal Ring, Nasolabial pole, Jaw pole, and Zygomatic Arch
 *       are collapsed into a leaner off-axis pole set ({@code innerEyeNose},
 *       {@code eyeOuterCorner}, {@code cheek}, {@code mouthCorner}) connected by direct
 *       curves. {@code eyeOuterCorner} marks the actual outer corner of the eye (rather than
 *       stretching the eye ring all the way out to the cheekbone), which both keeps the eye
 *       a believable width and turns the old temple/mask fan-triangles into genuine 4-sided
 *       Coons patches ({@code glabella}/{@code crown}/{@code cheek}/{@code eyeOuterCorner} and
 *       {@code innerEyeNose}/{@code eyeOuterCorner}/{@code cheek}/{@code mouthCorner}). Only
 *       the small brow triangle and the nose/mouth and jaw pentagons still need the
 *       single-center-pole fan fill (it requires a genuinely convex, roughly planar
 *       boundary).</li>
 *   <li>Each remaining filled fan region (brow, nose/mouth, jaw) is filled with a
 *       single-center-pole fan anchored on a small "phantom" {@link Pole} of matching
 *       valence placed inside its boundary and left unreferenced by any curve (see
 *       {@link TopologyGenerator#fillPoleFanPatch}). Every {@link GuideCurve} shares the
 *       same {@code densitySegmentCount} ({@value #DENSITY_SEGMENT_COUNT}, an even number
 *       so every patch boundary sum stays even too), fine enough that no single boundary
 *       segment spans more than a tenth of its curve's length.</li>
 * </ul>
 */
public final class HumanFaceSkeleton {

    private HumanFaceSkeleton() {
    }

    /** Builds the half-face skeleton described in the class Javadoc. */
    public static TopologicalSkeleton build() {
        Map<String, Pole> poles = new LinkedHashMap<>();

        // Poles sit on the surface of a head-shaped ellipsoid centred at (0, 0.55, 0) with
        // radii 0.42 lateral / 0.62 vertical / 0.45 front-to-back, so that the vector from the
        // skeleton's centroid to each pole approximates its true local outward surface normal
        // (required by TopologicalSkeleton#tracePatches() for correct curve rotation ordering)
        // while also matching a real face's width-to-height ratio closely enough to read as a
        // face rather than a narrow wedge.

        // On-axis (symmetry-plane) poles, ordered top-to-bottom down the centreline.
        addPole(poles, "crown", 0.0, 1.1632, 0.0666, 3, true);
        addPole(poles, "glabella", 0.0, 0.7403, 0.4283, 6, true);
        addPole(poles, "philtrum", 0.0, 0.5725, 0.4497, 2, true);
        addPole(poles, "upperLipMid", 0.0, 0.4946, 0.4482, 3, true);
        addPole(poles, "lowerLipMid", 0.0, 0.4222, 0.4403, 3, true);
        addPole(poles, "mentalCleft", 0.0, 0.2675, 0.4006, 2, true);
        addPole(poles, "neckBase", 0.0, 0.0190, 0.2323, 3, true);

        // Off-axis interior poles. innerEyeNose sits close to the bridge of the nose (not out
        // near the cheekbone) and eyeOuterCorner marks the true outer eye corner, so the eye
        // ring below spans only the width of an actual eye instead of the whole nose-to-cheek
        // temple region.
        addPole(poles, "innerEyeNose", 0.1350, 0.7154, 0.3780, 4, false);
        addPole(poles, "eyeOuterCorner", 0.3000, 0.7100, 0.3050, 4, false);
        addPole(poles, "cheek", 0.3590, 0.5838, 0.2323, 4, false);
        addPole(poles, "mouthCorner", 0.2637, 0.4685, 0.3452, 4, false);

        // Phantom fan-center poles: unreferenced by any curve, existing only to anchor
        // TopologyGenerator's single-center-pole fan fill of the small triangular/pentagonal
        // patches traced above, at each patch's approximate centre and matching side count.
        // Note there is no fan pole for the region behind the cheek (bounded by cheekToCrown,
        // backOfHead, cheekToNeck): that patch is deliberately left as a hole (see class Javadoc)
        // rather than filled, so it needs no phantom center. The temple (glabella/crown/cheek/
        // eyeOuterCorner) and mask (innerEyeNose/eyeOuterCorner/cheek/mouthCorner) regions are
        // now genuine 4-sided Coons patches (see eyeOuterCorner above), so they need no phantom
        // center either -- only the small brow triangle, and the nose/mouth and jaw pentagons,
        // still rely on a fan fill.
        addPole(poles, "browFan", 0.1862, 0.7754, 0.3538, 3, false);
        addPole(poles, "noseMouthFan", 0.0954, 0.5869, 0.4205, 5, false);
        addPole(poles, "jawFan", 0.1323, 0.3313, 0.3300, 5, false);

        List<GuideCurve> curves = List.of(
                // Centreline seam curves, closing the centreline into a loop from crown to neck.
                // Each is given a bulge control point pulled from the straight chord midpoint out
                // toward the head ellipsoid's surface, so the surface between distant poles follows
                // the rounded skull/jaw profile instead of a flat, faceted chord between them.
                curveVia("forehead", "crown", "glabella", 0.0, 0.9943, 0.2737),
                curveVia("noseDorsum", "glabella", "philtrum", 0.0, 0.6682, 0.4876),
                curveVia("upperLipSeam", "philtrum", "upperLipMid", 0.0, 0.5317, 0.4989),
                // mouthSeam is the direct seam curve between the mouth opening's upper- and
                // lower-midline poles; it is a hole curve (see below), not part of the filled
                // outer skin, so the opening is a single diamond rather than two pinched lobes.
                curveVia("mouthSeam", "upperLipMid", "lowerLipMid", 0.0, 0.4599, 0.4441),
                curveVia("lowerLipSeam", "lowerLipMid", "mentalCleft", 0.0, 0.3229, 0.4654),
                curveVia("throat", "mentalCleft", "neckBase", 0.0, 0.1038, 0.3472),
                curveVia("backOfHead", "neckBase", "crown", 0.0, 0.6043, 0.1977),

                // Eye ring, now bounded by eyeOuterCorner instead of reaching all the way out to
                // cheek: eyeUpperLoop bulges up (away from the brow triangle, toward the browRidge
                // side) and eyeUnderLoop bulges down, so the eye reads as a proper open lens sized
                // like an actual eye instead of spanning the whole nose-to-cheekbone width.
                curveVia("browRidge", "glabella", "eyeOuterCorner", 0.1800, 0.8000, 0.3550),
                curveVia("noseBridge", "glabella", "innerEyeNose", 0.0700, 0.7397, 0.4299),
                curveVia("eyeUpperLoop", "innerEyeNose", "eyeOuterCorner", 0.2200, 0.7500, 0.3500),
                curveVia("eyeUnderLoop", "innerEyeNose", "eyeOuterCorner", 0.2050, 0.6600, 0.3350),
                // Temple bridge: closes eyeOuterCorner back to cheek, turning the old temple and
                // mask fan-triangles into genuine 4-sided Coons patches (see class Javadoc).
                curveVia("templeBridge", "eyeOuterCorner", "cheek", 0.3350, 0.6550, 0.2700),

                // Nose / cheek mask: directly links the eye-nose and mouth-corner poles so the
                // mask region between the two hole rings stays a small, local quad instead of
                // wrapping around the whole lower face.
                curveVia("maskLink", "innerEyeNose", "mouthCorner", 0.2000, 0.5949, 0.3868),
                curveVia("cheekToMouth", "cheek", "mouthCorner", 0.3333, 0.5245, 0.3091),

                // Outer (temple/jaw) silhouette: closes the cheek pole back onto the centreline
                // chain directly. Both of these curves also bound the unfilled region directly
                // behind the cheek (see class Javadoc), so they are listed in holeCurveIds too.
                curveVia("cheekToCrown", "cheek", "crown", 0.1930, 0.8978, 0.1607),
                curveVia("cheekToNeck", "cheek", "neckBase", 0.1935, 0.2820, 0.2504),

                // Mouth opening: same bulge trick as the eye ring, above/below the straight line.
                // lowerLip's control point is pulled forward (toward larger z) so the lip bulges
                // outward like a real lower lip instead of curving back into the mouth cavity.
                curveVia("upperLip", "upperLipMid", "mouthCorner", 0.1381, 0.4783, 0.4154),
                curveVia("lowerLip", "mouthCorner", "lowerLipMid", 0.1500, 0.4300, 0.4100));

        Set<String> holeCurveIds = Set.of(
                "eyeUpperLoop", "eyeUnderLoop",
                "upperLip", "lowerLip", "mouthSeam",
                "cheekToCrown", "backOfHead", "cheekToNeck");
        Plane symmetryPlane = new Plane(Vector3.ZERO, new Vector3(1, 0, 0));

        return new TopologicalSkeleton(poles, curves, true, symmetryPlane, holeCurveIds);
    }

    private static void addPole(
            Map<String, Pole> poles, String id, double x, double y, double z, int requestedValence, boolean onSymmetryPlane) {
        poles.put(id, new Pole(id, new Vector3(x, y, z), requestedValence, onSymmetryPlane));
    }

    /**
     * Every curve's tessellation density, chosen so no boundary segment spans more than
     * roughly a tenth of its curve's length (deltaT &lt;= 0.1), giving the previewer and any
     * downstream quadrangulation enough samples to resolve real curvature instead of a coarse,
     * faceted approximation.
     */
    private static final int DENSITY_SEGMENT_COUNT = 10;

    private static GuideCurve curveVia(String id, String startPoleId, String endPoleId, double x, double y, double z) {
        return new GuideCurve(id, startPoleId, endPoleId, List.of(new Vector3(x, y, z)), DENSITY_SEGMENT_COUNT);
    }
}
