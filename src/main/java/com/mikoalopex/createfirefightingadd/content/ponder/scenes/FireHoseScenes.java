package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnections;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

public class FireHoseScenes {

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
			.text("Right-click either end with black dye to use a black hose. White dye restores the original color.");
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

	private static void connectHoses(CreateSceneBuilder scene, BlockPos first, BlockPos second) {
		scene.world().modifyBlockEntity(first, FireHoseBlockEntity.class, hose -> {
			if (hose.getLevel() != null && hose.getLevel().getBlockEntity(second) instanceof FireHoseBlockEntity other)
				FireHoseConnections.tryConnect(hose, other);
		});
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
}
