package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import net.minecraft.network.chat.Component;

/**
 * The two wrench actions offered by the hose-port toolbar.
 *
 * <p>ROTATE intentionally keeps the vanilla Create wrench flow. The client
 * sends no rotation request; the ordinary wrench use packet reaches
 * {@code WrenchItem#useOn} and finally the port block's
 * {@code onWrenched(BlockState, UseOnContext)}. This preserves Create's
 * copycat removal and avoids executing the rotation twice.</p>
 */
public enum HoseWrenchMode {
    // Ordinals are shared with Create: Powertrain through NBT.
    // Append new modes; never reorder the existing two.
    ROTATE("createfirefightingadd.hose_wrench.mode.rotate"),
    SIZE("createfirefightingadd.hose_wrench.mode.size");

    private final String translationKey;

    HoseWrenchMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public HoseWrenchMode next() {
        HoseWrenchMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public HoseWrenchMode previous() {
        HoseWrenchMode[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public static boolean isValid(int id) {
        return id >= 0 && id < values().length;
    }

    public static HoseWrenchMode byId(int id) {
        return isValid(id) ? values()[id] : ROTATE;
    }
}
