package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class HandheldNozzleClientExtensions implements IClientItemExtensions {
	private final HandheldNozzleControllerItemRenderer renderer = new HandheldNozzleControllerItemRenderer();

	@Override
	public BlockEntityWithoutLevelRenderer getCustomRenderer() {
		return renderer;
	}

	@Override
	public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
		if (HandheldNozzleClientHandler.isSyncedSpraying(entity) && isControllerHand(entity, hand))
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		return IClientItemExtensions.super.getArmPose(entity, hand, stack);
	}

	@Override
	public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
			ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
		if (!isSprayingArm(player, arm))
			return false;

		int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(sign * 0.56F, -0.48F + equipProcess * -0.6F, -0.74F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(sign * 28.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(sign * -8.0F));
		poseStack.translate(sign * -0.20F, 0.08F, 0.08F);
		poseStack.mulPose(Axis.YN.rotationDegrees(sign * 38.0F));
		return true;
	}

	private static boolean isSprayingArm(LocalPlayer player, HumanoidArm arm) {
		if (!HandheldNozzleClientHandler.isSpraying())
			return false;
		InteractionHand activeHand = activeControllerHand(player);
		if (activeHand == null)
			return false;
		HumanoidArm activeArm = activeHand == InteractionHand.MAIN_HAND
			? player.getMainArm()
			: player.getMainArm().getOpposite();
		return activeArm == arm;
	}

	private static InteractionHand activeControllerHand(LocalPlayer player) {
		if (player.getMainHandItem().is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get()))
			return InteractionHand.MAIN_HAND;
		if (player.getOffhandItem().is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get()))
			return InteractionHand.OFF_HAND;
		return null;
	}

	private static boolean isControllerHand(LivingEntity entity, InteractionHand hand) {
		return entity.getItemInHand(hand).is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get());
	}
}
