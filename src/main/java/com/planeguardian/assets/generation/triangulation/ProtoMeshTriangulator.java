package com.planeguardian.assets.generation.triangulation;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.LoopId;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoLoop;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic ear-clipping triangulation shared by all output adapters. */
public final class ProtoMeshTriangulator {
    private static final double EPSILON = 1.0e-12;

    private ProtoMeshTriangulator() {
    }

    public static TriangulatedMesh triangulate(ProtoMeshSnapshot mesh) {
        Objects.requireNonNull(mesh, "mesh");
        if (!mesh.isValid()) throw new IllegalArgumentException("Cannot triangulate invalid topology: " + mesh.issues());
        List<TriangleVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (ProtoFace face : mesh.faces().values()) {
            int base = vertices.size();
            List<Vector3> positions = new ArrayList<>(face.loops().size());
            for (LoopId loopId : face.loops()) {
                ProtoLoop loop = mesh.loops().get(loopId);
                Vector3 position = mesh.vertices().get(loop.vertexId()).position();
                positions.add(position);
                vertices.add(new TriangleVertex(loop.vertexId(), loop.id(), face.id(), position, loop.attributes()));
            }
            triangulateFace(positions).forEach(localIndex -> indices.add(base + localIndex));
        }
        int[] indexArray = indices.stream().mapToInt(Integer::intValue).toArray();
        return new TriangulatedMesh(vertices, indexArray);
    }

    private static List<Integer> triangulateFace(List<Vector3> positions) {
        Projection projection = Projection.forPolygon(positions);
        List<Point2> points = positions.stream().map(projection::project).toList();
        double orientation = signedArea(points);
        if (StrictMath.abs(orientation) <= EPSILON) throw new IllegalArgumentException("Cannot triangulate a degenerate polygon");
        List<Integer> remaining = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) remaining.add(index);
        List<Integer> result = new ArrayList<>((points.size() - 2) * 3);
        while (remaining.size() > 3) {
            boolean clipped = false;
            for (int cursor = 0; cursor < remaining.size(); cursor++) {
                int previous = remaining.get((cursor - 1 + remaining.size()) % remaining.size());
                int current = remaining.get(cursor);
                int next = remaining.get((cursor + 1) % remaining.size());
                if (!isConvex(points.get(previous), points.get(current), points.get(next), orientation)) continue;
                if (containsOtherPoint(points, remaining, previous, current, next, orientation)) continue;
                result.add(previous);
                result.add(current);
                result.add(next);
                remaining.remove(cursor);
                clipped = true;
                break;
            }
            if (!clipped) throw new IllegalArgumentException("Polygon is self-intersecting or numerically unstable");
        }
        result.addAll(remaining);
        return result;
    }

    private static boolean containsOtherPoint(
            List<Point2> points, List<Integer> remaining,
            int a, int b, int c, double orientation) {
        for (int candidate : remaining) {
            if (candidate == a || candidate == b || candidate == c) continue;
            if (insideTriangle(points.get(candidate), points.get(a), points.get(b), points.get(c), orientation)) return true;
        }
        return false;
    }

    private static boolean insideTriangle(Point2 point, Point2 a, Point2 b, Point2 c, double orientation) {
        double sign = StrictMath.signum(orientation);
        return cross(a, b, point) * sign >= -EPSILON
                && cross(b, c, point) * sign >= -EPSILON
                && cross(c, a, point) * sign >= -EPSILON;
    }

    private static boolean isConvex(Point2 a, Point2 b, Point2 c, double orientation) {
        return cross(a, b, c) * StrictMath.signum(orientation) > EPSILON;
    }

    private static double signedArea(List<Point2> points) {
        double area = 0;
        for (int index = 0; index < points.size(); index++) {
            Point2 current = points.get(index);
            Point2 next = points.get((index + 1) % points.size());
            area += current.x * next.y - next.x * current.y;
        }
        return area * 0.5;
    }

    private static double cross(Point2 a, Point2 b, Point2 c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private record Point2(double x, double y) {
    }

    private enum Projection {
        DROP_X { Point2 project(Vector3 value) { return new Point2(value.y(), value.z()); } },
        DROP_Y { Point2 project(Vector3 value) { return new Point2(value.x(), value.z()); } },
        DROP_Z { Point2 project(Vector3 value) { return new Point2(value.x(), value.y()); } };

        abstract Point2 project(Vector3 value);

        static Projection forPolygon(List<Vector3> positions) {
            double x = 0;
            double y = 0;
            double z = 0;
            for (int index = 0; index < positions.size(); index++) {
                Vector3 current = positions.get(index);
                Vector3 next = positions.get((index + 1) % positions.size());
                x += (current.y() - next.y()) * (current.z() + next.z());
                y += (current.z() - next.z()) * (current.x() + next.x());
                z += (current.x() - next.x()) * (current.y() + next.y());
            }
            double absX = StrictMath.abs(x);
            double absY = StrictMath.abs(y);
            double absZ = StrictMath.abs(z);
            if (absX >= absY && absX >= absZ) return DROP_X;
            if (absY >= absZ) return DROP_Y;
            return DROP_Z;
        }
    }
}
