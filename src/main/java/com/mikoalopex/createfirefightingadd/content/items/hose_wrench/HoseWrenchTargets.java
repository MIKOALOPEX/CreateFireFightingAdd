package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client and server must agree on what counts as one of this mod's hose ports.
 *
 * <p>A port must expose the thickness action and be registered in this mod's
 * namespace. The namespace check is important for the Powertrain split: an
 * unregistered legacy {@code FireHoseBlock} never becomes a toolbar target.</p>
 */
public final class HoseWrenchTargets {
    private HoseWrenchTargets() {
    }

    public static boolean isHosePort(BlockState state) {
        if (state == null || !(state.getBlock() instanceof HoseWrenchSizeCyclable)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && CreateFireFightingAdd.MODID.equals(id.getNamespace());
    }
}
