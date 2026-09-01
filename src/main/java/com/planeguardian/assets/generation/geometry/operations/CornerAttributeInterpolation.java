package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.math.VectorMath;
import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.Vector2;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

final class CornerAttributeInterpolation {
    private CornerAttributeInterpolation() {
    }

    static CornerAttributes interpolate(CornerAttributes from, CornerAttributes to, double fraction) {
        Optional<Vector2> uv = from.textureCoordinate().isPresent() && to.textureCoordinate().isPresent()
                ? Optional.of(new Vector2(
                lerp(from.textureCoordinate().orElseThrow().x(), to.textureCoordinate().orElseThrow().x(), fraction),
                lerp(from.textureCoordinate().orElseThrow().y(), to.textureCoordinate().orElseThrow().y(), fraction)))
                : Optional.empty();
        Optional<Vector3> normal = lerpDirection(from.normal(), to.normal(), fraction);
        Optional<Vector3> tangent = lerpDirection(from.tangent(), to.tangent(), fraction);
        Optional<Vector3> bitangent = lerpDirection(from.bitangent(), to.bitangent(), fraction);
        Optional<Vector3> color = from.color().isPresent() && to.color().isPresent()
                ? Optional.of(VectorMath.add(
                VectorMath.scale(from.color().orElseThrow(), 1 - fraction),
                VectorMath.scale(to.color().orElseThrow(), fraction)))
                : Optional.empty();
        Map<String, Double> layers = new TreeMap<>();
        from.scalarLayers().keySet().stream()
                .filter(to.scalarLayers()::containsKey)
                .forEach(name -> layers.put(name,
                        lerp(from.scalarLayers().get(name), to.scalarLayers().get(name), fraction)));
        return new CornerAttributes(uv, normal, tangent, bitangent, color, layers);
    }

    /** Linearly blends and re-normalizes a direction attribute (normal/tangent/bitangent), if both sides have it. */
    private static Optional<Vector3> lerpDirection(Optional<Vector3> from, Optional<Vector3> to, double fraction) {
        if (from.isEmpty() || to.isEmpty()) return Optional.empty();
        return Optional.of(VectorMath.normalize(VectorMath.add(
                VectorMath.scale(from.orElseThrow(), 1 - fraction),
                VectorMath.scale(to.orElseThrow(), fraction))));
    }

    private static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }
}
