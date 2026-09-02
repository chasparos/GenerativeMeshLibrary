package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * A sparse, user-authored description of a quad-mesh topology: a graph of
 * explicit {@link Pole}s connected by {@link GuideCurve}s, optionally mirrored
 * across a {@link Plane} of symmetry.
 *
 * <h2>Valence invariants</h2>
 * <p>Every mesh vertex produced by {@link TopologyGenerator} that is not an
 * explicit {@link Pole} is guaranteed valence 4. For explicit poles, this
 * class validates that {@link Pole#requestedValence()} is achievable from the
 * authored curve graph alone, so that generation can satisfy it purely by
 * construction:</p>
 * <ul>
 *   <li><b>Interior poles</b> (not on the symmetry plane): {@code requestedValence}
 *       must equal the pole's graph degree (its number of incident curves) —
 *       each incident curve contributes exactly one mesh edge at the pole.</li>
 *   <li><b>Symmetry-plane poles</b>: {@code requestedValence} must equal
 *       {@code seamCurveCount + 2 * freeCurveCount} (see {@link BoundaryConstraint}
 *       for the derivation: seam curves — to another symmetry-plane pole — map
 *       to themselves under reflection and are not duplicated; free curves —
 *       to an interior pole — gain a genuinely distinct mirrored counterpart).</li>
 * </ul>
 *
 * <h2>Sub-patch tracing</h2>
 * <p>{@link #tracePatches()} traces the closed face loops of the curve graph
 * using a standard rotation-system (DCEL-style) algorithm: at each pole, the
 * outgoing curve directions are sorted by angle around a per-pole axis
 * (the direction from the skeleton's centroid to the pole, approximating its
 * local outward surface normal on a roughly star-convex cage), and each face
 * is traced by always turning to "the next edge after the twin" in that
 * rotation order. Every directed curve traversal belongs to
 * exactly one traced face, so every traced face is returned as a fillable
 * {@link SubPatch} — this generator therefore targets closed, cage-like
 * skeletons (for example one half of a creature body, wrapped fully around in
 * 3D) rather than flat, open 2D layouts that would also have one unbounded
 * "outer" face to discard.</p>
 */
public final class TopologicalSkeleton {

    private static final double SYMMETRY_PLANE_TOLERANCE_METRES = 1.0e-6;
    private static final Vector3 GLOBAL_UP = new Vector3(0, 1, 0);
    private static final Vector3 WORLD_X = new Vector3(1, 0, 0);

    private final Map<String, Pole> poles;
    private final List<GuideCurve> curves;
    private final Map<String, GuideCurve> curvesById;
    private final boolean isMirrored;
    private final Plane symmetryPlane;

    public TopologicalSkeleton(Map<String, Pole> poles, List<GuideCurve> curves, boolean isMirrored, Plane symmetryPlane) {
        Objects.requireNonNull(poles, "poles");
        Objects.requireNonNull(curves, "curves");
        this.poles = Map.copyOf(poles);
        this.curves = List.copyOf(curves);
        this.isMirrored = isMirrored;
        this.symmetryPlane = symmetryPlane;

        Map<String, GuideCurve> byId = new TreeMap<>();
        for (GuideCurve curve : this.curves) {
            if (byId.putIfAbsent(curve.id(), curve) != null) {
                throw new IllegalArgumentException("Duplicate GuideCurve id: " + curve.id());
            }
        }
        this.curvesById = Map.copyOf(byId);

        validate();
    }

    public Map<String, Pole> poles() {
        return poles;
    }

    public List<GuideCurve> curves() {
        return curves;
    }

    public boolean isMirrored() {
        return isMirrored;
    }

    public Plane symmetryPlane() {
        return symmetryPlane;
    }

    public Pole pole(String id) {
        Pole pole = poles.get(id);
        if (pole == null) throw new IllegalArgumentException("Unknown pole: " + id);
        return pole;
    }

    public GuideCurve curve(String id) {
        GuideCurve curve = curvesById.get(id);
        if (curve == null) throw new IllegalArgumentException("Unknown guide curve: " + id);
        return curve;
    }

    /** Returns a copy of this skeleton with one curve replaced (used to repair odd boundary parity). */
    public TopologicalSkeleton withCurve(GuideCurve replacement) {
        List<GuideCurve> updated = new ArrayList<>(curves.size());
        boolean replaced = false;
        for (GuideCurve existing : curves) {
            if (existing.id().equals(replacement.id())) {
                updated.add(replacement);
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) throw new IllegalArgumentException("Cannot replace unknown curve: " + replacement.id());
        return new TopologicalSkeleton(poles, updated, isMirrored, symmetryPlane);
    }

    /** The curves incident to {@code poleId}, in authored order. */
    public List<GuideCurve> incidentCurves(String poleId) {
        pole(poleId);
        List<GuideCurve> incident = new ArrayList<>();
        for (GuideCurve curve : curves) {
            if (curve.startPoleId().equals(poleId) || curve.endPoleId().equals(poleId)) {
                incident.add(curve);
            }
        }
        return incident;
    }

    /** Number of curve endpoints touching {@code poleId}; each incident curve contributes exactly one edge. */
    public int graphDegree(String poleId) {
        return incidentCurves(poleId).size();
    }

    /** Among a symmetry-plane pole's incident curves, how many also terminate at another symmetry-plane pole. */
    public int seamCurveCount(String poleId) {
        int count = 0;
        for (GuideCurve curve : incidentCurves(poleId)) {
            String otherId = otherEndpoint(curve, poleId);
            if (poles.get(otherId).isOnSymmetryPlane()) {
                count++;
            }
        }
        return count;
    }

    /** Among a symmetry-plane pole's incident curves, how many terminate at an interior (non-symmetry) pole. */
    public int freeCurveCount(String poleId) {
        return graphDegree(poleId) - seamCurveCount(poleId);
    }

    private static String otherEndpoint(GuideCurve curve, String poleId) {
        return curve.startPoleId().equals(poleId) ? curve.endPoleId() : curve.startPoleId();
    }

    /**
     * Validates referential integrity, symmetry-plane consistency, and the
     * pole valence formulas described in the class Javadoc.
     *
     * @throws TopologyParityException if a pole's {@link Pole#requestedValence()}
     *         cannot be achieved by the authored curve graph.
     * @throws IllegalArgumentException for structural/referential errors.
     */
    public void validate() {
        if (isMirrored && symmetryPlane == null) {
            throw new IllegalArgumentException("A mirrored skeleton requires a symmetry plane");
        }
        for (GuideCurve curve : curves) {
            if (!poles.containsKey(curve.startPoleId())) {
                throw new IllegalArgumentException("GuideCurve " + curve.id() + " references unknown start pole " + curve.startPoleId());
            }
            if (!poles.containsKey(curve.endPoleId())) {
                throw new IllegalArgumentException("GuideCurve " + curve.id() + " references unknown end pole " + curve.endPoleId());
            }
        }
        for (Pole pole : poles.values()) {
            if (pole.isOnSymmetryPlane()) {
                if (symmetryPlane == null) {
                    throw new IllegalArgumentException("Pole " + pole.id() + " is on the symmetry plane but none is defined");
                }
                if (!symmetryPlane.contains(pole.position(), SYMMETRY_PLANE_TOLERANCE_METRES)) {
                    throw new IllegalArgumentException("Pole " + pole.id() + " is marked on the symmetry plane but its position is off-plane");
                }
            }
        }
        for (Pole pole : poles.values()) {
            if (pole.isOnSymmetryPlane()) {
                int seam = seamCurveCount(pole.id());
                int free = freeCurveCount(pole.id());
                int impliedValence = seam + (2 * free);
                if (impliedValence != pole.requestedValence()) {
                    throw new TopologyParityException(
                            "Symmetry-plane pole " + pole.id() + " requests valence " + pole.requestedValence()
                                    + " but its curve graph implies " + impliedValence
                                    + " (seamCurves=" + seam + ", freeCurves=" + free + "); "
                                    + "requestedValence must equal seamCurveCount + 2*freeCurveCount");
                }
            } else {
                int degree = graphDegree(pole.id());
                if (degree != pole.requestedValence()) {
                    throw new TopologyParityException(
                            "Interior pole " + pole.id() + " requests valence " + pole.requestedValence()
                                    + " but has " + degree + " incident curves; requestedValence must equal graph degree"
                                    + " for interior poles");
                }
            }
        }
    }

    /**
     * Traces every closed face loop of the curve graph via a rotation-system
     * (DCEL-style) algorithm. See the class Javadoc for the algorithm and its
     * scope (closed cage skeletons; every traced face is a fillable patch).
     */
    public List<SubPatch> tracePatches() {
        Map<String, List<OutgoingEdge>> rotations = buildRotations();
        Set<DirectedEdge> visited = new LinkedHashSet<>();
        List<SubPatch> patches = new ArrayList<>();

        for (GuideCurve curve : curves) {
            for (DirectedEdge start : List.of(
                    new DirectedEdge(curve.id(), curve.startPoleId(), curve.endPoleId()),
                    new DirectedEdge(curve.id(), curve.endPoleId(), curve.startPoleId()))) {
                if (visited.contains(start)) continue;
                patches.add(tracePatch(start, rotations, visited));
            }
        }
        return List.copyOf(patches);
    }

    private SubPatch tracePatch(DirectedEdge start, Map<String, List<OutgoingEdge>> rotations, Set<DirectedEdge> visited) {
        List<SubPatch.Side> sides = new ArrayList<>();
        DirectedEdge current = start;
        do {
            visited.add(current);
            GuideCurve curve = curve(current.curveId());
            boolean reversed = !current.fromPoleId().equals(curve.startPoleId());
            sides.add(new SubPatch.Side(current.curveId(), current.fromPoleId(), current.toPoleId(), reversed, curve.densitySegmentCount()));

            List<OutgoingEdge> outgoingAtDestination = rotations.get(current.toPoleId());
            int twinIndex = indexOfCurve(outgoingAtDestination, current.curveId());
            OutgoingEdge next = outgoingAtDestination.get((twinIndex + 1) % outgoingAtDestination.size());
            current = new DirectedEdge(next.curveId(), current.toPoleId(), next.toPoleId());
        } while (!current.equals(start));
        return new SubPatch(sides);
    }

    private Vector3 centroid() {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Pole pole : poles.values()) {
            x += pole.position().x();
            y += pole.position().y();
            z += pole.position().z();
        }
        int count = Math.max(1, poles.size());
        return new Vector3(x / count, y / count, z / count);
    }

    /**
     * The axis used to sort a pole's outgoing curve directions into a local rotation order.
     * Skeletons are assumed to describe roughly star-convex, closed cage surfaces (limbs,
     * heads, branch junctions), so the vector from the skeleton centroid to the pole is a
     * good proxy for its local outward surface normal; falls back to the global +Y axis for
     * the rare degenerate case of a pole exactly at the centroid.
     */
    private Vector3 localRotationAxis(Pole pole, Vector3 centroid) {
        Vector3 outward = VectorMath.subtract(pole.position(), centroid);
        return VectorMath.lengthSquared(outward) <= 1.0e-18 ? GLOBAL_UP : VectorMath.normalize(outward);
    }

    private static int indexOfCurve(List<OutgoingEdge> outgoing, String curveId) {
        for (int index = 0; index < outgoing.size(); index++) {
            if (outgoing.get(index).curveId().equals(curveId)) return index;
        }
        throw new IllegalStateException("Twin curve " + curveId + " not found in its destination pole's rotation");
    }

    private Map<String, List<OutgoingEdge>> buildRotations() {
        Vector3 centroid = centroid();
        Map<String, List<OutgoingEdge>> rotations = new LinkedHashMap<>();
        for (Pole pole : poles.values()) {
            Vector3 axis = localRotationAxis(pole, centroid);
            Vector3 reference = StrictMath.abs(VectorMath.dot(axis, WORLD_X)) < 0.9 ? WORLD_X : GLOBAL_UP;
            Vector3 e1 = VectorMath.normalize(VectorMath.reject(reference, axis));
            Vector3 e2 = VectorMath.cross(axis, e1);

            List<OutgoingEdge> outgoing = new ArrayList<>();
            for (GuideCurve curve : curves) {
                if (curve.startPoleId().equals(pole.id())) {
                    outgoing.add(outgoingEdge(curve, pole, poles.get(curve.endPoleId()), false, e1, e2));
                }
                if (curve.endPoleId().equals(pole.id())) {
                    outgoing.add(outgoingEdge(curve, pole, poles.get(curve.startPoleId()), true, e1, e2));
                }
            }
            outgoing.sort((a, b) -> Double.compare(a.angle(), b.angle()));
            rotations.put(pole.id(), List.copyOf(outgoing));
        }
        return rotations;
    }

    private static OutgoingEdge outgoingEdge(GuideCurve curve, Pole from, Pole to, boolean reversed, Vector3 e1, Vector3 e2) {
        List<Vector3> controlPoints = curve.controlPoints();
        Vector3 nextPoint;
        if (!controlPoints.isEmpty()) {
            nextPoint = reversed ? controlPoints.get(controlPoints.size() - 1) : controlPoints.get(0);
        } else {
            nextPoint = to.position();
        }
        Vector3 direction = VectorMath.subtract(nextPoint, from.position());
        if (VectorMath.lengthSquared(direction) <= 1.0e-18) {
            direction = VectorMath.subtract(to.position(), from.position());
        }
        double angle = StrictMath.atan2(VectorMath.dot(direction, e2), VectorMath.dot(direction, e1));
        return new OutgoingEdge(curve.id(), to.id(), angle);
    }

    private record OutgoingEdge(String curveId, String toPoleId, double angle) {
    }

    private record DirectedEdge(String curveId, String fromPoleId, String toPoleId) {
    }
}
