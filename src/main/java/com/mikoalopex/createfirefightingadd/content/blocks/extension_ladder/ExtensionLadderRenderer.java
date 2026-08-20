package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ExtensionLadderRenderer extends SmartBlockEntityRenderer<ExtensionLadderBlockEntity> {
	public ExtensionLadderRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(ExtensionLadderBlockEntity be, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if (!be.getBlockState().is(CreateFireFightingAdd.EXTENSION_LADDER.get()))
			return;

		float pitch = be.getPitch(partialTick);
		float movePixels = be.getMoveOffsetPixels(partialTick);
		float wobble = be.getDropWobbleDegrees(partialTick);
		Vec3 anchor = be.getAnchorOffset().add(0, be.getDropOffsetPixels(partialTick) * ExtensionLadderGeometry.PIXEL, 0);
		ExtensionLadderGeometry.LocalFrame modelFrame = ExtensionLadderGeometry.localFrame(anchor, be.getFallDirection(),
			pitch, true);

		poseStack.pushPose();
		poseStack.translate(anchor.x, anchor.y, anchor.z);
		poseStack.mulPose(basisRotation(modelFrame.right(), modelFrame.longAxis(), modelFrame.normal()));
		if (wobble != 0)
			poseStack.mulPose(Axis.ZP.rotationDegrees(wobble));
		poseStack.translate(-ExtensionLadderGeometry.MODEL_PIVOT.x, -ExtensionLadderGeometry.MODEL_PIVOT.y,
			-ExtensionLadderGeometry.MODEL_PIVOT.z);
		renderPartial(PartialModels.EXTENSION_LADDER_NOMOVE, poseStack, buffer, packedLight, packedOverlay);
		poseStack.pushPose();
		poseStack.translate(0, movePixels * ExtensionLadderGeometry.PIXEL, 0);
		renderPartial(PartialModels.EXTENSION_LADDER_ONLYMOVE, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
		poseStack.popPose();

		Vec3 p1 = modelPoint(modelFrame, ExtensionLadderGeometry.PULLEY_POINT_1, 0, wobble);
		Vec3 p2 = modelPoint(modelFrame, ExtensionLadderGeometry.PULLEY_POINT_2, 0, wobble);
		Vec3 tip = modelPoint(modelFrame, ExtensionLadderGeometry.TIP_SOUTH_FACE_CENTER, movePixels, wobble);
		renderRope(tip, p1, poseStack, buffer, packedLight, packedOverlay);
		renderRope(p1, p2, poseStack, buffer, packedLight, packedOverlay);
		renderRope(p2, p2.add(0, -30 * ExtensionLadderGeometry.PIXEL, 0), poseStack, buffer, packedLight, packedOverlay);
		renderClimbHitbox(be, pitch, movePixels, poseStack, buffer);
	}

	private static Quaternionf basisRotation(Vec3 xAxis, Vec3 yAxis, Vec3 zAxis) {
		Vector3f x = new Vector3f((float) xAxis.x, (float) xAxis.y, (float) xAxis.z).normalize();
		Vector3f y = new Vector3f((float) yAxis.x, (float) yAxis.y, (float) yAxis.z).normalize();
		Vector3f z = new Vector3f((float) zAxis.x, (float) zAxis.y, (float) zAxis.z).normalize();
		return new Quaternionf().setFromUnnormalized(new Matrix3f().set(x, y, z));
	}

	private static Vec3 modelPoint(ExtensionLadderGeometry.LocalFrame frame, Vec3 modelLocal, double moveOffsetPixels,
		float wobbleDegrees) {
		Vec3 local = modelLocal;
		if (moveOffsetPixels != 0)
			local = local.add(0, moveOffsetPixels * ExtensionLadderGeometry.PIXEL, 0);
		Vec3 delta = local.subtract(ExtensionLadderGeometry.MODEL_PIVOT);
		if (wobbleDegrees != 0) {
			double angle = Math.toRadians(wobbleDegrees);
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			delta = new Vec3(delta.x * cos - delta.y * sin, delta.x * sin + delta.y * cos, delta.z);
		}
		return frame.anchor()
			.add(frame.right().scale(delta.x))
			.add(frame.longAxis().scale(delta.y))
			.add(frame.normal().scale(delta.z));
	}

	private static void renderPartial(PartialModel model, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int packedOverlay) {
		SuperByteBuffer partial = CachedBuffers.partial(model, Blocks.AIR.defaultBlockState());
		partial.light(packedLight)
			.overlay(packedOverlay)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	private static void renderRope(Vec3 start, Vec3 end, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int packedOverlay) {
		Vec3 vector = end.subtract(start);
		double length = vector.length();
		if (length < 1.0E-4)
			return;
		Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
			new Vector3f((float) (vector.x / length), (float) (vector.y / length), (float) (vector.z / length)));
		poseStack.pushPose();
		poseStack.translate(start.x, start.y, start.z);
		poseStack.mulPose(rotation);
		poseStack.scale(1, (float) length, 1);
		renderPartial(PartialModels.EXTENSION_LADDER_ROPE, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static void renderClimbHitbox(ExtensionLadderBlockEntity be, float pitch, float movePixels,
		PoseStack poseStack, MultiBufferSource buffer) {
		if (!Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes())
			return;

		ExtensionLadderGeometry.LocalFrame localFrame = be.localPhysicsFrame(pitch);
		ExtensionLadderGeometry.WorldFrame frame =
			new ExtensionLadderGeometry.WorldFrame(localFrame.anchor(), localFrame.right(), localFrame.longAxis(),
				localFrame.normal());
		Vec3 base = Vec3.atLowerCornerOf(be.getBlockPos());
		Vec3[] corners = frame.climbBoxCorners(movePixels);
		int[][] edges = {
			{ 0, 1 }, { 0, 2 }, { 1, 3 }, { 2, 3 },
			{ 4, 5 }, { 4, 6 }, { 5, 7 }, { 6, 7 },
			{ 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 }
		};

		VertexConsumer vc = buffer.getBuffer(RenderType.lines());
		Matrix4f matrix = poseStack.last().pose();
		for (int[] edge : edges) {
			Vec3 a = corners[edge[0]].subtract(base);
			Vec3 b = corners[edge[1]].subtract(base);
			renderLine(matrix, vc, a, b);
		}
	}

	private static void renderLine(Matrix4f matrix, VertexConsumer vc, Vec3 from, Vec3 to) {
		vc.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
			.setColor(1f, 1f, 0f, 1f)
			.setNormal(0, 1, 0);
		vc.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
			.setColor(1f, 1f, 0f, 1f)
			.setNormal(0, 1, 0);
	}
}
