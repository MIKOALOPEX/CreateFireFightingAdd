package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A bracket is a visual guide, not a pipe or an additional fluid endpoint. */
public class HoseBracketBlock extends WrenchableDirectionalBlock implements IBE<HoseBracketBlockEntity>, IWrenchable {
    public static final BooleanProperty WOODEN = BooleanProperty.create("wooden");

    public HoseBracketBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(WOODEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WOODEN);
    }

    public static Quaternionf rotation(Direction face, float angle) {
        return new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
            new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ()))
            .rotateY((float) Math.toRadians(angle));
    }

    public static Vec3 rotate(Direction face, float angle, Vec3 vector) {
        Vector3f result = rotation(face, angle).transform(vector.toVector3f());
        return new Vec3(result.x, result.y, result.z);
    }

    public static ItemStack bracketItem(BlockState state) {
        return (state.getValue(WOODEN) ? AllBlocks.WOODEN_BRACKET : AllBlocks.METAL_BRACKET).asStack();
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(bracketItem(state));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return bracketItem(state);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // The Create value-settings dial owns normal wrench interaction.
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().mayBuild())
            return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            if (!context.getPlayer().isCreative())
                context.getPlayer().getInventory().placeItemBackInInventory(bracketItem(state));
            context.getLevel().destroyBlock(context.getClickedPos(), false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && !moving
            && level.getBlockEntity(pos) instanceof HoseBracketBlockEntity bracket && !bracket.assembling)
            HoseRoutes.get(level).removeBracket(level, bracket.route, bracket.nodeId);
        IBE.onRemove(state, level, pos, replacement);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        float angle = level.getBlockEntity(pos) instanceof HoseBracketBlockEntity bracket ? bracket.angle() : 0;
        Direction face = state.getValue(FACING);
        return rotatedBox(face, angle, 4, 0, 4, 12, 13, 12);
    }

    private static VoxelShape rotatedBox(Direction face, float angle, double x0, double y0, double z0,
                                         double x1, double y1, double z1) {
        AABB bounds = null;
        for (double x : new double[] {x0, x1})
            for (double y : new double[] {y0, y1})
                for (double z : new double[] {z0, z1}) {
                    Vec3 point = rotate(face, angle, new Vec3(x / 16 - 0.5, y / 16 - 0.5, z / 16 - 0.5))
                        .add(0.5, 0.5, 0.5);
                    AABB corner = new AABB(point, point);
                    bounds = bounds == null ? corner : bounds.minmax(corner);
                }
        return net.minecraft.world.phys.shapes.Shapes.create(bounds);
    }

    @Override
    public Class<HoseBracketBlockEntity> getBlockEntityClass() {
        return HoseBracketBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HoseBracketBlockEntity> getBlockEntityType() {
        return CreateFireFightingAdd.HOSE_BRACKET_BE.get();
    }
}
