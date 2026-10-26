package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;

/** Places heads and connects two matching, unoccupied endpoints. */
public final class PhysicalHoseItem extends BlockItem {
    private static final String SELECTION = "PhysicalHoseSelection";
    public PhysicalHoseItem(PhysicalHoseBlock block, Properties properties) { super(block, properties); }

    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("createfirefightingadd.physical_hose.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override public InteractionResult place(BlockPlaceContext context) {
        if (!PhysicalHoses.enabled()) return InteractionResult.FAIL;
        CompoundTag data = context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        InteractionResult result = super.place(context);
        if (result.consumesAction() && !context.getLevel().isClientSide && context.getPlayer() != null
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof PhysicalHoseBlockEntity hose) {
            CompoundTag selected = data.getCompound(SELECTION);
            BlockPos firstPos = BlockPos.of(selected.getLong("Pos"));
            if (selected.hasUUID("Id") && selected.getString("Dimension").equals(context.getLevel().dimension().location().toString())
                    && context.getLevel().hasChunkAt(firstPos)
                    && context.getLevel().getBlockEntity(firstPos) instanceof PhysicalHoseBlockEntity first
                    && first.getFireHoseEndpointId().equals(selected.getUUID("Id")))
                hose.setSize(first.getBlockState().getValue(PhysicalHoseBlock.SIZE));
            select(context, hose, data);
        }
        return result;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!PhysicalHoses.enabled()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("createfirefightingadd.compatibility.disabled"), true);
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PhysicalHoseBlockEntity target))
            return super.useOn(context);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        select(context, target, context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        return InteractionResult.SUCCESS;
    }

    private void select(UseOnContext context, PhysicalHoseBlockEntity target, CompoundTag data) {
        var level = context.getLevel();
        var player = context.getPlayer();
        var stack = context.getItemInHand();
        String message;
        if (player.isShiftKeyDown()) {
            data.remove(SELECTION);
            message = "cleared";
        } else if (target.attached()) {
            message = "invalid";
        } else if (!data.contains(SELECTION)) {
            CompoundTag selected = new CompoundTag();
            selected.putLong("Pos", target.getBlockPos().asLong());
            selected.putUUID("Id", target.getFireHoseEndpointId());
            selected.putString("Dimension", level.dimension().location().toString());
            data.put(SELECTION, selected);
            message = "selected";
        } else {
            CompoundTag selected = data.getCompound(SELECTION);
            BlockPos pos = BlockPos.of(selected.getLong("Pos"));
            boolean connected = selected.getString("Dimension").equals(level.dimension().location().toString())
                && selected.hasUUID("Id") && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof PhysicalHoseBlockEntity first
                && first.getFireHoseEndpointId().equals(selected.getUUID("Id")) && first.connectPhysical(target);
            data.remove(SELECTION);
            message = connected ? "connected" : "invalid";
        }
        if (!stack.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.displayClientMessage(Component.translatable("createfirefightingadd.physical_hose." + message), true);
    }
}
