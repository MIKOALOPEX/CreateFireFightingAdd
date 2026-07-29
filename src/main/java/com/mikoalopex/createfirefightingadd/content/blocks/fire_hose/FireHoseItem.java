package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Starts the client-side two-endpoint placement workflow for a fire hose.
 */
public class FireHoseItem extends Item {

    public FireHoseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isLocalPlayer()
                && FireHoseItemHandler.INSTANCE.tryStartPlacement(context)) {
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }
}
