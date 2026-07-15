package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.createmod.catnip.animation.AnimationTickHolder;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseDynamicRenderer;

import org.joml.Vector3f;
import org.joml.Vector4f;

public final class HandheldNozzleHoseRenderer {
	private static final RenderType HOSE_RENDER_TYPE = RenderType.entityCutoutNoCull(
		CreateFireFightingAdd.path("textures/block/fire_hose.png"));
	private static final float THIRD_PERSON_HOSE_WIDTH = 3.0f;
	private static final float FIRST_PERSON_HOSE_WIDTH = 3.0f;
	private static final float HANDHELD_HOSE_UV_WIDTH = 8.0f;
	private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 CABINET_HOSE_LOCAL_POINT = new Vec3(3.0 / 16.0, 3.0 / 16.0, 10.0 / 16.0);
	private static final double FIRST_PERSON_STRAIGHT_LENGTH = 0.62;
	private static final double FIRST_PERSON_PORT_INSERTION = 0.035;
	private static final double FIRST_PERSON_FAKE_HOSE_LENGTH = 1.35;
	private static final double FIRST_PERSON_FAKE_HOSE_DROP = 0.08;
	private static final double FIRST_PERSON_WORLD_HIDE_DISTANCE = 3.2;
	private static final double FIRST_PERSON_WORLD_HIDE_SIDE_OFFSET = 0.9;
	private static final double FIRST_PERSON_WORLD_HIDE_DOWN_OFFSET = 0.45;
	private static final double THIRD_PERSON_HAND_HEIGHT = 0.58;
	private static final double THIRD_PERSON_HAND_FORWARD_OFFSET = 0.24;
	private static final double THIRD_PERSON_HAND_SIDE_OFFSET = 0.36;
	private static final double THIRD_PERSON_REAR_FACE_OFFSET = 0.24;
	private static final double THIRD_PERSON_REAR_FACE_UP_OFFSET = 1.41421356237 / 16.0;
	private static final double THIRD_PERSON_STRAIGHT_LENGTH = 1.0;
	private static final Vector4f HANDHELD_HOSE_FACE_CENTER = new Vector4f(0.0f, 1.4142135f / 16.0f, 0.5f, 1.0f);
	private static final Vector4f HANDHELD_HOSE_FACE_NORMAL = new Vector4f(0.0f, 0.0f, 1.0f, 0.0f);
	private static final Vector4f HANDHELD_HOSE_FACE_UP = new Vector4f(-0.70710677f, 0.70710677f, 0.0f, 0.0f);
	private static ControllerAnchor renderedRightAnchor;
	private static ControllerAnchor renderedLeftAnchor;

	private HandheldNozzleHoseRenderer() {
	}

	static void captureRenderedAnchor(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack) {
		java.util.Optional<HandheldNozzleControllerItem.Binding> binding = HandheldNozzleControllerItem.readBinding(stack);
		if (binding.isEmpty())
			return;
		boolean leftHand = switch (transformType) {
			case FIRST_PERSON_LEFT_HAND, THIRD_PERSON_LEFT_HAND -> true;
			case FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_RIGHT_HAND -> false;
			default -> {
				yield false;
			}
		};
		boolean handContext = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			|| transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		if (!handContext)
			return;
		if (transformType.firstPerson())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return;
		Camera camera = mc.gameRenderer.getMainCamera();
		Vector4f point = new Vector4f(HANDHELD_HOSE_FACE_CENTER);
		Vector4f normal = new Vector4f(HANDHELD_HOSE_FACE_NORMAL);
		Vector4f up = new Vector4f(HANDHELD_HOSE_FACE_UP);
		poseStack.last().pose().transform(point);
		poseStack.last().pose().transform(normal);
		poseStack.last().pose().transform(up);

		ControllerAnchor anchor = new ControllerAnchor(
			renderPointToWorld(camera, point),
			safeNormalize(vector(normal), new Vec3(0.0, 0.0, 1.0)),
			safeNormalize(vector(up), WORLD_UP),
			THIRD_PERSON_STRAIGHT_LENGTH,
			mc.level.getGameTime(),
			false,
			binding.get());
		if (leftHand)
			renderedLeftAnchor = anchor;
		else
			renderedRightAnchor = anchor;
	}

	static void renderFirstPersonLocalHose(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
			MultiBufferSource buffer, int light) {
		if (!ClientConfig.renderHandheldHoseFirstPerson)
			return;
		if (!transformType.firstPerson())
			return;
		if (HandheldNozzleControllerItem.readBinding(stack).isEmpty())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null)
			return;

