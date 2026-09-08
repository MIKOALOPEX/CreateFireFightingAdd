package com.mikoalopex.createfirefightingadd.content.blocks.traffic_cone;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TrafficConeBlockEntity extends BlockEntity {
	private static final String DIRECTION_X_TAG = "DirectionX";
	private static final String DIRECTION_Z_TAG = "DirectionZ";

	private Vec3 direction = new Vec3(0, 0, 1);

	public TrafficConeBlockEntity(BlockPos pos, BlockState state) {
		super(CreateFireFightingAdd.TRAFFIC_CONE_BE.get(), pos, state);
	}

	public Vec3 getDirection() {
		return direction;
	}

	public void setDirection(Vec3 direction) {
		Vec3 normalized = normalizeHorizontal(direction);
		if (normalized.lengthSqr() < 1.0E-6)
			return;
		this.direction = normalized;
		setChanged();
		if (level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	public static Vec3 normalizeHorizontal(Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0, vector.z);
		if (horizontal.lengthSqr() < 1.0E-6)
			return Vec3.ZERO;
		return horizontal.normalize();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putDouble(DIRECTION_X_TAG, direction.x);
		tag.putDouble(DIRECTION_Z_TAG, direction.z);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		Vec3 loaded = normalizeHorizontal(new Vec3(tag.getDouble(DIRECTION_X_TAG), 0, tag.getDouble(DIRECTION_Z_TAG)));
		direction = loaded.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : loaded;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveAdditional(tag, registries);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		loadAdditional(tag, registries);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
