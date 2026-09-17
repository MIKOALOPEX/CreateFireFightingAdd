package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The single inventory representation for every stress coupling configuration. */
public final class StressCouplingItem extends BlockItem {
    public StressCouplingItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public String getDescriptionId() {
        return "item.createfirefightingadd.stress_coupling";
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!StressCouplingCompatibility.enabled()) {
            StressCouplingCompatibility.notifyPlayer(context.getPlayer());
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!StressCouplingCompatibility.enabled()) {
            StressCouplingCompatibility.notifyPlayer(player);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!StressCouplingCompatibility.enabled())
            tooltip.add(StressCouplingCompatibility.hint());
    }
}
