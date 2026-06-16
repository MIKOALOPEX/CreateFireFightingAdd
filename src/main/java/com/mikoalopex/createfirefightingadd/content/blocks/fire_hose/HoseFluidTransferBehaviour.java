package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.Map.Entry;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public class HoseFluidTransferBehaviour extends FluidTransportBehaviour {

    public HoseFluidTransferBehaviour(SmartBlockEntity be) {
        super(be);
    }

    @Override
    public boolean canHaveFlowToward(BlockState state, Direction direction) {
        if (!(state.getBlock() instanceof FireHoseBlock))
            return false;
        if (!(blockEntity instanceof FireHoseBlockEntity hose))
            return false;
        if (direction != state.getValue(FireHoseBlock.FACING).getOpposite())
            return false;
        return hose.shouldDriveBackPressure();
    }

    @Override
    public void tick() {
        super.tick();
        if (interfaces == null)
            return;
        if (!(blockEntity instanceof FireHoseBlockEntity hose))
            return;
        if (!hose.shouldDriveBackPressure())
            return;

        Direction back = hose.getBack();
        for (Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
            if (entry.getKey() != back)
                continue;
            if (entry.getValue() == null)
                continue;
            Couple<Float> pressure = entry.getValue().getPressure();
            if (pressure == null)
                continue;

            boolean pull = hose.isPulling();
            float speed = hose.getActivePumpSpeed();
            pressure.set(pull, speed);
            pressure.set(!pull, 0f);
        }
    }

    @Override
    public FluidStack getProvidedOutwardFluid(Direction side) {
        if (!(blockEntity instanceof FireHoseBlockEntity hose))
            return FluidStack.EMPTY;
        if (side != hose.getBack())
            return FluidStack.EMPTY;
        return hose.getSharedTankFluidForPipeOutput();
    }

    static void onPipeChangeNearby(FireHoseBlockEntity hose, Direction side) {
        if (hose == null || hose.getLevel() == null)
            return;
        BlockState state = hose.getBlockState();
        if (!(state.getBlock() instanceof FireHoseBlock))
            return;
        Direction facing = state.getValue(FireHoseBlock.FACING);
        if (side.getAxis() != facing.getAxis())
            return;
        hose.invalidateFluidTopology();
    }
}
