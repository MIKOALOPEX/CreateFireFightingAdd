package com.mikoalopex.createfirefightingadd.content.ponder.scenes;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseDynamicRenderer;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ParrotPose;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.joml.Vector3f;

public class FireHydrantCabinetScenes {
	private static final UUID PONDER_HYDRANT_ID = UUID.fromString("587de12f-ef2c-47b5-ad0a-6ad840798065");
	private static final UUID PONDER_PLAYER_ID = UUID.fromString("f8ee7b31-64e8-4f94-9f79-05a89b69819b");
	private static final Vec3 EAST_CABINET_HOSE_LOCAL_POINT = new Vec3(10.0 / 16.0, 3.0 / 16.0, 13.0 / 16.0);
	private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

	public static void handheldNozzleOperation(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);

		scene.title("fire_hydrant_cabinet_handheld_nozzle_operation", "Using a Fire Hydrant Cabinet");
		scene.configureBasePlate(0, 0, 9);
		scene.showBasePlate();
		scene.scaleSceneView(0.72f);
		scene.rotateCameraY(-15);

		BlockPos cabinet = util.grid().at(6, 2, 5);
		BlockPos seat = util.grid().at(2, 1, 2);
		Selection sceneBlocks = util.select().layersFrom(1);
		Selection cabinetOnly = util.select().position(cabinet);

