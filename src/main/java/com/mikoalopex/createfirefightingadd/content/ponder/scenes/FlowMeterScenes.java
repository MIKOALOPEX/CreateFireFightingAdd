package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class FlowMeterScenes {

	public static void flowAndPressure(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("fluid_flow_meter_flow_and_pressure", "Monitoring fluid information using the Flow Meter");
		scene.configureBasePlate(0, 0, 5);
		scene.showBasePlate();
		scene.scaleSceneView(0.9f);
		scene.rotateCameraY(-35);

		BlockPos motor = util.grid().at(2, 1, 2);
		BlockPos pump = util.grid().at(2, 2, 1);
		BlockPos meter = util.grid().at(2, 2, 2);
		BlockPos pipe = util.grid().at(2, 2, 3);
		Selection sceneBlocks = util.select().layersFrom(1);
		Selection tanks = util.select().fromTo(util.grid().at(2, 1, 0), util.grid().at(2, 3, 0))
			.add(util.select().fromTo(util.grid().at(2, 1, 4), util.grid().at(2, 3, 4)));
		Selection pipeRun = positions(util, pump, meter, pipe);

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showOutlineWithText(pipeRun.add(tanks), 80)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(meter))
			.text("The Flow Meter displays fluid information from the pipe it is installed in.");
		scene.idle(90);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(meter))
			.text("When wearing Engineer's Goggles, the player can read the pump speed, pressure, estimated flow rate, and fluid type.");
		scene.overlay().showLine(PonderPalette.GREEN,
			util.vector().centerOf(pump),
			util.vector().centerOf(meter),
			80);
		scene.idle(90);

		scene.overlay().showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(meter, Direction.UP))
			.text("Here, the source pump runs at 16 RPM. The gauge reads pressure 16.0, estimated flow rate 8.0 mB/t, and Water.");
		scene.overlay().showOutline(PonderPalette.WHITE, "meter_readout", util.select().position(meter), 90);
		scene.effects().indicateSuccess(meter);
		scene.idle(100);
	}

	private static Selection positions(SceneBuildingUtil util, BlockPos first, BlockPos... rest) {
		Selection selection = util.select().position(first);
		for (BlockPos pos : rest)
			selection = selection.add(util.select().position(pos));
		return selection;
	}
}
