# Topology methodology: poles, guide curves, and mirrored quad meshes

This document describes the idea behind the
`com.planeguardian.assets.generation.skeleton` package, why it should
reliably produce clean all-quadrilateral meshes, and the concrete technical
decisions made while turning that idea into working code
(`Pole`, `GuideCurve`, `TopologicalSkeleton`, `TopologyGenerator`).

## The initial idea

Instead of authoring or generating a mesh vertex-by-vertex, we describe its
*topology* sparsely, as a small graph:

- A **pole** (`Pole`) is an explicit vertex the author places by hand — a
  "reversing gear" location where the natural 4-sided grid of a quad mesh has
  to locally converge or diverge (an eye corner, the crown of a head, a
  branch junction). Every other vertex the generator produces is an ordinary,
  valence-4 grid point that never needs to be authored at all.
- A **guide curve** (`GuideCurve`) is a directed path between two poles,
  optionally bulged through a handful of control points and resampled at an
  even, arc-length-spaced density. Guide curves are the *only* edges an
  author draws; everything else (the interior grid of a 4-sided patch, the
  spokes of a fan around an irregular pole) is derived from them.
- A **topological skeleton** (`TopologicalSkeleton`) is the full graph of
  poles and curves for one half of a mirror-symmetric shape, plus the plane
  of symmetry. Only one half is authored; the other half is produced by
  reflection.

The reason to author a *mirrored half* rather than a whole closed shape is
the valence arithmetic it buys us:

- Every curve that stays **inside** one half (an "interior" or "free" curve,
  connecting an off-plane pole to anything) is duplicated by the mirror.
  Once the two halves are welded back together along the symmetry plane,
  each such curve contributes **two** edges to whatever valence its
  endpoints end up with.
- Every curve that runs **along the seam** (a "seam" curve, connecting two
  poles that already sit on the symmetry plane) maps to itself under
  reflection — it is not duplicated, and contributes exactly **one** edge.

For a pole that is not itself on the symmetry plane, this means its final
valence is `2 * (number of incident curves)` — always even, by construction,
regardless of how many curves the author draws into it. For a pole that
*is* on the symmetry plane, the same argument (see "Valence bookkeeping"
below) makes its final valence `seamCurveCount + 2 * freeCurveCount`,
which is even exactly when `seamCurveCount` is even — and a symmetry-plane
pole's seam curves are exactly the two curves that continue the seam through
it, so in every case relevant to a single, connected seam loop this is
naturally satisfied too.

An all-even valence at every explicit pole, plus the fact that every other
vertex is an unquestioned valence-4 grid point, is exactly the condition a
Catmull-Clark-style quadrangulation needs: **every closed region the curve
graph traces out has an even number of boundary segments**, which is the
precise, checkable condition under which that region can always be filled
with quads (either a regular grid, for a 4-sided region, or a single ring of
quads fanned around one interior pole, for any other even-or-odd-sided
region — see "Filling a patch", below, for why the fan technique doesn't
actually need the side count itself to be even). This is the sense in which
"mirroring makes the internal pole count structurally even, and therefore a
quad mesh should always be constructible" — the initial hunch that started
this package.

## What the pipeline actually enforces

The idea above is a good motivating intuition, but `TopologicalSkeleton` and
`TopologyGenerator` need something checkable and mechanical, not "roughly
even". Two independent, complementary invariants are validated (or, where
possible, automatically repaired) at generation time:

### 1. Pole valence formulas (`TopologicalSkeleton.validate()`)

Every `Pole` carries an explicit `requestedValence()` — the valence the
*generated* mesh must exhibit there once mirroring and welding are done.
`validate()` checks that this is achievable from the curve graph alone,
before any geometry is built:

- **Interior poles** (not on the symmetry plane): `requestedValence` must
  equal the pole's graph degree (its number of incident curves). Each
  incident curve becomes exactly one mesh edge once mirrored.
- **Symmetry-plane poles**: `requestedValence` must equal
  `seamCurveCount + 2 * freeCurveCount` (`BoundaryConstraint` documents and
  `TopologyGenerator.mapBoundaryConstraints()` reports this bookkeeping;
  `TopologyGenerator.verifySymmetryPlaneValences()` re-checks it against the
  actual welded mesh after generation, as a construction-correctness
  assertion rather than trusting the arithmetic blindly).

A pole that fails either formula throws `TopologyParityException` immediately
— the point is to fail fast on an inconsistent authoring mistake, long before
any expensive mesh construction happens.

### 2. Even boundary-segment sums per traced patch (`TopologyGenerator.repairParity`)

`TopologicalSkeleton.tracePatches()` walks the curve graph with a standard
rotation-system (DCEL-style) algorithm — at each pole, outgoing curve
directions are sorted by angle around a local axis (the vector from the
skeleton's centroid to the pole, standing in for its outward surface
normal), and every face is traced by always turning to "the next edge after
the twin" in that order. Every directed curve traversal belongs to exactly
one traced face, so this always produces a complete set of closed
`SubPatch` boundary loops for a closed, cage-like skeleton (see
"Design decision: closed cage skeletons only", below).

Each `SubPatch`'s boundary is a cycle of curve traversals, each contributing
its own `densitySegmentCount` (how many resampled segments that curve is
built from). `TopologyGenerator.repairParity()` sums those densities per
patch and, if the sum is odd, increments the lowest-density side by one
segment and retraces — repeating until every patch's boundary sum is even
(or giving up after a bounded number of iterations, which would indicate a
graph that cannot be repaired this way). An even boundary-segment sum is
exactly what both quadrangulation strategies below need.

