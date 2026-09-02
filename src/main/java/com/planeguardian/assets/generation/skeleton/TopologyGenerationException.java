package com.planeguardian.assets.generation.skeleton;

/**
 * Thrown when {@link TopologyGenerator} encounters a topologically valid but
 * structurally unsupported configuration: a sub-patch shape it cannot fill
 * (for example a non-4-sided patch without a uniquely matching interior
 * {@link Pole}), or a post-generation invariant that unexpectedly failed.
 */
public class TopologyGenerationException extends RuntimeException {

    public TopologyGenerationException(String message) {
        super(message);
    }

    public TopologyGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
