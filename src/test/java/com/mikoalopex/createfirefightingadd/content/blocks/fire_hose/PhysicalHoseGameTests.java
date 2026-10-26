package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoses;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateFireFightingAdd.MODID)
@PrefixGameTestTemplate(false)
public final class PhysicalHoseGameTests {
    @GameTest(template = "fluid_transport_empty", timeoutTicks = 80)
    public static void physicalHosePlacement(GameTestHelper helper) {
        if (!PhysicalHoses.enabled()) { helper.succeed(); return; }
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var stack = new net.minecraft.world.item.ItemStack(PhysicalHoses.SMALL_ITEM.get(), 2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        BlockPos first = new BlockPos(2, 1, 1), second = new BlockPos(7, 1, 1);
        for (var relative : java.util.List.of(first, second)) {
            helper.setBlock(relative, net.minecraft.world.level.block.Blocks.STONE);
            BlockPos pos = helper.absolutePos(relative);
            var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter().add(0, 0.5, 0), Direction.UP, pos, false);
            var result = PhysicalHoses.SMALL_ITEM.get().useOn(new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(result.consumesAction(), "Physical hose item placement rejected");
        }
        var a = (PhysicalHoseBlockEntity) helper.getBlockEntity(first.above());
        var b = (PhysicalHoseBlockEntity) helper.getBlockEntity(second.above());
        helper.assertTrue(stack.isEmpty(), "Two heads should consume exactly two items");
        helper.assertTrue(a.attached() && b.attached() && a.getPairedHose() == b, "Two placed heads did not connect");
        a.disconnectPhysical();
        helper.succeed();
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 160)
    public static void physicalHoseReload(GameTestHelper helper) {
        if (!PhysicalHoses.enabled()) { helper.succeed(); return; }
        var aPos = new BlockPos(2, 2, 1); var bPos = new BlockPos(7, 2, 1); var movedPos = new BlockPos(3, 2, 1);
        helper.setBlock(aPos, PhysicalHoses.SMALL.get().defaultBlockState().setValue(FireHoseBlock.FACING, Direction.EAST));
        helper.setBlock(bPos, PhysicalHoses.SMALL.get().defaultBlockState().setValue(FireHoseBlock.FACING, Direction.WEST));
        var b = (PhysicalHoseBlockEntity) helper.getBlockEntity(bPos);
        helper.runAtTickTime(2, () -> {
            var a = (PhysicalHoseBlockEntity) helper.getBlockEntity(aPos);
            helper.assertTrue(a.connectPhysical(b), "Initial connection failed");
        });
        helper.runAtTickTime(12, () -> {
            var a = (PhysicalHoseBlockEntity) helper.getBlockEntity(aPos);
            var tag = a.saveWithFullMetadata(helper.getLevel().registryAccess());
            a.onChunkUnloaded();
            helper.getLevel().removeBlockEntity(a.getBlockPos());
            var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(a.getBlockPos(), a.getBlockState(), tag,
                helper.getLevel().registryAccess());
            helper.assertTrue(restored instanceof PhysicalHoseBlockEntity, "Saved endpoint type changed");
            helper.getLevel().setBlockEntity(restored);
        });
        helper.runAtTickTime(25, () -> {
            var a = (PhysicalHoseBlockEntity) helper.getBlockEntity(aPos);
            helper.assertTrue(a.attached() && b.attached() && a.getPairedHose() == b, "Reload lost endpoint identities");
            helper.assertTrue(a.saveWithoutMetadata(helper.getLevel().registryAccess()).contains("Strand"), "Reload lost native strand");
            var state = a.getBlockState();
            var saved = a.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.setBlock(movedPos, state);
            var moved = (PhysicalHoseBlockEntity) helper.getBlockEntity(movedPos);
            moved.loadWithComponents(saved, helper.getLevel().registryAccess());
            try {
                // Exercise the same callback used by native structure assembly without importing optional classes.
                state.getBlock().getClass().getMethod("afterMove", net.minecraft.server.level.ServerLevel.class,
                    net.minecraft.server.level.ServerLevel.class, net.minecraft.world.level.block.state.BlockState.class,
                    BlockPos.class, BlockPos.class).invoke(state.getBlock(), helper.getLevel(), helper.getLevel(), state,
                        a.getBlockPos(), moved.getBlockPos());
            } catch (ReflectiveOperationException error) { throw new AssertionError("Native assembly callback failed", error); }
            helper.setBlock(aPos, net.minecraft.world.level.block.Blocks.AIR);
            helper.assertTrue(moved.getPairedHose() == b && b.getPairedHose() == moved, "Assembly left stale fluid positions");
        });
        helper.runAtTickTime(50, () -> {
            var moved = (PhysicalHoseBlockEntity) helper.getBlockEntity(movedPos);
            helper.assertTrue(moved.attached() && b.attached(), "Assembly destroyed native strand");
            b.disconnectPhysical();
            helper.assertTrue(!moved.attached() && !b.attached(), "Restored strand cannot be cut");
            helper.succeed();
        });
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 240)
    public static void physicalHoseLifecycle(GameTestHelper helper) {
        var aPos = new BlockPos(2, 2, 1); var bPos = new BlockPos(7, 2, 1);
        helper.setBlock(aPos, PhysicalHoses.MEDIUM.get().defaultBlockState().setValue(FireHoseBlock.FACING, Direction.EAST));
        helper.setBlock(bPos, PhysicalHoses.MEDIUM.get().defaultBlockState().setValue(FireHoseBlock.FACING, Direction.WEST));
        var a = (PhysicalHoseBlockEntity) helper.getBlockEntity(aPos);
        var b = (PhysicalHoseBlockEntity) helper.getBlockEntity(bPos);
        if (!PhysicalHoses.enabled()) {
            CompoundTag tag = a.saveWithoutMetadata(helper.getLevel().registryAccess());
            tag.putBoolean("OwnStrand", true); tag.putUUID("HasRopeAttached", java.util.UUID.randomUUID());
            CompoundTag strand = new CompoundTag(); strand.putString("future", "retain"); tag.put("Strand", strand);
            a.loadWithComponents(tag, helper.getLevel().registryAccess());
            helper.assertTrue(a.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("Strand").equals(strand), "Disabled strand data lost");
            helper.assertTrue(!a.connectPhysical(b), "Disabled endpoint connected");
            helper.succeed(); return;
        }
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(a.connectPhysical(b), "Native hose failed to connect");
            helper.assertTrue(a.attached() && b.attached(), "Attachment missing");
            helper.assertTrue(a.getPairedHose() == b && b.getPairedHose() == a, "Fluid identities disagree");
            helper.assertTrue(!a.connectPhysical(b), "Occupied connection replaced");
            helper.assertTrue(FireHoseConnections.tryConnect(a, b) != FireHoseConnections.Result.SUCCESS, "Ordinary hose hijacked physics");
            CompoundTag saved = a.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(saved.contains("Strand"), "Native physics not saved");
        });
        helper.runAtTickTime(30, () -> {
            b.disconnectPhysical();
            helper.assertTrue(!a.attached() && !b.attached(), "Cut from remote end left a native strand");
            helper.assertTrue(a.getFireHosePartnerPos() == null && b.getFireHosePartnerPos() == null, "Fluid link survived cut");
            helper.assertTrue(a.connectPhysical(b), "Cannot reconnect after cutting");
            helper.setBlock(bPos, net.minecraft.world.level.block.Blocks.AIR);
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(!a.attached() && a.getFireHosePartnerPos() == null, "Removed endpoint left a link");
            helper.succeed();
        });
    }

    @GameTest(template = "fluid_transport_empty", timeoutTicks = 240)
    public static void physicalHosePumping(GameTestHelper helper) {
        if (!PhysicalHoses.enabled()) { helper.succeed(); return; }
        var source = new BlockPos(1, 2, 1); var aPos = new BlockPos(2, 2, 1);
        var bPos = new BlockPos(7, 2, 1); var pumpPos = new BlockPos(8, 2, 1); var target = new BlockPos(9, 2, 1);
        helper.setBlock(source, AllBlocks.FLUID_TANK.getDefaultState());
        helper.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState());
        helper.setBlock(aPos, PhysicalHoses.LARGE.get().defaultBlockState().setValue(FireHoseBlock.FACING, Direction.EAST));
        helper.setBlock(bPos, PhysicalHoses.LARGE.get().defaultBlockState().setValue(FireHoseBlock.FACING, Direction.WEST));
        helper.setBlock(pumpPos, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        var a = (PhysicalHoseBlockEntity) helper.getBlockEntity(aPos);
        var b = (PhysicalHoseBlockEntity) helper.getBlockEntity(bPos);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(a.connectPhysical(b), "Pump hose could not connect");
            ((FluidTankBlockEntity) helper.getBlockEntity(source)).getTankInventory().fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
            var pump = (PumpBlockEntity) helper.getBlockEntity(pumpPos);
            pump.setSpeed(64); pump.onSpeedChanged(0);
        });
        helper.runAtTickTime(180, () -> {
            int remaining = ((FluidTankBlockEntity) helper.getBlockEntity(source)).getTankInventory().getFluidAmount();
            int received = ((FluidTankBlockEntity) helper.getBlockEntity(target)).getTankInventory().getFluidAmount();
            int buffered = ((FireHoseBlockEntity) a).getSharedTankRawAmount();
            helper.assertTrue(received > 0, "Physical hose did not pump: source=" + remaining + ", buffer=" + buffered);
            helper.assertTrue(remaining + received + buffered == 4000, "Physical hose fluid not conserved");
            a.disconnectPhysical();
            helper.succeed();
        });
    }
}
