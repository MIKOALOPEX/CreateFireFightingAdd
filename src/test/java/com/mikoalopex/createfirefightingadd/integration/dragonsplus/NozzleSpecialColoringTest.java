package com.mikoalopex.createfirefightingadd.integration.dragonsplus;

import java.util.List;
import java.util.Map;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Standalone regression checks for {@link NozzleSpecialColoring}. The isolated JVM first verifies
 * that Aeronautics can be absent, then registers exact-ID test blocks to exercise the real
 * envelope family and AXIS mapping without loading Aeronautics.
 */
public final class NozzleSpecialColoringTest {
	private static final ResourceLocation AERO_WHITE_ENVELOPE =
		aero("white_envelope");
	private static final ResourceLocation AERO_RED_ENVELOPE =
		aero("red_envelope");
	private static final ResourceLocation AERO_WHITE_ENCASED =
		aero("white_envelope_encased_shaft");
	private static final ResourceLocation AERO_RED_ENCASED =
		aero("red_envelope_encased_shaft");

	private static Block whiteEnvelope;
	private static Block redEnvelope;
	private static Block whiteEncased;
	private static Block redEncased;

	public static void main(String[] args) {
		bootstrap();

		verifyAeroAbsentSafety();
		installAeroTestBlocks();
		NozzleSpecialColoring.rebuild();

		testPowderAndConcrete();
		testAeroFamilyMapping();
		testUnknownAndForeignColors();
		testOrdinaryBlocks();
		testAxisCopy();

		System.out.println("Nozzle special coloring: aero-absent safety, exact-ID envelope family/AXIS, 16-color powder/concrete, namespace rejection and ordinary blocks passed");
	}

	private static void verifyAeroAbsentSafety() {
		check(BuiltInRegistries.BLOCK.getOptional(AERO_WHITE_ENVELOPE).isEmpty(),
			"Aeronautics white envelope must be absent before test registration");
		check(BuiltInRegistries.BLOCK.getOptional(AERO_RED_ENVELOPE).isEmpty(),
			"Aeronautics red envelope must be absent before test registration");
		check(BuiltInRegistries.BLOCK.getOptional(AERO_WHITE_ENCASED).isEmpty(),
			"Aeronautics white encased shaft must be absent before test registration");
		check(BuiltInRegistries.BLOCK.getOptional(AERO_RED_ENCASED).isEmpty(),
			"Aeronautics red encased shaft must be absent before test registration");

		NozzleSpecialColoring.rebuild();
		BlockState result = NozzleSpecialColoring.recolor(
			Blocks.WHITE_CONCRETE.defaultBlockState(), colorId(DyeColor.RED));
		check(result != null && result.getBlock() == Blocks.RED_CONCRETE,
			"vanilla mapping must work while Aeronautics is absent");
	}

	@SuppressWarnings({"unchecked", "deprecation"})
	private static void installAeroTestBlocks() {
		MappedRegistry<Block> registry = (MappedRegistry<Block>) (Registry<Block>) BuiltInRegistries.BLOCK;
		registry.unfreeze();
		try {
			whiteEnvelope = Registry.register(registry, AERO_WHITE_ENVELOPE, new TestEnvelopeBlock());
			redEnvelope = Registry.register(registry, AERO_RED_ENVELOPE, new TestEnvelopeBlock());
			whiteEncased = Registry.register(registry, AERO_WHITE_ENCASED, new TestEncasedShaftBlock());
			redEncased = Registry.register(registry, AERO_RED_ENCASED, new TestEncasedShaftBlock());
		} finally {
			registry.freeze();
		}
	}

	private static void testPowderAndConcrete() {
		for (DyeColor source : DyeColor.values()) {
			BlockState powder = powder(source).defaultBlockState();
			for (DyeColor target : DyeColor.values()) {
				BlockState result = NozzleSpecialColoring.recolor(powder, colorId(target));
				check(result != null && result.getBlock() == concrete(target),
					"powder " + source + " -> " + target + " concrete");
			}
		}

		BlockState sameColorPowder = powder(DyeColor.RED).defaultBlockState();
		BlockState solidified = NozzleSpecialColoring.recolor(sameColorPowder, colorId(DyeColor.RED));
		check(solidified != null && solidified.getBlock() == Blocks.RED_CONCRETE,
			"same-color powder must still solidify");

		for (DyeColor source : DyeColor.values()) {
			BlockState concrete = concrete(source).defaultBlockState();
			for (DyeColor target : DyeColor.values()) {
				BlockState result = NozzleSpecialColoring.recolor(concrete, colorId(target));
				if (source == target) {
					check(result == null, "same-color concrete " + source + " must be a no-op");
				} else {
					check(result != null && result.getBlock() == concrete(target),
						"concrete " + source + " -> " + target);
				}
			}
		}
	}

