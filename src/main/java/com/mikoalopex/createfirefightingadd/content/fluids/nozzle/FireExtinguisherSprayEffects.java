package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayFluidType;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayHitContext;
import com.mikoalopex.createfirefightingadd.content.equipment.extinguisher.FireExtinguisherItem;
import com.mikoalopex.createfirefightingadd.content.equipment.extinguisher.FireExtinguisherItemHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class FireExtinguisherSprayEffects {
	public static final int RANGE = 4;
	public static final double HALF_ANGLE = 14.0;

	private static final int CONSUMPTION_INTERVAL = 2;
	private static final int CONSUMPTION_PER_INTERVAL = 5;
	private static final int MAX_BLOCK_SAMPLES = 128;
	private static final double BLOCK_RADIUS_TOLERANCE = 0.65;
	private static final double TAN_HALF_ANGLE = Math.tan(Math.toRadians(HALF_ANGLE));

	private FireExtinguisherSprayEffects() {
	}

	public static boolean spray(ServerPlayer player, ItemStack stack, InteractionHand hand) {
		if (!stack.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
			return false;

		FluidStack fluid = FireExtinguisherItem.getFluid(stack);
		if (fluid.isEmpty())
			return false;

		NozzleSprayRuleSet customRules = FireExtinguisherItem.getSprayRules(stack, player.registryAccess());
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.classifyFluidForSpray(player.level(), fluid, customRules);
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED)
			return false;

		NozzleSprayRule customRule = null;
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM) {
			customRule = NozzleGlobalSprayRules.findGlobalRule(fluid).orElse(null);
			if (customRule == null)
				customRule = customRules.findForSpray(fluid).orElse(null);
		}

		if (player.tickCount % CONSUMPTION_INTERVAL == 0) {
			FireExtinguisherItemHandler handler =
				new FireExtinguisherItemHandler(CreateFireFightingAdd.FIRE_EXTINGUISHER_FLUID, stack);
			FluidStack drained = handler.drainForSpray(CONSUMPTION_PER_INTERVAL, FluidAction.EXECUTE);
			if (drained.isEmpty())
				return false;
		}

		ServerLevel level = player.serverLevel();
		Vec3 direction = player.getLookAngle().normalize();
		Vec3 origin = sprayOrigin(player, hand, direction);
		boolean ignited = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA
			|| behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
			|| customRule != null && (customRule.igniting() || customRule.flammable());

		NozzleSpraySounds.tick(level, NozzleSpraySounds.handheldKey(player.getUUID()) + ":extinguisher",
			origin, SoundSource.PLAYERS, SprayLoopSound.FIRE_EXTINGUISHER);
		applyBlockEffects(level, origin, direction, behavior, fluid, ignited, customRule);
		applyEntityEffects(level, origin, direction, behavior, fluid, ignited, customRule);
		return true;
	}

	public static Vec3 sprayOrigin(Player player, InteractionHand hand, Vec3 direction) {
		Vec3 origin = player.getEyePosition().add(direction.scale(0.65)).add(0.0, -0.18, 0.0);
		Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
		if (side.lengthSqr() < 1.0E-6)
			side = Vec3.directionFromRotation(0.0f, player.getYRot()).cross(new Vec3(0.0, 1.0, 0.0));
		double sideSign = isLeftHand(player, hand) ? -1.0 : 1.0;
		return origin.add(side.normalize().scale(0.5 * sideSign)).add(0.0, -0.5, 0.0);
	}

	public static void stopSound(ServerPlayer player) {
		if (player != null)
			NozzleSpraySounds.stop(player.level(), NozzleSpraySounds.handheldKey(player.getUUID()) + ":extinguisher",
				player.position(), SoundSource.PLAYERS);
	}

	private static void applyBlockEffects(ServerLevel level, Vec3 origin, Vec3 direction,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited,
			NozzleSprayRule customRule) {
		NozzleSprayEffects.BlockEffectOptions options =
			NozzleSprayEffects.BlockEffectOptions.projectilePath(NozzleSprayEffects.NO_EXTINGUISH_SOUND);
		for (ConeBlockSample sample : coneBlockSamples(level, origin, direction)) {
			BlockState state = level.getBlockState(sample.pos());
			NozzleSprayHitContext context = new NozzleSprayHitContext(level, sample.pos(), state, fluid.copy(),
				apiType(behavior), ignited, origin, sample.samplePos(), direction, sample.axialDistance(), List.of());
			if (!canAffectBlock(context, behavior, ignited, customRule, options))
				continue;
			if (!hasLineOfSight(level, origin, sample.samplePos(), sample.pos()))
				continue;
			applyBlockSample(context, behavior, ignited, customRule, options);
		}
	}

	private static void applyEntityEffects(ServerLevel level, Vec3 origin, Vec3 direction,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited,
			NozzleSprayRule customRule) {
		AABB area = new AABB(origin, origin.add(direction.scale(RANGE))).inflate(maxConeRadius() + 1.0);
		var fanProcessingType = customRule == null
			? NozzleSprayEffects.fanProcessingType(behavior, ignited)
			: NozzleSprayEffects.fanProcessingType(customRule, ignited);
		var processingType = fanProcessingType != null
			&& SprayAuxiliaryScheduler.shouldProcess(level, BlockPos.containing(origin), false)
				? fanProcessingType : null;
		Set<Integer> processed = new HashSet<>();
		for (Entity entity : level.getEntities(null, area)) {
			if (!processed.add(entity.getId()))
				continue;
			AbstractSprayDeviceBlockEntity.CenterlineSample sample = entityConeSample(entity, origin, direction);
			if (sample == null)
				continue;
			if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM && customRule != null)
				NozzleSprayEffects.applyCustomEntityEffect(level, entity, sample, RANGE,
					customRule, ignited, processingType, true);
			else
				NozzleSprayEffects.applyEntityEffect(level, entity, sample, RANGE,
					behavior, ignited, fluid, processingType, true);
		}
	}

	private static boolean canAffectBlock(NozzleSprayHitContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited, NozzleSprayRule customRule,
			NozzleSprayEffects.BlockEffectOptions options) {
		if (behavior != AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM || customRule == null)
			return NozzleSprayEffects.canAffectBlock(context, behavior, ignited, options);

		if (customRule.extinguishing() && !(customRule.igniting() || customRule.flammable() && ignited)
			&& NozzleSprayEffects.canAffectBlock(context,
				AbstractSprayDeviceBlockEntity.FluidBehavior.WATER, false, options))
			return true;

		return (customRule.igniting() || customRule.flammable() && ignited)
			&& NozzleSprayEffects.canAffectBlock(context,
				AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA, true, options);
	}

	private static void applyBlockSample(NozzleSprayHitContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited, NozzleSprayRule customRule,
			NozzleSprayEffects.BlockEffectOptions options) {
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM && customRule != null) {
			if (customRule.extinguishing() && !(customRule.igniting() || customRule.flammable() && ignited))
				NozzleSprayEffects.applyBlockSample(context,
					AbstractSprayDeviceBlockEntity.FluidBehavior.WATER, false, options);
			if (customRule.igniting() || customRule.flammable() && ignited)
				NozzleSprayEffects.applyBlockSample(context,
					AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA, true, options);
			return;
		}
		NozzleSprayEffects.applyBlockSample(context, behavior, ignited, options);
	}

	private static List<ConeBlockSample> coneBlockSamples(ServerLevel level, Vec3 origin, Vec3 direction) {
		Vec3 end = origin.add(direction.scale(RANGE));
		double inflate = maxConeRadius() + BLOCK_RADIUS_TOLERANCE + 1.0;
		AABB bounds = new AABB(origin, end).inflate(inflate);
		List<ConeBlockSample> samples = new java.util.ArrayList<>();
		Set<Long> visited = new HashSet<>();
		BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
		BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
		for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
			BlockPos pos = cursor.immutable();
			if (!visited.add(pos.asLong()) || !level.isLoaded(pos))
				continue;
			Vec3 center = Vec3.atCenterOf(pos);
			double axial = axialDistance(origin, direction, center);
			if (axial <= 0.0 || axial > RANGE + 0.5)
				continue;
			if (!insideCone(origin, direction, center, axial, BLOCK_RADIUS_TOLERANCE))
				continue;
			samples.add(new ConeBlockSample(pos, center, axial));
		}
		samples.sort(Comparator.comparingDouble(ConeBlockSample::axialDistance));
		if (samples.size() > MAX_BLOCK_SAMPLES)
			return samples.subList(0, MAX_BLOCK_SAMPLES);
		return samples;
	}

	private static AbstractSprayDeviceBlockEntity.CenterlineSample entityConeSample(
			Entity entity, Vec3 origin, Vec3 direction) {
		AABB box = entity.getBoundingBox();
		Vec3 center = box.getCenter();
		double axial = axialDistance(origin, direction, center);
		if (axial <= 0.0 || axial > RANGE)
			return null;
		double tolerance = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5;
		if (!insideCone(origin, direction, center, axial, tolerance))
			return null;
		return new AbstractSprayDeviceBlockEntity.CenterlineSample(origin.add(direction.scale(axial)), direction, axial);
	}

	private static boolean insideCone(Vec3 origin, Vec3 direction, Vec3 point, double axial, double tolerance) {
		Vec3 offset = point.subtract(origin);
		double radialSq = Math.max(0.0, offset.lengthSqr() - axial * axial);
		double radius = axial * TAN_HALF_ANGLE + tolerance;
		return radialSq <= radius * radius;
	}

	private static double axialDistance(Vec3 origin, Vec3 direction, Vec3 point) {
		return point.subtract(origin).dot(direction);
	}

	private static double maxConeRadius() {
		return RANGE * TAN_HALF_ANGLE;
	}

	private static boolean hasLineOfSight(ServerLevel level, Vec3 origin, Vec3 samplePos, BlockPos target) {
		BlockHitResult hit = level.clip(new ClipContext(
			origin, samplePos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}

	private static boolean isLeftHand(Player player, InteractionHand hand) {
		boolean mainHandLeft = player.getMainArm() == HumanoidArm.LEFT;
		return hand == InteractionHand.MAIN_HAND ? mainHandLeft : !mainHandLeft;
	}

	private static NozzleSprayFluidType apiType(AbstractSprayDeviceBlockEntity.FluidBehavior behavior) {
		return switch (behavior) {
			case WATER -> NozzleSprayFluidType.WATER;
			case LAVA -> NozzleSprayFluidType.LAVA;
			case MILK -> NozzleSprayFluidType.MILK;
			case POTION -> NozzleSprayFluidType.POTION;
			case DRAGON_BREATH -> NozzleSprayFluidType.DRAGON_BREATH;
			case FLAMMABLE, CUSTOM -> NozzleSprayFluidType.FLAMMABLE;
			case UNSUPPORTED -> NozzleSprayFluidType.UNSUPPORTED;
		};
	}

	private record ConeBlockSample(BlockPos pos, Vec3 samplePos, double axialDistance) {
	}
}
