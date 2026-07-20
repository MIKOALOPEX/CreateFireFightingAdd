package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnections;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class PipelineTurbineScenes {

	public static void flowToRotation(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("pipeline_turbine_flow_to_rotation", "Pipeline Turbine pipe networks");
		scene.configureBasePlate(0, 0, 9);
		scene.showBasePlate();
		scene.scaleSceneView(0.72f);
		scene.rotateCameraY(-35);

		BlockPos turbineA = util.grid().at(5, 2, 3);
		BlockPos turbineB = util.grid().at(5, 3, 5);
		BlockPos turbineC = util.grid().at(1, 2, 6);
		BlockPos sourcePump = util.grid().at(6, 2, 1);
		BlockPos stressA = util.grid().at(5, 3, 3);
		BlockPos speedA = util.grid().at(4, 3, 5);
		BlockPos speedB = util.grid().at(1, 3, 6);
		BlockPos stressB = util.grid().at(1, 4, 6);
		BlockPos hoseLow = util.grid().at(1, 3, 7);
		BlockPos hoseHigh = util.grid().at(3, 4, 7);

		Selection sceneBlocks = util.select().layersFrom(1);
		Selection activeNetwork = positions(util,
			util.grid().at(7, 1, 1), util.grid().at(6, 1, 1),
			sourcePump, util.grid().at(7, 2, 1),
			util.grid().at(7, 3, 1), util.grid().at(7, 4, 1),
			util.grid().at(5, 2, 1), util.grid().at(5, 2, 2),
			turbineA, util.grid().at(5, 2, 4), util.grid().at(5, 2, 5),
			turbineB, util.grid().at(5, 4, 5),
			stressA, speedA);
		Selection turbinesAB = positions(util, turbineA, turbineB);
		Selection gaugesA = positions(util, speedA, stressA);
		Selection hoseBoundary = positions(util,
			hoseLow, hoseHigh,
			util.grid().at(1, 2, 7), util.grid().at(3, 4, 7),
			util.grid().at(4, 4, 7), util.grid().at(5, 4, 7));
		Selection isolatedNetwork = positions(util,
			util.grid().at(1, 1, 5), util.grid().at(1, 2, 5),
			turbineC, util.grid().at(1, 2, 7),
			speedB, stressB, hoseLow);
		Selection gaugesB = positions(util, speedB, stressB);

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		connectHoses(scene, hoseLow, hoseHigh);
		scene.idle(20);

		scene.overlay().showOutlineWithText(activeNetwork, 95)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(sourcePump))
			.text("Pipeline Turbines read pressure from their own Create pipe network. Here, the source pump provides speed 32, so the pipe pressure is 32.");
		scene.overlay().showOutline(PonderPalette.GREEN, "turbines_ab", turbinesAB, 95);
		scene.overlay().showLine(PonderPalette.GREEN,
			util.vector().centerOf(turbineA),
			util.vector().centerOf(turbineB),
			95);
		scene.idle(105);

		scene.overlay().showText(75)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(speedA))
			.text("Speedometer A reads 32 RPM. A turbine's output speed equals the source pump speed.");
		scene.overlay().showOutline(PonderPalette.BLUE, "speedometer_a", util.select().position(speedA), 75);
		scene.idle(85);

		scene.overlay().showText(105)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(stressA))
			.text("Stressometer A reads 48 SU. The source pump consumes 128 SU; 128 x 0.75 = 96 SU total output, split evenly between A and B.");
		scene.overlay().showOutline(PonderPalette.GREEN, "gauges_a", gaugesA, 105);
		scene.idle(115);

		scene.overlay().showOutline(PonderPalette.BLUE, "hose_boundary", hoseBoundary, 115);
		scene.overlay().showOutlineWithText(isolatedNetwork, 95)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(turbineC))
			.text("The two Fire Hose endpoints are treated as different pipe networks. Fluid can pass through the connected hose, but pipe-network pressure does not cross it.");
		scene.overlay().showLine(PonderPalette.BLUE,
			util.vector().centerOf(hoseLow),
			util.vector().centerOf(hoseHigh),
			95);
		scene.idle(105);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(speedB))
			.text("Turbine C receives 0 pressure, so Speedometer B reads 0 RPM and Stressometer B reads 0 SU.");
		scene.overlay().showOutline(PonderPalette.RED, "gauges_b", gaugesB, 85);
		scene.idle(95);
	}

	private static void connectHoses(CreateSceneBuilder scene, BlockPos first, BlockPos second) {
		scene.world().modifyBlockEntity(first, FireHoseBlockEntity.class, hose -> {
			if (hose.getLevel() != null && hose.getLevel().getBlockEntity(second) instanceof FireHoseBlockEntity other)
				FireHoseConnections.tryConnect(hose, other);
		});
	}

	private static Selection positions(SceneBuildingUtil util, BlockPos first, BlockPos... rest) {
		Selection selection = util.select().position(first);
		for (BlockPos pos : rest)
			selection = selection.add(util.select().position(pos));
		return selection;
	}
}
