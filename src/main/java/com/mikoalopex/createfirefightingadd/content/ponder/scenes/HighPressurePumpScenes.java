package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class HighPressurePumpScenes {

	public static void rangeAndPressure(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("high_pressure_pump_range_and_pressure", "High Pressure Pump range and pressure");
		scene.configureBasePlate(0, 0, 5);
		scene.showBasePlate();
		scene.scaleSceneView(0.85f);

		BlockPos pumpPos = util.grid().at(1, 1, 1);
		Selection sceneBlocks = util.select().layersFrom(1);
		Selection pressurePath = util.select().fromTo(util.grid().at(0, 1, 1), util.grid().at(4, 1, 3));

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showText(70)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(pumpPos, Direction.UP))
			.text("High Pressure Pumps scale Create's mechanical pump behaviour.");
		scene.idle(80);

		scene.overlay().showOutlineWithText(pressurePath, 80)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(util.grid().at(4, 1, 2), Direction.UP))
			.text("The amplification multiplier increases the pipe distance reached by pressure.");
		scene.idle(90);

		AABB rangeBox = new AABB(util.grid().at(2, 1, 1)).expandTowards(2, 0, 0).inflate(0.1);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, rangeBox, rangeBox, 70);
		scene.overlay().showText(70)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(rangeBox.getCenter())
			.text("At the default multiplier of 2x, a 16 block Create range becomes 32 blocks.");
		scene.idle(90);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(pumpPos, Direction.EAST))
			.text("The same multiplier also scales pump pressure, allowing higher flow speeds.");
		scene.idle(90);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(pumpPos, Direction.NORTH))
			.text("Change HighPressurePump.amplificationMultiplier in the server config to tune both values together.");
		scene.idle(100);
	}
}