		Vec3 endNormal = safeNormalize(vector(HANDHELD_HOSE_FACE_NORMAL), new Vec3(0.0, 0.0, 1.0));
		Vec3 end = vector(HANDHELD_HOSE_FACE_CENTER).add(endNormal.scale(-FIRST_PERSON_PORT_INSERTION));
		Vec3 endUp = safeNormalize(vector(HANDHELD_HOSE_FACE_UP), WORLD_UP);
		Vec3 start = end
			.add(endNormal.scale(FIRST_PERSON_FAKE_HOSE_LENGTH))
			.add(endUp.scale(-FIRST_PERSON_FAKE_HOSE_DROP));
		Vec3 startNormal = safeNormalize(end.subtract(start), endNormal.scale(-1.0));

		VertexConsumer consumer = buffer.getBuffer(HOSE_RENDER_TYPE);
		FireHoseDynamicRenderer.renderSplineHose(poseStack, consumer, start, end,
			safeNormalize(startNormal, endNormal), endNormal, endUp, light, false,
			FIRST_PERSON_HOSE_WIDTH, HANDHELD_HOSE_UV_WIDTH);
	}

	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null)
			return;
		if (!shouldRenderForCamera(mc))
			return;

		ActiveController controller = activeController(mc.player);
		if (controller == null)
			return;
		HandheldNozzleControllerItem.readBinding(controller.stack()).ifPresent(binding -> {
			if (!mc.level.dimension().equals(binding.dimension()))
				return;
			if (!(mc.level.getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet)
				|| !cabinet.getHydrantId().equals(binding.hydrantId()))
				return;

			BlockState state = cabinet.getBlockState();
			Direction facing = state.hasProperty(FireHydrantCabinetBlock.FACING)
				? state.getValue(FireHydrantCabinetBlock.FACING)
				: Direction.NORTH;
			Vec3 start = cabinetConnectionPoint(binding.pos(), facing);
			float partialTicks = AnimationTickHolder.getPartialTicks();
			ControllerAnchor anchor = renderedAnchor(mc, controller.leftHand(), controller.stack())
				.orElseGet(() -> controllerAnchor(mc, event.getCamera(), controller.leftHand(), partialTicks));
			renderCurveTube(event, mc, start, facing, anchor);
		});
	}

	private static java.util.Optional<ControllerAnchor> renderedAnchor(Minecraft mc, boolean leftHand, ItemStack stack) {
		ControllerAnchor anchor = leftHand ? renderedLeftAnchor : renderedRightAnchor;
		if (anchor == null || mc.level == null)
			return java.util.Optional.empty();
		java.util.Optional<HandheldNozzleControllerItem.Binding> binding = HandheldNozzleControllerItem.readBinding(stack);
		if (binding.isEmpty() || !sameBinding(anchor.binding(), binding.get()))
			return java.util.Optional.empty();
		if (anchor.firstPerson() != mc.options.getCameraType().isFirstPerson())
			return java.util.Optional.empty();
		return mc.level.getGameTime() - anchor.gameTime() <= 2
			? java.util.Optional.of(anchor)
			: java.util.Optional.empty();
	}

	private static boolean sameBinding(HandheldNozzleControllerItem.Binding a, HandheldNozzleControllerItem.Binding b) {
		return a.pos().equals(b.pos())
			&& a.dimension().equals(b.dimension())
			&& a.hydrantId().equals(b.hydrantId())
			&& a.nozzleType() == b.nozzleType();
	}

	private static ActiveController activeController(Player player) {
		ItemStack main = player.getMainHandItem();
		if (main.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get())) {
			boolean leftHand = player.getMainArm() == HumanoidArm.LEFT;
			return new ActiveController(main, leftHand);
		}
		ItemStack off = player.getOffhandItem();
		if (off.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get())) {
			boolean leftHand = player.getMainArm() != HumanoidArm.LEFT;
			return new ActiveController(off, leftHand);
		}
		return null;
	}

	private static Vec3 cabinetConnectionPoint(BlockPos pos, Direction facing) {
		return Vec3.atLowerCornerOf(pos).add(rotateCabinetLocal(CABINET_HOSE_LOCAL_POINT, facing));
	}

	private static Vec3 rotateCabinetLocal(Vec3 local, Direction facing) {
		return switch (facing) {
			case SOUTH -> new Vec3(1.0 - local.x, local.y, 1.0 - local.z);
			case EAST -> new Vec3(local.z, local.y, 1.0 - local.x);
			case WEST -> new Vec3(1.0 - local.z, local.y, local.x);
			default -> local;
		};
	}

	private static Vec3 cabinetConnectionNormal(Direction facing) {
		return Vec3.atLowerCornerOf(switch (facing) {
			case EAST -> Direction.WEST.getNormal();
			case WEST -> Direction.EAST.getNormal();
			default -> facing.getNormal();
		});
	}

	private static ControllerAnchor controllerAnchor(Minecraft mc, Camera camera, boolean leftHand, float partialTick) {
		if (mc.options.getCameraType().isFirstPerson())
			return firstPersonControllerAnchor(mc.player, camera, leftHand, partialTick);
		return thirdPersonControllerAnchor(mc.player, leftHand, partialTick);
	}

	private static ControllerAnchor firstPersonControllerAnchor(Player player, Camera camera, boolean leftHand,
			float partialTick) {
		Vec3 look = vector(camera.getLookVector());
		Vec3 left = vector(camera.getLeftVector());
		Vec3 up = vector(camera.getUpVector());
		Vec3 rear = look.scale(-1.0);
		Vec3 side = left.scale(leftHand ? 1.0 : -1.0);

		Vec3 point = camera.getPosition()
			.add(rear.scale(FIRST_PERSON_WORLD_HIDE_DISTANCE))
			.add(side.scale(FIRST_PERSON_WORLD_HIDE_SIDE_OFFSET))
			.add(up.scale(-FIRST_PERSON_WORLD_HIDE_DOWN_OFFSET));
		Vec3 normal = safeNormalize(rear.add(side.scale(0.2)).add(up.scale(-0.1)), rear);
		return new ControllerAnchor(point, normal, WORLD_UP, FIRST_PERSON_STRAIGHT_LENGTH,
			player.level().getGameTime(), true, null);
	}

	private static ControllerAnchor thirdPersonControllerAnchor(Player player, boolean leftHand, float partialTick) {
		Vec3 bodyForward = horizontalBodyForward(player, partialTick);
		Vec3 bodySide = safeNormalize(bodyForward.cross(WORLD_UP), new Vec3(1.0, 0.0, 0.0));
		Vec3 rear = bodyForward.scale(-1.0);
		double sideSign = leftHand ? -1.0 : 1.0;

		Vec3 handAnchor = player.getPosition(partialTick)
			.add(0.0, player.getBbHeight() * THIRD_PERSON_HAND_HEIGHT, 0.0)
			.add(bodyForward.scale(THIRD_PERSON_HAND_FORWARD_OFFSET))
			.add(bodySide.scale(sideSign * THIRD_PERSON_HAND_SIDE_OFFSET));

		Vec3 point = handAnchor
			.add(rear.scale(THIRD_PERSON_REAR_FACE_OFFSET))
			.add(0.0, THIRD_PERSON_REAR_FACE_UP_OFFSET, 0.0);
		Vec3 normal = safeNormalize(rear.add(bodySide.scale(sideSign * 0.12)).add(0.0, -0.08, 0.0), rear);
		return new ControllerAnchor(point, normal, WORLD_UP, THIRD_PERSON_STRAIGHT_LENGTH, player.level().getGameTime(), false, null);
	}

	private static Vec3 renderPointToWorld(Camera camera, Vector4f point) {
		return camera.getPosition().add(point.x(), point.y(), point.z());
	}

	private static Vec3 vector(Vector3f vector) {
		return new Vec3(vector.x(), vector.y(), vector.z());
	}

	private static Vec3 vector(Vector4f vector) {
		return new Vec3(vector.x(), vector.y(), vector.z());
	}

	private static Vec3 horizontalBodyForward(Player player, float partialTick) {
		float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
		return vector.lengthSqr() < 1.0E-6 ? fallback : vector.normalize();
	}

	private static void renderCurveTube(RenderLevelStageEvent event, Minecraft mc, Vec3 start,
			Direction startFacing, ControllerAnchor anchor) {
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		VertexConsumer consumer = buffers.getBuffer(HOSE_RENDER_TYPE);
		PoseStack poseStack = event.getPoseStack();
		Camera camera = event.getCamera();
		Vec3 cameraPos = camera.getPosition();
		Vec3 end = anchor.point();
		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(start.add(end).scale(0.5)));

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		Vec3 startNormal = cabinetConnectionNormal(startFacing);
		Vec3 rearNormal = anchor.normal().normalize();
		Vec3 rearUp = safeNormalize(anchor.up(), WORLD_UP);
		float hoseWidth = anchor.firstPerson() ? FIRST_PERSON_HOSE_WIDTH : THIRD_PERSON_HOSE_WIDTH;
		FireHoseDynamicRenderer.renderSplineHose(poseStack, consumer, start, end,
			startNormal, rearNormal, rearUp, light, false, hoseWidth, HANDHELD_HOSE_UV_WIDTH);
		poseStack.popPose();
		buffers.endBatch(HOSE_RENDER_TYPE);
	}

	private static boolean shouldRenderForCamera(Minecraft mc) {
		return mc.options.getCameraType().isFirstPerson()
			? ClientConfig.renderHandheldHoseFirstPerson
			: ClientConfig.renderHandheldHoseThirdPerson;
	}

	private record ActiveController(ItemStack stack, boolean leftHand) {
	}

	private record ControllerAnchor(Vec3 point, Vec3 normal, Vec3 up, double straightLength, long gameTime,
			boolean firstPerson, HandheldNozzleControllerItem.Binding binding) {
	}
}
