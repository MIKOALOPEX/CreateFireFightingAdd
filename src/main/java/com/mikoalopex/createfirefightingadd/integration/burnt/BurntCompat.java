package com.mikoalopex.createfirefightingadd.integration.burnt;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Optional bridge to Burnt/Burnt Basic.
 *
 * <p>The mod keeps its fire and smoldering conversion tables behind procedure
 * classes, so this class deliberately uses reflection. That keeps Burnt out of
 * the required dependency set while still letting water sprays call Burnt's own
 * single-block extinguish rules when the mod is present.
 */
public final class BurntCompat {
	private static final String MOD_ID = "burnt";
	private static final String EXTINGUISH_PROCEDURE =
		"net.pixelbank.burnt.procedures.ExtinguishProcedure";

	private static final TagKey<Block> FIRE = tag("fire");
	private static final TagKey<Block> ACTIVE_FIRE = tag("active_fire");
	private static final TagKey<Block> ON_FIRE = tag("on_fire");
	private static final TagKey<Block> WOOD_FIRE = tag("wood_fire");
	private static final TagKey<Block> COPPER_FIRE = tag("copper_fire");
	private static final TagKey<Block> TALL_FLAMES = tag("tall_flames");
	private static final TagKey<Block> SMOLDERING_LEAVES = tag("smoldering_leaves");
	private static final TagKey<Block> SMOLDERING_LOGS = tag("smoldering_logs");
	private static final TagKey<Block> SMOLDERING_PLANKS = tag("smoldering_planks");
	private static final TagKey<Block> BURNING_LOGS = tag("burning_logs");
	private static final TagKey<Block> BURNING_WOOD = tag("burning_wood");
	private static final TagKey<Block> BURNING_STRIPPED_LOGS = tag("burning_stripped_logs");
	private static final TagKey<Block> BURNING_STRIPPED_WOOD = tag("burning_stripped_wood");
	private static final TagKey<Block> BURNING_PLANKS = tag("burning_planks");
	private static final TagKey<Block> BURNING_STAIRS = tag("burning_stairs");
	private static final TagKey<Block> BURNING_SLABS = tag("burning_slabs");
	private static final TagKey<Block> BURNING_FENCES = tag("burning_fences");
	private static final TagKey<Block> BURNING_FENCE_GATES = tag("burning_fence_gates");

	private static boolean checked;
	private static boolean available;
	private static Method extinguishAt;

	private BurntCompat() {
	}

	/**
	 * Fast pre-filter used before invoking Burnt's procedure.
	 *
	 * <p>Burnt Basic has a mixture of tags and generated block names. The name
	 * check catches smoldering blocks that are hard-coded in the procedure rather
	 * than exposed through a shared tag.
	 */
	public static boolean mightHandle(BlockState state) {
		if (!isLoaded())
			return false;
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (id.getPath().contains("smoldering") || id.getPath().contains("burning")
			|| id.getPath().contains("fire") || id.getPath().contains("flame"))
			return true;
		return state.is(FIRE)
			|| state.is(ACTIVE_FIRE)
			|| state.is(ON_FIRE)
			|| state.is(WOOD_FIRE)
			|| state.is(COPPER_FIRE)
			|| state.is(TALL_FLAMES)
			|| state.is(SMOLDERING_LEAVES)
			|| state.is(SMOLDERING_LOGS)
			|| state.is(SMOLDERING_PLANKS)
			|| state.is(BURNING_LOGS)
			|| state.is(BURNING_WOOD)
			|| state.is(BURNING_STRIPPED_LOGS)
			|| state.is(BURNING_STRIPPED_WOOD)
			|| state.is(BURNING_PLANKS)
			|| state.is(BURNING_STAIRS)
			|| state.is(BURNING_SLABS)
			|| state.is(BURNING_FENCES)
			|| state.is(BURNING_FENCE_GATES);
	}

	public static boolean extinguishAt(LevelAccessor level, BlockPos pos, BlockState state) {
		if (!mightHandle(state))
			return false;
		if (!init())
			return false;

		BlockState before = level.getBlockState(pos);
		try {
			extinguishAt.invoke(null, level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
		} catch (ReflectiveOperationException | RuntimeException e) {
			available = false;
			return false;
		}
		return !level.getBlockState(pos).equals(before);
	}

	private static boolean isLoaded() {
		return ModList.get().isLoaded(MOD_ID);
	}

	private static boolean init() {
		if (checked)
			return available;
		checked = true;
		if (!isLoaded())
			return false;
		try {
			Class<?> procedure = Class.forName(EXTINGUISH_PROCEDURE);
			extinguishAt = procedure.getMethod("execute",
				LevelAccessor.class, double.class, double.class, double.class);
			available = true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			available = false;
		}
		return available;
	}

	private static TagKey<Block> tag(String path) {
		return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
	}
}
