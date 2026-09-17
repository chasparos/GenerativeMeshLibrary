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
 *       {@code eyeOuterCorner}, {@code noseWing}, {@code cheek}, {@code mouthCorner})
 *       connected by direct curves. {@code eyeOuterCorner} marks the actual outer corner
 *       of the eye (rather than stretching the eye ring all the way out to the
 *       cheekbone), which keeps the eye a believable width and turns the old temple
 *       fan-triangle into a genuine 4-sided Coons patch
 *       ({@code glabella}/{@code crown}/{@code cheek}/{@code eyeOuterCorner}).
 *       {@code noseWing} (Swedish "nasvinge") marks the lateral flare of the nostril: it
 *       splits the old {@code glabella}-{@code innerEyeNose} nose-bridge curve into
 *       {@code noseBridge} (glabella to nose wing) and {@code eyeToNoseWing} (nose wing
 *       back to the inner eye corner), turning the old brow fan-triangle into a genuine
 *       4-sided Coons patch too ({@code glabella}/{@code eyeOuterCorner}/
 *       {@code innerEyeNose}/{@code noseWing}) with proper orbital edge flow around the
 *       eye. A new {@code noseSill} curve (nose wing to {@code philtrum}) gives the
 *       underside of the nose lateral definition and splits the old nose/mouth fan
 *       pentagon into a small {@code philtrum}/{@code glabella}/{@code noseWing}
 *       triangle plus a genuine 4-sided mouth-orbital patch
 *       ({@code upperLipMid}/{@code philtrum}/{@code noseWing}/{@code mouthCorner}).
 *       {@code maskLink} now runs from the nose wing (rather than directly from the
 *       inner eye corner) out to the mouth corner -- the outer pole of the mouth's
 *       orbital edge flow -- so the eye/nose/cheek "mask" region traces as a 5-sided
 *       patch ({@code noseWing}/{@code innerEyeNose}/{@code eyeOuterCorner}/
 *       {@code cheek}/{@code mouthCorner}) instead of a 4-sided one. Only that mask
 *       pentagon, the small nose-bridge triangle, and the jaw pentagon still need the
 *       single-center-pole fan fill (it requires a genuinely convex, roughly planar
 *       boundary).</li>
 *   <li>Each remaining filled fan region (nose bridge, mask, jaw) is filled with a
 *       single-center-pole fan anchored on a small "phantom" {@link Pole} of matching
 *       valence placed inside its boundary and left unreferenced by any curve (see
 *       {@link TopologyGenerator#fillPoleFanPatch}). Every {@link GuideCurve} shares the
 *       same {@code densitySegmentCount} ({@value #DENSITY_SEGMENT_COUNT}, an even number
 *       so every patch boundary sum stays even too), fine enough that no single boundary
 *       segment spans more than a tenth of its curve's length. The jaw/chin ({@code throat})
 *       and mouth-orbital ({@code upperLip}/{@code lowerLip}) curves each carry two control
 *       points rather than one, giving them enough shape control to round out the chin and
 *       lip contours instead of tapering to a sharp point -- only the first and last control
 *       points of a curve affect {@link TopologicalSkeleton#tracePatches()}'s rotation-order
 *       sort at each endpoint, so the extra interior points are purely cosmetic.</li>
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
        addPole(poles, "crown", 0.0, 1.1200, 0.1100, 3, true);
        addPole(poles, "glabella", 0.0, 0.7403, 0.4283, 6, true);
        addPole(poles, "philtrum", 0.0, 0.5725, 0.4497, 4, true);
        addPole(poles, "upperLipMid", 0.0, 0.4946, 0.4482, 3, true);
        addPole(poles, "lowerLipMid", 0.0, 0.4222, 0.4403, 3, true);
        addPole(poles, "mentalCleft", 0.0, 0.2675, 0.4006, 2, true);
        addPole(poles, "neckBase", 0.0, 0.0850, 0.2650, 3, true);

        // Off-axis interior poles. innerEyeNose sits close to the bridge of the nose (not out
        // near the cheekbone) and eyeOuterCorner marks the true outer eye corner, so the eye
        // ring below spans only the width of an actual eye instead of the whole nose-to-cheek
        // temple region. noseWing (Swedish "nasvinge") marks the lateral flare of the nostril,
        // giving the nose lateral definition: it splits the old glabella-innerEyeNose noseBridge
        // curve in two and reroutes maskLink so it runs from the nose wing to the mouth corner
        // (the outer pole of the mouth's orbital edge flow) instead of straight from the eye.
        addPole(poles, "innerEyeNose", 0.1350, 0.7154, 0.3780, 3, false);
        addPole(poles, "eyeOuterCorner", 0.3000, 0.7100, 0.3050, 4, false);
        addPole(poles, "noseWing", 0.1050, 0.6300, 0.4550, 4, false);
        addPole(poles, "cheek", 0.3590, 0.5838, 0.2323, 4, false);
        addPole(poles, "mouthCorner", 0.2637, 0.4685, 0.3452, 4, false);

        // Phantom fan-center poles: unreferenced by any curve, existing only to anchor
        // TopologyGenerator's single-center-pole fan fill of the small triangular/pentagonal
        // patches traced above, at each patch's approximate centre and matching side count.
        // Note there is no fan pole for the region behind the cheek (bounded by cheekToCrown,
        // backOfHead, cheekToNeck): that patch is deliberately left as a hole (see class Javadoc)
        // rather than filled, so it needs no phantom center. The temple (glabella/crown/cheek/
        // eyeOuterCorner) region is a genuine 4-sided Coons patch, and the brow triangle is now
        // also a genuine 4-sided Coons patch (glabella/eyeOuterCorner/innerEyeNose/noseWing), so
        // neither needs a phantom center. Only the eye/nose/cheek "mask" pentagon (now 5-sided
        // since noseWing sits between innerEyeNose and mouthCorner) and the jaw pentagon still
        // rely on a fan fill.
        addPole(poles, "maskFan", 0.2260, 0.6320, 0.3450, 5, false);
        addPole(poles, "noseBridgeFan", 0.0450, 0.6450, 0.4550, 3, false);
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
                // throat carries two control points (rather than one bulge) so the chin/jaw
                // contour can taper smoothly out to neckBase instead of reading as a single
                // straight, pointy wedge: the first point rounds the chin itself, the second
                // eases the profile back in toward the neck.
                curveVia("throat", "mentalCleft", "neckBase",
                        new Vector3(0.0, 0.1850, 0.4100), new Vector3(0.0, 0.0450, 0.3150)),
                curveVia("backOfHead", "neckBase", "crown", 0.0, 0.6043, 0.1977),

                // Eye ring, now bounded by eyeOuterCorner instead of reaching all the way out to
                // cheek: eyeUpperLoop bulges up (away from the brow quad, toward the browRidge
                // side) and eyeUnderLoop bulges down, so the eye reads as a proper open lens sized
                // like an actual eye instead of spanning the whole nose-to-cheekbone width.
                curveVia("browRidge", "glabella", "eyeOuterCorner", 0.1800, 0.8000, 0.3550),
                // noseBridge now only runs from glabella down to noseWing (the lateral flare of
                // the nostril, "nasvinge"); eyeToNoseWing closes the loop back to the inner eye
                // corner, so the old brow triangle becomes a genuine 4-sided Coons patch
                // (glabella/eyeOuterCorner/innerEyeNose/noseWing) with proper orbital edge flow.
                curveVia("noseBridge", "glabella", "noseWing", 0.0550, 0.6850, 0.4550),
                curveVia("eyeToNoseWing", "noseWing", "innerEyeNose", 0.1180, 0.6750, 0.4150),
                curveVia("eyeUpperLoop", "innerEyeNose", "eyeOuterCorner", 0.2200, 0.7500, 0.3500),
                curveVia("eyeUnderLoop", "innerEyeNose", "eyeOuterCorner", 0.2050, 0.6600, 0.3850),
                // Temple bridge: closes eyeOuterCorner back to cheek, turning the temple
                // fan-triangle into a genuine 4-sided Coons patch (see class Javadoc).
                curveVia("templeBridge", "eyeOuterCorner", "cheek", 0.3350, 0.6550, 0.2700),

                // Underside of the nose: links the nose wing across to the centre seam at the
                // philtrum, giving the nose lateral definition on its lower edge too (the
                // nostril-base/sill curve) and splitting the old nose/mouth fan pentagon into a
                // small nose-bridge triangle plus a genuine 4-sided mouth-orbital patch.
                curveVia("noseSill", "noseWing", "philtrum", 0.0550, 0.5950, 0.4700),

                // Nose / cheek mask: links the nose wing (the outer pole of the mouth's orbital
                // edge flow) to the mouth corner, so the mask region between the two hole rings
                // stays a small, local patch instead of wrapping around the whole lower face.
                curveVia("maskLink", "noseWing", "mouthCorner", 0.2000, 0.5949, 0.3868),
                curveVia("cheekToMouth", "cheek", "mouthCorner", 0.3333, 0.5245, 0.3091),

                // Outer (temple/jaw) silhouette: closes the cheek pole back onto the centreline
                // chain directly. Both of these curves also bound the unfilled region directly
                // behind the cheek (see class Javadoc), so they are listed in holeCurveIds too.
                curveVia("cheekToCrown", "cheek", "crown", 0.1930, 0.8978, 0.1607),
                curveVia("cheekToNeck", "cheek", "neckBase", 0.1935, 0.2820, 0.2504),

                // Mouth opening: same bulge trick as the eye ring, above/below the straight line.
                // Each lip curve now carries two control points instead of one, giving the mouth's
                // orbital (concentric) edge flow more shape control -- the upper lip's arc and the
                // lower lip's outward-rolling curl -- instead of a single coarse bulge.
                // upperLip's points ease from the philtrum corner into the corner of the mouth.
                curveVia("upperLip", "upperLipMid", "mouthCorner",
                        new Vector3(0.0700, 0.4870, 0.4300), new Vector3(0.2100, 0.4720, 0.3950)),
                // lowerLip's points are pulled forward (toward larger z) so the lip bulges outward
                // like a real lower lip instead of curving back into the mouth cavity.
                curveVia("lowerLip", "mouthCorner", "lowerLipMid",
                        new Vector3(0.2200, 0.4380, 0.3900), new Vector3(0.0750, 0.4220, 0.4250)));

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

    /**
     * Like {@link #curveVia(String, String, String, double, double, double)} but with two
     * control points, for curves that need finer shape control than a single bulge point can
     * express (for example the chin/jaw contour or the mouth's upper/lower orbital curvature).
     * Only the first and last control points affect {@link TopologicalSkeleton#tracePatches()}'s
     * rotation-order sort at each endpoint, so the interior points are purely cosmetic.
     */
    private static GuideCurve curveVia(
            String id, String startPoleId, String endPoleId, Vector3 controlPointA, Vector3 controlPointB) {
        return new GuideCurve(id, startPoleId, endPoleId, List.of(controlPointA, controlPointB), DENSITY_SEGMENT_COUNT);
    }
}
