package com.planeguardian.assets.generation.topology;

import java.util.Objects;

public record TopologyIssue(Severity severity, String code, String message) {
    public enum Severity { WARNING, ERROR }

    public TopologyIssue {
        Objects.requireNonNull(severity, "severity");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Issue code must not be blank");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Issue message must not be blank");
    }
}
