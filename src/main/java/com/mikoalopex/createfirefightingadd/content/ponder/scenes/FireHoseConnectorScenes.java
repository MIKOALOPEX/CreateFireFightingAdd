package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnectorBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnectorMode;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnections;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

public class FireHoseConnectorScenes {

	public static void modesAndReconnection(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("fire_hose_connector_modes_and_reconnection", "Fire Hose Connector modes and reconnection");
		scene.configureBasePlate(0, 1, 9);
		scene.showBasePlate();
		scene.scaleSceneView(0.7f);
		scene.rotateCameraY(-35);

		BlockPos connector = util.grid().at(6, 3, 2);
		BlockPos lever = util.grid().at(5, 3, 2);
		BlockPos lamp = util.grid().at(5, 2, 2);
		BlockPos hoseA = util.grid().at(6, 3, 3);
		BlockPos hoseB = util.grid().at(2, 2, 7);
		Selection pipe = util.select().fromTo(util.grid().at(6, 0, 0), util.grid().at(6, 3, 1));
		Selection tank = util.select().fromTo(util.grid().at(2, 2, 8), util.grid().at(2, 4, 8));
		Selection hoseEndpoints = util.select().position(hoseA)
			.add(util.select().position(hoseB));
		Selection sceneBlocks = util.select().layersFrom(1)
			.add(util.select().position(util.grid().at(6, 0, 0)));

		scene.world().showSection(sceneBlocks, Direction.DOWN);
		primeCachedEndpoint(scene, connector, hoseA, hoseB);
		scene.idle(20);

		scene.overlay().showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(connector, Direction.UP))
			.text("Fire Hose Connectors have three modes: Idle, Fixed, and Free.");
		scene.overlay().showOutline(PonderPalette.BLUE, "connector", util.select().position(connector), 90);
		scene.overlay().showOutline(PonderPalette.WHITE, "ports_and_pipe",
			hoseEndpoints.add(pipe).add(tank), 90);
		scene.overlay().showCenteredScrollInput(connector, Direction.UP, 65);
		scene.idle(100);

		setConnectorMode(scene, connector, FireHoseConnectorMode.IDLE);
		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(hoseB))
			.text("In Idle mode, a redstone pulse highlights the cached partner endpoint. With no cached endpoint, it reports that the cached endpoint is missing and does not connect.");
		pullLever(scene, util, lever, lamp);
		showConnectorHighlight(scene, util, hoseB, "idle_cached_target", 75);
		scene.idle(90);
		resetLever(scene, lever, lamp);
		scene.idle(20);

		setConnectorMode(scene, connector, FireHoseConnectorMode.FIXED);
		scene.overlay().showText(95)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(hoseB))
			.text("Fixed mode remembers the last connected hose endpoint and tries to restore that saved connection while it is still in range.");
		scene.overlay().showCenteredScrollInput(connector, Direction.UP, 45);
		pullLever(scene, util, lever, lamp);
		connectHoses(scene, hoseA, hoseB);
		scene.overlay().showOutline(PonderPalette.GREEN, "fixed_target",
			util.select().position(hoseB).add(tank), 95);
		scene.idle(105);
		resetLever(scene, lever, lamp);
		scene.idle(20);

		disconnectHose(scene, hoseA);
		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(hoseB))
			.text("If there is no saved endpoint, or the saved endpoint is missing or too far away, Fixed mode will not choose a different endpoint.");
		scene.overlay().showOutline(PonderPalette.RED, "missing_or_far_target",
			util.select().position(hoseB), 80);
		scene.idle(90);

		setConnectorMode(scene, connector, FireHoseConnectorMode.FREE);
		scene.overlay().showText(95)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(hoseB))
			.text("Free mode scans nearby space and connects to the nearest available hose endpoint it can find.");
		scene.overlay().showCenteredScrollInput(connector, Direction.UP, 45);
		pullLever(scene, util, lever, lamp);
		scene.overlay().showLine(PonderPalette.GREEN,
			util.vector().centerOf(hoseA),
			util.vector().centerOf(hoseB), 45);
		connectHoses(scene, hoseA, hoseB);
		scene.overlay().showOutline(PonderPalette.GREEN, "free_target",
			util.select().position(hoseB).add(tank), 95);
		scene.idle(105);
	}

	private static void setConnectorMode(CreateSceneBuilder scene, BlockPos connector, FireHoseConnectorMode mode) {
		scene.world().modifyBlockEntity(connector, FireHoseConnectorBlockEntity.class, be -> {
			ScrollValueBehaviour scroll = be.getBehaviour(ScrollValueBehaviour.TYPE);
			if (scroll != null)
				scroll.setValue(mode.ordinal());
		});
	}

	private static void pullLever(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos lever, BlockPos lamp) {
		scene.overlay().showControls(util.vector().blockSurface(lever, Direction.WEST), Pointing.RIGHT, 35)
			.rightClick();
		scene.idle(12);
		setRedstone(scene, lever, lamp, true);
		scene.effects().indicateSuccess(lamp);
		scene.idle(18);
	}

	private static void resetLever(CreateSceneBuilder scene, BlockPos lever, BlockPos lamp) {
		setRedstone(scene, lever, lamp, false);
	}

	private static void connectHoses(CreateSceneBuilder scene, BlockPos first, BlockPos second) {
		scene.world().modifyBlockEntity(first, FireHoseBlockEntity.class, hose -> {
			if (hose.getLevel() != null && hose.getLevel().getBlockEntity(second) instanceof FireHoseBlockEntity other)
				FireHoseConnections.tryConnect(hose, other);
		});
	}

	private static void primeCachedEndpoint(CreateSceneBuilder scene, BlockPos connector, BlockPos first, BlockPos second) {
		connectHoses(scene, first, second);
		scene.world().modifyBlockEntity(connector, FireHoseConnectorBlockEntity.class,
			FireHoseConnectorBlockEntity::refreshAttachedEndpoint);
		disconnectHose(scene, first);
	}

	private static void disconnectHose(CreateSceneBuilder scene, BlockPos hosePos) {
		scene.world().modifyBlockEntity(hosePos, FireHoseBlockEntity.class, FireHoseConnections::disconnect);
	}

	private static void setRedstone(CreateSceneBuilder scene, BlockPos lever, BlockPos lamp, boolean powered) {
		scene.world().modifyBlock(lever,
			state -> state.hasProperty(LeverBlock.POWERED) ? state.setValue(LeverBlock.POWERED, powered) : state,
			true);
		scene.world().modifyBlock(lamp,
			state -> state.hasProperty(RedstoneLampBlock.LIT) ? state.setValue(RedstoneLampBlock.LIT, powered) : state,
			true);
	}

	private static void showConnectorHighlight(CreateSceneBuilder scene, SceneBuildingUtil util,
			BlockPos target, String key, int duration) {
		scene.overlay().showOutline(PonderPalette.GREEN, key, util.select().position(target), duration);
		Vec3 center = Vec3.atCenterOf(target);
		Vector3f green = new Vector3f(0.44f, 1.0f, 0.2f);
		for (int i = 0; i < 24; i++) {
			double angle = (Math.PI * 2 * i) / 24.0;
			double radius = 0.32 + (i % 3) * 0.08;
			double y = ((i * 5) % 11 - 5) / 14.0;
			Vec3 pos = center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
			scene.effects().emitParticles(pos,
				scene.effects().simpleParticleEmitter(new DustParticleOptions(green, 1.0f), Vec3.ZERO),
				1.0f, duration);
		}
	}

}
