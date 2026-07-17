package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayFluidType;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayHitContext;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayInteractionRegistry;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerItem;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
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
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.classifyFluidForSpray(level, fluid);
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED)
			return false;

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
			|| behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE;

		applyBlockEffects(level, origin, direction, shape, range, behavior, fluid, ignited, tick);
		applyEntityEffects(level, origin, direction, shape, range, behavior, fluid, ignited);
		applyRecoil(player, direction);
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

	private static void applyBlockEffects(ServerLevel level, Vec3 origin, Vec3 direction, SprayShape shape,
			int range, AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited, long tick) {
		int rays = Math.max(8, Math.min(28, range / 2));
		SprayEffectSampler.traceRays(level, origin, direction, shape, range, rays, tick, tick * 37L,
			SAMPLE_STEP, (pos, state, samplePos, rayDirection, distance) -> {
				NozzleSprayHitContext context = new NozzleSprayHitContext(level, pos, state, fluid.copy(),
					apiType(behavior), ignited, origin, samplePos, rayDirection, distance);
				if (!canAffect(context))
					return;
				NozzleSprayInteractionRegistry.notifyHit(context);
				applyBlockEffect(context, behavior, ignited);
			});
	}

	private static boolean canAffect(NozzleSprayHitContext context) {
		BlockState state = context.state();
		return NozzleSprayInteractionRegistry.shouldNotify(context)
			|| state.getBlock() instanceof BaseFireBlock
			|| state.getBlock() instanceof CampfireBlock
			|| SprayEffectUtils.isConcretePowder(state)
			|| SprayEffectUtils.isDryFarmland(state)
			|| context.isWaterLike();
	}

	private static void applyBlockEffect(NozzleSprayHitContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited) {
		Level level = context.level();
		BlockPos pos = context.pos();
		BlockState state = context.state();
		switch (behavior) {
			case WATER -> {
				if (!SprayEffectUtils.hydrateConcretePowder(level, pos, state)
					&& !SprayEffectUtils.moistenFarmland(level, pos, state))
					SprayEffectUtils.extinguishBlock(level, pos, state);
			}
			case MILK, POTION -> SprayEffectUtils.extinguishBlock(level, pos, state);
			case LAVA -> ignite(level, pos);
			case FLAMMABLE -> {
				if (ignited)
					ignite(level, pos);
			}
			default -> {
			}
		}
	}

	private static void ignite(Level level, BlockPos pos) {
		if (!level.isEmptyBlock(pos))
			return;
		BlockState fire = SprayEffectUtils.getFireState(level, pos);
		if (fire.canSurvive(level, pos))
			level.setBlock(pos, fire, 3);
	}

	private static void applyEntityEffects(ServerLevel level, Vec3 origin, Vec3 direction, SprayShape shape,
			int range, AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, boolean ignited) {
		AABB area = new AABB(origin, origin.add(direction.scale(range))).inflate(range * 0.35 + 1.5);
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline = centerline(origin, direction, range);
		Set<Integer> processed = new HashSet<>();
		for (Entity entity : level.getEntities(null, area)) {
			if (!processed.add(entity.getId()))
				continue;
			AbstractSprayDeviceBlockEntity.CenterlineSample sample = closestSample(entity.position(), centerline, shape, range);
			if (sample == null)
				continue;
			switch (behavior) {
				case WATER -> {
					SprayEntityEffects.applyWaterContact(level, entity);
					pushEntity(entity, sample, range);
				}
				case LAVA -> {
					if (!(entity instanceof ItemEntity) && entity.getRemainingFireTicks() < 100)
						entity.setRemainingFireTicks(100);
				}
				case FLAMMABLE -> {
					if (ignited && !(entity instanceof ItemEntity) && entity.getRemainingFireTicks() < 100)
						entity.setRemainingFireTicks(100);
				}
				case MILK -> {
					if (entity instanceof LivingEntity living)
						living.removeAllEffects();
				}
				case POTION -> {
					PotionContents contents = fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
					if (entity instanceof LivingEntity living) {
						for (var effect : contents.getAllEffects())
							living.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
					}
				}
				case DRAGON_BREATH -> {
					if (entity instanceof LivingEntity living)
						living.hurt(level.damageSources().magic(), 2.0f);
				}
				default -> {
				}
			}
		}
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

	private static void pushEntity(Entity entity, AbstractSprayDeviceBlockEntity.CenterlineSample sample, int range) {
		if (entity.isCrouching() || entity.getPose() == Pose.SWIMMING)
			return;
		double pushSpeed = AbstractSprayDeviceBlockEntity.PUSH_STREAM_SPEED
			* (1.0 - sample.axialDist() / Math.max(1, range));
		Vec3 direction = sample.direction();
		entity.push(direction.x * pushSpeed, direction.y * pushSpeed, direction.z * pushSpeed);
		if (entity instanceof ServerPlayer player)
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
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
			case FLAMMABLE -> NozzleSprayFluidType.FLAMMABLE;
			case UNSUPPORTED -> NozzleSprayFluidType.UNSUPPORTED;
		};
	}
}
