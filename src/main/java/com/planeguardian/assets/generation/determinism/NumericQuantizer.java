package com.planeguardian.assets.generation.determinism;

/** Strict, finite numeric quantization used before hashing and serialization. */
public record NumericQuantizer(double step) {
    public NumericQuantizer {
        if (!Double.isFinite(step) || step <= 0) {
            throw new IllegalArgumentException("step must be finite and positive");
        }
    }

    public double quantize(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        double result = StrictMath.rint(value / step) * step;
        return result == 0.0 ? 0.0 : result;
    }
}