	private static void testAeroFamilyMapping() {
		BlockState plain = NozzleSpecialColoring.recolor(
			whiteEnvelope.defaultBlockState(), colorId(DyeColor.RED));
		check(plain != null && plain.getBlock() == redEnvelope,
			"plain envelope must stay plain and become red");

		BlockState plainBack = NozzleSpecialColoring.recolor(
			redEnvelope.defaultBlockState(), colorId(DyeColor.WHITE));
		check(plainBack != null && plainBack.getBlock() == whiteEnvelope,
			"plain envelope family must remain plain in both directions");

		for (Direction.Axis axis : Direction.Axis.values()) {
			BlockState source = whiteEncased.defaultBlockState()
				.setValue(BlockStateProperties.AXIS, axis);
			BlockState result = NozzleSpecialColoring.recolor(source, colorId(DyeColor.RED));
			check(result != null && result.getBlock() == redEncased,
				"encased shaft must stay encased and become red");
			check(result.getValue(BlockStateProperties.AXIS) == axis,
				"encased shaft AXIS " + axis + " must survive");
		}

		check(NozzleSpecialColoring.recolor(
			whiteEnvelope.defaultBlockState(), colorId(DyeColor.WHITE)) == null,
			"same-color plain envelope must be a no-op");
		check(NozzleSpecialColoring.recolor(
			whiteEncased.defaultBlockState(), colorId(DyeColor.WHITE)) == null,
			"same-color encased shaft must be a no-op");

		check(NozzleSpecialColoring.recolor(
			whiteEnvelope.defaultBlockState(), colorId(DyeColor.BLUE)) == null,
			"missing blue envelope must not change");
		check(NozzleSpecialColoring.recolor(
			whiteEncased.defaultBlockState(), colorId(DyeColor.BLUE)) == null,
			"missing blue encased shaft must not change");
	}

	private static void testUnknownAndForeignColors() {
		BlockState whiteConcrete = Blocks.WHITE_CONCRETE.defaultBlockState();

		check(NozzleSpecialColoring.recolor(whiteConcrete,
			ResourceLocation.fromNamespaceAndPath("minecraft", "not_a_dye")) == null,
			"unknown minecraft dye name must return null");
		check(NozzleSpecialColoring.recolor(whiteConcrete,
			ResourceLocation.fromNamespaceAndPath("dyed", "red")) == null,
			"foreign namespace must return null");
		check(NozzleSpecialColoring.recolor(whiteConcrete,
			ResourceLocation.fromNamespaceAndPath("aeronautics", "red")) == null,
			"aeronautics namespace must not be treated as a vanilla dye");
		check(NozzleSpecialColoring.recolor(whiteConcrete, null) == null,
			"null color must return null");
	}

	private static void testOrdinaryBlocks() {
		check(NozzleSpecialColoring.recolor(Blocks.STONE.defaultBlockState(), colorId(DyeColor.RED)) == null,
			"ordinary stone must not change");
		check(NozzleSpecialColoring.recolor(Blocks.OAK_LOG.defaultBlockState(), colorId(DyeColor.RED)) == null,
			"ordinary log must not change");
	}

	private static void testAxisCopy() {
		for (Direction.Axis axis : Direction.Axis.values()) {
			BlockState source = Blocks.OAK_LOG.defaultBlockState()
				.setValue(BlockStateProperties.AXIS, axis);
			BlockState target = NozzleSpecialColoring.copyProperties(source, Blocks.STRIPPED_OAK_LOG);
			check(target.getValue(BlockStateProperties.AXIS) == axis,
				"axis " + axis + " must survive a same-family state copy");
		}

		BlockState source = Blocks.OAK_LOG.defaultBlockState()
			.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		BlockState stone = NozzleSpecialColoring.copyProperties(source, Blocks.STONE);
		check(stone.getBlock() == Blocks.STONE && stone.getProperties().isEmpty(),
			"targets without AXIS must keep their default state");
	}

	private static Block concrete(DyeColor color) {
		return block(color.getSerializedName() + "_concrete");
	}

	private static Block powder(DyeColor color) {
		return block(color.getSerializedName() + "_concrete_powder");
	}

	private static Block block(String path) {
		return BuiltInRegistries.BLOCK
			.getOptional(ResourceLocation.fromNamespaceAndPath("minecraft", path))
			.orElseThrow(() -> new AssertionError("Missing block minecraft:" + path));
	}

	private static ResourceLocation colorId(DyeColor color) {
		return ResourceLocation.fromNamespaceAndPath("minecraft", color.getSerializedName());
	}

	private static ResourceLocation aero(String path) {
		return ResourceLocation.fromNamespaceAndPath("aeronautics", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static void bootstrap() {
		SharedConstants.tryDetectVersion();
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		Bootstrap.bootStrap();
	}

	private static final class TestEnvelopeBlock extends Block {
		private TestEnvelopeBlock() {
			super(BlockBehaviour.Properties.of());
		}
	}

	private static final class TestEncasedShaftBlock extends RotatedPillarBlock {
		private TestEncasedShaftBlock() {
			super(BlockBehaviour.Properties.of());
		}
	}
}
