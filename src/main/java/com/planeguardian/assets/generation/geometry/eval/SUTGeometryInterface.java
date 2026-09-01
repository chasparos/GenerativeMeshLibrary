package com.planeguardian.assets.generation.geometry.eval;

import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;

/**
 * A "system under test" (SUT) geometry generator exercised by the evaluation
 * modeler application. Implementations are engine-agnostic: they produce an
 * immutable, validated {@link ProtoMeshSnapshot} built entirely from the
 * topology/geometry-operations/triangulation toolkit in this library.
 */
public interface SUTGeometryInterface {

    /** Stable, machine-friendly identifier for this generator (e.g. {@code "tube"}). */
    String id();

    /** Human-readable label suitable for display in a viewer UI. */
    String displayName();

    /** Builds a fresh, validated mesh snapshot for the current parameters. */
    ProtoMeshSnapshot generate();
}
