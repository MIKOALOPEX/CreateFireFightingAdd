package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplingBlock;
import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplingBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplings;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateFireFightingAdd.MODID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class HoseTransportGameTests {
    @SubscribeEvent
    public static void template(ServerStartedEvent event) {
        var level = event.getServer().overworld();
        level.getStructureManager().getOrCreate(CreateFireFightingAdd.path("fluid_transport_empty"))
            .fillFromWorld(level, new BlockPos(0, 220, 0), new Vec3i(12, 4, 3), false, null);
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 220)
    public static void tankCouplingHosePump(GameTestHelper helper) {
        transfer(helper, false, false, false, false);
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 220)
    public static void reversedPump(GameTestHelper helper) {
        transfer(helper, true, false, false, false);
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 220)
    public static void closedCoupling(GameTestHelper helper) {
        transfer(helper, false, true, false, false);
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 220)
    public static void wrongHoseFace(GameTestHelper helper) {
        transfer(helper, false, false, true, false);
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 220)
    public static void stoppedPump(GameTestHelper helper) {
        transfer(helper, false, false, false, true);
    }

    private static void transfer(GameTestHelper helper, boolean reverse, boolean closed, boolean wrongFace, boolean stopped) {
        BlockPos source = new BlockPos(1, 1, 1), first = new BlockPos(2, 1, 1), second = new BlockPos(5, 1, 1);
        BlockPos hoseIn = new BlockPos(6, 1, 1), hoseOut = new BlockPos(8, 1, 1);
        BlockPos pumpPos = new BlockPos(9, 1, 1), target = new BlockPos(10, 1, 1);
        helper.setBlock(source, AllBlocks.FLUID_TANK.getDefaultState());
        helper.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState());
        var a = coupling(helper, first, Direction.EAST);
        var b = coupling(helper, second, Direction.WEST);
        a.paired = b; b.paired = a; a.isController = true;
        a.enabled = b.enabled = !closed;
        helper.setBlock(hoseIn, CreateFireFightingAdd.FIRE_HOSE.get().defaultBlockState()
            .setValue(FireHoseBlock.FACING, wrongFace ? Direction.WEST : Direction.EAST));
        helper.setBlock(hoseOut, CreateFireFightingAdd.FIRE_HOSE.get().defaultBlockState().setValue(FireHoseBlock.FACING, Direction.WEST));
        var c = (FireHoseBlockEntity) helper.getBlockEntity(hoseIn);
        var d = (FireHoseBlockEntity) helper.getBlockEntity(hoseOut);
        helper.assertTrue(FireHoseConnections.tryConnect(c, d) == FireHoseConnections.Result.SUCCESS, "Hose pair not connected");
        helper.setBlock(pumpPos, AllBlocks.MECHANICAL_PUMP.getDefaultState()
            .setValue(PumpBlock.FACING, reverse ? Direction.WEST : Direction.EAST));
        helper.runAtTickTime(5, () -> {
            var tank = (FluidTankBlockEntity) helper.getBlockEntity(reverse ? target : source);
            tank.getTankInventory().fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
            var pump = (PumpBlockEntity) helper.getBlockEntity(pumpPos);
            pump.setSpeed(stopped ? 0 : 64);
            pump.onSpeedChanged(0);
        });
        helper.runAtTickTime(180, () -> {
            int remaining = ((FluidTankBlockEntity) helper.getBlockEntity(reverse ? target : source)).getTankInventory().getFluidAmount();
            int received = ((FluidTankBlockEntity) helper.getBlockEntity(reverse ? source : target)).getTankInventory().getFluidAmount();
            boolean blocked = closed || wrongFace || stopped;
            helper.assertTrue(blocked ? received == 0 : received > 0, "Unexpected transfer: source=" + remaining
                + ", coupling=" + a.getFluidHandler(Direction.WEST).getFluidInTank(0).getAmount()
                + ", hose=" + c.getSharedTankRawAmount());
            helper.assertTrue(remaining + received + a.getFluidHandler(Direction.WEST).getFluidInTank(0).getAmount()
                + c.getSharedTankRawAmount() == 4000, "Fluid was created or lost");
            helper.succeed();
        });
    }

    private static ConnectedCoupling coupling(GameTestHelper helper, BlockPos pos, Direction facing) {
        BlockState state = FluidCouplings.BLOCK.get().defaultBlockState().setValue(FluidCouplingBlock.FACING, facing);
        helper.setBlock(pos, state);
        var endpoint = new ConnectedCoupling(helper.absolutePos(pos), state);
        helper.getLevel().setBlockEntity(endpoint);
        endpoint.initialize();
        return endpoint;
    }

    /** Fixes the physical link while exercising the real coupling ports and hose transport. */
    private static final class ConnectedCoupling extends FluidCouplingBlockEntity {
        private ConnectedCoupling paired;
        private boolean enabled = true;
        ConnectedCoupling(BlockPos pos, BlockState state) { super(pos, state); }
        @Override protected boolean transportEnabled() { return enabled && paired != null; }
        @Override public FireHoseBlockEntity getPairedHose() { return paired; }
        @Override public void tick() {
            forEachBehaviour(BlockEntityBehaviour::tick);
            if (transportEnabled()) tickConnectedTransport();
            else updateDisconnectedEndpoint();
        }
    }
}
