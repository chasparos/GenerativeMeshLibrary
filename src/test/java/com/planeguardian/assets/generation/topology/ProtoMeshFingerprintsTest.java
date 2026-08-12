package com.planeguardian.assets.generation.topology;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.determinism.NumericQuantizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProtoMeshFingerprintsTest {
    private static final NumericQuantizer MILLIMETRES = new NumericQuantizer(0.001);

    @Test
    void equivalentBuildsHaveTheSameCanonicalFingerprint() {
        assertEquals(fingerprintOfTriangle(1.0), fingerprintOfTriangle(1.0));
    }

    @Test
    void quantizationSuppressesSubMillimetreNoiseButRetainsMeaningfulChange() {
        assertEquals(fingerprintOfTriangle(1.0), fingerprintOfTriangle(1.0004));
        assertNotEquals(fingerprintOfTriangle(1.0), fingerprintOfTriangle(1.002));
    }

    private static String fingerprintOfTriangle(double x) {
        ProtoMeshBuilder builder = new ProtoMeshBuilder();
        VertexId a = builder.addVertex(Vector3.ZERO);
        VertexId b = builder.addVertex(new Vector3(x, 0, 0));
        VertexId c = builder.addVertex(new Vector3(0, 1, 0));
        builder.addFace(List.of(a, b, c));
        return ProtoMeshFingerprints.compute(builder.snapshot(), MILLIMETRES).hex();
    }
}
