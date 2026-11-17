package com.mikoalopex.createfirefightingadd.mixin.client;

import com.mikoalopex.createfirefightingadd.client.HoseWrenchRadialGuard;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Create's native wrench rotation wheel from opening on this mod's hose
 * ports, so the ALT wheel stays with {@code HoseWrenchClient}'s mode GUI.
 *
 * <p>The target is addressed by name and marked {@link Pseudo} so the mixin
 * degrades to a no-op instead of a hard class-load failure if a Create build
 * moves or removes the handler. {@code require = 0} keeps the same guarantee
 * for the method. This entry lives in the {@code client} section of
 * {@code createfirefightingadd.mixins.json}: dedicated servers never load it,
 * and the guard class it references is client-only.</p>
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.wrench.RadialWrenchHandler", remap = false)
public abstract class RadialWrenchMenuGuardMixin {
    @Inject(method = "onKeyInput", at = @At("HEAD"), cancellable = true, require = 0)
    private static void firefighting$keepHoseWheel(int key, boolean pressed, CallbackInfo callback) {
        if (HoseWrenchRadialGuard.shouldSuppressNativeRotationMenu(key, pressed)) {
            callback.cancel();
        }
    }
}
