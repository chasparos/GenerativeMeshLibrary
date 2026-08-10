package com.planeguardian.assets.generation.topology;

import com.planeguardian.assets.generation.api.Vector3;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Per-corner data permits UV and normal seams without duplicating positions. */
public record CornerAttributes(
        Optional<Vector2> textureCoordinate,
        Optional<Vector3> normal,
        Map<String, Double> scalarLayers) {

    public static final CornerAttributes EMPTY = new CornerAttributes(
            Optional.empty(), Optional.empty(), Map.of());

    public CornerAttributes {
        Objects.requireNonNull(textureCoordinate, "textureCoordinate");
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(scalarLayers, "scalarLayers");
        TreeMap<String, Double> copy = new TreeMap<>();
        scalarLayers.forEach((name, value) -> {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Layer name must not be blank");
            if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("Layer value must be finite");
            copy.put(name, value);
        });
        scalarLayers = Collections.unmodifiableMap(copy);
    }
}
