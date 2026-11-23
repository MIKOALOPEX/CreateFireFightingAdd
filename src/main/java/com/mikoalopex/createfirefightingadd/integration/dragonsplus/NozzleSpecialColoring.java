package com.mikoalopex.createfirefightingadd.integration.dragonsplus;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure block-state mapping for special nozzle dye targets.
 *
 * <p>This class only computes a replacement state. It never writes to a level and never calls
 * Aeronautics' dye interaction, so it cannot trigger that method's neighbour/BFS behaviour.
 *
 * <p>Only exact, explicitly listed block IDs are mapped. A non-vanilla namespace or an unknown
 * dye name returns {@code null}, leaving those colors to the recipe-derived index.
 */
public final class NozzleSpecialColoring {
	private static final String MINECRAFT = "minecraft";
	private static final String AERONAUTICS = "aeronautics";

	private static final List<VanillaColor> VANILLA_COLORS = List.of(
		new VanillaColor(DyeColor.WHITE, Blocks.WHITE_CONCRETE, Blocks.WHITE_CONCRETE_POWDER),
		new VanillaColor(DyeColor.ORANGE, Blocks.ORANGE_CONCRETE, Blocks.ORANGE_CONCRETE_POWDER),
		new VanillaColor(DyeColor.MAGENTA, Blocks.MAGENTA_CONCRETE, Blocks.MAGENTA_CONCRETE_POWDER),
		new VanillaColor(DyeColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE_POWDER),
		new VanillaColor(DyeColor.YELLOW, Blocks.YELLOW_CONCRETE, Blocks.YELLOW_CONCRETE_POWDER),
		new VanillaColor(DyeColor.LIME, Blocks.LIME_CONCRETE, Blocks.LIME_CONCRETE_POWDER),
		new VanillaColor(DyeColor.PINK, Blocks.PINK_CONCRETE, Blocks.PINK_CONCRETE_POWDER),
		new VanillaColor(DyeColor.GRAY, Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE_POWDER),
		new VanillaColor(DyeColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE_POWDER),
		new VanillaColor(DyeColor.CYAN, Blocks.CYAN_CONCRETE, Blocks.CYAN_CONCRETE_POWDER),
		new VanillaColor(DyeColor.PURPLE, Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CONCRETE_POWDER),
		new VanillaColor(DyeColor.BLUE, Blocks.BLUE_CONCRETE, Blocks.BLUE_CONCRETE_POWDER),
		new VanillaColor(DyeColor.BROWN, Blocks.BROWN_CONCRETE, Blocks.BROWN_CONCRETE_POWDER),
		new VanillaColor(DyeColor.GREEN, Blocks.GREEN_CONCRETE, Blocks.GREEN_CONCRETE_POWDER),
		new VanillaColor(DyeColor.RED, Blocks.RED_CONCRETE, Blocks.RED_CONCRETE_POWDER),
		new VanillaColor(DyeColor.BLACK, Blocks.BLACK_CONCRETE, Blocks.BLACK_CONCRETE_POWDER)
	);

	private static final List<AeroColor> AERO_COLORS = List.of(
		new AeroColor(DyeColor.WHITE,
			id(AERONAUTICS, "white_envelope"),
			id(AERONAUTICS, "white_envelope_encased_shaft")),
		new AeroColor(DyeColor.ORANGE,
			id(AERONAUTICS, "orange_envelope"),
			id(AERONAUTICS, "orange_envelope_encased_shaft")),
		new AeroColor(DyeColor.MAGENTA,
			id(AERONAUTICS, "magenta_envelope"),
			id(AERONAUTICS, "magenta_envelope_encased_shaft")),
		new AeroColor(DyeColor.LIGHT_BLUE,
			id(AERONAUTICS, "light_blue_envelope"),
			id(AERONAUTICS, "light_blue_envelope_encased_shaft")),
		new AeroColor(DyeColor.YELLOW,
			id(AERONAUTICS, "yellow_envelope"),
			id(AERONAUTICS, "yellow_envelope_encased_shaft")),
		new AeroColor(DyeColor.LIME,
			id(AERONAUTICS, "lime_envelope"),
			id(AERONAUTICS, "lime_envelope_encased_shaft")),
		new AeroColor(DyeColor.PINK,
			id(AERONAUTICS, "pink_envelope"),
			id(AERONAUTICS, "pink_envelope_encased_shaft")),
		new AeroColor(DyeColor.GRAY,
			id(AERONAUTICS, "gray_envelope"),
			id(AERONAUTICS, "gray_envelope_encased_shaft")),
		new AeroColor(DyeColor.LIGHT_GRAY,
			id(AERONAUTICS, "light_gray_envelope"),
			id(AERONAUTICS, "light_gray_envelope_encased_shaft")),
		new AeroColor(DyeColor.CYAN,
			id(AERONAUTICS, "cyan_envelope"),
			id(AERONAUTICS, "cyan_envelope_encased_shaft")),
		new AeroColor(DyeColor.PURPLE,
			id(AERONAUTICS, "purple_envelope"),
			id(AERONAUTICS, "purple_envelope_encased_shaft")),
		new AeroColor(DyeColor.BLUE,
			id(AERONAUTICS, "blue_envelope"),
			id(AERONAUTICS, "blue_envelope_encased_shaft")),
		new AeroColor(DyeColor.BROWN,
			id(AERONAUTICS, "brown_envelope"),
			id(AERONAUTICS, "brown_envelope_encased_shaft")),
		new AeroColor(DyeColor.GREEN,
			id(AERONAUTICS, "green_envelope"),
			id(AERONAUTICS, "green_envelope_encased_shaft")),
		new AeroColor(DyeColor.RED,
			id(AERONAUTICS, "red_envelope"),
			id(AERONAUTICS, "red_envelope_encased_shaft")),
		new AeroColor(DyeColor.BLACK,
			id(AERONAUTICS, "black_envelope"),
			id(AERONAUTICS, "black_envelope_encased_shaft"))
	);

