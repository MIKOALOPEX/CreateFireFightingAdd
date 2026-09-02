package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayFluidType;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayHitContext;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerItem;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleType;
import com.mikoalopex.createfirefightingadd.content.items.firefighter.FirefighterRecordStore;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class HandheldNozzleSprayEffects {
	private static final int CONSUMPTION_PER_TICK = 10;
	private static final double SAMPLE_STEP = 0.75;

	private HandheldNozzleSprayEffects() {
	}

	public static boolean spray(ServerPlayer player, ItemStack stack) {
		Optional<HandheldNozzleControllerItem.Binding> optional = HandheldNozzleControllerItem.readBinding(stack);
		if (optional.isEmpty())
			return false;

		HandheldNozzleControllerItem.Binding binding = optional.get();
		ServerLevel level = player.server.getLevel(binding.dimension());
		if (level == null)
			return false;
		if (!(level.getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet)
			|| !cabinet.getHydrantId().equals(binding.hydrantId())) {
			player.displayClientMessage(
				net.minecraft.network.chat.Component.translatable("createfirefightingadd.handheld_nozzle.missing_hydrant"),
				true);
			HandheldNozzleControllerItem.clearBinding(player.level(), stack, player);
			return false;
		}

		HandheldNozzleType nozzleType = cabinet.getNozzleType();
		if (!cabinet.hasHose() || !nozzleType.hasNozzle()) {
			player.displayClientMessage(
				net.minecraft.network.chat.Component.translatable("createfirefightingadd.handheld_nozzle.missing_nozzle"),
				true);
			return false;
		}

		FluidStack fluid = cabinet.drainForHandheldSpray(CONSUMPTION_PER_TICK, FluidAction.SIMULATE);
		if (fluid.isEmpty())
			return false;
		NozzleSprayRuleSet customRules = cabinet.getCustomSprayRules();
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.classifyFluidForSpray(level, fluid, customRules);
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED)
			return false;
		NozzleSprayRule customRule = null;
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM) {
			customRule = NozzleGlobalSprayRules.findGlobalRule(fluid).orElse(null);
			if (customRule == null)
				customRule = customRules.findForSpray(fluid).orElse(null);
		}

		fluid = cabinet.drainForHandheldSpray(CONSUMPTION_PER_TICK, FluidAction.EXECUTE);
		if (fluid.isEmpty())
			return false;

		Vec3 direction = player.getLookAngle().normalize();
		Vec3 origin = player.getEyePosition().add(direction.scale(0.75)).add(0.0, -0.18, 0.0);
		NozzleSpraySounds.tick(level, NozzleSpraySounds.handheldKey(player.getUUID()), origin, SoundSource.PLAYERS);
		SprayShape shape = shapeFor(nozzleType);
		int range = rangeFor(nozzleType);
		long tick = level.getGameTime();
		boolean ignited = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA
			|| behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
			|| customRule != null && (customRule.igniting() || customRule.flammable());

		long perfStart = SprayPerformanceDebug.start();
		applyBlockEffects(level, binding.pos(), origin, direction, shape, range, behavior, fluid, ignited, tick, customRule);
		applyEntityEffects(level, origin, direction, shape, range, behavior, fluid, ignited, customRule);
		applyRecoil(player, direction);
		SprayPerformanceDebug.record(level, "handheld_spray", player.blockPosition(), perfStart, -1,
			() -> "player=" + player.getGameProfile().getName()
				+ ", type=" + nozzleType
				+ ", fluid=" + behavior
				+ ", range=" + range);
		return true;
	}

	public static void stopSound(ServerPlayer player) {
		if (player != null)
			NozzleSpraySounds.stop(player.level(), NozzleSpraySounds.handheldKey(player.getUUID()),
				player.position(), SoundSource.PLAYERS);
	}

	private static SprayShape shapeFor(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> new FanSprayShape(Config.flatNozzleMaxDistance, 60.0, 4.0);
			default -> new ConeSprayShape(Config.coneNozzleMaxDistance, 10.0);
		};
	}

	private static int rangeFor(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> Config.flatNozzleMaxDistance;
			default -> Config.coneNozzleMaxDistance;
		};
	}

	private static void applyBlockEffects(ServerLevel level, BlockPos sourcePos, Vec3 origin, Vec3 direction, SprayShape shape,
			int range, AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited, long tick,
			@org.jetbrains.annotations.Nullable NozzleSprayRule customRule) {
		int rays = Math.max(8, Math.min(28, range / 2));
		NozzleSprayEffects.BlockEffectOptions options =
			NozzleSprayEffects.BlockEffectOptions.projectilePath(NozzleSprayEffects.NO_EXTINGUISH_SOUND);
		SprayEffectSampler.traceRays(level, origin, direction, shape, range, rays, tick, tick * 37L,
			SAMPLE_STEP, (pos, state, samplePos, rayDirection, distance) -> {
				NozzleSprayHitContext context = new NozzleSprayHitContext(level, pos, state, fluid.copy(),
					apiType(behavior), ignited, origin, samplePos, rayDirection, distance,
					FirefighterRecordStore.activeOwners(level, sourcePos));
				if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM && customRule != null) {
					if (customRuleExtinguishesBlocks(customRule, ignited))
						NozzleSprayEffects.applyBlockSample(context,
							AbstractSprayDeviceBlockEntity.FluidBehavior.WATER, false, options);
					if (customRuleIgnitesBlocks(customRule, ignited))
						NozzleSprayEffects.applyBlockSample(context,
							AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA, true, options);
					return;
				}
				if (!NozzleSprayEffects.canAffectBlock(context, behavior, ignited, options))
					return;
				NozzleSprayEffects.applyBlockSample(context, behavior, ignited, options);
			});
	}

	private static void applyEntityEffects(ServerLevel level, Vec3 origin, Vec3 direction, SprayShape shape,
			int range, AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited,
			@org.jetbrains.annotations.Nullable NozzleSprayRule customRule) {
		AABB area = new AABB(origin, origin.add(direction.scale(range))).inflate(range * 0.35 + 1.5);
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline = centerline(origin, direction, range);
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
			AbstractSprayDeviceBlockEntity.CenterlineSample sample = closestSample(entity.position(), centerline, shape, range);
			if (sample == null)
				continue;
			if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM && customRule != null)
				NozzleSprayEffects.applyCustomEntityEffect(level, entity, sample, range,
					customRule, ignited, processingType, true);
			else
				NozzleSprayEffects.applyEntityEffect(level, entity, sample, range,
					behavior, ignited, fluid, processingType, true);
		}
	}

	private static boolean customRuleIgnitesBlocks(NozzleSprayRule rule, boolean ignited) {
		return rule.igniting() || rule.flammable() && ignited;
	}

	private static boolean customRuleExtinguishesBlocks(NozzleSprayRule rule, boolean ignited) {
		return rule.extinguishing() && !customRuleIgnitesBlocks(rule, ignited);
	}

	private static List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline(Vec3 origin, Vec3 direction, int range) {
		java.util.ArrayList<AbstractSprayDeviceBlockEntity.CenterlineSample> samples = new java.util.ArrayList<>();
		Vec3 dir = direction.normalize();
		for (int i = 0; i <= range; i++) {
			Vec3 pos = origin.add(dir.scale(i));
			samples.add(new AbstractSprayDeviceBlockEntity.CenterlineSample(pos, dir, i));
		}
		return samples;
	}

	private static AbstractSprayDeviceBlockEntity.CenterlineSample closestSample(Vec3 point,
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> samples, SprayShape shape, int range) {
		AbstractSprayDeviceBlockEntity.CenterlineSample best = null;
		double bestDist = Double.MAX_VALUE;
		for (AbstractSprayDeviceBlockEntity.CenterlineSample sample : samples) {
			if (!shape.containsPoint(point, sample.position(), sample.direction(), sample.axialDist()))
				continue;
			double dist = point.distanceToSqr(sample.position());
			if (dist < bestDist) {
				bestDist = dist;
				best = sample;
			}
		}
		return best;
	}

	private static void applyRecoil(ServerPlayer player, Vec3 direction) {
		if (player.isCrouching() || player.getPose() == Pose.SWIMMING)
			return;
		player.push(-direction.x * 0.035, -direction.y * 0.01, -direction.z * 0.035);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		if (player.tickCount % 10 == 0)
			player.level().playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY,
				SoundSource.PLAYERS, 0.15f, 1.4f);
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
}
