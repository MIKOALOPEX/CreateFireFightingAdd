package com.createfireworkadd.createfirefightingadd.content.fluids.nozzle;

import com.createfireworkadd.createfirefightingadd.Config;
import com.createfireworkadd.createfirefightingadd.ClientConfig;
import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.createfireworkadd.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;

import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import net.neoforged.neoforge.capabilities.Capabilities;

import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import net.minecraft.world.entity.Entity;

public class BucketControllerBlockEntity extends AbstractSprayDeviceBlockEntity {

	private static final int TANK_CAPACITY = 64000;
	private static final String TAG_BOUND_INTAKE_POS = "BoundIntakePos";

	private final CylinderSprayShape sprayShape;
	private ScrollValueBehaviour rangeScroll;
	private static final int EXTINGUISH_SOUND_COOLDOWN = 5;
	private int ticksSinceLastExtinguishSound = 100;
	private BlockPos boundIntakePos;

	public BucketControllerBlockEntity(BlockPos pos, BlockState state) {
		this(Createfirefightingadd.BUCKET_CONTROLLER_BE.get(), pos, state);
	}

	public BucketControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		int initialRange = Math.min(Config.bucketDefaultHeight, Config.bucketMaxHeight);
		this.sprayShape = new CylinderSprayShape(initialRange, Config.bucketRadius);
		rangeScroll.setValue(Config.bucketDefaultHeight);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		tank.getPrimaryHandler().setValidator(stack -> stack.getFluid().is(FluidTags.WATER));

		rangeScroll = new ScrollValueBehaviour(
			Component.translatable("createfirefightingadd.bucket_controller.range"),
			this,
			new ClickValueBoxTransform()
		).between(1, 256)
		 .withCallback(v -> sprayShape.setRange(Math.min(v, Config.bucketMaxHeight)));

