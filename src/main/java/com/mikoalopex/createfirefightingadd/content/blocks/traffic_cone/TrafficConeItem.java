package com.mikoalopex.createfirefightingadd.content.blocks.traffic_cone;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class TrafficConeItem extends BlockItem implements Equipable {
	private static final ItemAttributeModifiers ARMOR_ATTRIBUTES = ItemAttributeModifiers.builder()
		.add(Attributes.ARMOR,
			new AttributeModifier(ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID,
				"traffic_cone.armor"), 1.0, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.HEAD)
		.build();

	public TrafficConeItem(Block block, Item.Properties properties) {
		super(block, properties.attributes(ARMOR_ATTRIBUTES));
	}

	@Override
	public EquipmentSlot getEquipmentSlot() {
		return EquipmentSlot.HEAD;
	}

	@Override
	public Holder<SoundEvent> getEquipSound() {
		return SoundEvents.ARMOR_EQUIP_LEATHER;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty())
			return InteractionResultHolder.fail(held);

		// Keep the placeable item stackable while equipping only one cone.
		player.setItemSlot(EquipmentSlot.HEAD, held.copyWithCount(1));
		if (!player.hasInfiniteMaterials())
			held.shrink(1);
		return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
	}
}
