package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ConeNozzleBlock extends WrenchableDirectionalBlock implements IBE<ConeNozzleBlockEntity> {

	private static final VoxelShape SHAPE_UP = box(4, 0, 4, 12, 16, 12);
	private static final VoxelShape SHAPE_DOWN = box(4, 0, 4, 12, 16, 12);
	private static final VoxelShape SHAPE_NORTH = box(4, 4, 0, 12, 12, 16);
	private static final VoxelShape SHAPE_SOUTH = box(4, 4, 0, 12, 12, 16);
	private static final VoxelShape SHAPE_EAST = box(0, 4, 4, 16, 12, 12);
	private static final VoxelShape SHAPE_WEST = box(0, 4, 4, 16, 12, 12);

	public ConeNozzleBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
		// Match Create DirectionalKineticBlock: normally face the player, sneak reverses.
		Direction look = context.getNearestLookingDirection();
		boolean reverse = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
		return defaultBlockState().setValue(FACING, reverse ? look : look.getOpposite());
	}

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && state != oldState)
            level.scheduleTick(pos, this, 1, net.minecraft.world.ticks.TickPriority.HIGH);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                        net.minecraft.util.RandomSource random) {
        NozzleFluidConnection.refresh(level, pos);
    }

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case UP -> SHAPE_UP;
			case DOWN -> SHAPE_DOWN;
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case EAST -> SHAPE_EAST;
			case WEST -> SHAPE_WEST;
		};
	}

	@Override
	public Class<ConeNozzleBlockEntity> getBlockEntityClass() {
		return ConeNozzleBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ConeNozzleBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.CONE_NOZZLE_BE.get();
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return IBE.super.newBlockEntity(pos, state);
	}
}
