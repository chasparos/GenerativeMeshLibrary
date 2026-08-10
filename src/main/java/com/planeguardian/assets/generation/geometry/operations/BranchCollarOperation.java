package com.planeguardian.assets.generation.geometry.operations;

import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.ProtoMeshBuilder;

/** Tree-independent collar proof composed entirely from inset and extrusion. */
public final class BranchCollarOperation {
    private BranchCollarOperation() {
    }

    public static BranchCollarResult create(
            ProtoMeshBuilder builder, FaceId hostFace,
            double insetFraction, Vector3 extrusionOffset) {
        FaceInsetOperation.InsetResult inset = FaceInsetOperation.inset(builder, hostFace, insetFraction);
        FaceExtrudeOperation.ExtrudeResult extrusion = FaceExtrudeOperation.extrude(
                builder, inset.insetFace(), extrusionOffset);
        return new BranchCollarResult(inset, extrusion);
    }

    public record BranchCollarResult(
            FaceInsetOperation.InsetResult inset,
            FaceExtrudeOperation.ExtrudeResult extrusion) {
    }
}
