package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import net.minecraft.world.item.BlockItem;
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
}
