package com.createfireworkadd.createfirefightingadd.content.fluids.water_intake;

import com.createfireworkadd.createfirefightingadd.content.items.WaterIntakeBindingItem;

import net.createmod.catnip.outliner.Outliner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public class BoundBlockHighlightHandler {

	private static final Object SLOT = new Object();
	private static final int COLOR = 0xCC4A90D9;

	public static void init() {
		NeoForge.EVENT_BUS.register(new BoundBlockHighlightHandler());
	}

	@SubscribeEvent
	public void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			Outliner.getInstance().remove(SLOT);
			return;
		}

		BlockPos boundPos = getBoundPosFromItem(mc.player.getMainHandItem());
		if (boundPos == null)
			boundPos = getBoundPosFromItem(mc.player.getOffhandItem());

		if (boundPos == null) {
			Outliner.getInstance().remove(SLOT);
			return;
		}

		AABB box = getWorldAABB(mc.level, boundPos);

		Outliner.getInstance().showAABB(SLOT, box)
			.colored(COLOR)
			.lineWidth(1f / 16f)
			.clearTextures()
			.disableLineNormals();
	}

	private static AABB getWorldAABB(ClientLevel level, BlockPos pos) {
		return new AABB(pos.getX(), pos.getY(), pos.getZ(),
			pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
	}

	private static BlockPos getBoundPosFromItem(ItemStack stack) {
		if (stack.isEmpty())
			return null;
		if (!(stack.getItem() instanceof WaterIntakeBindingItem))
			return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return null;
		CompoundTag tag = data.copyTag();
		if (!tag.contains("BoundIntakePos"))
			return null;
		return BlockPos.of(tag.getLong("BoundIntakePos"));
	}
}
