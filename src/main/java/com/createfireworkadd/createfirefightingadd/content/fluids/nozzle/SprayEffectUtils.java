package com.createfireworkadd.createfirefightingadd.content.fluids.nozzle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SprayEffectUtils {

	private static boolean tfcAvailable;
	private static boolean tfcChecked;
	private static Method tfcDouseMethod;

	private static boolean wildfireAvailable;
	private static boolean wildfireChecked;
	private static Class<?> smolderTrackerClass;
	private static Method smolderGetMethod;
	private static Field smolderStrengthField;
	private static Field smolderExpireField;

	private static Method fireGetStateMethod;
	private static boolean fireGetStateReady;

	public static boolean tryTfcDouse(Level level, BlockPos pos) {
		if (!tfcChecked) {
			tfcChecked = true;
			try {
				Class<?> clazz = Class.forName("net.dries007.tfc.util.events.DouseFireEvent");
				tfcDouseMethod = clazz.getMethod("douse", Level.class, BlockPos.class, net.minecraft.world.entity.player.Player.class);
				tfcAvailable = true;
			} catch (Exception e) {
				tfcAvailable = false;
			}
		}
		if (!tfcAvailable)
			return false;
		try {
			return (boolean) tfcDouseMethod.invoke(null, level, pos, null);
		} catch (Exception e) {
			return false;
		}
	}

	private static void initWildfireReflection() {
		if (wildfireChecked) return;
		wildfireChecked = true;
		try {
			smolderTrackerClass = Class.forName("com.tfcwildfire.wildfire.SmolderTracker");
			smolderGetMethod = smolderTrackerClass.getMethod("get", ServerLevel.class);
			smolderStrengthField = smolderTrackerClass.getDeclaredField("strengthByPos");
			smolderStrengthField.setAccessible(true);
			smolderExpireField = smolderTrackerClass.getDeclaredField("expireByPos");
			smolderExpireField.setAccessible(true);
			wildfireAvailable = true;
		} catch (Exception e) {
			wildfireAvailable = false;
		}
	}

	public static void clearWildfireHeat(Level level, BlockPos pos) {
		initWildfireReflection();
		if (!wildfireAvailable || !(level instanceof ServerLevel serverLevel))
			return;
		try {
			Object tracker = smolderGetMethod.invoke(null, serverLevel);
			if (tracker == null) return;
			long key = pos.asLong();
			((it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap) smolderStrengthField.get(tracker)).remove(key);
			((it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap) smolderExpireField.get(tracker)).remove(key);
		} catch (Exception ignored) {
		}
	}

	private static final FireBlock FIRE_BLOCK = (FireBlock) Blocks.FIRE;
	private static Method canBurnMethod;
	private static boolean canBurnMethodReady;

	public static boolean canBurn(BlockState state) {
		if (!canBurnMethodReady) {
			canBurnMethodReady = true;
			Class<?> clazz = FireBlock.class;
			while (clazz != null) {
				try {
					canBurnMethod = clazz.getDeclaredMethod("canBurn", BlockState.class);
					canBurnMethod.setAccessible(true);
					break;
				} catch (NoSuchMethodException e) {
					clazz = clazz.getSuperclass();
				}
			}
		}
		if (canBurnMethod == null)
			return false;
		try {
			return (boolean) canBurnMethod.invoke(FIRE_BLOCK, state);
		} catch (Exception e) {
			return false;
		}
	}

	public static BlockState getFireState(Level level, BlockPos pos) {
		if (!fireGetStateReady) {
			fireGetStateReady = true;
			try {
				fireGetStateMethod = BaseFireBlock.class.getDeclaredMethod("getState", LevelReader.class, BlockPos.class);
				fireGetStateMethod.setAccessible(true);
			} catch (NoSuchMethodException ignored) {
			}
		}
		if (fireGetStateMethod == null)
			return Blocks.FIRE.defaultBlockState();
		try {
			return (BlockState) fireGetStateMethod.invoke(null, level, pos);
		} catch (Exception e) {
			return Blocks.FIRE.defaultBlockState();
		}
	}

	public static void extinguishBlock(Level level, BlockPos pos, BlockState state) {
		if (tryTfcDouse(level, pos))
			return;
		if (state.getBlock() instanceof BaseFireBlock) {
			level.removeBlock(pos, false);
			clearWildfireHeat(level, pos);
		} else if (state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
				&& state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
			level.setBlock(pos, state.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), 3);
		}
	}
}