		behaviours.add(rangeScroll);
	}

	@Override
	protected int getEffectiveRange() {
		if (rangeScroll != null)
			return Math.min(rangeScroll.getValue(), Config.bucketMaxHeight);
		return Math.min(Config.bucketDefaultHeight, Config.bucketMaxHeight);
	}

	@Override
	protected SprayShape getSprayShape() {
		return sprayShape;
	}

	@Override
	protected int getTankCapacity() {
		return TANK_CAPACITY;
	}

	@Override
	protected int getFluidConsumptionPerTick() {
		if (level == null || !level.hasNeighborSignal(worldPosition))
			return 0;
		return Math.max(1, Config.bucketWaterConsumption / 20);
	}

	@Override
	protected Direction getFacing() {
		return getBlockState().getValue(BucketControllerBlock.FACING);
	}

	@Override
	protected Vec3 getWorldSprayOrigin() {
		Direction sprayFace = getFacing().getOpposite();
		Vec3 origin = Vec3.atCenterOf(worldPosition).relative(sprayFace, 0.6);
		var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
		if (subLevel != null)
			return subLevel.logicalPose().transformPosition(origin);
		return origin;
	}

	@Override
	protected Vec3 getWorldSprayDirection() {
		Direction sprayFace = getFacing().getOpposite();
		Vec3 direction = Vec3.atLowerCornerOf(sprayFace.getNormal());
		var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
		if (subLevel != null)
			return subLevel.logicalPose().transformNormal(direction).normalize();
		return direction;
	}

	@Override
	public IFluidHandler getFluidHandler(Direction side) {
		if (level != null && level.hasNeighborSignal(worldPosition))
			return null;
		if (side == getFacing().getOpposite() || side == Direction.UP)
			return null;
		return tank.getCapability();
	}

	@Override
	public boolean isSpraying() {
		if (level == null)
			return false;
		if (!level.hasNeighborSignal(worldPosition))
			return false;
		return super.isSpraying();
	}

	@Override
		public void tick() {
			super.tick();
			if (level == null)
				return;

			if (level.isClientSide())
				return;

			if (level.hasNeighborSignal(worldPosition)) {
				ticksSinceLastExtinguishSound++;
			} else {
				tryPullFromIntake();
				tryPullFromAdjacentTanks();
			}
		}

	@Override
	protected int getScanInterval() {
		return Config.bucketScanInterval;
	}

	@Override
	protected void waterBehavior() {
		if (!level.hasNeighborSignal(worldPosition))
			return;
		int radius = Config.bucketRadius;
		int range = getEffectiveRange();
		Vec3 origin = getWorldSprayOrigin();
		Vec3 direction = getWorldSprayDirection();
		Vec3[] perps = getPerpendicularVectors(direction);
		Vec3 perp1 = perps[0];
		Vec3 perp2 = perps[1];
		Set<BlockPos> blockedPositions = new HashSet<>();

		for (int a = -radius; a <= radius; a++) {
			for (int b = -radius; b <= radius; b++) {
				if (a * a + b * b > radius * radius)
					continue;

				for (int dist = 1; dist <= range; dist++) {
					Vec3 check = origin.add(direction.scale(dist))
						.add(perp1.scale(a))
						.add(perp2.scale(b));
					BlockPos pos = BlockPos.containing(check);

					if (blockedPositions.contains(pos))
						continue;

					BlockState state = level.getBlockState(pos);

					if (state.isCollisionShapeFullBlock(level, pos) && !canBurn(state)) {
						blockedPositions.add(pos);
						break;
					}

					extinguishBlock(pos, state);
				}
			}
		}
	}

	private void extinguishBlock(BlockPos pos, BlockState state) {
		if (tryTfcDouse(level, pos))
			return;
		if (state.getBlock() instanceof BaseFireBlock) {
			level.removeBlock(pos, false);
			if (ticksSinceLastExtinguishSound >= EXTINGUISH_SOUND_COOLDOWN) {
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
				ticksSinceLastExtinguishSound = 0;
			}
			clearWildfireHeat(level, pos);
			int r = Config.heatClearRadius;
			for (int dx = -r; dx <= r; dx++)
				for (int dy = -r; dy <= r; dy++)
					for (int dz = -r; dz <= r; dz++)
						clearWildfireHeat(level, pos.offset(dx, dy, dz));
			return;
		}
		if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
			level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
			if (ticksSinceLastExtinguishSound >= EXTINGUISH_SOUND_COOLDOWN) {
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
				ticksSinceLastExtinguishSound = 0;
			}
		}
	}

	private void tryPullFromIntake() {
		if (boundIntakePos == null)
			return;

		int space = TANK_CAPACITY - tank.getPrimaryHandler().getFluidInTank(0).getAmount();
		if (space < 250)
			return;

		var mySL = Sable.HELPER.getContaining(this);
		var worldLevel = mySL != null ? mySL.getLevel() : level;

		if (!worldLevel.isLoaded(boundIntakePos)) {
			return;
		}

		BlockEntity be = worldLevel.getBlockEntity(boundIntakePos);
		if (!(be instanceof WaterIntakeBlockEntity intake)) {
			boundIntakePos = null;
			setChanged();
			return;
		}

		double distSqr = Sable.HELPER.distanceSquaredWithSubLevels(
			worldLevel,
			worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
			boundIntakePos.getX() + 0.5, boundIntakePos.getY() + 0.5, boundIntakePos.getZ() + 0.5);
		if (distSqr > (double) Config.wirelessMaxBindDistance * Config.wirelessMaxBindDistance) {
			boundIntakePos = null;
			setChanged();
			return;
		}

		IFluidHandler intakeHandler = intake.getTankCapability();
		if (intakeHandler == null)
			return;

		FluidStack intakeFluid = intakeHandler.getFluidInTank(0);
		if (intakeFluid.isEmpty())
			return;

		int toPull = Math.min(intakeFluid.getAmount(), Math.min(space, Config.wirelessTransferSpeed));
		FluidStack drained = intakeHandler.drain(toPull, IFluidHandler.FluidAction.EXECUTE);
		if (!drained.isEmpty()) {
			tank.getPrimaryHandler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
		}
	}

	private void tryPullFromAdjacentTanks() {
		int space = TANK_CAPACITY - tank.getPrimaryHandler().getFluidInTank(0).getAmount();
		if (space < 250)
			return;

		for (Direction dir : Direction.values()) {
			if (dir == Direction.DOWN)
				continue;

			BlockPos neighborPos = worldPosition.relative(dir);
			BlockEntity be = level.getBlockEntity(neighborPos);
			if (!(be instanceof FluidTankBlockEntity))
				continue;

			IFluidHandler neighborHandler = level.getCapability(
				Capabilities.FluidHandler.BLOCK, neighborPos, dir.getOpposite());
			if (neighborHandler == null)
				continue;

			FluidStack neighborFluid = neighborHandler.getFluidInTank(0);
			if (neighborFluid.isEmpty())
				continue;

			int toPull = Math.min(neighborFluid.getAmount(), Math.min(space, Config.bucketDirectTransferSpeed));
			FluidStack drained = neighborHandler.drain(toPull, IFluidHandler.FluidAction.EXECUTE);
			if (!drained.isEmpty()) {
				tank.getPrimaryHandler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
			}
		}
	}

	public IFluidHandler getTankHandler() {
		return tank.getCapability();
	}

	public void setBoundIntake(BlockPos pos) {
		this.boundIntakePos = pos;
		notifyUpdate();
	}

	public BlockPos getBoundIntake() {
		return boundIntakePos;
	}

	@Override
	public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
		super.writeSafe(tag, registries);
		writeBoundIntake(tag);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		writeBoundIntake(tag);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		readBoundIntake(tag);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		readBoundIntake(tag);
	}

	private void writeBoundIntake(CompoundTag tag) {
		if (boundIntakePos == null)
			return;

		BlockPos pos = transformBoundPosForWrite(boundIntakePos);
		if (pos != null)
			tag.putLong(TAG_BOUND_INTAKE_POS, pos.asLong());
	}

	private void readBoundIntake(CompoundTag tag) {
		if (!tag.contains(TAG_BOUND_INTAKE_POS))
			return;

		BlockPos pos = BlockPos.of(tag.getLong(TAG_BOUND_INTAKE_POS));
		boundIntakePos = transformBoundPosForRead(pos);
	}

	private static BlockPos transformBoundPosForWrite(BlockPos pos) {
		SubLevelSchematicSerializationContext ctx = SubLevelSchematicSerializationContext.getCurrentContext();
		if (ctx == null)
			return pos;
		if (ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE)
			return ctx.getSetupTransform().apply(pos);
		if (!ctx.getBoundingBox().contains(pos.getX(), pos.getY(), pos.getZ()))
			return null;
		return ctx.getPlaceTransform().apply(pos);
	}

	private static BlockPos transformBoundPosForRead(BlockPos pos) {
		SubLevelSchematicSerializationContext ctx = SubLevelSchematicSerializationContext.getCurrentContext();
		if (ctx == null || ctx.getType() != SubLevelSchematicSerializationContext.Type.PLACE)
			return pos;
		return ctx.getPlaceTransform().apply(pos);
	}

	@Override
	protected int getMaxRange() {
		return Config.bucketMaxHeight;
	}

	@Override
	protected void forEachEntityInSpray(BiConsumer<Entity, Double> action) {
	}

	@Override
	protected void spawnClientParticles() {
		if (level == null || !level.hasNeighborSignal(worldPosition))
			return;
		Vec3 origin = getWorldSprayOrigin();
		Vec3 direction = getWorldSprayDirection();
		Vec3[] perps = getPerpendicularVectors(direction);
		Vec3 perp1 = perps[0];
		Vec3 perp2 = perps[1];
		RandomSource random = RandomSource.create();

		int range = getEffectiveRange();
		int radius = Config.bucketRadius;

		int count = 75;
		for (int i = 0; i < count; i++) {
			if (random.nextDouble() >= ClientConfig.particleDensity) continue;
			double dist = range * random.nextDouble();
			double rAtDist = 1.5 + (dist / range) * radius;
			double angle = random.nextDouble() * 2.0 * Math.PI;
			double off = random.nextDouble() * rAtDist;
			double o1 = Math.cos(angle) * off;
			double o2 = Math.sin(angle) * off;

			Vec3 pos = origin.add(direction.scale(dist))
				.add(perp1.scale(o1)).add(perp2.scale(o2));
			double speed = 1.5 + random.nextDouble() * 6.0;
			Vec3 vel = direction.scale(-speed)
				.add(perp1.scale((random.nextDouble() - 0.5) * 3.0))
				.add(perp2.scale((random.nextDouble() - 0.5) * 3.0));

			Vector3f color = pickMistColor(random);
			float size = 1.0f + random.nextFloat() * 2.0f;
			level.addParticle(new DustParticleOptions(color, size),
				pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
		}
	}

	private static Vec3[] getPerpendicularVectors(Vec3 facing) {
		Vec3 ref = Math.abs(facing.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 perp1 = facing.cross(ref).normalize();
		Vec3 perp2 = facing.cross(perp1).normalize();
		return new Vec3[] { perp1, perp2 };
	}

	private static class ClickValueBoxTransform extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 6, 16);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return !direction.getAxis().isVertical();
		}
	}
}