		prepareCabinet(scene, cabinet);
		scene.world().showSection(sceneBlocks, Direction.DOWN);
		scene.idle(20);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(cabinet))
			.text("The cabinet UI accepts a Fire Hose, a nozzle, and an empty bucket.");
		scene.overlay().showOutline(PonderPalette.BLUE, "cabinet_ui", cabinetOnly, 85);
		scene.idle(95);

		scene.overlay().showText(85)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(cabinet))
			.text("A cabinet with a hose, a nozzle, and fluid can serve a Handheld Nozzle Controller.");
		scene.overlay().showOutline(PonderPalette.GREEN, "cabinet_ready",
			cabinetOnly, 85);
		scene.idle(95);

		var birb = scene.special().createBirb(Vec3.atBottomCenterOf(seat.above()), ParrotPose.FlappyPose::new);
		Vec3 playerPoint = util.vector().topOf(seat).add(0, 0.65, 0);
		Vec3 controllerPoint = playerPoint.add(0.35, -0.25, 0.0);
		ItemStack emptyController = CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get().getDefaultInstance();
		ItemStack boundController = boundControllerStack(cabinet);

		scene.overlay().showControls(util.vector().blockSurface(cabinet, Direction.WEST), Pointing.RIGHT, 65)
			.withItem(emptyController)
			.whileSneaking()
			.rightClick();
		scene.overlay().showText(75)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().centerOf(cabinet))
			.text("Sneak-right-click the cabinet with an empty controller to bind it.");
		scene.idle(85);

		createBoundControllerEntity(scene, controllerPoint, boundController);
		showHandheldHose(scene, cabinetHosePoint(cabinet), controllerPoint.add(0, 0.25, 0), 120);
		scene.overlay().showText(95)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(playerPoint)
			.text("The bound controller becomes a handheld nozzle, linked back to the cabinet by a hose.");
		scene.idle(110);

		scene.overlay().showControls(playerPoint.add(0, 0.35, 0), Pointing.DOWN, 60)
			.withItem(boundController)
			.leftClick();
		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.placeNearTarget()
			.pointAt(playerPoint)
			.text("Hold attack to spray using the cabinet's stored fluid and installed nozzle.");
		showHandheldSpray(scene, playerPoint.add(-0.1, 0.15, 0), new Vec3(-1, 0, 0), 85);
		scene.special().moveParrot(birb, new Vec3(-0.8, 0, 0), 55);
		scene.idle(95);
	}

	private static void prepareCabinet(CreateSceneBuilder scene, BlockPos cabinet) {
		scene.world().modifyBlockEntity(cabinet, FireHydrantCabinetBlockEntity.class, be -> {
			be.getItemStackHandler().setStackInSlot(FireHydrantCabinetBlockEntity.SLOT_HOSE,
				CreateFireFightingAdd.FIRE_HOSE_ITEM.get().getDefaultInstance());
			be.getItemStackHandler().setStackInSlot(FireHydrantCabinetBlockEntity.SLOT_NOZZLE,
				CreateFireFightingAdd.CONE_NOZZLE_ITEM.get().getDefaultInstance());
			IFluidHandler fluidHandler = be.getFluidHandler(null);
			if (fluidHandler != null)
				fluidHandler.fill(new FluidStack(Fluids.WATER, FireHydrantCabinetBlockEntity.FLUID_CAPACITY),
					IFluidHandler.FluidAction.EXECUTE);
			if (be.getLevel() != null) {
				var registries = be.getLevel().registryAccess();
				CompoundTag tag = be.getUpdateTag(registries);
				tag.putUUID("HydrantId", PONDER_HYDRANT_ID);
				tag.putUUID("BoundPlayer", PONDER_PLAYER_ID);
				tag.putBoolean("DoorOpen", true);
				be.handleUpdateTag(tag, registries);
			}
			be.getDoorAnimation().startWithValue(1);
		});
	}

	private static ItemStack boundControllerStack(BlockPos cabinet) {
		ItemStack stack = CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get().getDefaultInstance();
		CompoundTag tag = new CompoundTag();
		tag.putLong("HydrantPos", cabinet.asLong());
		tag.putString("HydrantDimension", "minecraft:overworld");
		tag.putUUID("HydrantId", PONDER_HYDRANT_ID);
		tag.putString("NozzleType", HandheldNozzleType.CONE.name());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static void createBoundControllerEntity(CreateSceneBuilder scene, Vec3 position, ItemStack controller) {
		scene.world().createEntity(level -> {
			HandheldNozzleControllerEntity entity =
				new HandheldNozzleControllerEntity(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ENTITY.get(), level);
			entity.setPos(position.x, position.y, position.z);
			CompoundTag tag = new CompoundTag();
			tag.put("Controller", controller.save(level.registryAccess(), new CompoundTag()));
			tag.putFloat("FixedYaw", 70.0f);
			entity.readAdditionalSaveData(tag);
			return entity;
		});
	}

	private static Vec3 cabinetHosePoint(BlockPos cabinet) {
		return Vec3.atLowerCornerOf(cabinet).add(EAST_CABINET_HOSE_LOCAL_POINT);
	}

	private static void showHandheldHose(CreateSceneBuilder scene, Vec3 start, Vec3 end, int duration) {
		scene.addInstruction(ponderScene ->
			ponderScene.addElement(new HandheldHoseElement(start, end, duration)));
	}

	private static void showHandheldSpray(CreateSceneBuilder scene, Vec3 origin, Vec3 direction, int duration) {
		Vector3f primary = new Vector3f(0.38f, 0.66f, 1.0f);
		Vector3f secondary = new Vector3f(0.65f, 0.86f, 1.0f);
		for (int i = 0; i < 16; i++) {
			double progress = i / 15.0;
			double spread = 0.04 + progress * 0.5;
			for (int lane = -3; lane <= 3; lane++) {
				if (lane == 0 || Math.abs(lane) == 3 || i % 2 == 0) {
					double laneFactor = lane / 3.0;
					Vec3 pos = origin.add(direction.scale(progress * 4.5))
						.add(0, laneFactor * spread * 0.4, laneFactor * spread);
					Vector3f color = (i + lane) % 3 == 0 ? secondary : primary;
					scene.effects().emitParticles(pos,
						scene.effects().simpleParticleEmitter(new DustParticleOptions(color, 1.1f), direction.scale(0.05)),
						1.0f, duration);
				}
			}
		}
	}

	private static class HandheldHoseElement extends PonderElementBase implements PonderSceneElement {
		private static final ResourceLocation TEXTURE = CreateFireFightingAdd.path("textures/block/fire_hose.png");
		private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
		private static final float HOSE_WIDTH = 3.0f;
		private static final float HOSE_UV_WIDTH = 8.0f;

		private final Vec3 start;
		private final Vec3 end;
		private final int duration;
		private int ticks;

		private HandheldHoseElement(Vec3 start, Vec3 end, int duration) {
			this.start = start;
			this.end = end;
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
		public void renderLayer(PonderLevel level, MultiBufferSource buffer, RenderType type, GuiGraphics graphics,
				float partialTicks) {
		}

		@Override
		public void renderLast(PonderLevel level, MultiBufferSource buffer, GuiGraphics graphics, float partialTicks) {
			Vec3 startNormal = Vec3.atLowerCornerOf(Direction.WEST.getNormal());
			Vec3 endNormal = safeNormalize(start.subtract(end), Direction.WEST);
			int light = LevelRenderer.getLightColor(level, BlockPos.containing(start.add(end).scale(0.5)));
			VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
			FireHoseDynamicRenderer.renderSplineHose(graphics.pose(), consumer, start, end,
				startNormal, endNormal, WORLD_UP, light, false, HOSE_WIDTH, HOSE_UV_WIDTH);
		}

		private static Vec3 safeNormalize(Vec3 vector, Direction fallback) {
			return vector.lengthSqr() < 1.0E-6
				? Vec3.atLowerCornerOf(fallback.getNormal())
				: vector.normalize();
		}
	}
}
