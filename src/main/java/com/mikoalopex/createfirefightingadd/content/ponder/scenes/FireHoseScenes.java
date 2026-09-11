package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnections;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseDynamicRenderer;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseRoute;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

public class FireHoseScenes {
	private static final UUID PONDER_BRACKET_ROUTE_ID =
		UUID.fromString("dbf583be-66a5-4d52-996f-3a06b5ad7e80");

	public static void connectionAndRelay(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("fire_hose_connection_and_relay", "Fire Hose connection and relay");
		scene.configureBasePlate(0, 0, 5);
		scene.showBasePlate();
		scene.scaleSceneView(0.9f);
		scene.rotateCameraY(-35);

		Selection sceneBlocks = util.select().layersFrom(1);
		BlockPos pump = util.grid().at(3, 1, 0);
		BlockPos inputHose = util.grid().at(0, 1, 0);
		BlockPos outputHose = util.grid().at(0, 3, 3);
		BlockPos outputTank = util.grid().at(3, 3, 3);
		Selection hosePair = util.select().position(inputHose).add(util.select().position(outputHose));
		Selection physicalSupports = util.select().fromTo(util.grid().at(1, 1, 3), util.grid().at(3, 2, 3));
		Selection outputNetwork = util.select()
			.fromTo(util.grid().at(1, 3, 3), util.grid().at(2, 3, 3))
			.add(util.select().fromTo(util.grid().at(3, 3, 3), util.grid().at(3, 4, 3)));

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		connectHoses(scene, inputHose, outputHose);
		scene.idle(20);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(inputHose))
			.text("Fire Hoses carry fluid when driven by an external pump, even across moving physical structures.");
		scene.overlay().showOutline(PonderPalette.BLUE, "hose_pair", hosePair, 80);
		scene.overlay().showOutline(PonderPalette.WHITE, "physical_supports", physicalSupports, 80);
		scene.idle(90);