## Filling a patch

Once every patch boundary is even, `TopologyGenerator` fills each one with
one of two purely-geometric techniques, chosen by side count:

- **4-sided patches** (`fillFourSidedPatch`): a bilinear transfinite
  interpolation (a Coons patch) between the four boundary curves, producing
  a regular `u x v` grid of ordinary valence-4 vertices. This requires
  (and the same parity/graph-degree checks above guarantee) that opposite
  sides share the same segment density.
- **Any other side count** (`fillPoleFanPatch`): a single ring of `n` quads
  fanned around one interior pole, in the spirit of a single Catmull-Clark
  subdivision step around an irregular vertex. This is why an *odd*-sided
  patch is not actually a problem for quadrangulation on its own — the fan
  technique only needs every boundary curve to be resampled at exactly
  `densitySegmentCount == 2` (so each side contributes one boundary corner
  and one boundary midpoint to the fan ring) and exactly one unclaimed
  interior `Pole` of matching `requestedValence` positioned inside the
  patch's boundary polygon to act as the fan's center.

Every non-hole, non-seam patch in the authored skeleton must be fillable by
one of these two techniques — there is deliberately no general polygon
triangulator or arbitrary-valence solver in the pipeline; every irregular
patch is routed to a small, explicit, matching-valence pole instead.

## Technical decisions

A few concrete choices fell out of turning the idea into a working
pipeline; they are worth calling out because they shape what a skeleton
author can and can't do:

- **Only one half is authored, and only closed cage-like skeletons are
  supported.** `tracePatches()`'s rotation-system algorithm assumes the
  curve graph is a closed 3D cage (for example, one half of a creature
  limb or head, wrapped fully around) rather than a flat, open 2D layout —
  a flat layout would have an extra unbounded "outer" face with no natural
  place in the algorithm. This is why `HumanFaceSkeleton` closes its
  centreline into a full loop (crown → glabella → … → neck → crown) rather
  than leaving the top and bottom of the face as open boundaries.
- **The rotation axis at each pole is "outward from the skeleton centroid",
  not a true local surface normal.** Because there is no surface to sample
  a normal from (only a sparse point graph), `localRotationAxis()`
  approximates it with the direction from the whole skeleton's centroid to
  the pole. This is a genuine scope limitation: it only sorts curves
  correctly for a roughly star-convex, non-planar arrangement of poles (see
  `docs/human-face-skeleton.md` for how this constrained the actual pole
  placement).
- **Every fan-filled patch is authored at `densitySegmentCount == 2`.** The
  single-center-pole fan technique needs each boundary side to contribute
  exactly one midpoint vertex, so every curve bounding a non-4-sided patch
  must use exactly two segments. Because `densitySegmentCount` is a
  per-curve, not per-patch, property, any curve shared between a 4-sided and
  an n-sided patch is constrained by the stricter of the two.
  `HumanFaceSkeleton` sidesteps this entirely by authoring *every* curve at
  `densitySegmentCount == 2`, which trivially satisfies both the fan
  requirement everywhere and keeps every patch's boundary sum even without
  needing any automatic repair.
- **Odd boundary-segment sums are auto-repaired, not rejected.** Rather than
  forcing an author to hand-tune every curve's density so every patch sums
  to an even number (which is only possible by solving the whole graph
  simultaneously, since curves are shared between patches),
  `TopologyGenerator.repairParity()` treats it as a mechanical, automatic
  fix: increment the cheapest (lowest-density) offending side and retry.
  This keeps the authoring language declarative — pick a valence per pole,
  a density per curve where you care about it, and let the generator fix up
  the rest — at the cost of the generated segment count not always being
  exactly what was written down (`GenerationResult.appliedParityFixes()`
  reports every repair that was applied, for inspection).
- **Vertex welding after mirroring uses spatial hashing with a fixed
  tolerance**, not the DCEL boundary structure itself, to find coincident
  vertices across the seam (`weldCoincidentVertices`). This is simple and
  robust to the half-mesh and its mirror having been built independently,
  at the cost of needing every seam-adjacent vertex to land within
  `WELD_TOLERANCE_METRES` (1e-6 m) of its mirrored counterpart — which is
  why seam curves are always sampled with an explicit projection onto the
  symmetry plane (see `quadrangulate()`), rather than trusting authored
  control points to be exactly planar.
- **Hole patches are an explicit opt-out, not inferred.** A patch (for
  example an eye socket or mouth opening) that should remain an open
  boundary rather than being quadrangulated is marked by listing its
  bounding curve ids in `TopologicalSkeleton`'s `holeCurveIds`, and
  `TopologyGenerator` simply skips any traced patch whose every side is one
  of those ids (`isHolePatch`). Nothing about a patch's shape or size makes
  it a hole automatically.

## See also

- [`human-face-skeleton.md`](human-face-skeleton.md) — a concrete skeleton
  built on top of this methodology, and the compromises it makes to work
  within the scope described above.
- `TopologicalSkeleton`, `TopologyGenerator`, `Pole`, `GuideCurve`,
  `SubPatch`, `BoundaryConstraint`, and `GenerationResult` — the Javadoc on
  these classes is the most detailed, up-to-date source of truth for the
  exact rules; this document summarizes and explains the *why* behind them.
