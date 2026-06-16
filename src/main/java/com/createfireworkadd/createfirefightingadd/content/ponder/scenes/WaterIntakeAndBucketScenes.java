package com.createfireworkadd.createfirefightingadd.content.ponder.scenes;

import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.BucketControllerBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

public class WaterIntakeAndBucketScenes {

	public static void collectionAndBinding(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("water_intake_collection_and_binding", "Water Intake collection and binding");
		scene.configureBasePlate(0, 0, 15);
		scene.showBasePlate();
		scene.scaleSceneView(0.58f);
		scene.rotateCameraY(25);

		Selection sceneBlocks = util.select().layersFrom(1);
		Selection waterSource = util.select().fromTo(util.grid().at(2, 1, 2), util.grid().at(3, 1, 3));
		BlockPos bucket = util.grid().at(1, 3, 7);
		BlockPos intake = util.grid().at(5, 3, 6);
		BlockPos lever = util.grid().at(4, 3, 6);
		BlockPos lamp = util.grid().at(4, 2, 6);
		BlockPos motor = util.grid().at(5, 1, 11);
		Selection pipePath = util.select()
			.fromTo(util.grid().at(6, 3, 6), util.grid().at(7, 3, 6))
			.add(util.select().fromTo(util.grid().at(7, 4, 6), util.grid().at(7, 5, 6)))
			.add(util.select().fromTo(util.grid().at(7, 5, 4), util.grid().at(10, 5, 4)))
			.add(util.select().position(util.grid().at(10, 4, 4)));
		Selection tank = util.select().fromTo(util.grid().at(9, 1, 3), util.grid().at(11, 3, 5));

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showOutlineWithText(waterSource, 85)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(util.grid().at(2, 1, 2), Direction.UP))
			.text("Water Intakes search nearby infinite water sources and can find water below moving contraptions.");
		scene.overlay().showLine(PonderPalette.BLUE,
			util.vector().centerOf(intake),
			util.vector().centerOf(util.grid().at(2, 1, 2)),
			85);
		scene.idle(95);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(intake, Direction.WEST))
			.text("Right-click the intake while holding a Bucket Controller to bind them.");
		scene.overlay().showControls(util.vector().blockSurface(intake, Direction.WEST), Pointing.RIGHT, 55)
			.withItem(Createfirefightingadd.BUCKET_CONTROLLER_ITEM.get().getDefaultInstance())
			.rightClick();
		scene.overlay().showLine(PonderPalette.GREEN,
			util.vector().centerOf(intake),
			util.vector().centerOf(bucket),
			80);
		scene.idle(90);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(lever))
			.text("The intake works only while it has both kinetic stress and a redstone signal.");
		scene.overlay().showOutline(PonderPalette.RED, "intake_power",
			util.select().position(motor).add(util.select().position(lever)).add(util.select().position(lamp)), 85);
		scene.idle(35);
		scene.world().modifyBlock(lever,
			state -> state.hasProperty(LeverBlock.POWERED) ? state.setValue(LeverBlock.POWERED, true) : state,
			true);
		scene.world().modifyBlock(lamp,
			state -> state.hasProperty(RedstoneLampBlock.LIT) ? state.setValue(RedstoneLampBlock.LIT, true) : state,
			true);
		scene.effects().indicateSuccess(lamp);
		scene.idle(65);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(bucket, Direction.UP))
			.text("Bound Bucket Controllers are filled first.");
		for (int i = 1; i <= 4; i++) {
			showBucketFill(scene, bucket, i / 4.0, 22);
			scene.idle(14);
		}
		scene.idle(25);

		scene.overlay().showOutlineWithText(pipePath, 85)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(util.grid().at(8, 5, 4)))
			.text("Once the bound controller is full, remaining water is pushed into the rear pipe network.");
		showPipeFlow(scene, new BlockPos[] {
			util.grid().at(6, 3, 6),
			util.grid().at(7, 3, 6),
			util.grid().at(7, 4, 6),
			util.grid().at(7, 5, 6),
			util.grid().at(7, 5, 4),
			util.grid().at(8, 5, 4),
			util.grid().at(9, 5, 4),
			util.grid().at(10, 5, 4),
			util.grid().at(10, 4, 4)
		});
		scene.idle(90);

		scene.overlay().showOutlineWithText(tank, 80)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(util.grid().at(10, 2, 4)))
			.text("The connected tank receives the water after the controller has been supplied.");
		showTankFill(scene, util.grid().at(10, 2, 4));
		scene.idle(90);
	}

	public static void largeAreaSpray(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("bucket_controller_large_area_spray", "Bucket Controller large-area spray");
		scene.configureBasePlate(0, 0, 15);
		scene.showBasePlate();
		scene.scaleSceneView(0.62f);
		scene.rotateCameraY(-25);

		Selection sceneBlocks = util.select().layersFrom(1);
		BlockPos bucket = util.grid().at(7, 5, 7);
		BlockPos lever = util.grid().at(7, 5, 6);
		Selection fires = util.select().position(util.grid().at(2, 1, 3))
			.add(util.select().position(util.grid().at(3, 1, 5)))
			.add(util.select().position(util.grid().at(3, 1, 8)))
			.add(util.select().position(util.grid().at(6, 1, 4)))
			.add(util.select().position(util.grid().at(6, 1, 9)))
			.add(util.select().position(util.grid().at(8, 1, 3)))
			.add(util.select().position(util.grid().at(8, 1, 5)))
			.add(util.select().position(util.grid().at(8, 1, 7)));

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(bucket, Direction.DOWN))
			.text("Bucket Controllers are used for large-area fire suppression, usually supplied by a Water Intake.");
		scene.overlay().showOutline(PonderPalette.BLUE, "bucket_controller",
			util.select().position(bucket), 85);
		scene.idle(95);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(lever))
			.text("Apply redstone to activate the downward spray.");
		scene.world().modifyBlockEntity(bucket, BucketControllerBlockEntity.class,
			be -> be.getTankHandler().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4000),
				net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE));
		scene.idle(25);
		scene.world().modifyBlock(lever,
			state -> state.hasProperty(LeverBlock.POWERED) ? state.setValue(LeverBlock.POWERED, true) : state,
			true);
		scene.effects().indicateSuccess(lever);
		showBucketSpray(scene, bucket, 5.0, 45.0, 95);
		scene.idle(95);

		scene.overlay().showOutlineWithText(fires, 80)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(util.grid().at(6, 1, 6)))
			.text("The falling stream covers a wide area below the controller.");
		scene.world().setBlocks(fires, Blocks.AIR.defaultBlockState(), true);
		scene.effects().indicateSuccess(util.grid().at(6, 1, 4));
		scene.idle(90);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(bucket, Direction.EAST))
			.text("Use the side value setting to adjust spray height. Higher settings narrow the cone.");
		scene.overlay().showCenteredScrollInput(bucket, Direction.EAST, 60);
		showBucketSpray(scene, bucket, 3.0, 45.0, 70);
		scene.idle(40);
		showBucketSpray(scene, bucket, 5.0, 24.0, 70);
		scene.idle(85);
	}

	private static void showBucketFill(CreateSceneBuilder scene, BlockPos bucket, double fill, int duration) {
		Vec3 center = Vec3.atCenterOf(bucket);
		AABB box = new AABB(center.x - 0.38, center.y - 0.48, center.z - 0.38,
			center.x + 0.38, center.y - 0.48 + 0.9 * fill, center.z + 0.38);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, "bucket_fill_" + fill, box, duration);
		scene.effects().emitParticles(center.add(0, -0.15 + fill * 0.35, 0),
			scene.effects().simpleParticleEmitter(new DustParticleOptions(new Vector3f(0.35f, 0.65f, 1.0f), 1.1f), Vec3.ZERO),
			2.0f, duration);
	}

	private static void showPipeFlow(CreateSceneBuilder scene, BlockPos[] path) {
		for (int i = 0; i < path.length - 1; i++) {
			Vec3 from = Vec3.atCenterOf(path[i]);
			Vec3 to = Vec3.atCenterOf(path[i + 1]);
			Vec3 motion = to.subtract(from).normalize().scale(0.045);
			for (int sample = 0; sample <= 3; sample++) {
				Vec3 pos = from.lerp(to, sample / 3.0);
				scene.effects().emitParticles(pos,
					scene.effects().simpleParticleEmitter(new DustParticleOptions(new Vector3f(0.35f, 0.65f, 1.0f), 1.0f), motion),
					1.2f, 70);
			}
		}
	}

	private static void showTankFill(CreateSceneBuilder scene, BlockPos center) {
		for (int layer = 0; layer < 3; layer++) {
			Vec3 pos = Vec3.atCenterOf(center).add(0, -0.65 + layer * 0.45, 0);
			scene.effects().emitParticles(pos,
				scene.effects().simpleParticleEmitter(new DustParticleOptions(new Vector3f(0.35f, 0.65f, 1.0f), 1.3f), Vec3.ZERO),
				3.5f, 55);
		}
	}

	private static void showBucketSpray(CreateSceneBuilder scene, BlockPos bucket, double height, double halfAngleDegrees, int duration) {
		Vec3 origin = Vec3.atCenterOf(bucket).add(0, -0.5, 0);
		double radius = height * Math.tan(Math.toRadians(halfAngleDegrees));
		for (int layer = 0; layer < 16; layer++) {
			double progress = (layer + 1) / 16.0;
			double y = -height * progress;
			double ringRadius = radius * progress;
			int lanes = Math.max(6, (int) (ringRadius * 8));
			for (int lane = 0; lane < lanes; lane++) {
				if (lane % 2 != layer % 2)
					continue;
				double angle = (Math.PI * 2 * lane) / lanes;
				Vec3 pos = origin.add(Math.cos(angle) * ringRadius, y, Math.sin(angle) * ringRadius);
				Vec3 motion = pos.subtract(origin).normalize().scale(0.035);
				scene.effects().emitParticles(pos,
					scene.effects().simpleParticleEmitter(new DustParticleOptions(new Vector3f(0.35f, 0.65f, 1.0f), 1.0f), motion),
					0.8f, duration);
			}
		}
	}
}
