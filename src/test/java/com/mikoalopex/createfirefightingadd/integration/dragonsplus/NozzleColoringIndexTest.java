package com.mikoalopex.createfirefightingadd.integration.dragonsplus;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/** Standalone checks for the Dragons Plus coloring index. */
public final class NozzleColoringIndexTest {
	private static final ResourceLocation RED = id("minecraft:red");
	private static final ResourceLocation BLUE = id("test:blue");
	private static final ResourceLocation WHITE = id("minecraft:white");

	private NozzleColoringIndexTest() {
	}

	public static void main(String[] args) {
		bootstrap();

		woolRedye();
		decodedWoolAlternatives();
		nestedCompoundIngredients();
		shapedWoolRecipeStillWorks();
		eightBlockRecipe();
		mixedEightBlockRecipeRejected();
		noRecipeAndSameColor();
		incompatibleAndBlockEntity();
		randomExplicitOutputRejected();
		outputComponentsRejected();
		nonSimpleIngredientRejected();
		dynamicCraftingSubclassRejected();
		explicitPriorityAndConflict();
		rebuildReplacesOldIndex();

		System.out.println("Dragons Plus coloring index checks passed");
	}

	private static void woolRedye() {
		NozzleColoringIndex.clear();
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(recipe("blue_wool", stack(Blocks.BLUE_WOOL),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Items.WHITE_WOOL, Items.RED_WOOL))),
			RegistryAccess.EMPTY, variants());

		BlockState result = NozzleColoringIndex.recolor(Blocks.RED_WOOL.defaultBlockState(), BLUE);
		check(result != null && result.getBlock() == Blocks.BLUE_WOOL, "red wool should be re-dyeable to blue");
		BlockState white = NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE);
		check(white != null && white.getBlock() == Blocks.BLUE_WOOL, "white wool should be dyeable to blue");
	}

	private static void shapedWoolRecipeStillWorks() {
		NozzleColoringIndex.clear();
		ShapedRecipePattern pattern = ShapedRecipePattern.of(
			Map.of('D', Ingredient.of(Items.BLUE_DYE), 'W', Ingredient.of(Blocks.WHITE_WOOL)),
			"DW");
		CraftingRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, stack(Blocks.BLUE_WOOL));
		RecipeHolder<CraftingRecipe> holder = new RecipeHolder<>(id("test:shaped_blue_wool"), recipe);
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(holder), RegistryAccess.EMPTY, variants());
		BlockState result = NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE);
		check(result != null && result.getBlock() == Blocks.BLUE_WOOL, "standard shaped recipes must still be indexed");
	}

	private static void decodedWoolAlternatives() {
		// Vanilla wool re-dyeing recipes encode the source colors as a JSON array.
		Ingredient sources = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, JsonParser.parseString("""
			[{"item":"minecraft:white_wool"},{"item":"minecraft:red_wool"}]
			""")).getOrThrow();
		check(sources.getCustomIngredient() instanceof CompoundIngredient,
			"array ingredients should exercise NeoForge's compound representation");
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(recipe("decoded_blue_wool", stack(Blocks.BLUE_WOOL),
			Ingredient.of(Items.BLUE_DYE), sources)), RegistryAccess.EMPTY, variants());
		for (var source : List.of(Blocks.WHITE_WOOL, Blocks.RED_WOOL)) {
			BlockState result = NozzleColoringIndex.recolor(source.defaultBlockState(), BLUE);
			check(result != null && result.getBlock() == Blocks.BLUE_WOOL,
				"decoded wool alternatives must be indexed for re-dyeing");
		}
	}

	private static void eightBlockRecipe() {
		NozzleColoringIndex.clear();
		Ingredient[] ingredients = new Ingredient[9];
		ingredients[0] = Ingredient.of(Items.RED_DYE);
		for (int i = 1; i < ingredients.length; i++)
			ingredients[i] = Ingredient.of(Blocks.TERRACOTTA);
		RecipeHolder<CraftingRecipe> recipe = recipe("red_terracotta", new ItemStack(Blocks.RED_TERRACOTTA, 8), ingredients);
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(recipe), RegistryAccess.EMPTY, variants());

		BlockState result = NozzleColoringIndex.recolor(Blocks.TERRACOTTA.defaultBlockState(), RED);
		check(result != null && result.getBlock() == Blocks.RED_TERRACOTTA, "eight-block recipe should be indexed");
	}

	private static void nestedCompoundIngredients() {
		Ingredient dye = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, JsonParser.parseString("""
			[{"item":"minecraft:blue_dye"},{"item":"minecraft:blue_dye"}]
			""")).getOrThrow();
		Ingredient sources = CompoundIngredient.of(Ingredient.of(Items.WHITE_WOOL),
			CompoundIngredient.of(Ingredient.of(Items.RED_WOOL), Ingredient.of(Items.BLACK_WOOL)));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(recipe("nested_wool", stack(Blocks.BLUE_WOOL),
			dye, sources)), RegistryAccess.EMPTY, variants());
		for (var source : List.of(Blocks.WHITE_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL)) {
			BlockState result = NozzleColoringIndex.recolor(source.defaultBlockState(), BLUE);
			check(result != null && result.getBlock() == Blocks.BLUE_WOOL,
				"simple nested unions must work for both dye and block ingredients");
		}

		Ingredient dynamicSources = CompoundIngredient.of(Ingredient.of(Items.RED_WOOL),
			new Ingredient(new DynamicIngredient()));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(recipe("dynamic_union", stack(Blocks.BLUE_WOOL),
			dye, dynamicSources)), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.RED_WOOL.defaultBlockState(), BLUE) == null,
			"a union with a dynamic child must still be rejected");
	}

	private static void mixedEightBlockRecipeRejected() {
		NozzleColoringIndex.clear();
		Ingredient[] ingredients = new Ingredient[9];
		ingredients[0] = Ingredient.of(Items.RED_DYE);
		for (int i = 1; i < 5; i++)
			ingredients[i] = Ingredient.of(Blocks.SAND);
		for (int i = 5; i < ingredients.length; i++)
			ingredients[i] = Ingredient.of(Blocks.GRAVEL);
		RecipeHolder<CraftingRecipe> recipe = recipe("red_concrete_powder", new ItemStack(Blocks.RED_CONCRETE_POWDER, 8), ingredients);
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(recipe), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.SAND.defaultBlockState(), RED) == null,
			"a nine-item recipe with mixed materials is not a valid dye recipe");
	}

	private static void noRecipeAndSameColor() {
		NozzleColoringIndex.clear();
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.DIRT.defaultBlockState(), RED) == null,
			"a block without a recipe must not be colored");

		RecipeHolder<CraftingRecipe> same = recipe("white_wool_again", stack(Blocks.WHITE_WOOL),
			Ingredient.of(Items.WHITE_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(same), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), WHITE) == null,
			"same-block color mapping must be ignored");
	}

	private static void incompatibleAndBlockEntity() {
		NozzleColoringIndex.clear();
		RecipeHolder<CraftingRecipe> hostileState = recipe("stairs_to_stone", stack(Blocks.STONE),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.OAK_STAIRS));
		RecipeHolder<CraftingRecipe> blockEntityOutput = recipe("wool_to_chest", stack(Blocks.CHEST),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		RecipeHolder<CraftingRecipe> blockEntityInput = recipe("chest_to_wool", stack(Blocks.BLUE_WOOL),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.CHEST));
		RecipeHolder<CraftingRecipe> multiBlockOutput = recipe("wool_to_door", stack(Blocks.OAK_DOOR),
			Ingredient.of(Items.RED_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(
			hostileState, blockEntityOutput, blockEntityInput, multiBlockOutput), RegistryAccess.EMPTY, variants());

		check(NozzleColoringIndex.recolor(Blocks.OAK_STAIRS.defaultBlockState(), BLUE) == null,
			"incompatible state properties must be rejected");
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"block entity outputs and inputs must be rejected");
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), RED) == null,
			"multi-block outputs must be rejected");

		RecipeHolder<CraftingRecipe> compatible = recipe("oak_to_spruce_stairs", stack(Blocks.SPRUCE_STAIRS),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.OAK_STAIRS));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(compatible), RegistryAccess.EMPTY, variants());
		BlockState source = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(StairBlock.FACING, Direction.WEST)
			.setValue(StairBlock.HALF, Half.TOP)
			.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT)
			.setValue(StairBlock.WATERLOGGED, true);
		BlockState result = NozzleColoringIndex.recolor(source, BLUE);
		check(result != null && result.getBlock() == Blocks.SPRUCE_STAIRS, "compatible stairs should be recolored");
		check(result.getValue(StairBlock.FACING) == Direction.WEST, "facing should be preserved");
		check(result.getValue(StairBlock.HALF) == Half.TOP, "half should be preserved");
		check(result.getValue(StairBlock.SHAPE) == StairsShape.OUTER_LEFT, "shape should be preserved");
		check(result.getValue(StairBlock.WATERLOGGED), "waterlogged should be preserved");
	}

	private static void randomExplicitOutputRejected() {
		NozzleColoringIndex.clear();
		var random = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL)),
			List.of(new ProcessingOutput(stack(Blocks.BLUE_WOOL), 0.5F)),
			true,
			true);
		NozzleColoringIndex.rebuildIndex(List.of(random), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"random explicit output must be rejected");

		var doubled = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL)),
			List.of(new ProcessingOutput(new ItemStack(Blocks.BLUE_WOOL, 2), 1.0F)),
			true,
			true);
		NozzleColoringIndex.rebuildIndex(List.of(doubled), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"explicit output count other than one must be rejected");

		var fluid = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL)),
			List.of(new ProcessingOutput(stack(Blocks.BLUE_WOOL), 1.0F)),
			false,
			true);
		NozzleColoringIndex.rebuildIndex(List.of(fluid), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"explicit fluid input must be rejected");

		var twoInputs = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL), Ingredient.of(Blocks.BLUE_WOOL)),
			List.of(new ProcessingOutput(stack(Blocks.BLUE_WOOL), 1.0F)),
			true,
			true);
		NozzleColoringIndex.rebuildIndex(List.of(twoInputs), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"explicit recipes must have exactly one input");
	}

	private static void outputComponentsRejected() {
		NozzleColoringIndex.clear();
		ItemStack named = stack(Blocks.BLUE_WOOL);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		var namedExplicit = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL)),
			List.of(new ProcessingOutput(named, 1.0F)),
			true,
			true);
		NozzleColoringIndex.rebuildIndex(List.of(namedExplicit), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"explicit output with custom name components must be rejected");

		ItemStack stateful = stack(Blocks.BLUE_WOOL);
		stateful.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("facing", "east")));
		var statefulExplicit = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL)),
			List.of(new ProcessingOutput(stateful, 1.0F)),
			true,
			true);
		NozzleColoringIndex.rebuildIndex(List.of(statefulExplicit), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"explicit output with block state components must be rejected");

		ItemStack crafted = stack(Blocks.BLUE_WOOL);
		crafted.set(DataComponents.CUSTOM_NAME, Component.literal("crafted"));
		RecipeHolder<CraftingRecipe> namedCrafting = recipe("named_wool", crafted,
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(namedCrafting), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"crafting output with custom name components must be rejected");
	}

	private static void nonSimpleIngredientRejected() {
		NozzleColoringIndex.clear();
		RecipeHolder<CraftingRecipe> recipe = recipe("dynamic_ingredient", stack(Blocks.BLUE_WOOL),
			Ingredient.of(Items.BLUE_DYE), new Ingredient(new DynamicIngredient()));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(recipe), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"non-simple custom ingredients must be rejected");
	}

	private static void dynamicCraftingSubclassRejected() {
		NozzleColoringIndex.clear();
		NonNullList<Ingredient> ingredients = NonNullList.withSize(2, Ingredient.EMPTY);
		ingredients.set(0, Ingredient.of(Items.BLUE_DYE));
		ingredients.set(1, Ingredient.of(Blocks.WHITE_WOOL));
		CraftingRecipe dynamic = new DynamicShapelessRecipe(stack(Blocks.RED_WOOL), ingredients);
		RecipeHolder<CraftingRecipe> holder = new RecipeHolder<>(id("test:dynamic_shapeless"), dynamic);
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(holder), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"non-standard crafting subclasses must be rejected");
	}

	private static void explicitPriorityAndConflict() {
		NozzleColoringIndex.clear();
		var explicit = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL)),
			List.of(new ProcessingOutput(stack(Blocks.BLUE_WOOL), 1.0F)),
			true,
			true);
		RecipeHolder<CraftingRecipe> crafting = recipe("blue_dye_to_red_wool", stack(Blocks.RED_WOOL),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		NozzleColoringIndex.rebuildIndex(List.of(explicit), List.of(crafting), RegistryAccess.EMPTY, variants());
		BlockState result = NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE);
		check(result != null && result.getBlock() == Blocks.BLUE_WOOL, "explicit coloring must win over crafting");

		var conflicting = new NozzleColoringIndex.ExplicitColoring(
			BLUE,
			List.of(Ingredient.of(Blocks.WHITE_WOOL)),
			List.of(new ProcessingOutput(stack(Blocks.RED_WOOL), 1.0F)),
			true,
			true);
		NozzleColoringIndex.rebuildIndex(List.of(explicit, conflicting), List.of(), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"explicit conflicts must not produce a mapping");

		RecipeHolder<CraftingRecipe> blue = recipe("blue_wool_candidate", stack(Blocks.BLUE_WOOL),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		RecipeHolder<CraftingRecipe> red = recipe("red_wool_candidate", stack(Blocks.RED_WOOL),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(blue, red), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"crafting conflicts must not produce a mapping");
	}

	private static void rebuildReplacesOldIndex() {
		NozzleColoringIndex.clear();
		RecipeHolder<CraftingRecipe> blue = recipe("blue_wool", stack(Blocks.BLUE_WOOL),
			Ingredient.of(Items.BLUE_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(blue), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) != null,
			"first rebuild should install the blue mapping");

		RecipeHolder<CraftingRecipe> red = recipe("red_wool", stack(Blocks.RED_WOOL),
			Ingredient.of(Items.RED_DYE), Ingredient.of(Blocks.WHITE_WOOL));
		NozzleColoringIndex.rebuildIndex(List.of(), List.of(red), RegistryAccess.EMPTY, variants());
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), BLUE) == null,
			"rebuild must discard the old index");
		BlockState replacement = NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), RED);
		check(replacement != null && replacement.getBlock() == Blocks.RED_WOOL,
			"rebuild must install the new index");

		NozzleColoringIndex.clear();
		check(NozzleColoringIndex.recolor(Blocks.WHITE_WOOL.defaultBlockState(), RED) == null,
			"clear must remove every mapping");
	}

	private static List<NozzleColoringIndex.ColorVariant> variants() {
		return List.of(
			new NozzleColoringIndex.ColorVariant(RED, new ItemStack(Items.RED_DYE), tag("test:dyes/red")),
			new NozzleColoringIndex.ColorVariant(BLUE, new ItemStack(Items.BLUE_DYE), tag("test:dyes/blue")),
			new NozzleColoringIndex.ColorVariant(WHITE, new ItemStack(Items.WHITE_DYE), tag("test:dyes/white")));
	}

	private static RecipeHolder<CraftingRecipe> recipe(String path, ItemStack result, Ingredient... ingredients) {
		NonNullList<Ingredient> list = NonNullList.withSize(ingredients.length, Ingredient.EMPTY);
		for (int i = 0; i < ingredients.length; i++)
			list.set(i, ingredients[i]);
		CraftingRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, result, list);
		return new RecipeHolder<>(id("test:" + path), recipe);
	}

	private static ItemStack stack(net.minecraft.world.level.ItemLike item) {
		return new ItemStack(item);
	}

	private static ResourceLocation id(String value) {
		return ResourceLocation.parse(value);
	}

	private static TagKey<Item> tag(String value) {
		return TagKey.create(Registries.ITEM, id(value));
	}

	private static final class DynamicIngredient implements ICustomIngredient {
		@Override
		public boolean test(ItemStack stack) {
			return stack.is(Items.WHITE_WOOL);
		}

		@Override
		public Stream<ItemStack> getItems() {
			return Stream.of(new ItemStack(Items.WHITE_WOOL));
		}

		@Override
		public boolean isSimple() {
			return false;
		}

		@Override
		public IngredientType<?> getType() {
			return null;
		}
	}

	private static final class DynamicShapelessRecipe extends ShapelessRecipe {
		DynamicShapelessRecipe(ItemStack result, NonNullList<Ingredient> ingredients) {
			super("", CraftingBookCategory.MISC, result, ingredients);
		}

		@Override
		public ItemStack getResultItem(HolderLookup.Provider registries) {
			return stack(Blocks.RED_WOOL);
		}
	}

	private static void bootstrap() {
		SharedConstants.tryDetectVersion();
		net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		Bootstrap.bootStrap();
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
