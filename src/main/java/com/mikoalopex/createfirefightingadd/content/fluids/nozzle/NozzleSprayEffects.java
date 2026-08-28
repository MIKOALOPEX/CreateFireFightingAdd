package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayHitContext;
import com.mikoalopex.createfirefightingadd.api.nozzle.NozzleSprayInteractionRegistry;
import com.mikoalopex.createfirefightingadd.integration.burnt.BurntCompat;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

final class NozzleSprayEffects {
	static final ExtinguishSound NO_EXTINGUISH_SOUND = (level, pos) -> {
	};

	private NozzleSprayEffects() {
	}

	@FunctionalInterface
	interface ExtinguishSound {
		void play(Level level, BlockPos pos);
	}

	record BlockEffectOptions(
		boolean notifyInteractions,
		boolean includePhaseChanges,
		boolean clearNearbyWildfireHeat,
		boolean replaceableIgnition,
		@Nullable Direction ignitionDirection,
		ExtinguishSound extinguishSound
	) {
		static BlockEffectOptions basic(boolean notifyInteractions, ExtinguishSound extinguishSound) {
			return new BlockEffectOptions(notifyInteractions, false, false, false, Direction.UP, extinguishSound);
		}

		static BlockEffectOptions projectilePath(ExtinguishSound extinguishSound) {
			return new BlockEffectOptions(true, true, false, false, Direction.UP, extinguishSound);
		}

		BlockEffectOptions withClearNearbyWildfireHeat() {
			return new BlockEffectOptions(notifyInteractions, includePhaseChanges, true,
				replaceableIgnition, ignitionDirection, extinguishSound);
		}

		BlockEffectOptions withReplaceableIgnition(Direction direction) {
			return new BlockEffectOptions(notifyInteractions, includePhaseChanges, clearNearbyWildfireHeat,
				true, direction, extinguishSound);
		}
	}

	static boolean canAffectBlock(NozzleSprayHitContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
			BlockEffectOptions options) {
		if (options.notifyInteractions() && NozzleSprayInteractionRegistry.shouldNotify(context))
			return true;

		BlockState state = context.state();
		return switch (behavior) {
			case WATER -> isExtinguishable(state)
				|| SprayEffectUtils.isConcretePowder(state)
				|| SprayEffectUtils.isDryFarmland(state)
				|| (options.includePhaseChanges() && isSnow(state));
			case MILK, POTION -> isExtinguishable(state)
				|| (options.includePhaseChanges() && isSnow(state));
			case LAVA -> canIgnite(context, options)
				|| (options.includePhaseChanges() && isColdBlock(state));
			case FLAMMABLE -> ignited && (canIgnite(context, options)
				|| (options.includePhaseChanges() && isColdBlock(state)));
			case CUSTOM, DRAGON_BREATH, UNSUPPORTED -> false;
		};
	}

	static boolean applyBlockSample(NozzleSprayHitContext context,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
			BlockEffectOptions options) {
		if (options.notifyInteractions())
			NozzleSprayInteractionRegistry.notifyHit(context);

		BlockState state = context.state();
		return switch (behavior) {
			case WATER -> {
				if (SprayEffectUtils.hydrateConcretePowder(context.level(), context.pos(), state)
					|| SprayEffectUtils.moistenFarmland(context.level(), context.pos(), state))
					yield true;
				yield applyWaterLikeBlockEffect(context, options);
			}
			case MILK, POTION -> applyWaterLikeBlockEffect(context, options);
			case LAVA -> applyHeatedBlockEffect(context, options);
			case FLAMMABLE -> ignited && applyHeatedBlockEffect(context, options);
			case CUSTOM, DRAGON_BREATH, UNSUPPORTED -> false;
		};
	}

	private static boolean applyWaterLikeBlockEffect(NozzleSprayHitContext context, BlockEffectOptions options) {
		if (extinguishBlock(context.level(), context.pos(), context.state(),
			options.clearNearbyWildfireHeat(), options.extinguishSound()))
			return true;
		return options.includePhaseChanges()
			&& meltSnow(context.level(), context.pos(), context.state());
	}

	private static boolean applyHeatedBlockEffect(NozzleSprayHitContext context, BlockEffectOptions options) {
		if (tryIgnite(context.level(), context.pos(), options.ignitionDirection(), options.replaceableIgnition()))
			return true;
		return options.includePhaseChanges()
			&& meltColdBlock(context.level(), context.pos(), context.state());
	}