	private static volatile Mappings mappings = Mappings.EMPTY;

	private NozzleSpecialColoring() {
	}

	/**
	 * Rebuilds lookup tables from registered blocks, including available Aeronautics blocks.
	 * Call after block registration. Queries use the cached mappings.
	 */
	public static void rebuild() {
		Map<Block, SourceRule> rules = new HashMap<>();
		Map<DyeColor, Block> concreteByColor = new EnumMap<>(DyeColor.class);
		Map<DyeColor, Block> envelopeByColor = new EnumMap<>(DyeColor.class);
		Map<DyeColor, Block> encasedByColor = new EnumMap<>(DyeColor.class);
		Map<String, DyeColor> dyeByName = new HashMap<>();

		for (DyeColor dye : DyeColor.values())
			dyeByName.put(dye.getSerializedName(), dye);

		for (VanillaColor color : VANILLA_COLORS) {
			concreteByColor.put(color.color(), color.concrete());
			rules.put(color.concrete(), new SourceRule(color.color(), Kind.CONCRETE));
			rules.put(color.powder(), new SourceRule(color.color(), Kind.POWDER));
		}

		for (AeroColor color : AERO_COLORS) {
			Block envelope = optionalBlock(color.envelopeId());
			if (envelope != null) {
				envelopeByColor.put(color.color(), envelope);
				rules.put(envelope, new SourceRule(color.color(), Kind.ENVELOPE));
			}

			Block encased = optionalBlock(color.encasedId());
			if (encased != null) {
				encasedByColor.put(color.color(), encased);
				rules.put(encased, new SourceRule(color.color(), Kind.ENCASED_SHAFT));
			}
		}

		mappings = new Mappings(
			Map.copyOf(rules),
			Map.copyOf(concreteByColor),
			Map.copyOf(envelopeByColor),
			Map.copyOf(encasedByColor),
			Map.copyOf(dyeByName)
		);
	}

	/**
	 * Computes a replacement state without touching the world, or returns {@code null}
	 * when no mapping or change is needed.
	 *
	 * <p>Concrete keeps its family and becomes the requested color. Concrete powder becomes
	 * concrete of the requested color, including the same-color case. Envelopes and encased shafts
	 * keep their family, become the requested color, and preserve shared properties such as {@code AXIS}.
	 *
	 * <p>Applying an encased-shaft result follows the native single-block replacement path:
	 * {@code KineticBlock.onRemove -> IBE.onRemove} rebuilds the kinetic block entity. This helper
	 * does not copy block-entity data.
	 */
	public static @Nullable BlockState recolor(BlockState state, ResourceLocation color) {
		if (state == null || color == null || !MINECRAFT.equals(color.getNamespace()))
			return null;

		Mappings current = mappings;
		DyeColor dye = current.dyeByName().get(color.getPath());
		if (dye == null)
			return null;

		SourceRule rule = current.rules().get(state.getBlock());
		if (rule == null)
			return null;

		Block target = switch (rule.kind()) {
			case CONCRETE, POWDER -> current.concreteByColor().get(dye);
			case ENVELOPE -> current.envelopeByColor().get(dye);
			case ENCASED_SHAFT -> current.encasedByColor().get(dye);
		};
		if (target == null)
			return null;
		if (rule.kind() != Kind.POWDER && rule.color() == dye)
			return null;

		return copyProperties(state, target);
	}

	/**
	 * Copies only properties shared by the source and target, preserving {@code AXIS} for encased
	 * shafts. This does not copy block-entity data.
	 */
	static BlockState copyProperties(BlockState source, Block target) {
		return target.withPropertiesOf(source);
	}

	private static @Nullable Block optionalBlock(ResourceLocation id) {
		return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private enum Kind {
		CONCRETE,
		POWDER,
		ENVELOPE,
		ENCASED_SHAFT
	}

	private record SourceRule(DyeColor color, Kind kind) {
	}

	private record VanillaColor(DyeColor color, Block concrete, Block powder) {
	}

	private record AeroColor(DyeColor color, ResourceLocation envelopeId, ResourceLocation encasedId) {
	}

	private record Mappings(
		Map<Block, SourceRule> rules,
		Map<DyeColor, Block> concreteByColor,
		Map<DyeColor, Block> envelopeByColor,
		Map<DyeColor, Block> encasedByColor,
		Map<String, DyeColor> dyeByName
	) {
		private static final Mappings EMPTY = new Mappings(
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of()
		);
	}
}
