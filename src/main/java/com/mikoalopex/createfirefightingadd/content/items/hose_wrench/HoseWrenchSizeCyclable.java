package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by hose-port blocks that expose the thickness action.
 *
 * <p>The toolbar calls {@code cycleSize} directly. A block is a valid toolbar
 * target only when it implements this interface <em>and</em> is registered in
 * this mod's namespace.</p>
 */
@FunctionalInterface
public interface HoseWrenchSizeCyclable {
    InteractionResult cycleSize(BlockState state, UseOnContext context);
}
