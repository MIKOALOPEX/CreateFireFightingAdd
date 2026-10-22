package com.mikoalopex.createfirefightingadd.compatibility;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class CompatibleBlockItem extends BlockItem {
    public CompatibleBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override public InteractionResult place(BlockPlaceContext context) {
        if (!ComponentPolicies.enabled(getBlock())) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide)
                context.getPlayer().displayClientMessage(Component.translatable("createfirefightingadd.compatibility.disabled"), true);
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
