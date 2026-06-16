package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

public class NozzleScenes {

	public static void basicSpray(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("nozzle_basic_spray", "Nozzle types and fluid input");
		scene.configureBasePlate(0, 0, 15);
		scene.showBasePlate();
		scene.scaleSceneView(0.55f);
		scene.rotateCameraY(-25);

		Selection sceneBlocks = util.select().layersFrom(1);
		BlockPos coneDisplay = util.grid().at(3, 1, 1);
		BlockPos flatDisplay = util.grid().at(5, 1, 1);
		BlockPos tank = util.grid().at(13, 2, 4);
		BlockPos pump = util.grid().at(13, 1, 5);
		BlockPos workingNozzle = util.grid().at(11, 3, 7);

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showText(70)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(coneDisplay, Direction.UP))
			.text("Cone Nozzles spread a rounded stream, while Flat Spray Nozzles make a wider sheet.");
		scene.overlay().showOutline(PonderPalette.BLUE, "display_nozzles",
			util.select().fromTo(coneDisplay, flatDisplay), 70);
		scene.idle(80);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(tank, Direction.WEST))
			.text("Feed a nozzle with fluid through pipes. Creative tanks are used here as compact examples.");
		scene.overlay().showOutline(PonderPalette.GREEN, "fluid_input",
			util.select().fromTo(tank.below(), pump).add(util.select().fromTo(util.grid().at(13, 1, 6), workingNozzle)), 80);
		scene.idle(90);

		showWaterSpray(scene, workingNozzle);
		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(workingNozzle, Direction.WEST))
			.text("Water, milk, potions, lava, flammable fuels, and dragon breath each use their own spray behaviour.");
		scene.idle(90);
	}

	public static void fireAndProcessing(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("nozzle_fire_and_processing", "Nozzle interactions");
		scene.configureBasePlate(0, 0, 15);
		scene.showBasePlate();
		scene.scaleSceneView(0.55f);
		scene.rotateCameraY(-25);

		Selection sceneBlocks = util.select().layersFrom(1);
		BlockPos nozzle = util.grid().at(11, 3, 7);
		BlockPos target = util.grid().at(9, 2, 7);
		BlockPos depot = util.grid().at(7, 2, 7);
		BlockPos nearCampfire = util.grid().at(5, 2, 7);
		BlockPos protectedCampfire = util.grid().at(1, 2, 7);
		Selection glassWall = util.select().fromTo(util.grid().at(3, 1, 5), util.grid().at(3, 5, 9));

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showText(70)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(nozzle, Direction.WEST))
			.text("Sprays can push entities along the stream.");
		var birb = scene.special().createBirb(Vec3.atBottomCenterOf(target.above()), ParrotPose.FlappyPose::new);
		scene.overlay().showLine(PonderPalette.BLUE,
			util.vector().centerOf(target), util.vector().centerOf(target.west(5)), 90);
		showWaterSpray(scene, nozzle);
		scene.special().moveParrot(birb, new Vec3(-5, 0, 0), 70);
		scene.idle(70);
		scene.special().moveParrot(birb, new Vec3(0, -2, 0), 30);
		scene.idle(30);

		scene.overlay().showText(75)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(depot, Direction.UP))
			.text("Items on Depots and dropped items can be processed by matching Create fan recipes.");
		scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 60)
			.withItem(AllItems.CRUSHED_COPPER.asStack())
			.rightClick();
		scene.idle(85);

		scene.overlay().showText(75)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(nearCampfire, Direction.UP))
			.text("Water extinguishes fires and campfires in the visible spray path.");
		showWaterSpray(scene, nozzle);
		scene.idle(35);
		scene.world().modifyBlock(nearCampfire,
			state -> state.hasProperty(CampfireBlock.LIT) ? state.setValue(CampfireBlock.LIT, false) : state,
			true);
		scene.effects().indicateSuccess(nearCampfire);
		scene.idle(50);

		scene.overlay().showOutlineWithText(glassWall, 90)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(util.grid().at(3, 3, 7), Direction.EAST))
			.text("Walls block spray effects, so protected flames behind the glass remain untouched.");
		scene.overlay().showOutline(PonderPalette.RED, "protected_fire",
			util.select().position(protectedCampfire), 90);
		scene.idle(100);
	}

	public static void fluidVariants(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("nozzle_fluid_variants", "Fluid-specific spray effects");
		scene.configureBasePlate(0, 0, 15);
		scene.showBasePlate();
		scene.scaleSceneView(0.55f);
		scene.rotateCameraY(-25);

		Selection sceneBlocks = util.select().layersFrom(1);
		BlockPos nozzle = util.grid().at(11, 3, 7);
		BlockPos tank = util.grid().at(13, 2, 4);

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showText(75)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(tank, Direction.WEST))
			.text("Change the stored fluid to change what the nozzle does.");
		showWaterSpray(scene, nozzle);
		scene.idle(85);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(nozzle, Direction.WEST))
			.text("Lava and ignited fuels use heated processing, light fires, and melt snow or ice.");
		showLavaSpray(scene, nozzle);
		scene.idle(90);

		scene.overlay().showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.FAST)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(nozzle, Direction.WEST))
			.text("With Create: Dragons Plus installed, dragon breath gains its own particles and processing type.");
		showDragonBreathSpray(scene, nozzle);
		scene.idle(100);
	}

	private static void showWaterSpray(CreateSceneBuilder scene, BlockPos nozzle) {
		showDustStream(scene, nozzle, new Vector3f(0.38f, 0.66f, 1.0f), new Vector3f(0.65f, 0.86f, 1.0f),
			8.5, 20, 5, 7.0, 1.15f, 70);
	}

	private static void showLavaSpray(CreateSceneBuilder scene, BlockPos nozzle) {
		Vec3 origin = Vec3.atCenterOf(nozzle).add(-0.5, 0, 0);
		for (int i = 0; i < 13; i++) {
			double spread = 0.03 + i * 0.018;
			for (int lane = -1; lane <= 1; lane++) {
				Vec3 pos = origin.add(-i * 0.45, lane * spread * 0.35, lane * spread);
				scene.effects().emitParticles(pos,
					scene.effects().simpleParticleEmitter(ParticleTypes.FLAME, new Vec3(-0.04, 0.005, 0)),
					1.2f, 60);
			}
		}
	}

	private static void showDragonBreathSpray(CreateSceneBuilder scene, BlockPos nozzle) {
		showDustStream(scene, nozzle, new Vector3f(0.8f, 0.35f, 1.0f), new Vector3f(1.0f, 0.75f, 1.0f),
			8.0, 20, 5, 7.0, 1.2f, 70);
	}

	private static void showDustStream(CreateSceneBuilder scene, BlockPos nozzle, Vector3f primary, Vector3f secondary,
									   double length, int samples, int lanes, double halfAngleDegrees, float scale, int cycles) {
		Vec3 origin = Vec3.atCenterOf(nozzle).add(-0.5, 0, 0);
		for (int i = 0; i < samples; i++) {
			double progress = samples <= 1 ? 0 : (double) i / (samples - 1);
			double x = -progress * length;
			double spread = 0.03 + progress * length * Math.tan(Math.toRadians(halfAngleDegrees));

			for (int lane = -lanes; lane <= lanes; lane++) {
				if (lane == 0 || Math.abs(lane) == lanes || i % 2 == 0) {
					double laneFactor = lanes == 0 ? 0 : (double) lane / lanes;
					Vec3 pos = origin.add(x, laneFactor * spread * 0.45, laneFactor * spread);
					Vector3f color = (i + lane) % 3 == 0 ? secondary : primary;
					float density = (float) (1.1 + (1.0 - progress) * 0.8);
					scene.effects().emitParticles(pos,
						scene.effects().simpleParticleEmitter(new DustParticleOptions(color, scale), new Vec3(-0.055, 0, 0)),
						density, cycles);
				}
			}
		}
	}
}
