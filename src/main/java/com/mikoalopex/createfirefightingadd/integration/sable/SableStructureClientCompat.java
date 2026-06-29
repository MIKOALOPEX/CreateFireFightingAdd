package com.mikoalopex.createfirefightingadd.integration.sable;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class SableStructureClientCompat {

    private static final ClientStructureBackend BACKEND = loadBackend();

    private SableStructureClientCompat() {
    }

    public static FireHoseRenderTransform transformFireHoseTarget(BlockEntity owner,
                                                                  @Nullable UUID partnerSubLevel,
                                                                  Vector3d partnerCenter,
                                                                  Vector3d partnerNormal) {
        if (BACKEND != null)
            return BACKEND.transformFireHoseTarget(owner, partnerSubLevel, partnerCenter, partnerNormal);
        return new FireHoseRenderTransform(
            new Vector3d(partnerCenter),
            new Vector3d(partnerNormal),
            new Quaterniond(),
            new Quaterniond());
    }

    public static Vec3 renderPositionToWorld(BlockEntity owner, Vec3 localPos) {
        if (BACKEND != null)
            return BACKEND.renderPositionToWorld(owner, localPos);
        return localPos;
    }

    private static ClientStructureBackend loadBackend() {
        try {
            ClassLoader loader = SableStructureClientCompat.class.getClassLoader();
            if (!hasSableClientBackendApi(loader))
                return null;
            Class<?> backendClass = Class.forName(
                "com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientBackend",
                true,
                loader);
            return (ClientStructureBackend) backendClass.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasSableClientBackendApi(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable", false, loader);
        Class<?> helper = sable.getField("HELPER").getType();
        helper.getMethod("getContainingClient", BlockEntity.class);

        Class<?> subLevelContainer = Class.forName(
            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer",
            false,
            loader);
        subLevelContainer.getMethod("getContainer", net.minecraft.world.level.Level.class);

        Class<?> clientContainer = Class.forName(
            "dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer",
            false,
            loader);
        clientContainer.getMethod("getSubLevel", UUID.class);

        Class<?> clientSubLevel = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel", false, loader);
        clientSubLevel.getMethod("renderPose");

        Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc", false, loader);
        pose.getMethod("transformPosition", Vector3d.class);
        pose.getMethod("transformPositionInverse", Vector3d.class);
        pose.getMethod("transformNormal", Vector3d.class);
        pose.getMethod("transformNormalInverse", Vector3d.class);
        pose.getMethod("orientation");

        Class<?> clientAccess = Class.forName(
            "dev.ryanhcode.sable.companion.ClientSubLevelAccess",
            false,
            loader);
        clientAccess.getMethod("renderPose");
        return true;
    }

    public record FireHoseRenderTransform(Vector3d partnerCenter,
                                          Vector3d partnerNormal,
                                          Quaterniond ownerOrientation,
                                          Quaterniond partnerOrientation) {
    }

    interface ClientStructureBackend {
        FireHoseRenderTransform transformFireHoseTarget(BlockEntity owner,
                                                        @Nullable UUID partnerSubLevel,
                                                        Vector3d partnerCenter,
                                                        Vector3d partnerNormal);

        Vec3 renderPositionToWorld(BlockEntity owner, Vec3 localPos);
    }
}
