package com.mikoalopex.createfirefightingadd.mixin.client;

import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeCompatibility;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets connector selection handle use before the native hovered-rope action can cut another strand. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager", remap = false)
public abstract class SmartRopeShearsMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void smartRope$select(int modifiers, int action, KeyMapping key,
            CallbackInfoReturnable<InteractCallback.Result> cir) {
        if (SmartRopeCompatibility.selecting())
            cir.setReturnValue(InteractCallback.Result.empty());
    }
}
