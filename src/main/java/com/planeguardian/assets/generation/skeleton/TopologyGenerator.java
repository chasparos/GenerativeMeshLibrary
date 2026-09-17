package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.geometry.operations.VertexWeldOperation;
import com.planeguardian.assets.generation.math.VectorMath;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoLoop;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.ProtoVertex;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns a {@link TopologicalSkeleton} into a pure-quadrilateral {@link ProtoMeshSnapshot},
 * following the four-step pipeline described in the class-level design:
 *
 * <ol>
 *   <li>{@link #repairParity(TopologicalSkeleton)} — trace sub-patches and auto-increment
 *       the lowest-density boundary curve of any patch whose boundary segment sum is odd.</li>
 *   <li>{@link #mapBoundaryConstraints(TopologicalSkeleton)} — report the half-mesh valence
 *       bookkeeping for every symmetry-plane pole.</li>
 *   <li>Quadrangulate every sub-patch into the half-mesh: 4-sided patches via bilinear
 *       transfinite interpolation, and other patch sizes via a single central pole fan
 *       anchored on a matching, explicitly authored interior {@link Pole}. If the skeleton
 *       is mirrored, any patch whose every side connects two symmetry-plane poles is left
 *       unfilled — it lies exactly on the mirror seam and is naturally closed once the
 *       half-mesh is mirrored and welded.</li>
 *   <li>If mirrored, duplicate the half-mesh across the symmetry plane with inverted
 *       winding, weld coincident vertices, and verify every symmetry-plane pole now
 *       exhibits its full {@link Pole#requestedValence()}.</li>
 * </ol>
 */
public final class TopologyGenerator {

    private static final int MAX_PARITY_REPAIR_ITERATIONS = 64;
    private static final double WELD_TOLERANCE_METRES = 1.0e-6;

    /** Runs the full four-step pipeline and returns the generated mesh plus bookkeeping. */
    public GenerationResult generate(TopologicalSkeleton skeleton) {
        Objects.requireNonNull(skeleton, "skeleton");

        ParityRepairResult repair = repairParity(skeleton);
        TopologicalSkeleton repaired = repair.skeleton();
        Map<String, BoundaryConstraint> boundaryConstraints = mapBoundaryConstraints(repaired);

        HalfMesh halfMesh = quadrangulate(repaired);
        ProtoMeshSnapshot finalMesh = mirrorAndWeld(repaired, halfMesh.builder(), halfMesh.poleVertices());

        return new GenerationResult(finalMesh, repaired, repair.appliedFixes(), boundaryConstraints);
    }

    // --- Step 1: pre-generation parity validation -------------------------------------------------

    /**
     * Traces sub-patches and repeatedly increments the lowest-density boundary curve of any
     * patch whose boundary segment sum is odd, until every patch is even (or the repair fails
     * to stabilize, which throws {@link TopologyParityException}).
     */
    public ParityRepairResult repairParity(TopologicalSkeleton skeleton) {
        Objects.requireNonNull(skeleton, "skeleton");
        TopologicalSkeleton current = skeleton;
        List<String> appliedFixes = new ArrayList<>();

        for (int iteration = 0; iteration < MAX_PARITY_REPAIR_ITERATIONS; iteration++) {
            List<SubPatch> patches = current.tracePatches();
            SubPatch violating = patches.stream().filter(patch -> patch.boundarySegmentSum() % 2 != 0).findFirst().orElse(null);
            if (violating == null) {
                return new ParityRepairResult(current, appliedFixes);
            }
            SubPatch.Side lowestDensitySide = violating.sides().stream()
                    .min(Comparator.comparingInt(SubPatch.Side::densitySegmentCount))
                    .orElseThrow();
            GuideCurve curve = current.curve(lowestDensitySide.curveId());
            GuideCurve incremented = curve.withDensitySegmentCount(curve.densitySegmentCount() + 1);
            current = current.withCurve(incremented);
            appliedFixes.add("Incremented densitySegmentCount of curve " + curve.id() + " from "
                    + curve.densitySegmentCount() + " to " + incremented.densitySegmentCount()
                    + " to fix an odd boundary-segment sum");
        }
        throw new TopologyParityException(
                "Unable to stabilize sub-patch boundary parity after " + MAX_PARITY_REPAIR_ITERATIONS + " repair iterations");
    }

    /** The outcome of {@link #repairParity(TopologicalSkeleton)}: the repaired skeleton plus a human-readable change log. */
    public record ParityRepairResult(TopologicalSkeleton skeleton, List<String> appliedFixes) {
        public ParityRepairResult {
            Objects.requireNonNull(skeleton, "skeleton");
            appliedFixes = List.copyOf(appliedFixes);
        }
    }

    // --- Step 2: boundary property mapping ----------------------------------------------------------

    /** Reports the half-mesh valence bookkeeping (seam/free curve counts) for every symmetry-plane pole. */
    public Map<String, BoundaryConstraint> mapBoundaryConstraints(TopologicalSkeleton skeleton) {
        Objects.requireNonNull(skeleton, "skeleton");
        Map<String, BoundaryConstraint> constraints = new TreeMap<>();
        for (Pole pole : skeleton.poles().values()) {
            if (!pole.isOnSymmetryPlane()) continue;
            int seam = skeleton.seamCurveCount(pole.id());
            int free = skeleton.freeCurveCount(pole.id());
            constraints.put(pole.id(), new BoundaryConstraint(pole.id(), seam, free, pole.requestedValence()));
        }
        return Map.copyOf(constraints);
    }

    // --- Step 3: subspace quadrangulation ------------------------------------------------------------

    private record HalfMesh(ProtoMeshBuilder builder, Map<String, VertexId> poleVertices, List<SubPatch> patches) {
    }

    private HalfMesh quadrangulate(TopologicalSkeleton skeleton) {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        Map<String, VertexId> poleVertices = new TreeMap<>();
        for (Pole pole : skeleton.poles().values()) {
            poleVertices.put(pole.id(), builder.addVertex(pole.position()));
        }

        Map<String, List<VertexId>> curveVertices = new TreeMap<>();
        for (GuideCurve curve : skeleton.curves()) {
            Pole start = skeleton.pole(curve.startPoleId());
            Pole end = skeleton.pole(curve.endPoleId());
            boolean isSeam = start.isOnSymmetryPlane() && end.isOnSymmetryPlane();
            Plane projection = isSeam ? skeleton.symmetryPlane() : null;
            List<Vector3> samples = GuideCurveSampler.sample(curve, start.position(), end.position(), projection);

            List<VertexId> vertexIds = new ArrayList<>(samples.size());
            vertexIds.add(poleVertices.get(curve.startPoleId()));
            for (int i = 1; i < samples.size() - 1; i++) {
                vertexIds.add(builder.addVertex(samples.get(i)));
            }
            vertexIds.add(poleVertices.get(curve.endPoleId()));
            curveVertices.put(curve.id(), List.copyOf(vertexIds));
        }

        List<SubPatch> patches = skeleton.tracePatches();
        Set<String> claimedPoleIds = new HashSet<>();
        for (GuideCurve curve : skeleton.curves()) {
            claimedPoleIds.add(curve.startPoleId());
            claimedPoleIds.add(curve.endPoleId());
        }
        for (SubPatch patch : patches) {
            if (skeleton.isMirrored() && skeleton.isSeamOnlyPatch(patch)) {
                // Every side connects two symmetry-plane poles, so this patch lies exactly on the
                // mirror seam: it is naturally closed once the half-mesh is mirrored and welded,
                // and filling it here would create a degenerate, zero-thickness double face.
                continue;
            }
            if (skeleton.isHolePatch(patch)) {
                // Every side is an authored open-boundary curve (see TopologicalSkeleton#holeCurveIds()),
                // so this patch is an intentional opening (for example an eye socket or mouth) and is
                // left unfilled rather than quadrangulated.
                continue;
            }
            fillPatch(builder, skeleton, patch, poleVertices, curveVertices, claimedPoleIds);
        }
        return new HalfMesh(builder, Map.copyOf(poleVertices), patches);
    }

    private static List<VertexId> sideVertices(SubPatch.Side side, Map<String, List<VertexId>> curveVertices) {
        List<VertexId> canonical = curveVertices.get(side.curveId());
        if (!side.reversed()) return canonical;
        List<VertexId> reversed = new ArrayList<>(canonical);
        Collections.reverse(reversed);
        return reversed;
    }

    private void fillPatch(
            ProtoMeshBuilder builder,
            TopologicalSkeleton skeleton,
            SubPatch patch,
            Map<String, VertexId> poleVertices,
            Map<String, List<VertexId>> curveVertices,
            Set<String> claimedPoleIds) {
        if (patch.sideCount() == 4) {
            fillFourSidedPatch(builder, patch, curveVertices);
        } else {
            fillPoleFanPatch(builder, skeleton, patch, poleVertices, curveVertices, claimedPoleIds);
        }
    }

    /** Fills a 4-sided patch with a structured grid via bilinear transfinite interpolation (a Coons patch). */
    private void fillFourSidedPatch(ProtoMeshBuilder builder, SubPatch patch, Map<String, List<VertexId>> curveVertices) {
        List<SubPatch.Side> sides = patch.sides();
        SubPatch.Side s0 = sides.get(0);
        SubPatch.Side s1 = sides.get(1);
        SubPatch.Side s2 = sides.get(2);
        SubPatch.Side s3 = sides.get(3);

        int u = s0.densitySegmentCount();
        int v = s1.densitySegmentCount();
        if (s2.densitySegmentCount() != u || s3.densitySegmentCount() != v) {
            throw new TopologyGenerationException(
                    "4-sided patch requires opposite sides to share a density (got " + u + "/" + s2.densitySegmentCount()
                            + " and " + v + "/" + s3.densitySegmentCount() + ")");
        }

        List<VertexId> bottom = sideVertices(s0, curveVertices); // A -> B, size u+1
        List<VertexId> right = sideVertices(s1, curveVertices); // B -> C, size v+1
        List<VertexId> topReversed = sideVertices(s2, curveVertices); // C -> D, size u+1
        List<VertexId> leftReversed = sideVertices(s3, curveVertices); // D -> A, size v+1
        fillQuadGrid(builder, bottom, right, topReversed, leftReversed, patchCurveTags(patch));
    }

    /**
     * The set of semantic-group tags a patch's generated faces should carry: one
     * {@code "curve:" + curveId} tag per distinct boundary curve, so a viewer (for example a
     * "face inspector" mode) can look up every face descending from a patch bounded by a given
     * authored curve (see {@link ProtoFace#semanticGroups()}).
     */
    private static Set<String> patchCurveTags(SubPatch patch) {
        Set<String> tags = new java.util.LinkedHashSet<>();
        for (SubPatch.Side side : patch.sides()) {
            tags.add("curve:" + side.curveId());
        }
        return Set.copyOf(tags);
    }

    /**
     * Fills a structured u-by-v grid of quads bounded by 4 vertex chains via bilinear transfinite
     * interpolation (a Coons patch): {@code bottom} (A to B, u+1 vertices), {@code right} (B to C,
     * v+1 vertices), {@code topReversed} (C to D, u+1 vertices, i.e. already stored walking from C
     * to D), and {@code leftReversed} (D to A, v+1 vertices, walking from D to A). The four chains
     * must agree on shared corners: {@code bottom[0] == leftReversed[v]}, {@code bottom[u] ==
     * right[0]}, {@code right[v] == topReversed[0]}, {@code topReversed[u] == leftReversed[0]}.
     */
    private void fillQuadGrid(
            ProtoMeshBuilder builder,
            List<VertexId> bottom,
            List<VertexId> right,
            List<VertexId> topReversed,
            List<VertexId> leftReversed,
            Set<String> semanticGroups) {
        int u = bottom.size() - 1;
        int v = right.size() - 1;

        VertexId[][] grid = new VertexId[u + 1][v + 1];
        for (int i = 0; i <= u; i++) grid[i][0] = bottom.get(i);
        for (int j = 0; j <= v; j++) grid[u][j] = right.get(j);
        for (int i = 0; i <= u; i++) grid[i][v] = topReversed.get(u - i);
        for (int j = 0; j <= v; j++) grid[0][j] = leftReversed.get(v - j);

        Vector3 cornerA = builder.requireVertex(grid[0][0]).position();
        Vector3 cornerB = builder.requireVertex(grid[u][0]).position();
        Vector3 cornerC = builder.requireVertex(grid[u][v]).position();
        Vector3 cornerD = builder.requireVertex(grid[0][v]).position();

        for (int i = 1; i < u; i++) {
            for (int j = 1; j < v; j++) {
                double s = (double) i / u;
                double t = (double) j / v;
                Vector3 bottomI = builder.requireVertex(grid[i][0]).position();
                Vector3 topI = builder.requireVertex(grid[i][v]).position();
                Vector3 leftJ = builder.requireVertex(grid[0][j]).position();
                Vector3 rightJ = builder.requireVertex(grid[u][j]).position();

                Vector3 ruled = VectorMath.add(
                        VectorMath.add(VectorMath.scale(bottomI, 1 - t), VectorMath.scale(topI, t)),
                        VectorMath.add(VectorMath.scale(leftJ, 1 - s), VectorMath.scale(rightJ, s)));
                Vector3 bilinearCorners = VectorMath.add(
                        VectorMath.add(VectorMath.scale(cornerA, (1 - s) * (1 - t)), VectorMath.scale(cornerB, s * (1 - t))),
                        VectorMath.add(VectorMath.scale(cornerD, (1 - s) * t), VectorMath.scale(cornerC, s * t)));
                Vector3 interior = VectorMath.subtract(ruled, bilinearCorners);
                grid[i][j] = builder.addVertex(interior);
            }
        }

        for (int i = 0; i < u; i++) {
            for (int j = 0; j < v; j++) {
                builder.addFace(
                        List.of(grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]),
                        java.util.Collections.nCopies(4, com.planeguardian.assets.generation.topology.CornerAttributes.EMPTY),
                        semanticGroups);
            }
        }
    }

    /**
     * Fills an n-sided (n != 4) patch with a single central-pole fan, in the spirit of a single
     * Catmull-Clark subdivision level around an irregular vertex. Requires every boundary side to
     * share the same, even {@code densitySegmentCount} ({@code density}) and requires exactly one
     * unclaimed interior {@link Pole} of matching {@code requestedValence == n} located inside the
     * patch.
     *
     * <p>Each side is split at its own midpoint (index {@code density / 2}) into two halves; the
     * quad "wedge" between two adjacent half-sides and the center pole (exactly the single quad
     * produced when {@code density == 2}) is generalised into a full {@code density/2}-by-
     * {@code density/2} Coons grid, using a straight synthetic "spoke" from each side's midpoint to
     * the center pole as the wedge's two radial edges. Every spoke is built once and shared by the
     * two wedges that meet at that midpoint, so the fan stays crack-free and every quad's diagonal
     * neighbours can only be the fan's own quads or the patch's authored boundary curves.</p>
     */
    private void fillPoleFanPatch(
            ProtoMeshBuilder builder,
            TopologicalSkeleton skeleton,
            SubPatch patch,
            Map<String, VertexId> poleVertices,
            Map<String, List<VertexId>> curveVertices,
            Set<String> claimedPoleIds) {
        int n = patch.sideCount();
        int density = patch.sides().get(0).densitySegmentCount();
        for (SubPatch.Side side : patch.sides()) {
            if (side.densitySegmentCount() != density) {
                throw new TopologyGenerationException(
                        "A " + n + "-sided sub-patch requires every boundary curve to share the same densitySegmentCount "
                                + "(curve " + side.curveId() + " has " + side.densitySegmentCount() + ", expected " + density + ")");
            }
        }
        if (density % 2 != 0) {
            throw new TopologyGenerationException(
                    "A " + n + "-sided sub-patch requires an even densitySegmentCount to build its radial fan grid "
                            + "(got " + density + ")");
        }
        int half = density / 2;

        List<List<VertexId>> firstHalfPerSide = new ArrayList<>(n); // corner_k .. mid_k
        List<List<VertexId>> secondHalfPerSide = new ArrayList<>(n); // mid_k .. corner_{k+1}
        List<Vector3> ringPositions = new ArrayList<>(2 * n);
        for (SubPatch.Side side : patch.sides()) {
            List<VertexId> full = sideVertices(side, curveVertices); // size density + 1
            firstHalfPerSide.add(full.subList(0, half + 1));
            secondHalfPerSide.add(full.subList(half, full.size()));
            ringPositions.add(builder.requireVertex(full.get(0)).position());
            ringPositions.add(builder.requireVertex(full.get(half)).position());
        }

        Pole center = findMatchingCenterPole(skeleton, ringPositions, claimedPoleIds, n);
        VertexId centerVertex = poleVertices.get(center.id());
        Vector3 centerPosition = builder.requireVertex(centerVertex).position();

        // One synthetic straight spoke per side's midpoint (mid_k -> center), built once and
        // shared between the two wedges meeting at that midpoint (as its own wedge's "right" edge,
        // and reversed as the previous wedge's "top" edge).
        List<List<VertexId>> spokePerSide = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            VertexId midVertex = firstHalfPerSide.get(k).get(half);
            Vector3 midPosition = builder.requireVertex(midVertex).position();
            List<VertexId> spoke = new ArrayList<>(half + 1);
            spoke.add(midVertex);
            for (int step = 1; step < half; step++) {
                double t = (double) step / half;
                spoke.add(builder.addVertex(
                        VectorMath.add(VectorMath.scale(midPosition, 1 - t), VectorMath.scale(centerPosition, t))));
            }
            spoke.add(centerVertex);
            spokePerSide.add(spoke);
        }

        for (int k = 0; k < n; k++) {
            int previous = (k - 1 + n) % n;
            List<VertexId> bottom = firstHalfPerSide.get(k); // corner_k -> mid_k
            List<VertexId> right = spokePerSide.get(k); // mid_k -> center
            List<VertexId> topReversed = reversedCopy(spokePerSide.get(previous)); // center -> mid_{k-1}
            List<VertexId> leftReversed = secondHalfPerSide.get(previous); // mid_{k-1} -> corner_k
            fillQuadGrid(builder, bottom, right, topReversed, leftReversed, patchCurveTags(patch));
        }
    }

    private static List<VertexId> reversedCopy(List<VertexId> vertices) {
        List<VertexId> reversed = new ArrayList<>(vertices);
        Collections.reverse(reversed);
        return reversed;
    }

    private Pole findMatchingCenterPole(
            TopologicalSkeleton skeleton, List<Vector3> boundary, Set<String> claimedPoleIds, int requiredValence) {
        Vector3 normal = newellNormal(boundary);
        List<Pole> candidates = skeleton.poles().values().stream()
                .filter(pole -> !claimedPoleIds.contains(pole.id()))
                .filter(pole -> pole.requestedValence() == requiredValence)
                .filter(pole -> isInsideConvexBoundary(pole.position(), boundary, normal))
                .toList();
        if (candidates.size() != 1) {
            throw new TopologyGenerationException(
                    "A " + requiredValence + "-sided sub-patch requires exactly one unclaimed interior pole with "
                            + "requestedValence == " + requiredValence + " located inside it; found " + candidates.size());
        }
        return candidates.get(0);
    }

    private static Vector3 newellNormal(List<Vector3> positions) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (int index = 0; index < positions.size(); index++) {
            Vector3 current = positions.get(index);
            Vector3 next = positions.get((index + 1) % positions.size());
            x += (current.y() - next.y()) * (current.z() + next.z());
            y += (current.z() - next.z()) * (current.x() + next.x());
            z += (current.x() - next.x()) * (current.y() + next.y());
        }
        return new Vector3(x, y, z);
    }

    /** Assumes a (roughly) convex, planar boundary; documented scope limitation for irregular patches. */
    private static boolean isInsideConvexBoundary(Vector3 point, List<Vector3> boundary, Vector3 normal) {
        Double sign = null;
        int size = boundary.size();
        for (int index = 0; index < size; index++) {
            Vector3 a = boundary.get(index);
            Vector3 b = boundary.get((index + 1) % size);
            Vector3 edge = VectorMath.subtract(b, a);
            Vector3 toPoint = VectorMath.subtract(point, a);
            double signedArea = VectorMath.dot(VectorMath.cross(edge, toPoint), normal);
            if (StrictMath.abs(signedArea) < 1.0e-9) continue;
            double edgeSign = StrictMath.signum(signedArea);
            if (sign == null) {
                sign = edgeSign;
            } else if (!sign.equals(edgeSign)) {
                return false;
            }
        }
        return true;
    }

    // --- Step 4: mirroring and vertex welding ---------------------------------------------------------

    private ProtoMeshSnapshot mirrorAndWeld(
            TopologicalSkeleton skeleton, ProtoMeshBuilder halfBuilder, Map<String, VertexId> poleVertices) {
        if (!skeleton.isMirrored()) {
            return halfBuilder.snapshot();
        }
        Plane plane = skeleton.symmetryPlane();
        ProtoMeshSnapshot halfSnapshot = halfBuilder.snapshot();
        ProtoMeshBuilder combined = ProtoMeshBuilder.copyOf(halfSnapshot);

        Map<VertexId, VertexId> canonical = new TreeMap<>();
        for (VertexId id : halfSnapshot.vertices().keySet()) {
            canonical.put(id, id);
        }
        Map<VertexId, VertexId> mirrorOf = new TreeMap<>();
        for (ProtoVertex vertex : halfSnapshot.vertices().values()) {
            VertexId mirrored = combined.addVertex(plane.reflect(vertex.position()));
            mirrorOf.put(vertex.id(), mirrored);
            canonical.put(mirrored, mirrored);
        }
        for (ProtoFace face : halfSnapshot.faces().values()) {
            List<VertexId> original = face.loops().stream()
                    .map(loopId -> halfSnapshot.loops().get(loopId))
                    .map(ProtoLoop::vertexId)
                    .toList();
            List<VertexId> mirroredFace = new ArrayList<>(original.size());
            for (VertexId id : original) mirroredFace.add(mirrorOf.get(id));
            Collections.reverse(mirroredFace); // invert winding so mirrored normals stay outward-consistent
            combined.addFace(mirroredFace);
        }

        weldCoincidentVertices(combined, canonical);
        ProtoMeshSnapshot combinedSnapshot = combined.snapshot();
        verifySymmetryPlaneValences(skeleton, poleVertices, canonical, combinedSnapshot);
        return combinedSnapshot;
    }

    /** Groups vertices by (rounded) position via a spatial hash and welds every extra vertex in a group onto the first. */
    private void weldCoincidentVertices(ProtoMeshBuilder combined, Map<VertexId, VertexId> canonical) {
        double step = Math.max(WELD_TOLERANCE_METRES, 1.0e-9);
        Map<GridKey, List<VertexId>> buckets = new TreeMap<>();
        for (ProtoVertex vertex : combined.snapshot().vertices().values()) {
            Vector3 position = vertex.position();
            GridKey key = new GridKey(
                    Math.round(position.x() / step), Math.round(position.y() / step), Math.round(position.z() / step));
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(vertex.id());
        }
        for (List<VertexId> group : buckets.values()) {
            if (group.size() < 2) continue;
            // A curve lying entirely between symmetry-seam poles whose only adjacent sub-patches
            // are both left unfilled (a seam-only patch and/or a hole) has an interior sample
            // vertex that no face ever references on either half. Such an orphan has nothing to
            // weld onto (or be welded from): drop it, and only weld the group's remaining,
            // genuinely face-referenced duplicates onto each other as before.
            List<VertexId> unused = group.stream().filter(id -> !isVertexUsed(combined, id)).toList();
            for (VertexId orphan : unused) {
                combined.removeVertex(orphan);
            }
            List<VertexId> used = group.stream().filter(id -> !unused.contains(id)).toList();
            if (used.size() < 2) continue;
            VertexId retained = used.get(0);
            for (int index = 1; index < used.size(); index++) {
                VertexId retired = used.get(index);
                VertexWeldOperation.weldCoincident(combined, retained, retired, WELD_TOLERANCE_METRES);
                canonical.put(retired, retained);
                for (Map.Entry<VertexId, VertexId> entry : canonical.entrySet()) {
                    if (entry.getValue().equals(retired)) entry.setValue(retained);
                }
            }
        }
    }

    /** Whether any face loop currently references {@code vertexId}. */
    private static boolean isVertexUsed(ProtoMeshBuilder builder, VertexId vertexId) {
        return builder.snapshot().loops().values().stream().anyMatch(loop -> loop.vertexId().equals(vertexId));
    }

    private void verifySymmetryPlaneValences(
            TopologicalSkeleton skeleton,
            Map<String, VertexId> poleVertices,
            Map<VertexId, VertexId> canonical,
            ProtoMeshSnapshot finalSnapshot) {
        for (Pole pole : skeleton.poles().values()) {
            if (!pole.isOnSymmetryPlane()) continue;
            VertexId original = poleVertices.get(pole.id());
            VertexId resolved = resolveCanonical(canonical, original);
            long valence = finalSnapshot.edges().values().stream()
                    .filter(edge -> edge.vertexA().equals(resolved) || edge.vertexB().equals(resolved))
                    .count();
            if (valence != pole.requestedValence()) {
                throw new TopologyGenerationException(
                        "Post-weld verification failed for symmetry-plane pole " + pole.id() + ": expected valence "
                                + pole.requestedValence() + " but the welded mesh has " + valence);
            }
        }
    }

    private static VertexId resolveCanonical(Map<VertexId, VertexId> canonical, VertexId id) {
        VertexId current = id;
        while (!canonical.get(current).equals(current)) {
            current = canonical.get(current);
        }
        return current;
    }

    private record GridKey(long x, long y, long z) implements Comparable<GridKey> {
        @Override
        public int compareTo(GridKey other) {
            int cx = Long.compare(x, other.x);
            if (cx != 0) return cx;
            int cy = Long.compare(y, other.y);
            if (cy != 0) return cy;
            return Long.compare(z, other.z);
        }
    }
}
