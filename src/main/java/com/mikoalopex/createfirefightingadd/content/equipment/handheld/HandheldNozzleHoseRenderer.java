package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.ClientConfig;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
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
	private static final double THIRD_PERSON_HAND_HEIGHT = 0.48;
	private static final double THIRD_PERSON_HAND_FORWARD_OFFSET = 0.48;
	private static final double THIRD_PERSON_HAND_SIDE_OFFSET = 0.46;
	private static final double THIRD_PERSON_REAR_FACE_OFFSET = 0.23;
	private static final double THIRD_PERSON_REAR_FACE_UP_OFFSET = 0.02;
	private static final double THIRD_PERSON_TOOL_DOWN_BIAS = 0.62;
	private static final double THIRD_PERSON_STRAIGHT_LENGTH = 1.0;
	private static final double STOWED_RIGHT_OFFSET = 0.34;
	private static final double STOWED_BACK_OFFSET = 0.30;
	private static final double STOWED_HEIGHT = 0.78;
	private static final double UNBOUND_STOWED_DROP = 0.16;
	private static final double STOWED_STRAIGHT_LENGTH = 0.75;
	private static final float STOWED_MODEL_SCALE = 1.2f;
	private static final Vector4f HANDHELD_HOSE_FACE_CENTER = new Vector4f(0.0f, 1.4142135f / 16.0f, 0.5f, 1.0f);
	private static final Vector4f HANDHELD_HOSE_FACE_NORMAL = new Vector4f(0.0f, 0.0f, 1.0f, 0.0f);
	private static final Vector4f HANDHELD_HOSE_FACE_UP = new Vector4f(-0.70710677f, 0.70710677f, 0.0f, 0.0f);
	private static final Map<RenderedAnchorKey, ControllerAnchor> RENDERED_ANCHORS = new HashMap<>();

	private HandheldNozzleHoseRenderer() {
	}

	static void captureRenderedAnchor(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack) {
		java.util.Optional<HandheldNozzleControllerItem.Binding> binding = HandheldNozzleControllerItem.readBinding(stack);
		if (binding.isEmpty())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null)
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
		cleanupRenderedAnchors(mc.level.getGameTime());
		RENDERED_ANCHORS.put(RenderedAnchorKey.from(renderedPlayerId(mc, stack, leftHand, binding.get()), binding.get(),
			leftHand), anchor);
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
		float partialTicks = AnimationTickHolder.getPartialTicks();

		for (Player player : mc.level.players()) {
			ActiveController controller = controllerForPlayer(mc, player);
			if (controller == null)
				continue;

			boolean localPlayer = player == mc.player;
			boolean firstPersonStowed = controller.stowed() && localPlayer && mc.options.getCameraType().isFirstPerson();
			if (controller.stowed() && !firstPersonStowed)
				renderStowedController(event, mc, player, controller.nozzleType(), partialTicks);

			HandheldNozzleControllerItem.Binding binding = controller.binding();
			if (binding == null)
				continue;
			if (!shouldRenderHoseForPlayer(mc, localPlayer))
				continue;
			if (!mc.level.dimension().equals(binding.dimension()))
				continue;
			if (!(mc.level.getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet)
				|| !cabinet.getHydrantId().equals(binding.hydrantId()))
				continue;

			BlockState state = cabinet.getBlockState();
			Direction facing = state.hasProperty(FireHydrantCabinetBlock.FACING)
				? state.getValue(FireHydrantCabinetBlock.FACING)
				: Direction.NORTH;
			CabinetAnchor start = cabinetAnchor(cabinet, facing);
			ControllerAnchor anchor = controllerAnchorForRender(mc, event.getCamera(), player, controller,
				partialTicks, firstPersonStowed);
			renderCurveTube(event, mc, start, anchor);
		}
		renderDroppedControllerHoses(event, mc, partialTicks);
	}

	private static void renderDroppedControllerHoses(RenderLevelStageEvent event, Minecraft mc, float partialTicks) {
		if (mc.player == null)
			return;
		for (HandheldNozzleControllerEntity entity : mc.level.getEntitiesOfClass(
				HandheldNozzleControllerEntity.class, mc.player.getBoundingBox().inflate(128.0))) {
			HandheldNozzleControllerItem.Binding binding = HandheldNozzleControllerItem.readBinding(entity.getControllerStack())
				.orElse(null);
			if (binding == null)
				continue;
			if (!mc.level.dimension().equals(binding.dimension()))
				continue;
			if (!(mc.level.getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet)
				|| !cabinet.getHydrantId().equals(binding.hydrantId()))
				continue;

			BlockState state = cabinet.getBlockState();
			Direction facing = state.hasProperty(FireHydrantCabinetBlock.FACING)
				? state.getValue(FireHydrantCabinetBlock.FACING)
				: Direction.NORTH;
			CabinetAnchor start = cabinetAnchor(cabinet, facing);
			ControllerAnchor anchor = new ControllerAnchor(entity.getHoseAnchor(partialTicks),
				entity.getHoseNormal(), entity.getHoseUp(), THIRD_PERSON_STRAIGHT_LENGTH,
				mc.level.getGameTime(), false, binding);
			renderCurveTube(event, mc, start, anchor);
		}
	}

	private static ControllerAnchor controllerAnchorForRender(Minecraft mc, Camera camera, Player player,
			ActiveController controller, float partialTicks, boolean firstPersonStowed) {
		if (firstPersonStowed)
			return firstPersonControllerAnchor(player, camera, controller.leftHand(), partialTicks);
		if (controller.stowed())
			return stowedControllerAnchor(player, partialTicks, controller.nozzleType().hasNozzle(), controller.binding());
		return renderedAnchor(mc, player, controller.leftHand(), controller.binding(), controller.stack())
			.orElseGet(() -> controllerAnchor(mc, camera, player, controller.leftHand(), partialTicks));
	}

	private static java.util.Optional<ControllerAnchor> renderedAnchor(Minecraft mc, Player player, boolean leftHand,
			HandheldNozzleControllerItem.Binding fallbackBinding, ItemStack stack) {
		if (mc.level == null)
			return java.util.Optional.empty();
		HandheldNozzleControllerItem.Binding binding = fallbackBinding != null
			? fallbackBinding
			: HandheldNozzleControllerItem.readBinding(stack).orElse(null);
		if (binding == null)
			return java.util.Optional.empty();
		RenderedAnchorKey key = RenderedAnchorKey.from(player.getUUID(), binding, leftHand);
		ControllerAnchor anchor = freshRenderedAnchor(mc, key);
		if (anchor == null)
			anchor = freshRenderedAnchor(mc, RenderedAnchorKey.from(null, binding, leftHand));
		if (anchor == null)
			return java.util.Optional.empty();
		return java.util.Optional.of(anchor);
	}

	private static ControllerAnchor freshRenderedAnchor(Minecraft mc, RenderedAnchorKey key) {
		ControllerAnchor anchor = RENDERED_ANCHORS.get(key);
		if (anchor == null)
			return null;
		if (mc.level.getGameTime() - anchor.gameTime() > 2) {
			RENDERED_ANCHORS.remove(key);
			return null;
		}
		return anchor;
	}

	private static UUID renderedPlayerId(Minecraft mc, ItemStack stack, boolean leftHand,
			HandheldNozzleControllerItem.Binding binding) {
		if (mc.level == null)
			return null;
		for (Player player : mc.level.players()) {
			if (visibleControllerStack(player, leftHand, binding) == stack)
				return player.getUUID();
		}
		for (Player player : mc.level.players()) {
			if (visibleControllerStack(player, leftHand, binding).is(stack.getItem()))
				return player.getUUID();
		}
		return null;
	}

	private static void cleanupRenderedAnchors(long gameTime) {
		if (RENDERED_ANCHORS.size() <= 128)
			return;
		RENDERED_ANCHORS.entrySet().removeIf(entry -> gameTime - entry.getValue().gameTime() > 2);
	}

	private static boolean sameBinding(HandheldNozzleControllerItem.Binding a, HandheldNozzleControllerItem.Binding b) {
		return a.pos().equals(b.pos())
			&& a.dimension().equals(b.dimension())
			&& a.hydrantId().equals(b.hydrantId())
			&& a.nozzleType() == b.nozzleType();
	}

	private static ActiveController controllerForPlayer(Minecraft mc, Player player) {
		if (player == mc.player)
			return localController(player);
		HandheldNozzleClientHandler.SyncedControllerState state =
			HandheldNozzleClientHandler.getSyncedControllerState(player);
		if (state != null && state.present()) {
			HandheldNozzleType type = state.binding() == null
				? HandheldNozzleType.NONE
				: state.binding().nozzleType();
			ItemStack stack = visibleControllerStack(player, state.leftHand(), state.binding());
			return new ActiveController(stack, state.leftHand(), state.stowed(), state.binding(), type);
		}
		return visibleHandController(player);
	}

	private static ItemStack visibleControllerStack(Player player, boolean leftHand,
			HandheldNozzleControllerItem.Binding binding) {
		boolean mainHandLeft = player.getMainArm() == HumanoidArm.LEFT;
		ItemStack main = player.getMainHandItem();
		if (mainHandLeft == leftHand && isControllerStackForBinding(main, binding))
			return main;

		ItemStack off = player.getOffhandItem();
		if (mainHandLeft != leftHand && isControllerStackForBinding(off, binding))
			return off;

		if (isControllerStackForBinding(main, binding))
			return main;
		if (isControllerStackForBinding(off, binding))
			return off;
		return ItemStack.EMPTY;
	}

	private static boolean isControllerStackForBinding(ItemStack stack, HandheldNozzleControllerItem.Binding binding) {
		if (!stack.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get()))
			return false;
		if (binding == null)
			return true;
		return HandheldNozzleControllerItem.readBinding(stack)
			.map(stackBinding -> sameBinding(stackBinding, binding))
			.orElse(false);
	}

	private static ActiveController localController(Player player) {
		ItemStack main = player.getMainHandItem();
		if (main.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get())) {
			boolean leftHand = player.getMainArm() == HumanoidArm.LEFT;
			return activeController(main, leftHand, false);
		}
		ItemStack off = player.getOffhandItem();
		if (off.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get())) {
			boolean leftHand = player.getMainArm() != HumanoidArm.LEFT;
			return activeController(off, leftHand, false);
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack == main || stack == off)
				continue;
			if (stack.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get())) {
				return activeController(stack, false, true);
			}
		}
		return null;
	}

	private static ActiveController visibleHandController(Player player) {
		ItemStack main = player.getMainHandItem();
		if (main.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get())) {
			boolean leftHand = player.getMainArm() == HumanoidArm.LEFT;
			return activeController(main, leftHand, false);
		}
		ItemStack off = player.getOffhandItem();
		if (off.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get())) {
			boolean leftHand = player.getMainArm() != HumanoidArm.LEFT;
			return activeController(off, leftHand, false);
		}
		return null;
	}

	private static ActiveController activeController(ItemStack stack, boolean leftHand, boolean stowed) {
		HandheldNozzleControllerItem.Binding binding = HandheldNozzleControllerItem.readBinding(stack).orElse(null);
		HandheldNozzleType type = binding == null ? HandheldNozzleType.NONE : binding.nozzleType();
		return new ActiveController(stack, leftHand, stowed, binding, type);
	}

	private static Vec3 cabinetConnectionPoint(BlockPos pos, Direction facing) {
		return Vec3.atLowerCornerOf(pos).add(rotateCabinetLocal(CABINET_HOSE_LOCAL_POINT, facing));
	}

	private static CabinetAnchor cabinetAnchor(FireHydrantCabinetBlockEntity cabinet, Direction facing) {
		Vec3 localPoint = cabinetConnectionPoint(cabinet.getBlockPos(), facing);
		Vec3 localNormal = cabinetConnectionNormal(facing);
		Vec3 point = SableStructureClientCompat.renderPositionToWorld(cabinet, localPoint);
		Vec3 normal = SableStructureClientCompat.renderNormalToWorld(cabinet, localNormal);
		return new CabinetAnchor(point, safeNormalize(normal, localNormal));
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

	private static ControllerAnchor controllerAnchor(Minecraft mc, Camera camera, Player player, boolean leftHand,
			float partialTick) {
		if (player == mc.player && mc.options.getCameraType().isFirstPerson())
			return firstPersonControllerAnchor(mc.player, camera, leftHand, partialTick);
		return thirdPersonControllerAnchor(player, leftHand, partialTick);
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
		double sideSign = leftHand ? -1.0 : 1.0;
		Vec3 viewForward = safeNormalize(player.getViewVector(partialTick), bodyForward);
		Vec3 toolForward = safeNormalize(viewForward.add(0.0, -THIRD_PERSON_TOOL_DOWN_BIAS, 0.0), bodyForward);
		Vec3 toolRear = toolForward.scale(-1.0);
		Vec3 toolUp = safeNormalize(WORLD_UP.subtract(toolForward.scale(WORLD_UP.dot(toolForward))), WORLD_UP);

		Vec3 handAnchor = player.getPosition(partialTick)
			.add(0.0, player.getBbHeight() * THIRD_PERSON_HAND_HEIGHT, 0.0)
			.add(bodyForward.scale(THIRD_PERSON_HAND_FORWARD_OFFSET))
			.add(bodySide.scale(sideSign * THIRD_PERSON_HAND_SIDE_OFFSET));

		Vec3 point = handAnchor
			.add(toolRear.scale(THIRD_PERSON_REAR_FACE_OFFSET))
			.add(0.0, THIRD_PERSON_REAR_FACE_UP_OFFSET, 0.0);
		return new ControllerAnchor(point, toolRear, toolUp, THIRD_PERSON_STRAIGHT_LENGTH,
			player.level().getGameTime(), false, null);
	}

	private static ControllerAnchor stowedControllerAnchor(Player player, float partialTick, boolean hasNozzle,
			HandheldNozzleControllerItem.Binding binding) {
		StowedFrame frame = stowedFrame(player, partialTick, hasNozzle);
		return new ControllerAnchor(frame.anchor(), frame.normal(), frame.up(), STOWED_STRAIGHT_LENGTH,
			player.level().getGameTime(), false, binding);
	}

	private static StowedFrame stowedFrame(Player player, float partialTick, boolean hasNozzle) {
		Vec3 bodyForward = horizontalBodyForward(player, partialTick);
		Vec3 bodySide = safeNormalize(bodyForward.cross(WORLD_UP), new Vec3(1.0, 0.0, 0.0));
		Vec3 rear = bodyForward.scale(-1.0);
		float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
		double height = STOWED_HEIGHT - (hasNozzle ? 0.0 : UNBOUND_STOWED_DROP);
		Vec3 origin = player.getPosition(partialTick)
			.add(0.0, player.getBbHeight() * height, 0.0)
			.add(rear.scale(STOWED_BACK_OFFSET))
			.add(bodySide.scale(STOWED_RIGHT_OFFSET));
		Vec3 anchor = origin.add(transformStowedLocal(vector(HANDHELD_HOSE_FACE_CENTER), yaw, STOWED_MODEL_SCALE));
		Vec3 normal = transformStowedDirection(vector(HANDHELD_HOSE_FACE_NORMAL), yaw);
		Vec3 up = transformStowedDirection(vector(HANDHELD_HOSE_FACE_UP), yaw);
		return new StowedFrame(origin, anchor, normal, up, yaw);
	}

	private static void renderStowedController(RenderLevelStageEvent event, Minecraft mc, Player player,
			HandheldNozzleType nozzleType, float partialTick) {
		StowedFrame frame = stowedFrame(player, partialTick, nozzleType.hasNozzle());
		PoseStack poseStack = event.getPoseStack();
		Vec3 cameraPos = event.getCamera().getPosition();
		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(frame.anchor()));

		poseStack.pushPose();
		poseStack.translate(frame.origin().x - cameraPos.x, frame.origin().y - cameraPos.y, frame.origin().z - cameraPos.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - frame.yaw()));
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
		poseStack.scale(STOWED_MODEL_SCALE, STOWED_MODEL_SCALE, STOWED_MODEL_SCALE);
		poseStack.translate(-0.5f, -0.5f, -0.5f);

		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		renderPartial(PartialModels.HANDHELD_NOZZLE_BASE, poseStack, buffers, light);
		renderPartial(PartialModels.HANDHELD_NOZZLE_HANDLE, poseStack, buffers, light);
		renderPartial(PartialModels.HANDHELD_NOZZLE_COG, poseStack, buffers, light);
		if (nozzleType == HandheldNozzleType.CONE)
			renderPartial(PartialModels.HANDHELD_NOZZLE_CONE, poseStack, buffers, light);
		else if (nozzleType == HandheldNozzleType.FLAT)
			renderPartial(PartialModels.HANDHELD_NOZZLE_FLAT, poseStack, buffers, light);
		poseStack.popPose();
		buffers.endBatch(RenderType.cutoutMipped());
	}

	private static void renderPartial(PartialModel model, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		SuperByteBuffer partial = CachedBuffers.partial(model, Blocks.AIR.defaultBlockState());
		partial.light(packedLight)
			.overlay(OverlayTexture.NO_OVERLAY)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
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

	private static Vec3 transformStowedLocal(Vec3 local, float yaw, float scale) {
		Vector3f vector = new Vector3f((float) local.x, (float) local.y, (float) local.z);
		vector.mul(scale);
		Axis.XP.rotationDegrees(-90.0f).transform(vector);
		Axis.YP.rotationDegrees(180.0f - yaw).transform(vector);
		return new Vec3(vector.x(), vector.y(), vector.z());
	}

	private static Vec3 transformStowedDirection(Vec3 local, float yaw) {
		Vector3f vector = new Vector3f((float) local.x, (float) local.y, (float) local.z);
		Axis.XP.rotationDegrees(-90.0f).transform(vector);
		Axis.YP.rotationDegrees(180.0f - yaw).transform(vector);
		return safeNormalize(new Vec3(vector.x(), vector.y(), vector.z()), WORLD_UP);
	}

	private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
		return vector.lengthSqr() < 1.0E-6 ? fallback : vector.normalize();
	}

	private static void renderCurveTube(RenderLevelStageEvent event, Minecraft mc, CabinetAnchor start,
			ControllerAnchor anchor) {
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		VertexConsumer consumer = buffers.getBuffer(HOSE_RENDER_TYPE);
		PoseStack poseStack = event.getPoseStack();
		Camera camera = event.getCamera();
		Vec3 cameraPos = camera.getPosition();
		Vec3 end = anchor.point();
		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(start.point().add(end).scale(0.5)));

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		Vec3 rearNormal = anchor.normal().normalize();
		Vec3 rearUp = safeNormalize(anchor.up(), WORLD_UP);
		float hoseWidth = anchor.firstPerson() ? FIRST_PERSON_HOSE_WIDTH : THIRD_PERSON_HOSE_WIDTH;
		FireHoseDynamicRenderer.renderSplineHose(poseStack, consumer, start.point(), end,
			start.normal(), rearNormal, rearUp, light, false, hoseWidth, HANDHELD_HOSE_UV_WIDTH);
		poseStack.popPose();
		buffers.endBatch(HOSE_RENDER_TYPE);
	}

	private static boolean shouldRenderHoseForPlayer(Minecraft mc, boolean localPlayer) {
		if (!localPlayer)
			return ClientConfig.renderHandheldHoseThirdPerson;
		return mc.options.getCameraType().isFirstPerson()
			? ClientConfig.renderHandheldHoseFirstPerson
			: ClientConfig.renderHandheldHoseThirdPerson;
	}

	private record ActiveController(ItemStack stack, boolean leftHand, boolean stowed,
			HandheldNozzleControllerItem.Binding binding, HandheldNozzleType nozzleType) {
	}

	private record ControllerAnchor(Vec3 point, Vec3 normal, Vec3 up, double straightLength, long gameTime,
			boolean firstPerson, HandheldNozzleControllerItem.Binding binding) {
	}

	private record CabinetAnchor(Vec3 point, Vec3 normal) {
	}

	private record RenderedAnchorKey(UUID playerId, BlockPos pos, ResourceKey<Level> dimension, UUID hydrantId,
			HandheldNozzleType nozzleType, boolean leftHand) {
		private static RenderedAnchorKey from(UUID playerId, HandheldNozzleControllerItem.Binding binding, boolean leftHand) {
			return new RenderedAnchorKey(playerId, binding.pos(), binding.dimension(), binding.hydrantId(),
				binding.nozzleType(), leftHand);
		}
	}

	private record StowedFrame(Vec3 origin, Vec3 anchor, Vec3 normal, Vec3 up, float yaw) {
	}
}
