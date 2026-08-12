package com.planeguardian.assets.generation.math;

import com.planeguardian.assets.generation.api.Vector3;

public final class VectorMath {
    private static final double EPSILON_SQUARED = 1.0e-24;

    private VectorMath() {
    }

    public static Vector3 add(Vector3 a, Vector3 b) { return new Vector3(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    public static Vector3 subtract(Vector3 a, Vector3 b) { return new Vector3(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    public static Vector3 scale(Vector3 value, double factor) { return new Vector3(value.x() * factor, value.y() * factor, value.z() * factor); }
    public static double dot(Vector3 a, Vector3 b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    public static Vector3 cross(Vector3 a, Vector3 b) { return new Vector3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x()); }
    public static double lengthSquared(Vector3 value) { return dot(value, value); }
    public static double length(Vector3 value) { return StrictMath.sqrt(lengthSquared(value)); }
    public static double distance(Vector3 a, Vector3 b) { return length(subtract(a, b)); }

    public static Vector3 normalize(Vector3 value) {
        double lengthSquared = lengthSquared(value);
        if (lengthSquared <= EPSILON_SQUARED) throw new IllegalArgumentException("Cannot normalize a zero-length vector");
        return scale(value, 1.0 / StrictMath.sqrt(lengthSquared));
    }

    public static Vector3 reject(Vector3 value, Vector3 unitAxis) {
        return subtract(value, scale(unitAxis, dot(value, unitAxis)));
    }

    public static Vector3 rotateAroundUnitAxis(Vector3 value, Vector3 unitAxis, double radians) {
        double cosine = StrictMath.cos(radians);
        double sine = StrictMath.sin(radians);
        return add(add(scale(value, cosine), scale(cross(unitAxis, value), sine)),
                scale(unitAxis, dot(unitAxis, value) * (1.0 - cosine)));
    }

    public static double clamp(double value, double minimum, double maximum) {
        return StrictMath.max(minimum, StrictMath.min(maximum, value));
    }
}
