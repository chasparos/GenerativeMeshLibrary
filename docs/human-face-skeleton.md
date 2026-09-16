# The `HumanFaceSkeleton` proof of concept

`HumanFaceSkeleton` is the first non-trivial exercise of the pole/guide-curve
methodology described in
[`topology-methodology.md`](topology-methodology.md): a symmetric human face,
authored as one half mirrored across `x = 0`, with two open-boundary "ring"
holes (the eye socket and the mouth opening) and a small set of poles routing
the forehead, nose, cheek, and jaw around them.

It exists to answer a concrete question — "does the general pole/curve
pipeline actually produce a clean, fillable quad mesh on a shape people
recognise as a face, and not just on toy test skeletons?" — and to surface,
in the process, exactly where the pipeline's current scope limitations bite.

## How it is intended to work

### Pole layout

Poles are placed on (or very near) the surface of a head-shaped ellipsoid
centred at `(0, 0.55, 0)` with radii `0.24` lateral, `0.55` vertical, and
`0.55` front-to-back. This is a deliberate, non-negotiable choice, not just
an aesthetic one: `TopologicalSkeleton.tracePatches()` sorts each pole's
outgoing curves by angle around the direction from the whole skeleton's
centroid to that pole (its stand-in for a local outward surface normal — see
`topology-methodology.md`'s note on "Design decision: closed cage
skeletons only"), and that approximation only produces a correct rotation
order when poles sit on a genuinely non-planar, roughly convex 3D surface.
Placing poles on an ellipsoid, rather than in a flatter, more literal
face-proportioned layout, is what keeps that assumption true.

Two families of poles are authored:

- **On-axis (symmetry-plane) poles**, running top-to-bottom down the
  centreline: `crown`, `glabella` (between the brows), `philtrum`,
  `lipCenter`, `mentalCleft` (chin), `neckBase`. These close into a loop
  (crown → … → neckBase → back of head → crown) rather than leaving the
  top or bottom of the face open, satisfying the "closed cage skeleton"
  requirement `tracePatches()` needs.
- **Off-axis interior poles**, one per side of the mirror plane:
  `innerEyeNose`, `cheek`, `mouthCorner` — the minimal set needed to route
  curves around the eye and mouth holes and out to the temple/jaw
  silhouette.

A third family, **phantom fan-center poles** (`templeFan`, `noseMouthFan`,
`jawFan`, `backJawFan`, `browFan`, `maskFan`), are never referenced by any
`GuideCurve`. They exist purely to give `TopologyGenerator.fillPoleFanPatch`
a matching-valence anchor point for each small irregular (3- or 5-sided)
patch the curve network traces out — one phantom pole per patch, positioned
near that patch's centre with `requestedValence` equal to the patch's side
count. `TopologicalSkeleton.validate()` has an explicit carve-out for this:
an interior pole with zero incident curves is accepted as an intentional
phantom fan-center rather than rejected as an unreachable valence request.

### Curve layout and the two open-boundary holes

Every curve is authored at `densitySegmentCount == 2`. This single choice
does double duty (see the corresponding bullet in
`topology-methodology.md`'s "Technical decisions"): it satisfies
`fillPoleFanPatch`'s requirement that every side of a fan-filled patch
contribute exactly one boundary midpoint, and it keeps every patch's
boundary-segment sum trivially even everywhere, so `repairParity()` never
needs to change anything for this skeleton.

The eyelid margin and lip margin are each modelled as a literal **bigon** —
two distinct `GuideCurve`s sharing the same start/end pole pair
(`eyeUpperLoop`/`eyeUnderLoop` between `innerEyeNose` and `cheek`;
`upperLip`/`lowerLip` between `lipCenter` and `mouthCorner`) — and both
curves of each pair are listed in `holeCurveIds`, so
`TopologyGenerator.isHolePatch()` leaves the small loop they trace out
unfilled: a genuine open boundary, rather than a triangulated cap. The two
curves of each pair are also given deliberately distinct bulge control
points (rather than being exact mirror images of the same chord), so that
`tracePatches()`'s angular sort sees two genuinely different outgoing
directions at each shared pole instead of two numerically coincident ones —
without that separation the loop would not be traced as its own patch.

### Curvature

Every curve that is not part of a hole ring is authored with a bulge control
point (via a `curveVia(...)` helper that adds one interior control point) so
`GuideCurveSampler` follows a rounded arc rather than a flat chord between
its two poles. Control points were computed by projecting each straight
chord's midpoint outward onto the head ellipsoid's surface, so the generated
surface tracks the rounded skull/jaw/cheek profile the pole ellipsoid
describes instead of faceting it into flat segments. `browRidge` and
`noseBridge` are the two exceptions kept as plain straight curves: giving
them a bulge repeatedly produced ambiguous or unsatisfiable phantom-fan-pole
containment for the small brow/under-eye patches they border (see
"Known simplifications and rough edges", below), so they were left straight
rather than destabilizing the traced patch set.

### Why the graph is this small

A full production edge-flow blueprint for a face (an Orbital Ring, an Oral
Ring, a Nasal Ring, a Nasolabial pole, a Jaw pole, a Zygomatic Arch, and so
on) traces out several much larger, irregular, non-planar/non-convex regions
— up to 12-sided in early iterations of this skeleton — which
`fillPoleFanPatch` cannot reliably fill: its fan technique needs a boundary
that is both convex and close to planar so that the "matching interior pole"
containment test is unambiguous. `HumanFaceSkeleton` deliberately collapses
that richer graph into a leaner one (the `innerEyeNose`/`cheek`/
`mouthCorner` pole set connected by direct curves) so that every non-hole
patch it traces out is small — either 3-sided or 5-sided — and safely
fillable by a single fan.

## Known simplifications and rough edges

- **This is a proof of concept, not a production blueprint.** It
  demonstrates that the pipeline can produce a valid, all-quad, mirrored
  face mesh, not that its edge flow matches an animation-ready topology
  blueprint. The class Javadoc explicitly frames every simplification below
  as a concession to the library's *current* quadrangulation capabilities
  (4-sided transfinite-interpolation patches and single-center-pole fans),
  not a claim that this is the ideal edge flow for a face.
- **Fan-fill pole containment is fragile for thin, near-degenerate
  patches.** When two small fan-filled patches share two of their three
  corner poles (as happens around the thin eye-hole boundary), the interior
  point-in-polygon containment test used to pick each patch's phantom fan
  pole can find both phantom poles ambiguously "inside" both patches, or —
  if a boundary curve's bulge pushes a corner outward — find no valid
  candidate for a patch at all. The concrete workaround used here is to bias
  each phantom fan pole's authored position (~70% weighted) toward its own
  patch's unique, non-shared corner pole, which keeps the two candidates
  unambiguously associated with their own patch. This is why `browFan` and
  `maskFan` sit close to `glabella`/`philtrum` respectively rather than at
  the literal centroid of their patch.
- **Lateral widening was attempted and reverted.** Scaling the ellipsoid's
  lateral radius and every off-axis pole's `x` coordinate to make the head
  read as wider/more face-like broke the same fan-containment logic (the
  `templeFan`/`maskFan` pair became ambiguous, and a different patch lost
  its only valid candidate entirely). Because a robust fix would need a more
  general polygon-containment or multi-candidate disambiguation strategy in
  `TopologyGenerator` rather than a per-skeleton pole nudge, this was
  reverted and left as a follow-up rather than worked around further inside
  this skeleton.
- **`browRidge` and `noseBridge` remain straight chords.** They border the
  same small, fragile fan patches described above; bulging them shifted a
  boundary corner enough to reproduce the ambiguous/zero-candidate failures.
  Curvature was only added where it did not destabilize the traced patch
  set (see "Curvature", above).

## Where to look in the code

- `HumanFaceSkeleton.build()` — the full pole/curve authoring, with inline
  comments at each pole/curve group explaining its purpose.
- `HumanFaceSkeletonTest` — regression coverage that `build()` still
  produces a `TopologicalSkeleton` that validates and generates without
  error, and (via a patch-tracing helper) that the traced patch set matches
  the expected shape (one seam-only hexagon, two triangles, two pentagons,
  two more triangles, and the two open-boundary hole bigons).
- `FaceGeometry` / `GeometryEvaluatorApp` — the JME3 viewer used to inspect
  the generated mesh interactively (wireframe, edge/curve overlays, a
  "Face Inspector" mode that labels each named curve, and a clipboard-export
  action for sharing the raw generated geometry as OBJ text).
