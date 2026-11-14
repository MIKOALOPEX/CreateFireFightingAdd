package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.simibubi.create.content.fluids.FluidPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Refresh after BE installation, like Create's delayed pipe propagation. */
final class NozzleFluidConnection {
    private NozzleFluidConnection() {}

    static void refresh(ServerLevel level, BlockPos pos) {
        level.invalidateCapabilities(pos);
        // Create FluidPipeBlock recalculates open faces in updateShape, not neighborChanged.
        // Replay shape updates now that this endpoint's BE/capability is available.
        level.getBlockState(pos).updateNeighbourShapes(level, pos, 3);
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        // A nozzle is a tank endpoint, not a pipe: propagation must start on adjacent pipes.
        // Refresh all sides to remove the previous inlet as well after a wrench rotation.
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (level.hasChunkAt(adjacent) && FluidPropagator.getPipe(level, adjacent) != null)
                FluidPropagator.propagateChangedPipe(level, adjacent, level.getBlockState(adjacent));
        }
    }
}