		scene.overlay().showText(95)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(pump))
			.text("The hose inherits the pump range: distance from pump to hose plus distance behind the output hose equals the pump's total range. Non-Create pumps are supported, but may have compatibility issues.");
		scene.overlay().showLine(PonderPalette.GREEN,
			util.vector().centerOf(pump),
			util.vector().centerOf(inputHose), 95);
		scene.overlay().showLine(PonderPalette.GREEN,
			util.vector().centerOf(outputHose),
			util.vector().centerOf(outputTank), 95);
		showPipeFlow(scene, new BlockPos[] {
			util.grid().at(1, 3, 3),
			util.grid().at(2, 3, 3),
			util.grid().at(3, 3, 3)
		}, 95);
		scene.overlay().showOutline(PonderPalette.GREEN, "output_network", outputNetwork, 95);
		scene.idle(105);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(outputHose))
			.text("Try using different dyes to color the hose, or use a Phantom Membrane to hide its flexible section.");
		scene.overlay().showControls(util.vector().blockSurface(outputHose, Direction.UP), Pointing.DOWN, 45)
			.withItem(Items.BLACK_DYE.getDefaultInstance())
			.rightClick();
		scene.idle(15);
		scene.world().modifyBlockEntity(outputHose, FireHoseBlockEntity.class, hose -> hose.setBlackHose(true));
		scene.effects().indicateSuccess(outputHose);
		scene.idle(75);
		scene.overlay().showControls(util.vector().blockSurface(outputHose, Direction.UP), Pointing.DOWN, 45)
			.withItem(Items.WHITE_DYE.getDefaultInstance())
			.rightClick();
		scene.idle(15);
		scene.world().modifyBlockEntity(outputHose, FireHoseBlockEntity.class, hose -> hose.setBlackHose(false));
		scene.effects().indicateSuccess(outputHose);
		scene.idle(75);
	}

	public static void bracketRouting(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("fire_hose_bracket_routing", "Routing Fire Hoses with brackets");
		scene.configureBasePlate(0, 0, 5);
		scene.showBasePlate();
		scene.scaleSceneView(0.9f);
		scene.rotateCameraY(-30);

		BlockPos firstHose = util.grid().at(3, 1, 1);
		BlockPos secondHose = util.grid().at(1, 2, 3);
		BlockPos bracket = util.grid().at(3, 3, 2);
		BlockPos support = util.grid().at(4, 3, 2);
		Selection bracketOnly = util.select().position(bracket);
		Selection hoseEndpoints = util.select().position(firstHose)
			.add(util.select().position(secondHose));
		Selection initialBlocks = util.select().layersFrom(1).substract(bracketOnly);

		clearHoseRoute(scene, firstHose);
		clearHoseRoute(scene, secondHose);
		connectHoses(scene, firstHose, secondHose);
		scene.world().showSection(initialBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showText(95)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(firstHose))
			.text("Hold a Wooden or Metal Bracket and right-click two adjacent nodes on the same Fire Hose in sequence.");
		scene.overlay().showControls(util.vector().blockSurface(firstHose, Direction.UP), Pointing.DOWN, 35)
			.withItem(AllBlocks.WOODEN_BRACKET.asStack())
			.rightClick();
		scene.idle(15);
		scene.overlay().showOutline(PonderPalette.BLUE, "bracket_first_node",
			util.select().position(firstHose), 75);
		scene.overlay().showControls(util.vector().blockSurface(secondHose, Direction.UP), Pointing.DOWN, 35)
			.withItem(AllBlocks.WOODEN_BRACKET.asStack())
			.rightClick();
		scene.idle(15);
		scene.overlay().showOutline(PonderPalette.GREEN, "bracket_selected_nodes", hoseEndpoints, 60);
		scene.effects().indicateSuccess(secondHose);
		scene.idle(75);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(support, Direction.WEST))
			.text("After selecting the second node, right-click to place a bracket and insert it into that hose segment.");
		scene.overlay().showControls(util.vector().blockSurface(support, Direction.WEST), Pointing.RIGHT, 40)
			.withItem(AllBlocks.WOODEN_BRACKET.asStack())
			.rightClick();
		scene.idle(15);
		installBracketRoute(scene, secondHose, bracket, firstHose);
		showBracketHose(scene, secondHose, bracket, firstHose, 240);
		scene.world().showSection(bracketOnly, Direction.EAST);
		scene.effects().indicateSuccess(bracket);
		scene.idle(80);

		scene.overlay().showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(bracket, Direction.WEST))
			.text("The bracket has a value dial. Use a Wrench and scroll it to adjust the hose angle from -90 degrees to 90 degrees.");
		scene.overlay().showCenteredScrollInput(bracket, Direction.WEST, 110);
		setBracketAngle(scene, bracket, -45);
		scene.idle(38);
		setBracketAngle(scene, bracket, 45);
		scene.idle(38);
		setBracketAngle(scene, bracket, 0);
		scene.idle(55);
	}

	private static void connectHoses(CreateSceneBuilder scene, BlockPos first, BlockPos second) {
		scene.world().modifyBlockEntity(first, FireHoseBlockEntity.class, hose -> {
			if (hose.getLevel() != null && hose.getLevel().getBlockEntity(second) instanceof FireHoseBlockEntity other)
				FireHoseConnections.tryConnect(hose, other);
		});
	}

	private static void clearHoseRoute(CreateSceneBuilder scene, BlockPos hosePos) {
		scene.world().modifyBlockEntity(hosePos, FireHoseBlockEntity.class, hose -> hose.route = null);
	}

	private static void installBracketRoute(CreateSceneBuilder scene, BlockPos firstPos,
			BlockPos bracketPos, BlockPos secondPos) {
		scene.world().modifyBlockEntity(bracketPos, HoseBracketBlockEntity.class, bracket -> {
			if (bracket.getLevel() == null
				|| !(bracket.getLevel().getBlockEntity(firstPos) instanceof FireHoseBlockEntity first)
				|| !(bracket.getLevel().getBlockEntity(secondPos) instanceof FireHoseBlockEntity second))
				return;
			HoseRoute route = new HoseRoute(PONDER_BRACKET_ROUTE_ID);
			route.nodes.add(HoseRoute.Node.of(first));
			route.nodes.add(HoseRoute.Node.of(bracket));
			route.nodes.add(HoseRoute.Node.of(second));
			route.revision = 1;
			first.route = HoseRoute.read(route.write());
			bracket.route = HoseRoute.read(route.write());
			second.route = HoseRoute.read(route.write());
		});
	}

	private static void setBracketAngle(CreateSceneBuilder scene, BlockPos bracket, float angle) {
		scene.world().modifyBlockEntity(bracket, HoseBracketBlockEntity.class, be -> be.setAngle(angle));
	}

	private static void showBracketHose(CreateSceneBuilder scene, BlockPos first,
			BlockPos bracket, BlockPos second, int duration) {
		scene.addInstruction(ponderScene ->
			ponderScene.addElement(new BracketHoseElement(first, bracket, second, duration)));
	}

	private static void showPipeFlow(CreateSceneBuilder scene, BlockPos[] path, int duration) {
		for (int i = 0; i < path.length - 1; i++) {
			Vec3 from = Vec3.atCenterOf(path[i]);
			Vec3 to = Vec3.atCenterOf(path[i + 1]);
			Vec3 motion = to.subtract(from).normalize().scale(0.04);
			for (int sample = 0; sample <= 3; sample++) {
				Vec3 pos = from.lerp(to, sample / 3.0);
				scene.effects().emitParticles(pos,
					scene.effects().simpleParticleEmitter(new DustParticleOptions(new Vector3f(0.35f, 0.65f, 1.0f), 1.0f), motion),
					1.0f, duration);
			}
		}
	}

	private static class BracketHoseElement extends PonderElementBase implements PonderSceneElement {
		private static final ResourceLocation TEXTURE =
			CreateFireFightingAdd.path("textures/block/fire_hose.png");
		private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
		private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
		private static final float HOSE_WIDTH = 8.0f;
		private final BlockPos first;
		private final BlockPos bracket;
		private final BlockPos second;
		private final int duration;
		private int ticks;

		private BracketHoseElement(BlockPos first, BlockPos bracket, BlockPos second, int duration) {
			this.first = first;
			this.bracket = bracket;
			this.second = second;
			this.duration = duration;
			setVisible(true);
		}

		@Override
		public void tick(net.createmod.ponder.foundation.PonderScene scene) {
			if (++ticks >= duration)
				setVisible(false);
		}

		@Override
		public void reset(net.createmod.ponder.foundation.PonderScene scene) {
			ticks = 0;
			setVisible(true);
		}

		@Override
		public void renderFirst(PonderLevel level, MultiBufferSource buffer, GuiGraphics graphics, float partialTicks) {
		}

		@Override
		public void renderLayer(PonderLevel level, MultiBufferSource buffer, RenderType type,
				GuiGraphics graphics, float partialTicks) {
		}

		@Override
		public void renderLast(PonderLevel level, MultiBufferSource buffer, GuiGraphics graphics, float partialTicks) {
			if (!(level.getBlockEntity(bracket) instanceof HoseBracketBlockEntity bracketEntity))
				return;
			Direction firstFacing = level.getBlockState(first).getValue(FireHoseBlock.FACING);
			Direction secondFacing = level.getBlockState(second).getValue(FireHoseBlock.FACING);
			Direction bracketFace = level.getBlockState(bracket).getValue(HoseBracketBlock.FACING);
			Vec3 firstNormal = Vec3.atLowerCornerOf(firstFacing.getNormal());
			Vec3 secondNormal = Vec3.atLowerCornerOf(secondFacing.getNormal());
			Vec3 bracketNormal = HoseBracketBlock.rotate(bracketFace, bracketEntity.angle(),
				new Vec3(0, 0, bracketEntity.reversed ? -1 : 1));
			Vec3 bracketUp = HoseBracketBlock.rotate(bracketFace, bracketEntity.angle(), WORLD_UP);
			Vec3 firstPort = first.getCenter().add(firstNormal.scale(-0.25));
			Vec3 bracketCenter = bracket.getCenter();
			Vec3 secondPort = second.getCenter().add(secondNormal.scale(-0.25));
			VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);

			renderSegment(level, graphics, consumer, firstPort,
				bracketCenter.add(bracketNormal.scale(-0.25)), firstNormal,
				bracketNormal.scale(-1), bracketUp);
			renderSegment(level, graphics, consumer, bracketCenter.add(bracketNormal.scale(0.25)),
				secondPort, bracketNormal, secondNormal, WORLD_UP);
		}

		private static void renderSegment(PonderLevel level, GuiGraphics graphics, VertexConsumer consumer,
				Vec3 start, Vec3 end, Vec3 startNormal, Vec3 endNormal, Vec3 endUp) {
			int light = LevelRenderer.getLightColor(level, BlockPos.containing(start.add(end).scale(0.5)));
			FireHoseDynamicRenderer.renderSplineHose(graphics.pose(), consumer, start, end,
				startNormal, endNormal, endUp, light, false, HOSE_WIDTH, HOSE_WIDTH);
		}
	}
}