	static boolean isExtinguishable(BlockState state) {
		return state.getBlock() instanceof BaseFireBlock
			|| (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))
			|| BurntCompat.mightHandle(state);
	}

	static boolean extinguishBlock(Level level, BlockPos pos, BlockState state,
			boolean clearNearbyWildfireHeat, ExtinguishSound sound) {
		if (SprayEffectUtils.tryTfcDouse(level, pos))
			return true;
		if (BurntCompat.extinguishAt(level, pos, state)) {
			sound.play(level, pos);
			return true;
		}
		if (state.getBlock() instanceof BaseFireBlock) {
			level.removeBlock(pos, false);
			sound.play(level, pos);
			SprayEffectUtils.clearWildfireHeat(level, pos);
			if (clearNearbyWildfireHeat)
				clearNearbyWildfireHeat(level, pos);
			return true;
		}
		if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
			level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
			sound.play(level, pos);
			return true;
		}
		return false;
	}

	static boolean tryIgnite(Level level, BlockPos pos, @Nullable Direction direction, boolean replaceable) {
		if (level.random.nextDouble() >= Config.nozzleIgnitionChance / 100.0)
			return false;
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BaseFireBlock)
			return false;
		if (replaceable) {
			if (!state.canBeReplaced())
				return false;
		} else if (!state.isAir()) {
			return false;
		}
		if (!level.getFluidState(pos).isEmpty())
			return false;
		Direction placeDirection = direction == null ? Direction.UP : direction;
		if (!BaseFireBlock.canBePlacedAt(level, pos, placeDirection))
			return false;
		level.setBlock(pos, SprayEffectUtils.getFireState(level, pos), 3);
		return true;
	}

	static boolean meltColdBlock(Level level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.PACKED_ICE)) {
			level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
			return true;
		}
		if (state.is(Blocks.SNOW_BLOCK)) {
			level.removeBlock(pos, false);
			return true;
		}
		return meltSnow(level, pos, state);
	}

	private static boolean meltSnow(Level level, BlockPos pos, BlockState state) {
		if (!state.is(Blocks.SNOW))
			return false;
		int layers = state.getValue(SnowLayerBlock.LAYERS);
		if (layers > 1)
			level.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1), 3);
		else
			level.removeBlock(pos, false);
		return true;
	}

	private static boolean isColdBlock(BlockState state) {
		return state.is(Blocks.ICE)
			|| state.is(Blocks.FROSTED_ICE)
			|| state.is(Blocks.PACKED_ICE)
			|| state.is(Blocks.SNOW)
			|| state.is(Blocks.SNOW_BLOCK);
	}

	private static boolean isSnow(BlockState state) {
		return state.is(Blocks.SNOW);
	}

	private static boolean canIgnite(NozzleSprayHitContext context, BlockEffectOptions options) {
		BlockState state = context.state();
		if (state.getBlock() instanceof BaseFireBlock)
			return false;
		if (options.replaceableIgnition()) {
			if (!state.canBeReplaced())
				return false;
		} else if (!state.isAir()) {
			return false;
		}
		Direction direction = options.ignitionDirection() == null ? Direction.UP : options.ignitionDirection();
		return context.level().getFluidState(context.pos()).isEmpty()
			&& BaseFireBlock.canBePlacedAt(context.level(), context.pos(), direction);
	}

	private static void clearNearbyWildfireHeat(Level level, BlockPos pos) {
		int r = Config.heatClearRadius;
		for (int dx = -r; dx <= r; dx++)
			for (int dy = -r; dy <= r; dy++)
				for (int dz = -r; dz <= r; dz++)
					SprayEffectUtils.clearWildfireHeat(level, pos.offset(dx, dy, dz));
	}

	static @Nullable FanProcessingType fanProcessingType(
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited) {
		return switch (behavior) {
			case WATER -> AllFanProcessingTypes.SPLASHING;
			case LAVA -> AllFanProcessingTypes.BLASTING;
			case FLAMMABLE -> ignited ? AllFanProcessingTypes.BLASTING : null;
			case DRAGON_BREATH -> FanProcessingType.parse("create_dragons_plus:ending");
			default -> null;
		};
	}

	static @Nullable FanProcessingType fanProcessingType(@Nullable NozzleSprayRule rule, boolean ignited) {
		if (rule == null || rule.fanProcessingType() == null)
			return null;
		try {
			return FanProcessingType.parse(rule.fanProcessingType().toString());
		} catch (RuntimeException e) {
			return null;
		}
	}

	static void applyEntityEffect(Level level, Entity entity,
			AbstractSprayDeviceBlockEntity.CenterlineSample sample, double range,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
			FluidStack fluid, @Nullable FanProcessingType processingType, boolean pushWater) {
		PotionContents potionContents = fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		applyEntityEffect(level, entity, sample, range, behavior, ignited,
			potionContents, processingType, pushWater);
	}

	static void applyEntityEffect(Level level, Entity entity,
			AbstractSprayDeviceBlockEntity.CenterlineSample sample, double range,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
			PotionContents potionContents, @Nullable FanProcessingType processingType, boolean pushWater) {
		switch (behavior) {
			case WATER -> {
				if (entity instanceof ItemEntity itemEntity)
					processItemEntity(itemEntity, processingType);
				SprayEntityEffects.applyWaterContact(level, entity);
				if (pushWater)
					pushEntity(entity, sample, range);
			}
			case LAVA -> {
				if (entity instanceof ItemEntity itemEntity) {
					processItemEntity(itemEntity, processingType);
					return;
				}
				igniteEntity(entity);
			}
			case FLAMMABLE -> {
				if (!ignited)
					return;
				if (entity instanceof ItemEntity itemEntity) {
					processItemEntity(itemEntity, processingType);
					return;
				}
				igniteEntity(entity);
			}
			case MILK -> {
				if (entity instanceof LivingEntity living)
					living.removeAllEffects();
			}
			case POTION -> {
				if (!potionContents.equals(PotionContents.EMPTY) && entity instanceof LivingEntity living) {
					for (var effect : potionContents.getAllEffects())
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
			case CUSTOM, UNSUPPORTED -> {
			}
		}
	}

	static void applyCustomEntityEffect(Level level, Entity entity,
			AbstractSprayDeviceBlockEntity.CenterlineSample sample, double range,
			NozzleSprayRule rule, boolean ignited, @Nullable FanProcessingType processingType, boolean push) {
		if (entity instanceof ItemEntity itemEntity) {
			processItemEntity(itemEntity, processingType);
			return;
		}
		if (rule.igniting() || rule.flammable() && ignited)
			igniteEntity(entity);
		if (entity instanceof LivingEntity living) {
			for (ResourceLocation id : rule.effects()) {
				BuiltInRegistries.MOB_EFFECT.getHolder(id)
					.ifPresent(holder -> living.addEffect(new MobEffectInstance(holder, 100, 0)));
			}
		}
		if (push)
			pushEntity(entity, sample, range);
	}

	private static void igniteEntity(Entity entity) {
		if (entity.getRemainingFireTicks() < 100)
			entity.setRemainingFireTicks(100);
	}

	private static void pushEntity(Entity entity,
			AbstractSprayDeviceBlockEntity.CenterlineSample sample, double range) {
		if (entity.isCrouching() || entity.getPose() == Pose.SWIMMING)
			return;
		double pushSpeed = AbstractSprayDeviceBlockEntity.PUSH_STREAM_SPEED
			* (1.0 - sample.axialDist() / Math.max(1.0, range));
		Vec3Helper.push(entity, sample.direction(), pushSpeed);
	}

	static void processItemEntity(ItemEntity entity, @Nullable FanProcessingType type) {
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

	private static int processingTimeFor(ItemStack stack) {
		try {
			float timeModifierForStackSize = (float) (1 + (stack.getCount() - 1) / 64.0);
			return (int) (AllConfigs.server().kinetics.fanProcessingTime.get() * timeModifierForStackSize) + 1;
		} catch (RuntimeException e) {
			return 150;
		}
	}

	private static final class Vec3Helper {
		private Vec3Helper() {
		}

		private static void push(Entity entity, net.minecraft.world.phys.Vec3 direction, double speed) {
			entity.push(direction.x * speed, direction.y * speed, direction.z * speed);
			if (entity instanceof net.minecraft.server.level.ServerPlayer player)
				player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}
}
