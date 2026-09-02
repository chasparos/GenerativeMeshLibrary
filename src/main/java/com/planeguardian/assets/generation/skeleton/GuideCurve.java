package com.planeguardian.assets.generation.skeleton;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.List;
import java.util.Objects;

/**
 * A directed guide curve connecting two {@link Pole}s, sampled at
 * {@code densitySegmentCount} evenly spaced segments (so
 * {@code densitySegmentCount + 1} points including both endpoints).
 *
 * <p>{@code densitySegmentCount} is not immutable across the generation
 * pipeline in spirit: {@link TopologyGenerator} may need to increase the
 * lowest-density curve of a sub-patch by one segment to satisfy the even-sum
 * parity rule (see {@link TopologicalSkeleton}). Because this record is
 * immutable, such a repair produces a brand-new {@code GuideCurve} (and a
 * brand-new {@link TopologicalSkeleton}) rather than mutating this one.</p>
 */
public record GuideCurve(
        String id,
        String startPoleId,
        String endPoleId,
        List<Vector3> controlPoints,
        int densitySegmentCount) {

    public GuideCurve {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("GuideCurve id must not be blank");
        }
        if (startPoleId == null || startPoleId.isBlank()) {
            throw new IllegalArgumentException("GuideCurve startPoleId must not be blank");
        }
        if (endPoleId == null || endPoleId.isBlank()) {
            throw new IllegalArgumentException("GuideCurve endPoleId must not be blank");
        }
        if (startPoleId.equals(endPoleId)) {
            throw new IllegalArgumentException("GuideCurve " + id + " cannot start and end at the same pole");
        }
        Objects.requireNonNull(controlPoints, "controlPoints");
        controlPoints = List.copyOf(controlPoints);
        if (densitySegmentCount < 1) {
            throw new IllegalArgumentException("GuideCurve densitySegmentCount must be at least 1, got " + densitySegmentCount);
        }
    }

    /** Returns a copy of this curve with a different segment density, all else unchanged. */
    public GuideCurve withDensitySegmentCount(int newDensitySegmentCount) {
        return new GuideCurve(id, startPoleId, endPoleId, controlPoints, newDensitySegmentCount);
    }
}
