package com.mikoalopex.createfirefightingadd.integration.dragonsplus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;

import plus.dragons.createdragonsplus.common.fluids.dye.DyeVariant;
import plus.dragons.createdragonsplus.common.fluids.dye.DyeVariantRegistry;
import plus.dragons.createdragonsplus.common.kinetics.fan.coloring.ColoringRecipe;
import plus.dragons.createdragonsplus.common.registry.CDPRecipes;

/**
 * Builds an immutable block-to-color lookup from Dragons Plus coloring recipes
 * and the narrow vanilla crafting shapes that Dragons Plus accepts as dyeing.
 * Recipe data is read during index rebuilding; {@link #recolor(BlockState, ResourceLocation)}
 * only reads cached mappings and copies compatible state properties.
 */
public final class NozzleColoringIndex {
	private static final TagKey<Item> NOT_APPLICABLE_COLORING = TagKey.create(
		Registries.ITEM,
		ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "not_applicable_for_coloring"));

	private static volatile Map<Key, BlockState> index = Map.of();

	private NozzleColoringIndex() {
	}

	/** Replaces the index from the live recipe and dye-variant registries. */
	public static void rebuild(RecipeManager recipeManager, HolderLookup.Provider registries) {
		var variants = new ArrayList<ColorVariant>();
		for (DyeVariant variant : DyeVariantRegistry.all()) {
			variants.add(new ColorVariant(variant.id(), variant.dyeItemStack(), variant.dyeItemTag()));
		}

		var explicitRecipes = new ArrayList<ExplicitColoring>();
		for (var holder : recipeManager.getAllRecipesFor(CDPRecipes.COLORING.getType())) {
			ColoringRecipe recipe = holder.value();
			explicitRecipes.add(new ExplicitColoring(
				recipe.getColor(),
				List.copyOf(recipe.getIngredients()),
				List.copyOf(recipe.getRollableResults()),
				recipe.getFluidIngredients().isEmpty(),
				recipe.getFluidResults().isEmpty()));
		}

		List<RecipeHolder<CraftingRecipe>> craftingRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
		rebuildIndex(explicitRecipes, craftingRecipes, registries, variants);
	}

	/**
	 * Returns the recolored state for one block and color, or {@code null}.
	 * This method only reads the immutable index and never mutates the world.
	 */
	public static @Nullable BlockState recolor(BlockState source, ResourceLocation color) {
		if (source == null || color == null)
			return null;
		BlockState target = index.get(new Key(source.getBlock(), color));
		if (target == null || target.getBlock() == source.getBlock())
			return null;
		return copyCompatibleState(source, target);
	}

	/** Drops every mapping and leaves the index empty. */
	public static void clear() {
		index = Map.of();
	}

	/**
	 * Visible for the standalone regression test. The production entry point
	 * above supplies the same inputs from the live recipe and dye-variant registries.
	 */
	static void rebuildIndex(
		List<ExplicitColoring> explicitRecipes,
		List<RecipeHolder<CraftingRecipe>> craftingRecipes,
		HolderLookup.Provider registries,
		List<ColorVariant> variants) {
		var variantsById = new LinkedHashMap<ResourceLocation, ColorVariant>();
		for (ColorVariant variant : variants)
			variantsById.put(variant.id(), variant);

		var explicit = new HashMap<Key, BlockState>();
		var blocked = new HashSet<Key>();

		for (ExplicitColoring recipe : explicitRecipes) {
			if (!isUsableExplicitRecipe(recipe) || !variantsById.containsKey(recipe.color()))
				continue;
			Block output = outputBlock(recipe.outputs());
			if (output == null || isUnsafeBlock(output))
				continue;
			List<Block> inputs = blockItems(recipe.ingredients().get(0));
			if (inputs == null)
				continue;
			for (Block input : inputs) {
				if (input == output || isUnsafeBlock(input))
					continue;
				Key key = new Key(input, recipe.color());
				BlockState target = output.defaultBlockState();
				BlockState previous = explicit.putIfAbsent(key, target);
				if (previous != null && previous.getBlock() != output)
					blocked.add(key);
			}
		}

		var next = new HashMap<Key, BlockState>();
		var explicitKeys = new HashSet<Key>();
		for (var entry : explicit.entrySet()) {
			if (!blocked.contains(entry.getKey())) {
				next.put(entry.getKey(), entry.getValue());
				explicitKeys.add(entry.getKey());
			}
		}

		// Crafting recipes are scanned once, never once per block and color.
		var craftingConflicts = new HashSet<Key>();
		for (RecipeHolder<CraftingRecipe> holder : craftingRecipes) {
			CraftingRecipe recipe = holder.value();
			// Only the deterministic vanilla recipe classes; subclasses may override matching or assembly.
			if (recipe.getClass() != ShapedRecipe.class && recipe.getClass() != ShapelessRecipe.class)
				continue;
			if (recipe.isSpecial())
				continue;
			ItemStack result = recipe.getResultItem(registries);
			// Reject component-bearing results because only the target BlockState survives.
			if (result.isEmpty() || !result.getComponentsPatch().isEmpty()
				|| !(result.getItem() instanceof BlockItem resultItem))
				continue;
			Block output = resultItem.getBlock();
			if (output == Blocks.AIR || isUnsafeBlock(output))
				continue;
			CraftingCandidate candidate = craftingCandidate(recipe, result, variants);
			if (candidate == null)
				continue;
			List<Block> inputs = blockItems(candidate.input());
			if (inputs == null)
				continue;
			for (Block input : inputs) {
				if (input == output || isUnsafeBlock(input))
					continue;
				Key key = new Key(input, candidate.color());
				if (blocked.contains(key) || explicitKeys.contains(key) || craftingConflicts.contains(key))
					continue;
				BlockState target = output.defaultBlockState();
				BlockState previous = next.putIfAbsent(key, target);
				if (previous != null && previous.getBlock() != output) {
					next.remove(key);
					craftingConflicts.add(key);
				}
			}
		}

		index = Map.copyOf(next);
	}

	private static boolean isUsableExplicitRecipe(ExplicitColoring recipe) {
		if (recipe.color() == null || !recipe.noFluidIngredients() || !recipe.noFluidResults())
			return false;
		if (recipe.ingredients().size() != 1 || recipe.outputs().size() != 1)
			return false;
		Ingredient input = recipe.ingredients().get(0);
		return !input.hasNoItems();
	}

	private static @Nullable Block outputBlock(List<ProcessingOutput> outputs) {
		if (outputs.size() != 1)
			return null;
		ProcessingOutput output = outputs.get(0);
		if (Float.compare(output.getChance(), 1.0F) != 0)
			return null;
		ItemStack stack = output.getStack();
		// A block-state result cannot preserve item components.
		if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty() || stack.getCount() != 1
			|| !(stack.getItem() instanceof BlockItem item))
			return null;
		Block block = item.getBlock();
		return block == Blocks.AIR ? null : block;
	}

	private static @Nullable CraftingCandidate craftingCandidate(
		CraftingRecipe recipe,
		ItemStack result,
		List<ColorVariant> variants) {
		var ingredients = recipe.getIngredients();
		if (recipe.canCraftInDimensions(2, 1) && ingredients.size() == 2 && result.getCount() == 1) {
			Set<ResourceLocation> first = matchingVariants(ingredients.get(0), variants);
			Set<ResourceLocation> second = matchingVariants(ingredients.get(1), variants);
			if (first.size() == 1 && second.isEmpty())
				return new CraftingCandidate(first.iterator().next(), ingredients.get(1));
			if (second.size() == 1 && first.isEmpty())
				return new CraftingCandidate(second.iterator().next(), ingredients.get(0));
			return null;
		}

		if (recipe.canCraftInDimensions(3, 3) && ingredients.size() == 9 && result.getCount() == 8) {
			ResourceLocation color = null;
			Ingredient dyeable = null;
			for (Ingredient ingredient : ingredients) {
				Set<ResourceLocation> matches = matchingVariants(ingredient, variants);
				if (matches.size() > 1)
					return null;
				if (matches.size() == 1) {
					if (color != null)
						return null;
					color = matches.iterator().next();
				} else if (dyeable == null) {
					dyeable = ingredient;
				} else if (!sameBlockItems(dyeable, ingredient)) {
					return null;
				}
			}
			if (color == null || dyeable == null)
				return null;
			return new CraftingCandidate(color, dyeable);
		}
		return null;
	}

	private static Set<ResourceLocation> matchingVariants(Ingredient ingredient, List<ColorVariant> variants) {
		var matches = new LinkedHashSet<ResourceLocation>();
		if (!hasEnumerableItems(ingredient))
			return matches;
		ItemStack[] items = ingredient.getItems();
		if (items.length == 0)
			return matches;
		for (ColorVariant variant : variants) {
			if (variant.matches(ingredient, items))
				matches.add(variant.id());
		}
		return matches;
	}

	private static boolean sameBlockItems(Ingredient first, Ingredient second) {
		Set<Block> firstBlocks = blockItemSet(first);
		Set<Block> secondBlocks = blockItemSet(second);
		return firstBlocks != null && firstBlocks.equals(secondBlocks);
	}

	private static @Nullable List<Block> blockItems(Ingredient ingredient) {
		Set<Block> blocks = blockItemSet(ingredient);
		return blocks == null ? null : List.copyOf(blocks);
	}

	private static @Nullable Set<Block> blockItemSet(Ingredient ingredient) {
		if (!hasEnumerableItems(ingredient))
			return null;

		var blocks = new LinkedHashSet<Block>();
		for (ItemStack stack : ingredient.getItems()) {
			if (stack.isEmpty() || stack.is(NOT_APPLICABLE_COLORING))
				continue;
			if (!(stack.getItem() instanceof BlockItem item))
				return null;
			Block block = item.getBlock();
			if (block == Blocks.AIR)
				return null;
			// Reject the ingredient if a displayed item is not accepted as a default stack.
			if (!ingredient.test(new ItemStack(item)))
				return null;
			blocks.add(block);
		}
		return blocks.isEmpty() ? null : blocks;
	}

	private static boolean hasEnumerableItems(Ingredient ingredient) {
		if (!ingredient.isSimple())
			return false;
		if (!ingredient.isCustom())
			return true;
		// NeoForge decodes vanilla ingredient arrays as unions of child ingredients.
		// Other custom ingredients may expose display samples instead of every accepted item.
		return ingredient.getCustomIngredient() instanceof CompoundIngredient compound
			&& compound.children().stream().allMatch(NozzleColoringIndex::hasEnumerableItems);
	}

	private static boolean isUnsafeBlock(Block block) {
		if (block == Blocks.AIR)
			return true;
		BlockState state = block.defaultBlockState();
		return state.hasBlockEntity() || isMultiBlock(state);
	}

	private static boolean isMultiBlock(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof DoorBlock || block instanceof DoublePlantBlock)
			return true;
		for (Property<?> property : state.getProperties()) {
			if ("part".equals(property.getName()))
				return true;
		}
		return false;
	}

	private static @Nullable BlockState copyCompatibleState(BlockState source, BlockState target) {
		if (source.hasBlockEntity() || target.hasBlockEntity())
			return null;
		if (isMultiBlock(source) || isMultiBlock(target))
			return null;

		// Every source property must exist on the target; target-only properties keep defaults.
		BlockState result = target;
		for (Property<?> sourceProperty : source.getProperties()) {
			Property<?> targetProperty = findProperty(target, sourceProperty.getName());
			if (targetProperty == null || sourceProperty.getValueClass() != targetProperty.getValueClass())
				return null;
			try {
				result = copyProperty(result, targetProperty, source.getValue(sourceProperty));
			} catch (IllegalArgumentException exception) {
				return null;
			}
		}
		return result;
	}

	private static @Nullable Property<?> findProperty(BlockState state, String name) {
		for (Property<?> property : state.getProperties()) {
			if (name.equals(property.getName()))
				return property;
		}
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static BlockState copyProperty(BlockState state, Property property, Comparable value) {
		return state.setValue(property, value);
	}

	private record Key(Block block, ResourceLocation color) {
	}

	record ColorVariant(ResourceLocation id, ItemStack dyeStack, TagKey<Item> dyeTag) {
		ColorVariant {
			dyeStack = dyeStack.copy();
		}

		boolean matches(Ingredient ingredient, ItemStack[] ingredientItems) {
			if (!dyeStack.isEmpty() && ingredient.test(dyeStack))
				return true;
			if (dyeTag == null)
				return false;
			for (ItemStack stack : ingredientItems) {
				if (!stack.isEmpty() && stack.is(dyeTag))
					return true;
			}
			return false;
		}
	}

	record ExplicitColoring(
		ResourceLocation color,
		List<Ingredient> ingredients,
		List<ProcessingOutput> outputs,
		boolean noFluidIngredients,
		boolean noFluidResults) {
		ExplicitColoring {
			ingredients = List.copyOf(ingredients);
			outputs = List.copyOf(outputs);
		}
	}

	private record CraftingCandidate(ResourceLocation color, Ingredient input) {
	}
}
