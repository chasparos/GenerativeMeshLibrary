package com.planeguardian.assets.generation.determinism;

import com.planeguardian.assets.generation.api.ReproducibilityFingerprint;
import com.planeguardian.assets.generation.api.StableId;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Length-delimited canonical SHA-256 fingerprint input builder. */
public final class FingerprintBuilder {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream output = new DataOutputStream(bytes);

    public FingerprintBuilder() {
        addString("PlaneGuardianAssets.Fingerprint/1");
    }

    public FingerprintBuilder addString(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        try {
            output.writeByte(1);
            output.writeInt(encoded.length);
            output.write(encoded);
            return this;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public FingerprintBuilder addId(StableId value) {
        return addString(value.value());
    }

    public FingerprintBuilder addLong(long value) {
        try {
            output.writeByte(2);
            output.writeLong(value);
            return this;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public FingerprintBuilder addQuantized(double value, NumericQuantizer quantizer) {
        return addLong(Double.doubleToLongBits(quantizer.quantize(value)));
    }

    public ReproducibilityFingerprint build() {
        try {
            return new ReproducibilityFingerprint(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }
}
