package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The output of {@link TopologyGenerator#generate(TopologicalSkeleton)}: the generated mesh,
 * the (possibly parity-repaired) skeleton it was built from, a human-readable log of any
 * automatic parity repairs applied, and the boundary-constraint bookkeeping for every
 * symmetry-plane pole.
 */
public record GenerationResult(
        ProtoMeshSnapshot mesh,
        TopologicalSkeleton repairedSkeleton,
        List<String> appliedParityFixes,
        Map<String, BoundaryConstraint> boundaryConstraints) {

    public GenerationResult {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(repairedSkeleton, "repairedSkeleton");
        appliedParityFixes = List.copyOf(appliedParityFixes);
        boundaryConstraints = Map.copyOf(boundaryConstraints);
    }
}
