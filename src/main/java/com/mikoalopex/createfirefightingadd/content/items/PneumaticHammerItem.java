package com.mikoalopex.createfirefightingadd.content.items;

import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.content.equipment.armor.BacktankUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class PneumaticHammerItem extends Item {
	private static final String CHARGED_KEY = "Charged";
	private static final int CHARGE_TIME_TICKS = 60;
	private static final int CHARGE_AIR_COST = 100;
	private static final float STONE_TOOL_SPEED = 4.0f;
	private static final float CHARGED_TARGET_DAMAGE_BONUS = 1.0f;
	private static final float IRON_SWORD_SWEEP_DAMAGE = 6.0f;

	public PneumaticHammerItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static int getChargeTimeTicks() {
		return CHARGE_TIME_TICKS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (isCharged(stack)) {
			if (!level.isClientSide)
				player.displayClientMessage(Component.translatable("createfirefightingadd.pneumatic_hammer.already_charged"), true);
			return InteractionResultHolder.fail(stack);
		}

		if (!player.isCreative() && getAvailableAir(player) < CHARGE_AIR_COST) {
			if (!level.isClientSide)
				player.displayClientMessage(Component.translatable("createfirefightingadd.pneumatic_hammer.not_enough_air"), true);
			return InteractionResultHolder.fail(stack);
		}

		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		completeCharge(stack, level, entity);
		return stack;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
		if (remainingUseDuration > 1)
			return;
		completeCharge(stack, level, entity);
		entity.stopUsingItem();
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		int usedTicks = getUseDuration(stack, entity) - timeLeft;
		if (usedTicks >= CHARGE_TIME_TICKS)
			completeCharge(stack, level, entity);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return CHARGE_TIME_TICKS;
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
		return itemAbility == ItemAbilities.PICKAXE_DIG
			|| itemAbility == ItemAbilities.AXE_DIG
			|| itemAbility == ItemAbilities.SHOVEL_DIG;
	}

	public static boolean isCharged(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(CHARGED_KEY);
	}

	private static void setCharged(ItemStack stack, boolean charged) {
		CompoundTag tag = new CompoundTag();
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null)
			tag = data.copyTag();
		if (charged)
			tag.putBoolean(CHARGED_KEY, true);
		else
			tag.remove(CHARGED_KEY);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static void handleBreakSpeed(PlayerEvent.BreakSpeed event) {
		ItemStack stack = event.getEntity().getMainHandItem();
		if (!(stack.getItem() instanceof PneumaticHammerItem))
			return;

		float speed = getToolSpeed(event.getState());
		if (speed > 0)
			event.setNewSpeed(speed * event.getOriginalSpeed());
	}

	public static void handleHarvestCheck(PlayerEvent.HarvestCheck event) {
		ItemStack stack = event.getEntity().getMainHandItem();
		if (!(stack.getItem() instanceof PneumaticHammerItem))
			return;
		if (canHarvestNormally(event.getTargetBlock()) || isCharged(stack) && canHarvestCharged(event.getTargetBlock()))
			event.setCanHarvest(true);
	}

	public static void handleChargedBlockBreak(PlayerInteractEvent.LeftClickBlock event) {
		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof PneumaticHammerItem) || !isCharged(stack))
			return;
		if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START)
			return;

		if (event.getLevel().isClientSide) {
			event.setCanceled(true);
			return;
		}

		Player player = event.getEntity();
		if (!(player instanceof ServerPlayer serverPlayer))
			return;

		List<BlockPos> positions = collectBreakPositions(event.getPos(), event.getFace());
		boolean brokeAny = false;
		for (BlockPos pos : positions) {
			BlockState state = event.getLevel().getBlockState(pos);
			if (!canHarvestCharged(state))
				continue;
			if (state.getDestroySpeed(event.getLevel(), pos) < 0)
				continue;
			if (serverPlayer.gameMode.destroyBlock(pos))
				brokeAny = true;
		}

		if (brokeAny) {
			setCharged(stack, false);
			spawnChargedBreakParticles(serverPlayer.serverLevel(), event.getPos());
			event.getLevel().playSound(null, event.getPos(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8f, 1.15f);
			playHammerUseSound(event.getLevel(), event.getPos(), SoundSource.BLOCKS);
			event.setCanceled(true);
		}
	}

	public static void boostChargedAttackDamage(LivingDamageEvent.Pre event) {
		DamageSource source = event.getSource();
		if (!(source.getEntity() instanceof Player player))
			return;
		if (source.getDirectEntity() != player)
			return;
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof PneumaticHammerItem) || !isCharged(stack))
			return;
		if (event.getEntity() == player)
			return;
		event.setNewDamage(event.getNewDamage() + CHARGED_TARGET_DAMAGE_BONUS);
	}

	public static void applyChargedAttackArea(LivingDamageEvent.Post event) {
		DamageSource source = event.getSource();
		if (!(source.getEntity() instanceof Player player))
			return;
		if (source.getDirectEntity() != player)
			return;
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof PneumaticHammerItem) || !isCharged(stack))
			return;

		setCharged(stack, false);
		LivingEntity primaryTarget = event.getEntity();
		Level level = primaryTarget.level();
		AABB area = primaryTarget.getBoundingBox().inflate(1.5);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
			if (target == player || target == primaryTarget || !target.isAlive())
				continue;
			target.hurt(level.damageSources().playerAttack(player), IRON_SWORD_SWEEP_DAMAGE);
		}
		level.playSound(null, primaryTarget.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.65f, 1.25f);
		playHammerUseSound(level, primaryTarget.blockPosition(), SoundSource.PLAYERS);
	}

	private static void playHammerUseSound(Level level, BlockPos pos, SoundSource source) {
		level.playSound(null, pos, SoundType.NETHERITE_BLOCK.getPlaceSound(), source, 0.8f, 1.0f);
	}

	private static float getToolSpeed(BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)
			|| state.is(BlockTags.MINEABLE_WITH_AXE)
			|| state.is(BlockTags.MINEABLE_WITH_SHOVEL))
			return STONE_TOOL_SPEED;
		return 0;
	}

	private static boolean canHarvestNormally(BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE))
			return !state.is(BlockTags.NEEDS_DIAMOND_TOOL);
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL))
			return !state.is(BlockTags.NEEDS_IRON_TOOL) && !state.is(BlockTags.NEEDS_DIAMOND_TOOL);
		return false;
	}

	private static boolean canHarvestCharged(BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
			|| state.is(BlockTags.MINEABLE_WITH_AXE)
			|| state.is(BlockTags.MINEABLE_WITH_SHOVEL);
	}

	private static List<BlockPos> collectBreakPositions(BlockPos center, Direction face) {
		Direction.Axis axis = face == null ? Direction.Axis.Y : face.getAxis();
		return switch (axis) {
			case X -> BlockPos.betweenClosedStream(center.offset(0, -1, -1), center.offset(0, 1, 1)).map(BlockPos::immutable).toList();
			case Y -> BlockPos.betweenClosedStream(center.offset(-1, 0, -1), center.offset(1, 0, 1)).map(BlockPos::immutable).toList();
			case Z -> BlockPos.betweenClosedStream(center.offset(-1, -1, 0), center.offset(1, 1, 0)).map(BlockPos::immutable).toList();
		};
	}

	private static void spawnChargedBreakParticles(ServerLevel level, BlockPos pos) {
		level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			2, 0.35, 0.35, 0.35, 0.02);
	}

	private static void completeCharge(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof Player player))
			return;
		if (isCharged(stack))
			return;
		if (level.isClientSide)
			return;

		if (consumeAir(player, CHARGE_AIR_COST)) {
			setCharged(stack, true);
			level.playSound(null, player.blockPosition(), CreateFireFightingAdd.PNEUMATIC_HAMMER_CHARGE_SOUND.get(),
				SoundSource.PLAYERS, 1.0f, 1.0f);
			player.displayClientMessage(Component.translatable("createfirefightingadd.pneumatic_hammer.charged"), true);
		} else {
			player.displayClientMessage(Component.translatable("createfirefightingadd.pneumatic_hammer.not_enough_air"), true);
		}
	}

	private static int getAvailableAir(Player player) {
		if (player.isCreative())
			return CHARGE_AIR_COST;
		int air = 0;
		for (ItemStack backtank : BacktankUtil.getAllWithAir(player))
			air += BacktankUtil.getAir(backtank);
		return air;
	}

	private static boolean consumeAir(Player player, int amount) {
		if (player.isCreative())
			return true;
		if (getAvailableAir(player) < amount)
			return false;

		int remaining = amount;
		for (ItemStack backtank : BacktankUtil.getAllWithAir(player)) {
			int toConsume = Math.min(remaining, BacktankUtil.getAir(backtank));
			BacktankUtil.consumeAir(player, backtank, toConsume);
			remaining -= toConsume;
			if (remaining <= 0)
				return true;
		}
		return false;
	}
}
