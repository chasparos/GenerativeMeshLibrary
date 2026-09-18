package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A mutable, editor-facing working copy of a {@link TopologicalSkeleton}, deliberately kept
 * free of any JME3 (rendering/input) dependency so its curve-graph mutations can be unit
 * tested in isolation from the interactive {@code CurveEditorAppState} that drives them.
 *
 * <p>Unlike {@link TopologicalSkeleton} (which validates its valence/parity invariants in its
 * constructor and is immutable), this class tolerates transient, "editing-in-progress" states
 * that would fail those invariants — most importantly <em>dangling</em> poles that currently
 * have only a single incident curve (see {@link #singleCurvePoleIds()}). The user is expected
 * to keep dragging such a half-connected curve onto a second pole; until then the working copy
 * simply refuses to hand those curves to {@link TopologyGenerator} (see
 * {@link #toGenerationSkeleton()}), rather than throwing.</p>
 *
 * <h2>Symmetry-plane rules</h2>
 * <ul>
 *   <li>A pole marked {@link Pole#isOnSymmetryPlane()} is constrained to the mirror plane: every
 *       position handed to {@link #movePole} is first projected onto the plane, so the pole can
 *       only ever slide within it.</li>
 *   <li>A curve whose two endpoints are both on the symmetry plane is a <em>seam</em> curve
 *       (it lies flat on {@code x=0}); its authored control points are projected onto the plane
 *       whenever the graph changes, so the seam stays flat regardless of how it is edited.</li>
 * </ul>
 *
 * <h2>Valence bookkeeping</h2>
 * <p>Because interior-pole valence must equal graph degree and symmetry-plane-pole valence must
 * equal {@code seamCurveCount + 2*freeCurveCount} (see {@link TopologicalSkeleton}), this class
 * recomputes every pole's {@link Pole#requestedValence()} from the current curve graph when it
 * builds the generation skeleton, so an edit that changes a pole's degree does not leave a stale
 * authored valence behind.</p>
 */
public final class SkeletonEditOperations {


    private final Map<String, Pole> poles = new LinkedHashMap<>();
    private final List<GuideCurve> curves = new ArrayList<>();
    private final boolean mirrored;
    private final Plane symmetryPlane;
    private final Set<String> holeCurveIds = new LinkedHashSet<>();
    private final Set<String> ringInsetCurveIds = new LinkedHashSet<>();

    private int nextGeneratedId = 0;

    /** Starts a fresh working copy from an authored, already-valid {@link TopologicalSkeleton}. */
    public SkeletonEditOperations(TopologicalSkeleton source) {
        Objects.requireNonNull(source, "source");
        this.poles.putAll(source.poles());
        this.curves.addAll(source.curves());
        this.mirrored = source.isMirrored();
        this.symmetryPlane = source.symmetryPlane();
        this.holeCurveIds.addAll(source.holeCurveIds());
        this.ringInsetCurveIds.addAll(source.ringInsetCurveIds());
    }

    // ---- Read-only accessors ---------------------------------------------------------------

    public Map<String, Pole> poles() {
        return Map.copyOf(poles);
    }

    public List<GuideCurve> curves() {
        return List.copyOf(curves);
    }

    public boolean isMirrored() {
        return mirrored;
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
        for (GuideCurve curve : curves) {
            if (curve.id().equals(id)) return curve;
        }
        throw new IllegalArgumentException("Unknown guide curve: " + id);
    }

    /** Number of curve endpoints touching {@code poleId}. */
    public int degree(String poleId) {
        pole(poleId);
        int degree = 0;
        for (GuideCurve curve : curves) {
            if (curve.startPoleId().equals(poleId)) degree++;
            if (curve.endPoleId().equals(poleId)) degree++;
        }
        return degree;
    }

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

    /**
     * Ids of poles that currently have exactly one incident curve — the "lit up", not-yet-finished
     * poles the editor highlights and whose single curve is excluded from mesh generation.
     */
    public Set<String> singleCurvePoleIds() {
        Set<String> lit = new LinkedHashSet<>();
        for (String poleId : poles.keySet()) {
            if (degree(poleId) == 1) lit.add(poleId);
        }
        return lit;
    }

    /** Whether the curve joins two symmetry-plane poles and therefore lies flat on the mirror seam. */
    public boolean isSeamCurve(GuideCurve curve) {
        Pole start = poles.get(curve.startPoleId());
        Pole end = poles.get(curve.endPoleId());
        return start != null && end != null && start.isOnSymmetryPlane() && end.isOnSymmetryPlane();
    }

    // ---- Mutations -------------------------------------------------------------------------

    /**
     * Moves {@code poleId} to {@code newPosition}, which re-anchors every incident curve (they
     * reference the pole by id, so re-sampling picks up the new endpoint automatically). If the
     * pole is on the symmetry plane the target is first projected onto it, constraining the pole
     * to slide within the plane; any incident seam curve then has its control points re-flattened.
     */
    public void movePole(String poleId, Vector3 newPosition) {
        Pole existing = pole(poleId);
        Objects.requireNonNull(newPosition, "newPosition");
        Vector3 target = existing.isOnSymmetryPlane() && symmetryPlane != null
                ? symmetryPlane.project(newPosition) : newPosition;
        poles.put(poleId, new Pole(poleId, target, existing.requestedValence(), existing.isOnSymmetryPlane()));
        reflattenSeamCurvesAround(poleId);
    }

    /**
     * Rewrites one endpoint of {@code curveId} to reference {@code newPoleId} instead — the data
     * change behind both "detach an endpoint" and "reattach it to another pole". Re-flattens the
     * curve if the change turned it into (or out of) a seam curve.
     */
    public void reattachEndpoint(String curveId, boolean startEndpoint, String newPoleId) {
        GuideCurve curve = curve(curveId);
        pole(newPoleId);
        String start = startEndpoint ? newPoleId : curve.startPoleId();
        String end = startEndpoint ? curve.endPoleId() : newPoleId;
        if (start.equals(end)) {
            throw new IllegalArgumentException("Reattaching would make curve " + curveId + " start and end at the same pole");
        }
        GuideCurve updated = new GuideCurve(curve.id(), start, end, curve.controlPoints(), curve.densitySegmentCount());
        replaceCurve(updated);
    }

    /**
     * Adds a new free-standing pole and returns its id. On-symmetry-plane poles are projected onto
     * the plane so they satisfy the skeleton's on-plane constraint. Mainly used by the editor when
     * a "create new curve" or "split" gesture needs a fresh pole.
     */
    public String addPole(String id, Vector3 position, int requestedValence, boolean onSymmetryPlane) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        if (poles.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate pole id: " + id);
        }
        Vector3 target = onSymmetryPlane && symmetryPlane != null ? symmetryPlane.project(position) : position;
        poles.put(id, new Pole(id, target, requestedValence, onSymmetryPlane));
        return id;
    }

    /**
     * Creates a brand-new curve connecting two existing poles and returns its generated id.
     * Used by "create new curve from selected pole" once the user drags the free end onto a
     * second pole. Rejects a self-loop or an exact duplicate of an existing pole pair.
     */
    public String createCurve(String startPoleId, String endPoleId, int densitySegmentCount) {
        pole(startPoleId);
        pole(endPoleId);
        if (startPoleId.equals(endPoleId)) {
            throw new IllegalArgumentException("A curve cannot connect a pole to itself");
        }
        String id = uniqueCurveId("curve");
        GuideCurve curve = new GuideCurve(id, startPoleId, endPoleId, List.of(), Math.max(1, densitySegmentCount));
        curves.add(seamProjected(curve));
        return id;
    }

    /** Removes {@code curveId} from the graph (also dropping it from the hole/ring-inset sets). */
    public void deleteCurve(String curveId) {        curve(curveId);
        curves.removeIf(curve -> curve.id().equals(curveId));
        holeCurveIds.remove(curveId);
        ringInsetCurveIds.remove(curveId);
    }

    /**
     * Splits {@code curveId} at {@code splitPosition} by inserting a new pole there and replacing
     * the one curve with two curves that share the new pole (the original id keeps the first half;
     * a {@code "_split"}-suffixed id gets the second). Returns the id of the newly created pole so
     * the caller can immediately drag it. The new pole gets a default valence of 4, which keeps the
     * two resulting patches' parity satisfiable by {@link TopologyGenerator#repairParity}.
     */
    public String splitCurve(String curveId, Vector3 splitPosition) {
        GuideCurve curve = curve(curveId);
        Objects.requireNonNull(splitPosition, "splitPosition");
        boolean onSeam = isSeamCurve(curve);
        Vector3 position = onSeam && symmetryPlane != null ? symmetryPlane.project(splitPosition) : splitPosition;

        String newPoleId = uniqueId("pole");
        boolean newPoleOnPlane = onSeam;
        poles.put(newPoleId, new Pole(newPoleId, position, 4, newPoleOnPlane));

        String secondId = uniqueCurveId(curve.id() + "_split");
        GuideCurve first = new GuideCurve(curve.id(), curve.startPoleId(), newPoleId, List.of(), curve.densitySegmentCount());
        GuideCurve second = new GuideCurve(secondId, newPoleId, curve.endPoleId(), List.of(), curve.densitySegmentCount());
        replaceCurve(first);
        int insertionIndex = indexOfCurve(curve.id()) + 1;
        curves.add(insertionIndex, seamProjected(second));

        boolean wasHole = holeCurveIds.remove(curve.id());
        boolean wasRingInset = ringInsetCurveIds.remove(curve.id());
        if (wasHole) {
            holeCurveIds.add(curve.id());
            holeCurveIds.add(secondId);
        }
        if (wasRingInset) {
            ringInsetCurveIds.add(curve.id());
            ringInsetCurveIds.add(secondId);
        }
        return newPoleId;
    }

    /** The midpoint of a curve's straight chord, a convenient default split position. */
    public Vector3 curveMidpoint(String curveId) {
        GuideCurve curve = curve(curveId);
        Vector3 a = pole(curve.startPoleId()).position();
        Vector3 b = pole(curve.endPoleId()).position();
        return new Vector3((a.x() + b.x()) * 0.5, (a.y() + b.y()) * 0.5, (a.z() + b.z()) * 0.5);
    }

    /**
     * Writes {@code controlPoints} onto {@code curveId} (for example after a tangent handle drag),
     * projecting them onto the symmetry plane first if the curve is a seam curve so it stays flat.
     */
    public void setControlPoints(String curveId, List<Vector3> controlPoints) {
        GuideCurve curve = curve(curveId);
        Objects.requireNonNull(controlPoints, "controlPoints");
        GuideCurve updated = new GuideCurve(curve.id(), curve.startPoleId(), curve.endPoleId(), controlPoints, curve.densitySegmentCount());
        replaceCurve(seamProjected(updated));
    }

    // ---- Generation ------------------------------------------------------------------------

    /**
     * Builds a validated {@link TopologicalSkeleton} for mesh generation from the current working
     * copy, pruning every "lit up" dangling curve (any curve incident to a degree-1 pole, applied
     * repeatedly so a dangling chain is pruned all the way back) and recomputing each surviving
     * pole's requestedValence from the pruned graph. Interior poles that end up with zero incident
     * curves are kept as-is (they may be authored fan centers). May throw the same
     * {@link TopologyParityException}/{@link IllegalArgumentException} the skeleton constructor
     * throws — callers (the editor UI) are expected to catch and surface these rather than crash.
     */
    public TopologicalSkeleton toGenerationSkeleton() {
        List<GuideCurve> kept = pruneDanglingCurves(curves);

        Set<String> referenced = new LinkedHashSet<>();
        for (GuideCurve curve : kept) {
            referenced.add(curve.startPoleId());
            referenced.add(curve.endPoleId());
        }

        Map<String, Pole> generationPoles = new LinkedHashMap<>();
        for (Pole pole : poles.values()) {
            boolean referencedInKept = referenced.contains(pole.id());
            boolean phantomCenter = !referencedInKept && degreeIn(curves, pole.id()) == 0;
            if (!referencedInKept && !phantomCenter) {
                continue; // dropped along with its pruned dangling curve(s)
            }
            int valence = referencedInKept ? impliedValence(pole, kept) : pole.requestedValence();
            generationPoles.put(pole.id(), new Pole(pole.id(), pole.position(), Math.max(2, valence), pole.isOnSymmetryPlane()));
        }

        Set<String> keptHoleIds = new LinkedHashSet<>(holeCurveIds);
        keptHoleIds.retainAll(idsOf(kept));
        Set<String> keptRingInsetIds = new LinkedHashSet<>(ringInsetCurveIds);
        keptRingInsetIds.retainAll(keptHoleIds);

        return new TopologicalSkeleton(generationPoles, kept, mirrored, symmetryPlane, keptHoleIds, keptRingInsetIds);
    }

    private List<GuideCurve> pruneDanglingCurves(List<GuideCurve> input) {
        List<GuideCurve> working = new ArrayList<>(input);
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, Integer> degree = new LinkedHashMap<>();
            for (GuideCurve curve : working) {
                degree.merge(curve.startPoleId(), 1, Integer::sum);
                degree.merge(curve.endPoleId(), 1, Integer::sum);
            }
            List<GuideCurve> next = new ArrayList<>(working.size());
            for (GuideCurve curve : working) {
                if (degree.getOrDefault(curve.startPoleId(), 0) <= 1 || degree.getOrDefault(curve.endPoleId(), 0) <= 1) {
                    changed = true; // drop this dangling curve
                } else {
                    next.add(curve);
                }
            }
            working = next;
        }
        return working;
    }

    private int impliedValence(Pole pole, List<GuideCurve> graph) {
        if (!pole.isOnSymmetryPlane()) {
            return degreeIn(graph, pole.id());
        }
        int seam = 0;
        int free = 0;
        for (GuideCurve curve : graph) {
            String otherId = null;
            if (curve.startPoleId().equals(pole.id())) otherId = curve.endPoleId();
            else if (curve.endPoleId().equals(pole.id())) otherId = curve.startPoleId();
            if (otherId == null) continue;
            Pole other = poles.get(otherId);
            boolean otherOnPlane = other != null && other.isOnSymmetryPlane();
            if (otherOnPlane && holeCurveIds.contains(curve.id())) {
                // Mirrors TopologicalSkeleton#unfillableCurveIds' main case: a hole-boundary
                // curve directly joining two symmetry-plane poles (e.g. a mouth opening's
                // center seam) is unfillable on both traced sides, so it contributes no edge
                // and must not count toward either endpoint's implied valence.
                continue;
            }
            if (otherOnPlane) seam++;
            else free++;
        }
        return seam + (2 * free);
    }

    private static int degreeIn(List<GuideCurve> graph, String poleId) {
        int degree = 0;
        for (GuideCurve curve : graph) {
            if (curve.startPoleId().equals(poleId)) degree++;
            if (curve.endPoleId().equals(poleId)) degree++;
        }
        return degree;
    }

    private static Set<String> idsOf(List<GuideCurve> graph) {
        Set<String> ids = new LinkedHashSet<>();
        for (GuideCurve curve : graph) ids.add(curve.id());
        return ids;
    }

    // ---- Internal helpers ------------------------------------------------------------------

    private void reflattenSeamCurvesAround(String poleId) {
        if (symmetryPlane == null) return;
        for (int i = 0; i < curves.size(); i++) {
            GuideCurve curve = curves.get(i);
            if ((curve.startPoleId().equals(poleId) || curve.endPoleId().equals(poleId)) && isSeamCurve(curve)) {
                curves.set(i, seamProjected(curve));
            }
        }
    }

    private GuideCurve seamProjected(GuideCurve curve) {
        if (symmetryPlane == null || !isSeamCurve(curve) || curve.controlPoints().isEmpty()) {
            return curve;
        }
        List<Vector3> projected = new ArrayList<>(curve.controlPoints().size());
        for (Vector3 point : curve.controlPoints()) {
            projected.add(symmetryPlane.project(point));
        }
        return new GuideCurve(curve.id(), curve.startPoleId(), curve.endPoleId(), projected, curve.densitySegmentCount());
    }

    private void replaceCurve(GuideCurve replacement) {
        int index = indexOfCurve(replacement.id());
        curves.set(index, replacement);
    }

    private int indexOfCurve(String curveId) {
        for (int i = 0; i < curves.size(); i++) {
            if (curves.get(i).id().equals(curveId)) return i;
        }
        throw new IllegalArgumentException("Unknown guide curve: " + curveId);
    }

    private boolean hasCurveId(String id) {
        for (GuideCurve curve : curves) {
            if (curve.id().equals(id)) return true;
        }
        return false;
    }

    private String uniqueCurveId(String base) {
        if (!hasCurveId(base)) return base;
        String candidate;
        do {
            candidate = base + "_" + (nextGeneratedId++);
        } while (hasCurveId(candidate));
        return candidate;
    }

    private String uniqueId(String base) {
        String candidate;
        do {
            candidate = base + "_" + (nextGeneratedId++);
        } while (poles.containsKey(candidate));
        return candidate;
    }
}
