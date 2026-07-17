package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mikoalopex.createfirefightingadd.ClientConfig;
import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayFluidType;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayHitContext;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayInteractionRegistry;
import com.mikoalopex.createfirefightingadd.content.contraptions.ContraptionFluidAccess;
import com.mikoalopex.createfirefightingadd.integration.burnt.BurntCompat;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.depot.storage.DepotMountedStorage;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class SprayDeviceMovementBehaviour implements MovementBehaviour {
	public static final SprayDeviceMovementBehaviour INSTANCE = new SprayDeviceMovementBehaviour();

	private static final String TICK_COUNTER = "SprayTickCounter";
	private static final String IGNITED = "SprayIgnited";
	private static final String LAST_SERVER_TICK = "SprayLastServerTick";
	private static final String LAST_STATE_SYNC = "SprayLastStateSync";
	private static final String LAST_SYNCED_BEHAVIOR = "SprayLastSyncedBehavior";
	private static final String LAST_SYNCED_IGNITED = "SprayLastSyncedIgnited";
	private static final int STATE_SYNC_INTERVAL = 20;
	private static final int CLIENT_STATE_TTL = 28;
	private static final int CLIENT_PROJECTILE_CAP_PER_SPRAYER = 128;
	private static final int NOZZLE_MOVEMENT_SCAN_INTERVAL = 1;
	private static final double EFFECT_RAY_STEP = 0.75;
	private static final int NOZZLE_CONSUMPTION = 10;
	private static final Vector3f WATER = new Vector3f(0.3f, 0.55f, 1.0f);
	private static final Vector3f WATER_LIGHT = new Vector3f(0.6f, 0.8f, 1.0f);
	private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
	private static final Vector3f LAVA = new Vector3f(1.0f, 0.28f, 0.05f);
	private static final Vector3f LAVA_LIGHT = new Vector3f(1.0f, 0.65f, 0.1f);
	private static final Vector3f DRAGON = new Vector3f(0.62f, 0.25f, 1.0f);
	private static final Vector3f DRAGON_LIGHT = new Vector3f(1.0f, 0.42f, 0.88f);
	private static final Map<SprayKey, List<LightweightProjectile>> CLIENT_PROJECTILES =
		Collections.synchronizedMap(new HashMap<>());
	private static final Map<SprayKey, ClientSprayState> CLIENT_STATES =
		Collections.synchronizedMap(new HashMap<>());
	private static final Map<Level, Map<DepotKey, ProcessingProgress>> DEPOT_PROCESSING =
		Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Level, Map<MovingDepotKey, ProcessingProgress>> MOVING_DEPOT_PROCESSING =
		Collections.synchronizedMap(new WeakHashMap<>());

	@Override
	public void tick(MovementContext context) {
		if (context.position == null)
			return;
		SprayProfile profile = profile(context);
		if (profile == null) {
			clearClientProjectiles(context);
			stopSpraySound(context, context.position);
			return;
		}

		Vec3 direction = rotatedDirection(context, profile.localDirection()).normalize();
		Vec3 origin = context.position.add(direction.scale(0.6));
		if (!context.world.isClientSide && alreadyHandledServerTick(context))
			return;
		int tick = context.data.getInt(TICK_COUNTER) + 1;
		context.data.putInt(TICK_COUNTER, tick);

		if (context.world.isClientSide) {
			ClientSprayState state = clientState(context);
			if (state != null) {
				if (profile.mode() == ParticleMode.BUCKET) {
					spawnBucketParticles(context.world, origin, direction, profile, state.behavior(), FluidStack.EMPTY, tick);
				} else {
					SprayDebugRenderer.submitDynamicSpray(SprayKey.of(context).hashCode(), origin, direction,
						profile.mode() == ParticleMode.CONE, profile.range(), profile.projectileSpeed(),
						profile.projectileGravity(), profile.projectileFriction(), projectileLifetime(profile),
						context.world.getGameTime() + 2);
					spawnNozzleProjectileParticles(context, origin, direction, profile, state);
				}
				return;
			}

			IFluidHandler clientStorage = sprayFluidStorage(context);
			if (clientStorage == null || clientStorage.getTanks() == 0)
				return;
			FluidStack clientFluid = selectSprayFluid(context.world, clientStorage, profile);
			AbstractSprayDeviceBlockEntity.FluidBehavior clientBehavior =
				AbstractSprayDeviceBlockEntity.classifyFluidForSpray(context.world, clientFluid);
			if (clientBehavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED
				|| clientFluid.getAmount() < profile.consumption()) {
				clearClientProjectiles(context);
				return;
			}
			if (profile.mode() == ParticleMode.BUCKET)
				spawnBucketParticles(context.world, origin, direction, profile, clientBehavior, clientFluid, tick);
			else {
				SprayDebugRenderer.submitDynamicSpray(SprayKey.of(context).hashCode(), origin, direction,
					profile.mode() == ParticleMode.CONE, profile.range(), profile.projectileSpeed(),
					profile.projectileGravity(), profile.projectileFriction(), projectileLifetime(profile),
					context.world.getGameTime() + 2);
				spawnNozzleProjectileParticles(context, origin, direction, profile,
					ClientSprayState.fromFluid(context, clientBehavior, clientFluid));
			}
			return;
		}

		IFluidHandler storage = sprayFluidStorage(context);
		if (storage == null || storage.getTanks() == 0) {
			stopSpraySound(context, origin);
			return;
		}

		FluidStack fluid = selectSprayFluid(context.world, storage, profile);
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.classifyFluidForSpray(context.world, fluid);
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED) {
			stopSpraySound(context, origin);
			return;
		}
		if (fluid.getAmount() < profile.consumption()) {
			stopSpraySound(context, origin);
			return;
		}

		boolean ignited = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
			&& (context.data.getBoolean(IGNITED) || hasIgnitionSource(context.world, origin));
		if (ignited)
			context.data.putBoolean(IGNITED, true);

		FluidStack request = fluid.copyWithAmount(profile.consumption());
		FluidStack drained = storage.drain(request, IFluidHandler.FluidAction.SIMULATE);
		if (drained.getAmount() < profile.consumption()) {
			stopSpraySound(context, origin);
			return;
		}
		storage.drain(request, IFluidHandler.FluidAction.EXECUTE);
		syncNozzleState(context, behavior, drained, ignited);
		tickSpraySound(context, origin);

		if (tick % profile.scanInterval() != 0)
			return;

		applyBlockEffects(context.world, origin, direction, profile, behavior, fluid, ignited, tick, context.localPos);
		applyEntityAndItemEffects(context.world, origin, direction, profile, behavior, ignited, fluid);
	}

	private static SprayProfile profile(MovementContext context) {
		BlockState state = context.state;
		if (state.getBlock() instanceof ConeNozzleBlock) {
			Direction facing = state.getValue(ConeNozzleBlock.FACING);
			int range = Config.coneNozzleMaxDistance;
			return new SprayProfile(Vec3.atLowerCornerOf(facing.getNormal()), range, NOZZLE_CONSUMPTION,
				NOZZLE_MOVEMENT_SCAN_INTERVAL, new ConeSprayShape(range, 20.0), new ConeSprayShape(range, 10.0),
				ParticleMode.CONE, Config.coneNozzleSpeed, Config.coneNozzleGravity,
				Config.coneNozzleFriction, 3);
		}
		if (state.getBlock() instanceof FlatNozzleBlock) {
			Direction facing = state.getValue(FlatNozzleBlock.FACING);
			int range = Config.flatNozzleMaxDistance;
			return new SprayProfile(Vec3.atLowerCornerOf(facing.getNormal()), range, NOZZLE_CONSUMPTION,
				NOZZLE_MOVEMENT_SCAN_INTERVAL, new FanSprayShape(range, 60.0, 4.0), new FanSprayShape(range, 60.0, 4.0),
				ParticleMode.FLAT, Config.flatNozzleSpeed, Config.flatNozzleGravity,
				Config.flatNozzleFriction, 4);
		}
		if (state.getBlock() instanceof BucketControllerBlock) {
			Direction facing = state.getValue(BucketControllerBlock.FACING).getOpposite();
			int range = readBucketRange(context);
			int consumption = Math.max(1, Config.bucketWaterConsumption / 20);
			return new SprayProfile(Vec3.atLowerCornerOf(facing.getNormal()), range, consumption,
				Config.bucketScanInterval, new CylinderSprayShape(range, Config.bucketRadius),
				new CylinderSprayShape(range, Config.bucketRadius), ParticleMode.BUCKET,
				0.0, 0.0, 1.0, 0);
		}
		return null;
	}

	private static int readBucketRange(MovementContext context) {
		int value = context.blockEntityData.contains("ScrollValue")
			? context.blockEntityData.getInt("ScrollValue")
			: Config.bucketDefaultHeight;
		return Mth.clamp(value, 1, Config.bucketMaxHeight);
	}

	private static Vec3 rotatedDirection(MovementContext context, Vec3 localDirection) {
		Vec3 rotated = context.rotation.apply(localDirection);
		if (rotated.lengthSqr() < 1e-6)
			return localDirection;
		return rotated;
	}

	private static boolean alreadyHandledServerTick(MovementContext context) {
		long gameTime = context.world.getGameTime();
		if (context.data.contains(LAST_SERVER_TICK) && context.data.getLong(LAST_SERVER_TICK) == gameTime)
			return true;
		context.data.putLong(LAST_SERVER_TICK, gameTime);
		return false;
	}

	@Nullable
	private static IFluidHandler sprayFluidStorage(MovementContext context) {
		return ContraptionFluidAccess.mountedFluids(context);
	}

	private static FluidStack selectSprayFluid(Level level, IFluidHandler storage, SprayProfile profile) {
		return ContraptionFluidAccess.findDrainable(storage, profile.consumption(), stack -> {
			if (profile.mode() == ParticleMode.BUCKET)
				return stack.getFluid().is(FluidTags.WATER);
			return AbstractSprayDeviceBlockEntity.classifyFluidForSpray(level, stack)
				!= AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED;
		});
	}

	private static void applyBlockEffects(Level level, Vec3 origin, Vec3 direction, SprayProfile profile,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid,
			boolean ignited, long tick, BlockPos localPos) {
		if (profile.mode() == ParticleMode.BUCKET) {
			applyVolumeBlockEffects(level, origin, direction, profile.shape(), behavior, fluid, ignited);
			return;
		}

		long seed = tick * 31L + (localPos == null ? 0L : localPos.asLong());
		int rays = profile.mode() == ParticleMode.FLAT ? 5 : 4;
		SprayEffectSampler.traceRays(level, origin, direction, profile.projectileShape(), profile.range(),
			rays, tick, seed, EFFECT_RAY_STEP,
			(pos, state, samplePos, rayDirection, distance) -> {
				NozzleSprayHitContext hitContext = sprayHitContext(level, pos, state, fluid, behavior, ignited,
					origin, samplePos, rayDirection, distance);
				if (canAffectBlock(hitContext, behavior, ignited, state))
					applySampleEffect(hitContext, behavior, ignited);
			});
	}

	private static void applyVolumeBlockEffects(Level level, Vec3 origin, Vec3 direction, SprayShape shape,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited) {
		Set<Long> visited = new HashSet<>();
		shape.forEachPosition(origin, direction, (pos, dist, gridU, gridV) -> {
			if (!visited.add(pos.asLong()))
				return;
			if (!level.isLoaded(pos))
				return;
			BlockState state = level.getBlockState(pos);
			NozzleSprayHitContext hitContext = sprayHitContext(level, pos, state, fluid, behavior, ignited,
				origin, Vec3.atCenterOf(pos), direction, dist);
			if (!canAffectBlock(hitContext, behavior, ignited, state))
				return;
			if (isRayBlocked(level, origin, pos))
				return;
			applySampleEffect(hitContext, behavior, ignited);
		});
	}

	private static void applySampleEffect(NozzleSprayHitContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited) {
		NozzleSprayInteractionRegistry.notifyHit(context);
		Level level = context.level();
		BlockPos pos = context.pos();
		BlockState state = context.state();
		switch (behavior) {
			case WATER -> {
				if (!SprayEffectUtils.hydrateConcretePowder(level, pos, state)
					&& !SprayEffectUtils.moistenFarmland(level, pos, state))
					extinguish(level, pos, state);
			}
			case MILK, POTION -> extinguish(level, pos, state);
			case LAVA -> tryIgnite(level, pos);
			case FLAMMABLE -> {
				if (ignited)
					tryIgnite(level, pos);
			}
			default -> {
			}
		}
	}

	private static void applyEntityAndItemEffects(Level level, Vec3 origin, Vec3 direction, SprayProfile profile,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited, FluidStack fluid) {
		if (profile.mode() == ParticleMode.BUCKET)
			return;
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline =
			computeCenterline(origin, direction, profile);
		if (centerline.size() < 2)
			return;
		AABB scanArea = centerlineAabb(centerline).inflate(4.0);
		FanProcessingType processingType = fanProcessingType(behavior, ignited);
		processDepots(level, centerline, scanArea, origin, profile, null, processingType);
		processSableSubLevelDepots(level, centerline, profile, processingType);
		processMovingContraptionDepots(level, centerline, scanArea, origin, profile, processingType);
		Set<Integer> processed = new HashSet<>();
		for (Entity entity : level.getEntities(null, scanArea)) {
			if (!processed.add(entity.getId()))
				continue;
			AbstractSprayDeviceBlockEntity.CenterlineSample sample =
				findClosestSample(entity, centerline, profile.projectileShape(), profile.range());
			if (sample == null)
				continue;
			if (isEntityBlockedByWall(level, origin, entity))
				continue;
			applyEntityEffect(level, entity, sample, profile, behavior, ignited, fluid, processingType);
		}
	}

	private static void applyEntityEffect(Level level, Entity entity,
			AbstractSprayDeviceBlockEntity.CenterlineSample sample, SprayProfile profile,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
			FluidStack fluid, FanProcessingType processingType) {
		switch (behavior) {
			case WATER -> {
				if (entity instanceof ItemEntity itemEntity)
					processItemEntity(itemEntity, processingType);
				SprayEntityEffects.applyWaterContact(level, entity);
				pushEntity(entity, sample, profile);
			}
			case LAVA -> {
				if (entity instanceof ItemEntity itemEntity) {
					processItemEntity(itemEntity, processingType);
					return;
				}
				if (entity.getRemainingFireTicks() < 100)
					entity.setRemainingFireTicks(100);
			}
			case FLAMMABLE -> {
				if (!ignited)
					return;
				if (entity instanceof ItemEntity itemEntity) {
					processItemEntity(itemEntity, processingType);
					return;
				}
				if (entity.getRemainingFireTicks() < 100)
					entity.setRemainingFireTicks(100);
			}
			case MILK -> {
				if (entity instanceof LivingEntity living)
					living.removeAllEffects();
			}
			case POTION -> {
				PotionContents contents = fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
				if (contents.equals(PotionContents.EMPTY))
					return;
				if (entity instanceof LivingEntity living) {
					for (var effect : contents.getAllEffects())
						living.addEffect(new MobEffectInstance(effect));
				}
			}
			case DRAGON_BREATH -> {
				if (entity instanceof ItemEntity itemEntity) {
					processItemEntity(itemEntity, processingType);
					return;
				}
				if (entity instanceof LivingEntity living)
					living.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1, false, false, false));
			}
			default -> {
			}
		}
	}

	private static void pushEntity(Entity entity, AbstractSprayDeviceBlockEntity.CenterlineSample sample,
			SprayProfile profile) {
		if (entity.isCrouching() || entity.getPose() == Pose.SWIMMING)
			return;
		double pushSpeed = AbstractSprayDeviceBlockEntity.PUSH_STREAM_SPEED
			* (1.0 - sample.axialDist() / Math.max(1, profile.range()));
		Vec3 direction = sample.direction();
		entity.push(direction.x * pushSpeed, direction.y * pushSpeed, direction.z * pushSpeed);
		if (entity instanceof ServerPlayer player)
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
	}

	private static void processDepots(Level level, List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline,
			AABB scanArea, Vec3 origin, SprayProfile profile, UUID subLevelId, FanProcessingType type) {
		if (type == null)
			return;
		Set<Long> seen = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(
			(int) Math.floor(scanArea.minX), (int) Math.floor(scanArea.minY), (int) Math.floor(scanArea.minZ),
			(int) Math.floor(scanArea.maxX), (int) Math.floor(scanArea.maxY), (int) Math.floor(scanArea.maxZ))) {
			BlockPos depotPos = pos.immutable();
			if (!seen.add(depotPos.asLong()))
				continue;
			if (!level.isLoaded(depotPos))
				continue;
			if (!(level.getBlockEntity(depotPos) instanceof DepotBlockEntity depot))
				continue;
			ItemStack stack = depot.getHeldItem();
			if (stack.isEmpty() || !type.canProcess(stack, level))
				continue;
			Vec3 itemPos = Vec3.atCenterOf(depotPos).add(0, 0.55, 0);
			AbstractSprayDeviceBlockEntity.CenterlineSample sample =
				findClosestSample(itemPos, centerline, profile.projectileShape(), profile.range());
			if (sample == null || isPointBlockedByWall(level, origin, itemPos))
				continue;
			DepotKey key = new DepotKey(subLevelId, depotPos);
			if (decrementDepotProcessingTime(level, key, stack, type) > 0)
				continue;
			List<ItemStack> results = type.process(stack, level);
			if (results == null || results.isEmpty()) {
				markDepotProcessingBlocked(level, key, stack, type);
				continue;
			}
			depot.setHeldItem(results.get(0).copy());
			for (int i = 1; i < results.size(); i++) {
				ItemStack extra = results.get(i);
				if (!extra.isEmpty())
					Block.popResource(level, depotPos.above(), extra.copy());
			}
			depot.notifyUpdate();
		}
	}

	private static void processSableSubLevelDepots(Level worldLevel,
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> worldCenterline, SprayProfile profile,
			FanProcessingType type) {
		if (type == null)
			return;
		List<Vec3> positions = new ArrayList<>(worldCenterline.size());
		for (AbstractSprayDeviceBlockEntity.CenterlineSample sample : worldCenterline)
			positions.add(sample.position());
		for (SableStructureCompat.SubLevelProjection projection :
				SableStructureCompat.projectWorldPositionsToSubLevels(worldLevel, positions)) {
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> localCenterline =
				rebuildProjectedCenterline(worldCenterline, projection.positions());
			if (localCenterline.size() < 2)
				continue;
			AABB scanArea = centerlineAabb(localCenterline).inflate(4.0);
			processDepots(projection.level(), localCenterline, scanArea,
				localCenterline.get(0).position(), profile, projection.id(), type);
		}
	}

	private static void processMovingContraptionDepots(Level worldLevel,
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> worldCenterline, AABB worldScanArea,
			Vec3 origin, SprayProfile profile, FanProcessingType type) {
		if (type == null || worldCenterline.size() < 2)
			return;
		for (AbstractContraptionEntity contraptionEntity :
				worldLevel.getEntitiesOfClass(AbstractContraptionEntity.class, worldScanArea.inflate(2.0))) {
			if (contraptionEntity.getContraption() == null || contraptionEntity.getContraption().getStorage() == null)
				continue;
			Map<BlockPos, MountedItemStorage> itemStorages;
			try {
				itemStorages = contraptionEntity.getContraption().getStorage().getAllItemStorages();
			} catch (IllegalStateException ignored) {
				continue;
			}
			if (itemStorages.isEmpty())
				continue;

			List<AbstractSprayDeviceBlockEntity.CenterlineSample> localCenterline =
				projectCenterlineToContraption(contraptionEntity, worldCenterline);
			if (localCenterline.size() < 2)
				continue;
			AABB localScanArea = centerlineAabb(localCenterline).inflate(4.0);

			for (Map.Entry<BlockPos, MountedItemStorage> entry : itemStorages.entrySet()) {
				if (!(entry.getValue() instanceof DepotMountedStorage depotStorage))
					continue;
				BlockPos localPos = entry.getKey().immutable();
				Vec3 localItemPos = Vec3.atCenterOf(localPos).add(0, 0.55, 0);
				if (!localScanArea.contains(localItemPos))
					continue;
				ItemStack stack = depotStorage.getItem();
				if (stack.isEmpty() || !type.canProcess(stack, worldLevel))
					continue;
				AbstractSprayDeviceBlockEntity.CenterlineSample sample =
					findClosestSample(localItemPos, localCenterline, profile.projectileShape(), profile.range());
				if (sample == null)
					continue;
				Vec3 worldItemPos = contraptionEntity.toGlobalVector(localItemPos, 1.0f);
				if (isPointBlockedByWall(worldLevel, origin, worldItemPos))
					continue;
				MovingDepotKey key = new MovingDepotKey(contraptionEntity.getId(), localPos);
				if (decrementMovingDepotProcessingTime(worldLevel, key, stack, type) > 0)
					continue;
				List<ItemStack> results = type.process(stack, worldLevel);
				if (results == null || results.isEmpty()) {
					markMovingDepotProcessingBlocked(worldLevel, key, stack, type);
					continue;
				}
				depotStorage.setItem(results.get(0).copy());
				for (int i = 1; i < results.size(); i++) {
					ItemStack extra = results.get(i);
					if (!extra.isEmpty()) {
						ItemEntity extraEntity = new ItemEntity(worldLevel, worldItemPos.x, worldItemPos.y,
							worldItemPos.z, extra.copy());
						worldLevel.addFreshEntity(extraEntity);
					}
				}
			}
		}
	}

	private static List<AbstractSprayDeviceBlockEntity.CenterlineSample> projectCenterlineToContraption(
			AbstractContraptionEntity contraptionEntity,
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> worldCenterline) {
		List<Vec3> positions = new ArrayList<>(worldCenterline.size());
		for (AbstractSprayDeviceBlockEntity.CenterlineSample sample : worldCenterline)
			positions.add(contraptionEntity.toLocalVector(sample.position(), 1.0f));
		return rebuildProjectedCenterline(worldCenterline, positions);
	}

	private static List<AbstractSprayDeviceBlockEntity.CenterlineSample> rebuildProjectedCenterline(
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> source, List<Vec3> positions) {
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> rebuilt =
			new ArrayList<>(Math.min(source.size(), positions.size()));
		int count = Math.min(source.size(), positions.size());
		for (int i = 0; i < count; i++) {
			Vec3 direction = source.get(i).direction();
			if (i + 1 < count) {
				Vec3 delta = positions.get(i + 1).subtract(positions.get(i));
				if (delta.lengthSqr() > 0.0001)
					direction = delta.normalize();
			} else if (i > 0) {
				direction = rebuilt.get(i - 1).direction();
			}
			rebuilt.add(new AbstractSprayDeviceBlockEntity.CenterlineSample(
				positions.get(i), direction, source.get(i).axialDist()));
		}
		return rebuilt;
	}

	private static List<AbstractSprayDeviceBlockEntity.CenterlineSample> computeCenterline(Vec3 origin, Vec3 direction,
			SprayProfile profile) {
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> samples = new ArrayList<>();
		Vec3 position = origin;
		Vec3 velocity = direction.scale(profile.projectileSpeed());
		Vec3 gravity = new Vec3(0, -profile.projectileGravity(), 0);
		double traveled = 0;
		int maxTicks = projectileLifetime(profile);
		samples.add(new AbstractSprayDeviceBlockEntity.CenterlineSample(position, direction, 0));
		for (int tick = 0; tick < maxTicks; tick++) {
			Vec3 previous = position;
			velocity = velocity.add(gravity).scale(profile.projectileFriction());
			position = position.add(velocity);
			traveled += position.distanceTo(previous);
			if (traveled > profile.range() || velocity.lengthSqr() < 0.0001)
				break;
			samples.add(new AbstractSprayDeviceBlockEntity.CenterlineSample(position, velocity.normalize(), traveled));
		}
		return samples;
	}

	private static AABB centerlineAabb(List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (AbstractSprayDeviceBlockEntity.CenterlineSample sample : centerline) {
			Vec3 position = sample.position();
			minX = Math.min(minX, position.x);
			minY = Math.min(minY, position.y);
			minZ = Math.min(minZ, position.z);
			maxX = Math.max(maxX, position.x);
			maxY = Math.max(maxY, position.y);
			maxZ = Math.max(maxZ, position.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static AbstractSprayDeviceBlockEntity.CenterlineSample findClosestSample(Entity entity,
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline, SprayShape shape, int range) {
		AABB aabb = entity.getBoundingBox();
		double cx = (aabb.minX + aabb.maxX) / 2.0;
		double cy = (aabb.minY + aabb.maxY) / 2.0;
		double cz = (aabb.minZ + aabb.maxZ) / 2.0;
		Vec3[] points = {
			new Vec3(cx, cy, cz), new Vec3(aabb.minX, cy, cz), new Vec3(aabb.maxX, cy, cz),
			new Vec3(cx, aabb.minY, cz), new Vec3(cx, aabb.maxY, cz),
			new Vec3(cx, cy, aabb.minZ), new Vec3(cx, cy, aabb.maxZ)
		};
		AbstractSprayDeviceBlockEntity.CenterlineSample best = null;
		double bestScore = Double.MAX_VALUE;
		for (Vec3 point : points) {
			AbstractSprayDeviceBlockEntity.CenterlineSample sample = findClosestSample(point, centerline, shape, range);
			if (sample == null)
				continue;
			double score = point.distanceToSqr(sample.position());
			if (score < bestScore) {
				bestScore = score;
				best = sample;
			}
		}
		return best;
	}

	private static AbstractSprayDeviceBlockEntity.CenterlineSample findClosestSample(Vec3 point,
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline, SprayShape shape, int range) {
		AbstractSprayDeviceBlockEntity.CenterlineSample best = null;
		double bestScore = Double.MAX_VALUE;
		for (AbstractSprayDeviceBlockEntity.CenterlineSample sample : centerline) {
			if (sample.axialDist() <= 0 || sample.axialDist() > range)
				continue;
			if (!shape.containsPoint(point, sample.position(), sample.direction(), sample.axialDist()))
				continue;
			double score = point.distanceToSqr(sample.position());
			if (score < bestScore) {
				bestScore = score;
				best = sample;
			}
		}
		return best;
	}

	private static boolean isEntityBlockedByWall(Level level, Vec3 origin, Entity entity) {
		Vec3 target = entity.getEyePosition();
		double dist = origin.distanceTo(target);
		if (dist < 0.5)
			return false;
		BlockHitResult hit = level.clip(new ClipContext(
			origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.MISS)
			return false;
		return hit.getLocation().distanceToSqr(origin) < (dist - 0.3) * (dist - 0.3);
	}

	private static boolean isPointBlockedByWall(Level level, Vec3 origin, Vec3 target) {
		double dist = origin.distanceTo(target);
		if (dist < 0.5)
			return false;
		BlockHitResult hit = level.clip(new ClipContext(
			origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.MISS)
			return false;
		return hit.getLocation().distanceToSqr(origin) < (dist - 0.3) * (dist - 0.3);
	}

	private static FanProcessingType fanProcessingType(AbstractSprayDeviceBlockEntity.FluidBehavior behavior,
			boolean ignited) {
		return switch (behavior) {
			case WATER -> AllFanProcessingTypes.SPLASHING;
			case LAVA -> AllFanProcessingTypes.BLASTING;
			case FLAMMABLE -> ignited ? AllFanProcessingTypes.BLASTING : null;
			case DRAGON_BREATH -> FanProcessingType.parse("create_dragons_plus:ending");
			default -> null;
		};
	}

	private static void processItemEntity(ItemEntity entity, FanProcessingType type) {
		if (type == null || entity.isRemoved())
			return;
		Level level = entity.level();
		if (!type.canProcess(entity.getItem(), level))
			return;
		if (decrementEntityProcessingTime(entity, type) > 0)
			return;
		ItemStack original = entity.getItem();
		List<ItemStack> results = type.process(original, level);
		if (results == null || results.isEmpty()) {
			markEntityProcessingBlocked(entity, type);
			return;
		}
		entity.setItem(results.get(0).copy());
		for (int i = 1; i < results.size(); i++) {
			ItemStack extra = results.get(i);
			if (extra.isEmpty())
				continue;
			ItemEntity extraEntity = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), extra.copy());
			extraEntity.setDeltaMovement(entity.getDeltaMovement());
			level.addFreshEntity(extraEntity);
		}
	}

	private static int decrementEntityProcessingTime(ItemEntity entity, FanProcessingType type) {
		CompoundTag processing = getNozzleProcessingTag(entity);
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		String typeName = typeId == null ? "" : typeId.toString();
		if (!typeName.equals(processing.getString("Type"))
			|| !ItemStack.matches(entity.getItem(), readProcessingStack(entity, processing))) {
			processing.putString("Type", typeName);
			processing.put("Stack", entity.getItem().saveOptional(entity.registryAccess()));
			processing.putInt("Time", processingTimeFor(entity.getItem()));
		}
		int time = processing.getInt("Time");
		if (time < 0)
			return time;
		processing.putInt("Time", --time);
		return time;
	}

	private static void markEntityProcessingBlocked(ItemEntity entity, FanProcessingType type) {
		CompoundTag processing = getNozzleProcessingTag(entity);
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		processing.putString("Type", typeId == null ? "" : typeId.toString());
		processing.put("Stack", entity.getItem().saveOptional(entity.registryAccess()));
		processing.putInt("Time", -1);
	}

	private static CompoundTag getNozzleProcessingTag(ItemEntity entity) {
		CompoundTag data = entity.getPersistentData();
		if (!data.contains("CreateFireFightingAdd"))
			data.put("CreateFireFightingAdd", new CompoundTag());
		CompoundTag modData = data.getCompound("CreateFireFightingAdd");
		if (!modData.contains("NozzleProcessing"))
			modData.put("NozzleProcessing", new CompoundTag());
		return modData.getCompound("NozzleProcessing");
	}

	private static ItemStack readProcessingStack(ItemEntity entity, CompoundTag processing) {
		if (!processing.contains("Stack"))
			return ItemStack.EMPTY;
		return ItemStack.parseOptional(entity.registryAccess(), processing.getCompound("Stack"));
	}

	private static int decrementDepotProcessingTime(Level level, DepotKey key, ItemStack stack, FanProcessingType type) {
		Map<DepotKey, ProcessingProgress> progressMap = DEPOT_PROCESSING.computeIfAbsent(level, unused -> new HashMap<>());
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		ProcessingProgress progress = progressMap.get(key);
		if (progress == null || !typeId.equals(progress.type()) || !ItemStack.matches(stack, progress.stack()))
			progress = new ProcessingProgress(typeId, stack.copy(), processingTimeFor(stack));
		int time = progress.time();
		if (time < 0)
			return time;
		time--;
		progressMap.put(key, new ProcessingProgress(typeId, stack.copy(), time));
		return time;
	}

	private static void markDepotProcessingBlocked(Level level, DepotKey key, ItemStack stack, FanProcessingType type) {
		Map<DepotKey, ProcessingProgress> progressMap = DEPOT_PROCESSING.computeIfAbsent(level, unused -> new HashMap<>());
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		progressMap.put(key, new ProcessingProgress(typeId, stack.copy(), -1));
	}

	private static int processingTimeFor(ItemStack stack) {
		float timeModifierForStackSize = (float) (1 + (stack.getCount() - 1) / 64.0);
		return (int) (AllConfigs.server().kinetics.fanProcessingTime.get() * timeModifierForStackSize) + 1;
	}

	private static int decrementMovingDepotProcessingTime(Level level, MovingDepotKey key, ItemStack stack,
			FanProcessingType type) {
		Map<MovingDepotKey, ProcessingProgress> progressMap =
			MOVING_DEPOT_PROCESSING.computeIfAbsent(level, unused -> new HashMap<>());
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		ProcessingProgress progress = progressMap.get(key);
		if (progress == null || !typeId.equals(progress.type()) || !ItemStack.matches(stack, progress.stack()))
			progress = new ProcessingProgress(typeId, stack.copy(), processingTimeFor(stack));
		int time = progress.time();
		if (time < 0)
			return time;
		time--;
		progressMap.put(key, new ProcessingProgress(typeId, stack.copy(), time));
		return time;
	}

	private static void markMovingDepotProcessingBlocked(Level level, MovingDepotKey key, ItemStack stack,
			FanProcessingType type) {
		Map<MovingDepotKey, ProcessingProgress> progressMap =
			MOVING_DEPOT_PROCESSING.computeIfAbsent(level, unused -> new HashMap<>());
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		progressMap.put(key, new ProcessingProgress(typeId, stack.copy(), -1));
	}

	private record ProcessingProgress(ResourceLocation type, ItemStack stack, int time) {
	}

	private record DepotKey(UUID subLevelId, BlockPos pos) {
	}

	private record MovingDepotKey(int entityId, BlockPos localPos) {
	}

	private static boolean canAffectBlock(NozzleSprayHitContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior,
			boolean ignited, BlockState state) {
		if (NozzleSprayInteractionRegistry.shouldNotify(context))
			return true;
		return switch (behavior) {
			case WATER -> state.getBlock() instanceof BaseFireBlock
				|| (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))
				|| BurntCompat.mightHandle(state)
				|| SprayEffectUtils.isConcretePowder(state)
				|| SprayEffectUtils.isDryFarmland(state);
			case MILK, POTION -> state.getBlock() instanceof BaseFireBlock
				|| (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))
				|| BurntCompat.mightHandle(state);
			case LAVA -> state.isAir();
			case FLAMMABLE -> ignited && state.isAir();
			case DRAGON_BREATH, UNSUPPORTED -> false;
		};
	}

	private static NozzleSprayHitContext sprayHitContext(Level level, BlockPos pos, BlockState state,
			FluidStack fluid, AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
			@Nullable Vec3 origin, @Nullable Vec3 hitLocation, @Nullable Vec3 direction, double distance) {
		return new NozzleSprayHitContext(level, pos, state, fluid.copy(),
			apiFluidType(behavior), ignited, origin, hitLocation, direction, distance);
	}

	private static NozzleSprayFluidType apiFluidType(AbstractSprayDeviceBlockEntity.FluidBehavior behavior) {
		return switch (behavior) {
			case WATER -> NozzleSprayFluidType.WATER;
			case LAVA -> NozzleSprayFluidType.LAVA;
			case MILK -> NozzleSprayFluidType.MILK;
			case POTION -> NozzleSprayFluidType.POTION;
			case DRAGON_BREATH -> NozzleSprayFluidType.DRAGON_BREATH;
			case FLAMMABLE -> NozzleSprayFluidType.FLAMMABLE;
			case UNSUPPORTED -> NozzleSprayFluidType.UNSUPPORTED;
		};
	}

	private static boolean isRayBlocked(Level level, Vec3 origin, BlockPos targetPos) {
		BlockHitResult hit = level.clip(new ClipContext(
			origin,
			Vec3.atCenterOf(targetPos),
			ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		return hit.getType() != HitResult.Type.MISS && !targetPos.equals(hit.getBlockPos());
	}

	private static void extinguish(Level level, BlockPos pos, BlockState state) {
		if (AbstractSprayDeviceBlockEntity.tryTfcDouse(level, pos))
			return;
		if (BurntCompat.extinguishAt(level, pos, state)) {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
			return;
		}
		if (state.getBlock() instanceof BaseFireBlock) {
			level.removeBlock(pos, false);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
			AbstractSprayDeviceBlockEntity.clearWildfireHeat(level, pos);
			return;
		}
		if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
			level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
	}

	private static void tryIgnite(Level level, BlockPos pos) {
		if (level.random.nextDouble() >= Config.nozzleIgnitionChance / 100.0)
			return;
		if (!level.getBlockState(pos).isAir())
			return;
		BlockState fire = Blocks.FIRE.defaultBlockState();
		if (fire.canSurvive(level, pos))
			level.setBlock(pos, fire, 3);
	}

	private static boolean hasIgnitionSource(Level level, Vec3 origin) {
		BlockPos center = BlockPos.containing(origin);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BaseFireBlock)
				return true;
			if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))
				return true;
		}
		return false;
	}

	private static void clearClientProjectiles(MovementContext context) {
		if (context.world != null && context.world.isClientSide)
			CLIENT_PROJECTILES.remove(SprayKey.of(context));
	}

	private static void spawnNozzleProjectileParticles(MovementContext context, Vec3 origin, Vec3 direction,
			SprayProfile profile, ClientSprayState state) {
		SprayKey key = SprayKey.of(context);
		List<LightweightProjectile> projectiles =
			CLIENT_PROJECTILES.computeIfAbsent(key, unused -> new ArrayList<>());
		int lifetime = projectileLifetime(profile);
		int projectilesPerTick = Math.max(1, Config.serverProjectilesPerTick);
		SprayProjectileVisuals.spawnProjectiles(projectiles, origin, direction, profile.projectileShape(),
			projectilesPerTick, lifetime, profile.projectileSpeed(), state.behavior(), state.ignited(),
			new Vec3(0, -profile.projectileGravity(), 0), profile.projectileFriction(), context.world.random);
		if (projectiles.size() > CLIENT_PROJECTILE_CAP_PER_SPRAYER)
			projectiles.subList(0, projectiles.size() - CLIENT_PROJECTILE_CAP_PER_SPRAYER).clear();

		SprayProjectileVisuals.tickClientProjectiles(context.world, projectiles, state.behavior(),
			state.fuelPath(), state.potionColor(), profile.trailParticlesPerTick(), pos -> pos);
	}

	static void handleClientSprayState(ContraptionSprayStatePacket packet, long gameTime) {
		cleanupClientStates(gameTime);
		SprayKey key = new SprayKey(packet.entityId(), packet.localPos());
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior = packet.behavior();
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED) {
			CLIENT_STATES.remove(key);
			CLIENT_PROJECTILES.remove(key);
			return;
		}
		CLIENT_STATES.put(key, new ClientSprayState(behavior, packet.ignited(), packet.fuelPath(),
			new Vector3f(packet.potionR(), packet.potionG(), packet.potionB()), gameTime + CLIENT_STATE_TTL));
	}

	private static ClientSprayState clientState(MovementContext context) {
		SprayKey key = SprayKey.of(context);
		ClientSprayState state = CLIENT_STATES.get(key);
		if (state == null)
			return null;
		long gameTime = context.world.getGameTime();
		if (gameTime > state.expiresAt()) {
			CLIENT_STATES.remove(key);
			CLIENT_PROJECTILES.remove(key);
			return null;
		}
		return state;
	}

	private static void tickSpraySound(MovementContext context, Vec3 origin) {
		String key = spraySoundKey(context);
		if (key != null)
			NozzleSpraySounds.tick(context.world, key, origin, SoundSource.BLOCKS);
	}

	private static void stopSpraySound(MovementContext context, Vec3 origin) {
		String key = spraySoundKey(context);
		if (key != null)
			NozzleSpraySounds.stop(context.world, key, origin, SoundSource.BLOCKS);
	}

	@Nullable
	private static String spraySoundKey(MovementContext context) {
		if (context == null || context.localPos == null || context.contraption == null || context.contraption.entity == null)
			return null;
		return NozzleSpraySounds.contraptionKey(context.contraption.entity.getId(), context.localPos);
	}

	private static void syncNozzleState(MovementContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited) {
		if (context.contraption == null || context.contraption.entity == null || context.localPos == null)
			return;
		long gameTime = context.world.getGameTime();
		int behaviorOrdinal = behavior.ordinal();
		boolean firstSync = !context.data.contains(LAST_STATE_SYNC);
		boolean changed = firstSync
			|| context.data.getInt(LAST_SYNCED_BEHAVIOR) != behaviorOrdinal
			|| context.data.getBoolean(LAST_SYNCED_IGNITED) != ignited;
		if (!changed && gameTime - context.data.getLong(LAST_STATE_SYNC) < STATE_SYNC_INTERVAL)
			return;

		context.data.putLong(LAST_STATE_SYNC, gameTime);
		context.data.putInt(LAST_SYNCED_BEHAVIOR, behaviorOrdinal);
		context.data.putBoolean(LAST_SYNCED_IGNITED, ignited);

		Vector3f potionColor = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.POTION
			? SprayProjectileVisuals.potionColor(fluid)
			: WHITE;
		String fuelPath = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
			? SprayProjectileVisuals.fuelPath(fluid)
			: "";
		if (fuelPath.length() > 256)
			fuelPath = fuelPath.substring(0, 256);
		PacketDistributor.sendToPlayersTrackingEntity(context.contraption.entity,
			new ContraptionSprayStatePacket(context.contraption.entity.getId(), context.localPos,
				behaviorOrdinal, ignited, fuelPath, potionColor.x, potionColor.y, potionColor.z));
	}

	private static void cleanupClientStates(long gameTime) {
		if (gameTime % 20 != 0)
			return;
		CLIENT_STATES.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAt());
		CLIENT_PROJECTILES.keySet().removeIf(key -> !CLIENT_STATES.containsKey(key));
	}

	private static int projectileLifetime(SprayProfile profile) {
		double f = profile.projectileFriction();
		double v = profile.projectileSpeed();
		double d = Math.min(profile.range(), v / (1.0 - f));
		double target = 1.0 - d * (1.0 - f) / v;
		if (target <= 0)
			target = 0.001;
		int ticks = (int) (Math.log(target) / Math.log(f));
		return Math.max(20, ticks);
	}

	private static void spawnBucketParticles(Level level, Vec3 origin, Vec3 direction, SprayProfile profile,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, int tick) {
		RandomSource random = level.random;
		int count = 48;
		for (int i = 0; i < count; i++) {
			if (random.nextDouble() >= ClientConfig.particleDensity)
				continue;
			Vec3[] perps = perpendiculars(direction);
			double dist = profile.range() * random.nextDouble();
			double radius = 1.5 + (dist / Math.max(1, profile.range())) * Config.bucketRadius;
			double angle = random.nextDouble() * Math.PI * 2.0;
			double off = random.nextDouble() * radius;
			Vec3 pos = origin.add(direction.scale(dist))
				.add(perps[0].scale(Math.cos(angle) * off))
				.add(perps[1].scale(Math.sin(angle) * off));
			Vec3 vel = direction.scale(-1.5 - random.nextDouble() * 4.0);

			Vector3f color = colorFor(behavior, fluid, random, tick);
			level.addParticle(new DustParticleOptions(color, 1.5f + random.nextFloat() * 1.5f),
				pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
		}
	}

	private static Vector3f colorFor(AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid,
			RandomSource random, int tick) {
		return switch (behavior) {
			case LAVA, FLAMMABLE -> random.nextBoolean() ? LAVA : LAVA_LIGHT;
			case MILK -> WHITE;
			case POTION -> {
				PotionContents contents = fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
				int rgb = contents.getColor();
				yield new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
			}
			case DRAGON_BREATH -> (tick + random.nextInt(3)) % 2 == 0 ? DRAGON : DRAGON_LIGHT;
			default -> random.nextBoolean() ? WATER : WATER_LIGHT;
		};
	}

	private static Vec3[] perpendiculars(Vec3 facing) {
		Vec3 ref = Math.abs(facing.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 perp1 = facing.cross(ref).normalize();
		Vec3 perp2 = facing.cross(perp1).normalize();
		return new Vec3[] { perp1, perp2 };
	}

	private enum ParticleMode {
		CONE, FLAT, BUCKET
	}

	private record SprayKey(int entityId, BlockPos localPos) {
		static SprayKey of(MovementContext context) {
			int entityId = context.contraption != null && context.contraption.entity != null
				? context.contraption.entity.getId()
				: 0;
			return new SprayKey(entityId, context.localPos);
		}
	}

	private record ClientSprayState(AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
									String fuelPath, Vector3f potionColor, long expiresAt) {
		static ClientSprayState fromFluid(MovementContext context,
				AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid) {
			String fuelPath = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
				? SprayProjectileVisuals.fuelPath(fluid)
				: "";
			Vector3f potionColor = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.POTION
				? SprayProjectileVisuals.potionColor(fluid)
				: WHITE;
			boolean ignited = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
				&& context.data.getBoolean(IGNITED);
			return new ClientSprayState(behavior, ignited, fuelPath, potionColor,
				context.world.getGameTime() + 2);
		}
	}

	private record SprayProfile(Vec3 localDirection, int range, int consumption, int scanInterval,
								SprayShape shape, SprayShape projectileShape, ParticleMode mode,
								double projectileSpeed, double projectileGravity,
								double projectileFriction, int trailParticlesPerTick) {
	}
}
