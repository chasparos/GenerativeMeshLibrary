package com.planeguardian.assets.generation.skeleton;

/**
 * Thrown when a {@link TopologicalSkeleton}'s guide-curve network cannot be
 * packed into a pure quadrilateral mesh: an odd boundary-segment sum around a
 * sub-patch, or a pole whose {@link Pole#requestedValence()} does not match
 * the valence implied by its incident curves (see {@link TopologicalSkeleton}
 * class Javadoc for the exact formulas).
 */
public class TopologyParityException extends RuntimeException {

    public TopologyParityException(String message) {
        super(message);
    }

    public TopologyParityException(String message, Throwable cause) {
        super(message, cause);
    }
}
