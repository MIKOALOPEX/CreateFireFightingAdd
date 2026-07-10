package com.mikoalopex.createfirefightingadd.content.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class PneumaticHammerClientExtensions implements IClientItemExtensions {
	private static final float CHARGE_SCREEN_CLEAR_X_OFFSET = 0.18F;
	private static final float CHARGE_SCREEN_CLEAR_Y_OFFSET = -0.16F;

	private final PneumaticHammerItemRenderer renderer = new PneumaticHammerItemRenderer();

	@Override
	public BlockEntityWithoutLevelRenderer getCustomRenderer() {
		return renderer;
	}

	@Override
	public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
		ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
		if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0)
			return false;

		HumanoidArm usedArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
			? player.getMainArm()
			: player.getMainArm().getOpposite();
		if (usedArm != arm)
			return false;

		applyBaseHandTransform(poseStack, arm, equipProcess);
		applyChargeTransform(poseStack, arm, itemInHand, player, partialTick);
		return true;
	}

	private static void applyBaseHandTransform(PoseStack poseStack, HumanoidArm arm, float equipProcess) {
		int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(sign * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
	}

	private static void applyChargeTransform(PoseStack poseStack, HumanoidArm arm, ItemStack itemInHand,
		LocalPlayer player, float partialTick) {
		int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(sign * (-0.2785682F + CHARGE_SCREEN_CLEAR_X_OFFSET),
			0.18344387F + CHARGE_SCREEN_CLEAR_Y_OFFSET, 0.15731531F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-13.935F));
		poseStack.mulPose(Axis.YP.rotationDegrees(sign * 35.3F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(sign * -9.785F));

		float usedTicks = itemInHand.getUseDuration(player) - player.getUseItemRemainingTicks() + partialTick;
		float charge = Mth.clamp(usedTicks / PneumaticHammerItem.getChargeTimeTicks(), 0.0F, 1.0F);
		charge = (charge * charge + charge * 2.0F) / 3.0F;

		if (charge > 0.1F) {
			float shake = Mth.sin((usedTicks - 0.1F) * 1.3F) * (charge - 0.1F);
			poseStack.translate(0.0F, shake * 0.004F, 0.0F);
		}

		poseStack.translate(0.0F, 0.0F, charge * 0.04F);
		poseStack.scale(1.0F, 1.0F, 1.0F + charge * 0.2F);
		poseStack.mulPose(Axis.YN.rotationDegrees(sign * 45.0F));
	}
}
