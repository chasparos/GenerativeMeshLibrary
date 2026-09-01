package com.planeguardian.assets.generation.topology;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Per-corner data permits UV, normal, tangent-space, and color seams without
 * duplicating positions. Covers the standard render-vertex attribute gamut:
 * texture coordinate, normal, tangent, bitangent (binormal), vertex color,
 * and arbitrary named scalar layers (masks, blend weights, and so on).
 *
 * <p>{@code tangent} and {@code bitangent} are only meaningful together with
 * {@code normal}; they form the tangent-space basis used for normal mapping.
 * When authored, downstream adapters may still re-orthogonalize them against
 * the interpolated normal (Gram-Schmidt) and derive handedness from their
 * cross product rather than trusting an authored sign.</p>
 */
public record CornerAttributes(
        Optional<Vector2> textureCoordinate,
        Optional<Vector3> normal,
        Optional<Vector3> tangent,
        Optional<Vector3> bitangent,
        Optional<Vector3> color,
        Map<String, Double> scalarLayers) {

    public static final CornerAttributes EMPTY = new CornerAttributes(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of());

    public CornerAttributes {
        Objects.requireNonNull(textureCoordinate, "textureCoordinate");
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(tangent, "tangent");
        Objects.requireNonNull(bitangent, "bitangent");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(scalarLayers, "scalarLayers");
        TreeMap<String, Double> copy = new TreeMap<>();
        scalarLayers.forEach((name, value) -> {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Layer name must not be blank");
            if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("Layer value must be finite");
            copy.put(name, value);
        });
        scalarLayers = Collections.unmodifiableMap(copy);
    }

    /** Convenience constructor for the common case of authoring only UV + normal, matching prior call sites. */
    public CornerAttributes(Optional<Vector2> textureCoordinate, Optional<Vector3> normal, Map<String, Double> scalarLayers) {
        this(textureCoordinate, normal, Optional.empty(), Optional.empty(), Optional.empty(), scalarLayers);
    }
}
