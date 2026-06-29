package com.mikoalopex.createfirefightingadd.integration.sable;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

final class SableStructureClientBackend implements SableStructureClientCompat.ClientStructureBackend {

    @Override
    public SableStructureClientCompat.FireHoseRenderTransform transformFireHoseTarget(BlockEntity owner,
                                                                                       @Nullable UUID partnerSubLevel,
                                                                                       Vector3d partnerCenter,
                                                                                       Vector3d partnerNormal) {
        Vector3d transformedCenter = new Vector3d(partnerCenter);
        Vector3d transformedNormal = new Vector3d(partnerNormal);
        Pose3dc ownerPose = null;
        Pose3dc partnerPose = null;

        if (Minecraft.getInstance().level != null) {
            ClientSubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
            ClientSubLevel otherSubLevel = partnerSubLevel != null && container != null
                ? (ClientSubLevel) container.getSubLevel(partnerSubLevel)
                : null;
            ClientSubLevel ownerSubLevel = Sable.HELPER.getContainingClient(owner);

            ownerPose = ownerSubLevel != null ? ownerSubLevel.renderPose() : null;
            partnerPose = otherSubLevel != null ? otherSubLevel.renderPose() : null;
        }

        if (partnerPose != null) {
            partnerPose.transformNormal(transformedNormal);
            partnerPose.transformPosition(transformedCenter);
        }
        if (ownerPose != null) {
            ownerPose.transformNormalInverse(transformedNormal);
            ownerPose.transformPositionInverse(transformedCenter);
        }

        return new SableStructureClientCompat.FireHoseRenderTransform(
            transformedCenter,
            transformedNormal,
            ownerPose != null ? new Quaterniond(ownerPose.orientation()) : new Quaterniond(),
            partnerPose != null ? new Quaterniond(partnerPose.orientation()) : new Quaterniond());
    }

    @Override
    public Vec3 renderPositionToWorld(BlockEntity owner, Vec3 localPos) {
        dev.ryanhcode.sable.companion.ClientSubLevelAccess clientAccess = Sable.HELPER.getContainingClient(owner);
        return clientAccess != null ? clientAccess.renderPose().transformPosition(localPos) : localPos;
    }
}
