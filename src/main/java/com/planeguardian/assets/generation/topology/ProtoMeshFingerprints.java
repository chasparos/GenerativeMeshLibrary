package com.planeguardian.assets.generation.topology;

import com.planeguardian.assets.generation.api.ReproducibilityFingerprint;
import com.planeguardian.assets.generation.determinism.FingerprintBuilder;
import com.planeguardian.assets.generation.determinism.NumericQuantizer;

import java.util.Objects;

/** Canonical topology fingerprint in stable ID and attribute-name order. */
public final class ProtoMeshFingerprints {
    private ProtoMeshFingerprints() {
    }

    public static ReproducibilityFingerprint compute(
            ProtoMeshSnapshot mesh, NumericQuantizer positionQuantizer) {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(positionQuantizer, "positionQuantizer");
        FingerprintBuilder fingerprint = new FingerprintBuilder().addString("ProtoMesh/1");
        fingerprint.addLong(mesh.vertices().size());
        mesh.vertices().values().forEach(vertex -> {
            fingerprint.addLong(vertex.id().value());
            fingerprint.addQuantized(vertex.position().x(), positionQuantizer);
            fingerprint.addQuantized(vertex.position().y(), positionQuantizer);
            fingerprint.addQuantized(vertex.position().z(), positionQuantizer);
        });
        fingerprint.addLong(mesh.faces().size());
        mesh.faces().values().forEach(face -> {
            fingerprint.addLong(face.id().value()).addLong(face.loops().size());
            face.semanticGroups().forEach(fingerprint::addString);
            face.loops().forEach(loopId -> addLoop(fingerprint, mesh.loops().get(loopId), positionQuantizer));
        });
        return fingerprint.build();
    }

    private static void addLoop(
            FingerprintBuilder fingerprint, ProtoLoop loop, NumericQuantizer quantizer) {
        CornerAttributes attributes = loop.attributes();
        fingerprint.addLong(loop.id().value());
        fingerprint.addLong(loop.vertexId().value());
        fingerprint.addLong(loop.edgeId().value());
        fingerprint.addLong(attributes.textureCoordinate().isPresent() ? 1 : 0);
        attributes.textureCoordinate().ifPresent(uv -> {
            fingerprint.addQuantized(uv.x(), quantizer);
            fingerprint.addQuantized(uv.y(), quantizer);
        });
        fingerprint.addLong(attributes.normal().isPresent() ? 1 : 0);
        attributes.normal().ifPresent(normal -> {
            fingerprint.addQuantized(normal.x(), quantizer);
            fingerprint.addQuantized(normal.y(), quantizer);
            fingerprint.addQuantized(normal.z(), quantizer);
        });
        fingerprint.addLong(attributes.scalarLayers().size());
        attributes.scalarLayers().forEach((name, value) -> {
            fingerprint.addString(name);
            fingerprint.addQuantized(value, quantizer);
        });
    }
}
