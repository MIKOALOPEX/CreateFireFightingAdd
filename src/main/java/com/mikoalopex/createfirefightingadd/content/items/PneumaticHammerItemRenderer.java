package com.mikoalopex.createfirefightingadd.content.items;

import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PneumaticHammerItemRenderer extends CustomRenderedItemModelRenderer {
	private static final float COG_ORIGIN_X = 8.0F / 16.0F - 0.5F;
	private static final float COG_ORIGIN_Y = 27.0F / 16.0F - 0.5F;
	private static final float COG_ORIGIN_Z = 7.0F / 16.0F - 0.5F;
	private static final float IDLE_ROTATION_DEGREES_PER_TICK = 1.2F;
	private static final float CHARGE_ROTATION_DEGREES = 360.0F;
	private static final float RELEASE_SPIN_TICKS = 8.0F;

	private static float releaseSpinStart = -1000.0F;

	public static void triggerReleaseSpin() {
		releaseSpinStart = AnimationTickHolder.getRenderTime();
	}

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		renderer.renderSolid(PartialModels.PNEUMATIC_HAMMER_BASE.get(), light);

		poseStack.pushPose();
		poseStack.translate(COG_ORIGIN_X, COG_ORIGIN_Y, COG_ORIGIN_Z);
		poseStack.mulPose(Axis.ZP.rotationDegrees(getCogAngle(stack, transformType)));
		poseStack.translate(-COG_ORIGIN_X, -COG_ORIGIN_Y, -COG_ORIGIN_Z);
		renderer.renderSolid(PartialModels.PNEUMATIC_HAMMER_COG.get(), light);
		poseStack.popPose();
	}

	private static float getCogAngle(ItemStack stack, ItemDisplayContext transformType) {
		float angle = 0.0F;
		if (isHandContext(transformType)) {
			angle += getIdleAngle();
			angle += getChargeAngle(stack);
			angle += getReleaseAngle();
		}
		return angle % 360.0F;
	}

	private static float getIdleAngle() {
		return AnimationTickHolder.getRenderTime() * IDLE_ROTATION_DEGREES_PER_TICK;
	}

	private static float getChargeAngle(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isUsingItem())
			return 0.0F;
		if (!(player.getUseItem().getItem() instanceof PneumaticHammerItem))
			return 0.0F;

		float usedTicks = player.getUseItem().getUseDuration(player)
			- player.getUseItemRemainingTicks()
			+ AnimationTickHolder.getPartialTicks();
		float progress = Mth.clamp(usedTicks / PneumaticHammerItem.getChargeTimeTicks(), 0.0F, 1.0F);
		return progress * CHARGE_ROTATION_DEGREES;
	}

	private static float getReleaseAngle() {
		float age = AnimationTickHolder.getRenderTime() - releaseSpinStart;
		if (age < 0.0F || age > RELEASE_SPIN_TICKS)
			return 0.0F;
		float progress = Mth.clamp(age / RELEASE_SPIN_TICKS, 0.0F, 1.0F);
		float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
		return eased * 360.0F;
	}

	private static boolean isHandContext(ItemDisplayContext transformType) {
		return transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
			|| transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
	}
}
