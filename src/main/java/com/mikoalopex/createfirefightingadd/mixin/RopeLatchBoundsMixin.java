package com.mikoalopex.createfirefightingadd.mixin;

import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.RopeLatchCompatibility;
import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.RopeLatchIndex;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses bounds already computed by Sable's ticket update, without scanning rope points again. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject", remap = false)
public abstract class RopeLatchBoundsMixin {
    @Unique private ServerLevel firefighting$ropeLevel;

    @Inject(method = "onAddition", at = @At("RETURN"))
    private void firefighting$added(SubLevelPhysicsSystem system, CallbackInfo ci) {
        if (RopeLatchCompatibility.available()) firefighting$ropeLevel = system.getLevel();
    }

    @Inject(method = "getBoundingBox", at = @At("RETURN"))
    private void firefighting$bounds(BoundingBox3d bounds, CallbackInfo ci) {
        if (firefighting$ropeLevel != null)
            RopeLatchIndex.observe(firefighting$ropeLevel, this, bounds);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void firefighting$removed(CallbackInfo ci) {
        if (firefighting$ropeLevel != null) {
            RopeLatchIndex.forget(firefighting$ropeLevel, this);
            firefighting$ropeLevel = null;
        }
    }
}
