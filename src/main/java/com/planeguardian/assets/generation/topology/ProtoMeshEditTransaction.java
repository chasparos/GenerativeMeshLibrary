package com.planeguardian.assets.generation.topology;

import java.util.Objects;
import java.util.function.Function;

/** Isolated topology edit: source snapshots never change and invalid commits fail. */
public final class ProtoMeshEditTransaction {
    private final ProtoMeshBuilder builder;
    private boolean closed;

    private ProtoMeshEditTransaction(ProtoMeshSnapshot source) {
        builder = ProtoMeshBuilder.copyOf(source);
    }

    public static ProtoMeshEditTransaction begin(ProtoMeshSnapshot source) {
        return new ProtoMeshEditTransaction(Objects.requireNonNull(source, "source"));
    }

    public <T> T apply(Function<ProtoMeshBuilder, T> operation) {
        requireOpen();
        return Objects.requireNonNull(operation, "operation").apply(builder);
    }

    public ProtoMeshSnapshot preview() {
        requireOpen();
        return builder.snapshot();
    }

    public ProtoMeshSnapshot commit() {
        requireOpen();
        ProtoMeshSnapshot result = builder.snapshot();
        if (!result.isValid()) throw new IllegalStateException("Cannot commit invalid topology: " + result.issues());
        closed = true;
        return result;
    }

    public void rollback() {
        requireOpen();
        closed = true;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Topology transaction is closed");
    }
}
