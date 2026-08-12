package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.topology.CornerAttributes;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;
import com.planeguardian.assets.generation.topology.VertexId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Applies only reviewed standard patterns; it is not a general remesher. */
public final class UnequalRingBridgeOperation {
    private UnequalRingBridgeOperation() {
    }

    public static TransitionResult bridge(
            ProtoMeshBuilder builder,
            List<VertexId> first,
            List<VertexId> second,
            StandardLoopTransition pattern,
            Set<String> semanticGroups) {
        List<VertexId> ringA = List.copyOf(first);
        List<VertexId> ringB = List.copyOf(second);
        boolean increasing;
        List<VertexId> smaller;
        List<VertexId> larger;
        if (ringA.size() == pattern.smallerRingSize() && ringB.size() == pattern.largerRingSize()) {
            increasing = true;
            smaller = ringA;
            larger = ringB;
        } else if (ringA.size() == pattern.largerRingSize() && ringB.size() == pattern.smallerRingSize()) {
            increasing = false;
            smaller = ringB;
            larger = ringA;
        } else {
            throw new IllegalArgumentException("Ring sizes do not match transition " + pattern);
        }

        int wedgeCount = pattern.wedgeCount();
        int wedgeInterval = smaller.size() / wedgeCount;
        int largerCursor = -1;
        List<FaceId> regularFaces = new ArrayList<>(smaller.size());
        List<FaceId> wedgeFaces = new ArrayList<>(wedgeCount);
        for (int smallerIndex = 0; smallerIndex < smaller.size(); smallerIndex++) {
            if (smallerIndex % wedgeInterval == 0) {
                List<VertexId> wedge = List.of(
                        smaller.get(smallerIndex),
                        larger.get(wrap(largerCursor + 2, larger.size())),
                        larger.get(wrap(largerCursor + 1, larger.size())),
                        larger.get(wrap(largerCursor, larger.size())));
                wedgeFaces.add(addFace(builder, wedge, increasing, semanticGroups));
                largerCursor += 2;
            }
            List<VertexId> regular = List.of(
                    smaller.get(smallerIndex),
                    smaller.get((smallerIndex + 1) % smaller.size()),
                    larger.get(wrap(largerCursor + 1, larger.size())),
                    larger.get(wrap(largerCursor, larger.size())));
            regularFaces.add(addFace(builder, regular, increasing, semanticGroups));
            largerCursor++;
        }
        if (wrap(largerCursor, larger.size()) != larger.size() - 1) {
            throw new IllegalStateException("Transition pattern did not consume the larger ring exactly");
        }
        return new TransitionResult(pattern, increasing, regularFaces, wedgeFaces);
    }

    private static FaceId addFace(
            ProtoMeshBuilder builder, List<VertexId> vertices,
            boolean increasing, Set<String> semanticGroups) {
        List<VertexId> ordered = new ArrayList<>(vertices);
        if (!increasing) Collections.reverse(ordered);
        return builder.addFace(ordered, Collections.nCopies(4, CornerAttributes.EMPTY), semanticGroups);
    }

    private static int wrap(int value, int size) {
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    public record TransitionResult(
            StandardLoopTransition pattern,
            boolean increasing,
            List<FaceId> regularFaces,
            List<FaceId> wedgeFaces) {
        public TransitionResult {
            regularFaces = List.copyOf(regularFaces);
            wedgeFaces = List.copyOf(wedgeFaces);
        }

        public List<FaceId> allFaces() {
            List<FaceId> result = new ArrayList<>(regularFaces);
            result.addAll(wedgeFaces);
            return List.copyOf(result);
        }
    }
}
