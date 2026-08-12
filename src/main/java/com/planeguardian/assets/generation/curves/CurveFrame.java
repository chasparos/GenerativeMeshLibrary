package com.planeguardian.assets.generation.curves;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.Objects;

/** Right-handed local tube frame: tangent × normal = binormal. */
public record CurveFrame(
        double arcFraction,
        double parameter,
        Vector3 position,
        Vector3 tangent,
        Vector3 normal,
        Vector3 binormal,
        double rollRadians) {
    public CurveFrame {
        if (!Double.isFinite(arcFraction) || !Double.isFinite(parameter) || !Double.isFinite(rollRadians)) {
            throw new IllegalArgumentException("Frame scalars must be finite");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(tangent, "tangent");
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(binormal, "binormal");
    }
}
