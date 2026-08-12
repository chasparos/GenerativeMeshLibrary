package com.planeguardian.assets.generation.api;

import java.util.Arrays;
import java.util.HexFormat;

/** SHA-256 identity for quantized generation inputs or outputs. */
public final class ReproducibilityFingerprint {
    private static final int LENGTH = 32;
    private final byte[] bytes;

    public ReproducibilityFingerprint(byte[] bytes) {
        if (bytes == null || bytes.length != LENGTH) {
            throw new IllegalArgumentException("Fingerprint must contain 32 bytes");
        }
        this.bytes = bytes.clone();
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String hex() {
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReproducibilityFingerprint fingerprint
                && Arrays.equals(bytes, fingerprint.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return hex();
    }
}
