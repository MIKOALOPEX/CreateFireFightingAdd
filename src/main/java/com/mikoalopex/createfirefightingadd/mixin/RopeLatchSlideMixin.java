package com.mikoalopex.createfirefightingadd.mixin;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.SlidingRopeHandle;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Transfers length at the native pre-solver boundary, never during a solver step. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand", remap = false)
public abstract class RopeLatchSlideMixin {
    @Inject(method = "prePhysicsTick", at = @At("RETURN"))
    private void firefighting$slide(SubLevelPhysicsSystem system, ServerLevel level, double timeStep, CallbackInfo ci) {
        if (!(((RopeLatchPhysicsAccess) this).firefighting$getHandle() instanceof SlidingRopeHandle slide)) return;
        try {
            slide.physicsStep(level.getGameTime());
        } catch (RuntimeException exception) {
            slide.markFailed();
            ServerRopeStrand rope = (ServerRopeStrand) (Object) this;
            try {
                slide.release(level, rope);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
                if (rope.isActive()) system.removeObject(rope);
            }
            CreateFireFightingAdd.LOGGER.warn("Sliding rope latch released rope {} after a physics update failure.", rope.getUUID(), exception);
        }
    }
}
