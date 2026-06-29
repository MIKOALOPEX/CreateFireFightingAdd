package com.mikoalopex.createfirefightingadd.content.kinetics.turbine;

import java.util.List;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class PipelineTurbineBlockEntity extends GeneratingKineticBlockEntity {
	private static final String GENERATED_SPEED_TAG = "PipelineTurbineGeneratedSpeed";
	private static final String GENERATED_CAPACITY_TAG = "PipelineTurbineGeneratedCapacity";
	private static final float PRESSURE_EPSILON = 0.001f;

	private float generatedSpeed;
	private float generatedCapacity;
	private float lastReceivedPressure = -1;
	private boolean registered;
	private TurbineFluidTransportBehaviour fluidTransport;
	private ScrollOptionBehaviour<PipelineTurbineDirection> direction;

	public PipelineTurbineBlockEntity(BlockPos pos, BlockState state) {
		this(CreateFireFightingAdd.PIPELINE_TURBINE_BE.get(), pos, state);
	}

	public PipelineTurbineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		fluidTransport = new TurbineFluidTransportBehaviour(this);
		behaviours.add(fluidTransport);

		direction = new ScrollOptionBehaviour<>(PipelineTurbineDirection.class,
			Component.translatable("createfirefightingadd.pipeline_turbine.direction"),
			this, new DirectionValueBoxTransform());
		behaviours.add(direction);
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && !level.isClientSide)
			PipelineTurbineRegistry.process(level);
	}

	public void scanNow() {
		PipelineTurbineRegistry.submit(this);
	}

	@Override
	public void remove() {
		PipelineTurbineRegistry.unregister(this);
		super.remove();
	}

	@Override
	public float getGeneratedSpeed() {
		return generatedSpeed;
	}

	@Override
	public float calculateAddedStressCapacity() {
		lastCapacityProvided = generatedCapacity;
		return generatedCapacity;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		lastCapacityProvided = generatedCapacity;
		tag.putFloat(GENERATED_SPEED_TAG, generatedSpeed);
		tag.putFloat(GENERATED_CAPACITY_TAG, generatedCapacity);
		super.write(tag, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);

		generatedSpeed = tag.contains(GENERATED_SPEED_TAG)
			? tag.getFloat(GENERATED_SPEED_TAG)
			: getTheoreticalSpeed();
		generatedCapacity = tag.contains(GENERATED_CAPACITY_TAG)
			? tag.getFloat(GENERATED_CAPACITY_TAG)
			: readLegacyGeneratedCapacity(tag);
	}

	private float readLegacyGeneratedCapacity(CompoundTag tag) {
		if (!tag.contains("Network"))
			return 0;
		return tag.getCompound("Network")
			.getFloat("AddedCapacity");
	}

	private PipelineTurbineDirection getDirection() {
		return direction == null ? PipelineTurbineDirection.CLOCKWISE : direction.get();
	}

	private void updateFromPipePressure(float pressure) {
		pressure = Math.max(0, pressure);
		if (pressure <= 0) {
			recordReceivedPressure(0);
			PipelineTurbineRegistry.unregister(this);
			clearCoordinatorAssignment();
			return;
		}

		boolean changed = Math.abs(pressure - lastReceivedPressure) > PRESSURE_EPSILON;
		recordReceivedPressure(pressure);
		if (changed || !registered)
			PipelineTurbineRegistry.submit(this);
	}

	float readCurrentPipePressure() {
		if (fluidTransport == null)
			return Math.max(0, lastReceivedPressure);
		return fluidTransport.readPipePressure(this);
	}

	void recordReceivedPressure(float pressure) {
		lastReceivedPressure = Math.max(0, pressure);
	}

	void applyCoordinatorOutput(float sourceSpeed, float pressure, float sharedCapacity) {
		recordReceivedPressure(pressure);
		registered = pressure > 0;
		float outputSpeed = pressure > 0 ? (float) Math.floor(Math.abs(sourceSpeed)) : 0;
		float newSpeed = Math.min(outputSpeed, Config.pipelineTurbineMaxOutputSpeed) * getDirection().sign();
		float newCapacity = pressure > 0 ? Math.max(0, sharedCapacity) : 0;
		applyGeneratedOutput(newSpeed, newCapacity);
	}

	void clearCoordinatorAssignment() {
		registered = false;
		lastReceivedPressure = 0;
		applyGeneratedOutput(0, 0);
	}

	private void applyGeneratedOutput(float newSpeed, float newCapacity) {
		if (generatedSpeed == newSpeed && generatedCapacity == newCapacity)
			return;
		generatedSpeed = newSpeed;
		generatedCapacity = newCapacity;
		updateGeneratedRotation();
	}

	private static class TurbineFluidTransportBehaviour extends FluidTransportBehaviour {
		public TurbineFluidTransportBehaviour(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return state.getBlock() instanceof PipelineTurbineBlock
				&& direction.getAxis() == PipelineTurbineBlock.pipeAxis(state);
		}

		@Override
		public void tick() {
			if (blockEntity instanceof PipelineTurbineBlockEntity turbine
				&& turbine.getLevel() != null
				&& !turbine.getLevel().isClientSide
				&& interfaces != null)
				turbine.updateFromPipePressure(readPipePressure(turbine));

			super.tick();
		}

		private float readPipePressure(PipelineTurbineBlockEntity turbine) {
			float pressure = 0;
			for (Direction side : Direction.values()) {
				if (side.getAxis() != PipelineTurbineBlock.pipeAxis(turbine.getBlockState()))
					continue;
				PipeConnection connection = interfaces.get(side);
				if (connection == null)
					continue;
				Couple<Float> sidePressure = connection.getPressure();
				if (sidePressure == null)
					continue;
				pressure = Math.max(pressure, Math.max(sidePressure.getFirst(), sidePressure.getSecond()));
			}
			return pressure;
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos,
				BlockState state, Direction direction) {
			if (canHaveFlowToward(state, direction))
				return super.getRenderedRimAttachment(world, pos, state, direction);
			return AttachmentTypes.NONE;
		}
	}

	private static class DirectionValueBoxTransform extends ValueBoxTransform.Sided {
		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 15);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() == PipelineTurbineBlock.dialAxis(state);
		}
	}
}
