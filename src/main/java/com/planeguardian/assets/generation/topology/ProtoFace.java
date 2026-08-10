package com.planeguardian.assets.generation.topology;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record ProtoFace(FaceId id, List<LoopId> loops, Set<String> semanticGroups) {
    public ProtoFace {
        Objects.requireNonNull(id, "id");
        loops = List.copyOf(loops);
        if (loops.size() < 3) throw new IllegalArgumentException("A polygon face needs at least three loops");
        TreeSet<String> groups = new TreeSet<>();
        semanticGroups.forEach(group -> {
            if (group == null || group.isBlank()) throw new IllegalArgumentException("Semantic group must not be blank");
            groups.add(group);
        });
        semanticGroups = Collections.unmodifiableSet(groups);
    }
}
