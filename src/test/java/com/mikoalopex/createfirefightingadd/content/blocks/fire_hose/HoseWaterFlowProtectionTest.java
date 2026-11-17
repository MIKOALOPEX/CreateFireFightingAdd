package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.lang.reflect.Method;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reproduces the vanilla water-flow decision for every hose state without
 * opening a world. The endpoint collider is only four pixels high, so vanilla
 * cannot infer solidity from it; the forced solid property must make both
 * {@code blocksMotion()} and {@code canBeReplaced(Fluid)} reject water.
 */
@SuppressWarnings("deprecation")
public final class HoseWaterFlowProtectionTest {
    private static final Method CAN_HOLD_FLUID = method("canHoldFluid",
        BlockGetter.class, BlockPos.class, BlockState.class, Fluid.class);
    private static final Method CAN_SPREAD_TO = method("canSpreadTo",
        BlockGetter.class, BlockPos.class, BlockState.class, Direction.class, BlockPos.class,
        BlockState.class, FluidState.class, Fluid.class);

    private HoseWaterFlowProtectionTest() {
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        SharedConstants.tryDetectVersion();
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
            java.util.List.of(), java.util.List.of(), java.util.Map.of());
        Bootstrap.bootStrap();
        // The standalone check owns no mod registry, so create the test blocks
        // through the normal Block constructor while the block registry is writable.
        net.minecraft.core.MappedRegistry<Block> blockRegistry =
            (net.minecraft.core.MappedRegistry<Block>) net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        blockRegistry.unfreeze();
        FireHoseBlock fire;
        CopycatFireHoseBlock copycat;
        VulnerableHoseBlock vulnerable;
        Block[] additional;
        try {
            additional = new Block[]{new com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlock(BlockBehaviour.Properties.of())};
            for (int i=0;i<additional.length;i++) register(blockRegistry, "water_protection_additional_"+i, additional[i]);
            fire = new FireHoseBlock(BlockBehaviour.Properties.of());
            copycat = new CopycatFireHoseBlock(BlockBehaviour.Properties.of());
            vulnerable = new VulnerableHoseBlock();
            register(blockRegistry, "water_protection_fire_hose", fire);
            register(blockRegistry, "water_protection_copycat_fire_hose", copycat);
            register(blockRegistry, "water_protection_control", vulnerable);
        } finally {
            blockRegistry.freeze();
        }

        int additionalStates = 0;
        for (Block block : additional) for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            state.initCache();
            check(!state.canBeReplaced(Fluids.WATER), "additional block accepts replacement: " + state);
            check(!canHoldFluid(state), "additional block accepts flowing water: " + state);
            additionalStates++;
        }
        System.out.println("Additional water-protected block states passed: " + additionalStates);
        checkVulnerable(vulnerable.defaultBlockState());
        System.out.println("Unprotected control reproduced the water replacement path");
        checkVariants(fire, "fire hose");
        checkVariants(copycat, "copycat fire hose");
        System.out.println("Fire and copycat hose water-flow protection passed: "
            + (FireHoseSize.values().length * Direction.values().length * 2) + " states");
    }

    /**
     * Same four-pixel collider without the forced solid flag. This is the
     * negative control that reproduces the original water replacement path.
     */
    private static void checkVulnerable(BlockState state) {
        state.initCache();
        check(!state.isSolid() && !state.blocksMotion(), "control must stay non-solid");
        check(state.canBeReplaced(Fluids.WATER), "control must accept water replacement");
        check(canHoldFluid(state), "control must satisfy FlowingFluid.canHoldFluid");
        for (Direction direction : Direction.values())
            check(canSpreadTo(state, direction), "control must satisfy canSpreadTo(" + direction + ")");
    }

    private static void checkVariants(FireHoseBlock block, String name) {
        for (FireHoseSize size : FireHoseSize.values()) {
            for (Direction facing : Direction.values()) {
                BlockState state = block.defaultBlockState()
                    .setValue(FireHoseBlock.FACING, facing)
                    .setValue(FireHoseBlock.SIZE, size);
                checkState(state, name + " size=" + size.width() + " facing=" + facing, size.width());
            }
        }
    }

    private static void register(net.minecraft.core.MappedRegistry<Block> registry, String path, Block block) {
        registry.register(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createfirefightingadd_test", path)),
            block, net.minecraft.core.RegistrationInfo.BUILT_IN);
    }

    private static void checkState(BlockState state, String label, int width) {
        // Modded states receive this from NeoForge's registration callback; the
        // standalone registry never fires it, so build the same state cache here.
        state.initCache();
        check(state.isSolid(), label + " must be solid");
        check(state.blocksMotion(), label + " must block water motion");
        check(!state.canBeReplaced(Fluids.WATER), label + " must reject water replacement");
        check(state.getFluidState().isEmpty(), label + " must not become waterlogged");
        check(!canHoldFluid(state), label + " must fail FlowingFluid.canHoldFluid");
        for (Direction direction : Direction.values())
            check(!canSpreadTo(state, direction), label + " must fail canSpreadTo(" + direction + ")");

        int edge = (14 - width) / 2;
        VoxelShape expected = VoxelShaper.forDirectional(
            Block.box(edge, 0, edge, 16 - edge, 4, 16 - edge), Direction.UP)
            .get(state.getValue(FireHoseBlock.FACING));
        AABB collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
            CollisionContext.empty()).bounds();
        check(collision.equals(expected.bounds()), label + " collision shape changed: " + collision
            + " expected " + expected.bounds());
        // The tiny collider is exactly why vanilla's calculateSolid() cannot infer solidity.
        double largest = Math.max(collision.getXsize(), Math.max(collision.getYsize(), collision.getZsize()));
        double smallest = Math.min(collision.getXsize(), Math.min(collision.getYsize(), collision.getZsize()));
        check(Math.abs(smallest - 0.25) < 1e-9 && largest < 0.7291667,
            label + " collision must remain the small port collider: " + collision);
    }

    private static boolean canHoldFluid(BlockState state) {
        try {
            return (Boolean) CAN_HOLD_FLUID.invoke(Fluids.WATER, EmptyBlockGetter.INSTANCE,
                BlockPos.ZERO, state, Fluids.WATER);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not call FlowingFluid.canHoldFluid", e);
        }
    }

    private static boolean canSpreadTo(BlockState state, Direction direction) {
        BlockPos target = BlockPos.ZERO.relative(direction);
        try {
            return (Boolean) CAN_SPREAD_TO.invoke(Fluids.WATER, EmptyBlockGetter.INSTANCE,
                BlockPos.ZERO, Blocks.AIR.defaultBlockState(), direction, target, state,
                Fluids.EMPTY.defaultFluidState(), Fluids.WATER);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not call FlowingFluid.canSpreadTo", e);
        }
    }

    private static final class VulnerableHoseBlock extends Block {
        private VulnerableHoseBlock() {
            super(BlockBehaviour.Properties.of());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Block.box(3, 0, 3, 13, 4, 13);
        }
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            Method method = FlowingFluid.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Vanilla FlowingFluid." + name + " changed", e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
