package com.mikoalopex.createfirefightingadd.integration.sable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder.ExtensionLadderBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.AbstractSprayDeviceBlockEntity;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SableStructureCallbacks {
    private static final AtomicBoolean REPORTED_LADDER_MOVE_FAILURE = new AtomicBoolean();

    private SableStructureCallbacks() {
    }

    public static Iterable<@NotNull SubLevel> connectionDependencies(Level level, UUID subLevelId) {
        if (level == null || subLevelId == null)
            return List.of();

        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(subLevelId);
        return subLevel != null ? List.of(subLevel) : List.of();
    }

    public static void applyPropulsionForce(ServerSubLevel subLevel, BlockPos pos,
                                            Vector3d direction, double magnitude, double timeStep) {
        Vector3d point = new Vector3d(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5);
        Vector3d impulse = new Vector3d(direction).mul(magnitude * timeStep);

        subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get())
            .applyAndRecordPointForce(point, impulse);
    }

    public static void applySprayDevicePropulsion(AbstractSprayDeviceBlockEntity device,
                                                  ServerSubLevel subLevel, double timeStep) {
        if (!device.hasStructureThrust())
            return;
        applyPropulsionForce(
            subLevel,
            device.getBlockPos(),
            device.getStructureThrustDirection(),
            device.getStructureThrustMagnitude(),
            timeStep);
    }

    public static void beforeFireHoseMove(ServerLevel newLevel, BlockPos oldPos) {
        if (newLevel.getBlockEntity(oldPos) instanceof FireHoseBlockEntity hose)
            hose.markStructureAssembling();
    }

    public static void afterFireHoseMove(ServerLevel newLevel, BlockState state, BlockPos newPos) {
        if (newLevel.getBlockEntity(newPos) instanceof FireHoseBlockEntity hose) {
            hose.updatePartnerEndpoint(newPos, SableStructureCompat.containingSubLevelId(newLevel, newPos));
            SableStructureCompat.notifyBlockChanged(newLevel, newPos, state);
        }
    }

    public static void afterExtensionLadderMove(ServerLevel newLevel, BlockState state, BlockPos newPos) {
        try {
            if (newLevel.getBlockEntity(newPos) instanceof ExtensionLadderBlockEntity ladder) {
                ladder.reinitializeAfterStructureMove();
                SableStructureCompat.notifyBlockChanged(newLevel, newPos, state);
            }
        } catch (Throwable e) {
            if (REPORTED_LADDER_MOVE_FAILURE.compareAndSet(false, true)) {
                CreateFireFightingAdd.LOGGER.warn(
                    "An extension ladder could not be restored after a Sable structure moved it; "
                        + "further matching errors are suppressed: {}",
                    e.toString());
                CreateFireFightingAdd.LOGGER.debug("Extension ladder Sable move failure", e);
            }
        }
    }
}
