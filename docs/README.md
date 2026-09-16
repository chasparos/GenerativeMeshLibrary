# Documentation

This folder documents the ideas, purpose, and technical decisions behind the
`com.planeguardian.assets.generation.skeleton` package: a pole/guide-curve
authoring language for generating clean, all-quad meshes.

- [`topology-methodology.md`](topology-methodology.md) — the founding idea (a
  sparse network of poles and curves, mirrored across a symmetry plane, whose
  valence arithmetic guarantees a fillable all-quad mesh), how the pipeline
  actually implements it, and the technical decisions/trade-offs made along
  the way.
- [`human-face-skeleton.md`](human-face-skeleton.md) — a walkthrough of
  `HumanFaceSkeleton`, the proof-of-concept skeleton that exercises the
  methodology on a recognisable, anatomically-inspired shape, and the
  simplifications it makes relative to a full production edge-flow blueprint.

For the broader library (topology data structures, edit operations,
triangulation, the JME3 evaluation viewer), see the top-level
[`README.md`](../README.md).
