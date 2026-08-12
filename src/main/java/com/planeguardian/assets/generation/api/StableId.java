package com.planeguardian.assets.generation.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable machine identifier; display names must never replace this value. */
public record StableId(String value) implements Comparable<StableId> {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9._:/-]*");

    public StableId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid stable identifier: " + value);
        }
    }

    @Override
    public int compareTo(StableId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
