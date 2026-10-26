package com.mikoalopex.createfirefightingadd.mixin.client;

import com.mikoalopex.createfirefightingadd.compatibility.CompatibilitySnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTabs.class)
public abstract class CompatibilityCreativeTabsMixin {
    @Shadow private static CreativeModeTab.ItemDisplayParameters CACHED_PARAMETERS;
    @Unique private static long firefighting$revision = -1;

    @Inject(method = "tryRebuildTabContents", at = @At("HEAD"))
    private static void firefighting$invalidate(FeatureFlagSet flags, boolean permissions,
            HolderLookup.Provider registries, CallbackInfoReturnable<Boolean> ci) {
        long revision = CompatibilitySnapshot.revision();
        if (firefighting$revision == revision) return;
        firefighting$revision = revision;
        // Vanilla refreshes the current tab and its search trees after rebuilding.
        CACHED_PARAMETERS = null;
    }
}
