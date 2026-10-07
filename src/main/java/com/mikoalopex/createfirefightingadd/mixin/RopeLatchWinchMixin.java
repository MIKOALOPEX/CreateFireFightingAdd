package com.mikoalopex.createfirefightingadd.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.PinnedRopeHandle;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Unlatched winches keep their native extension, stretch and range handling. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity", remap = false)
public abstract class RopeLatchWinchMixin {
    @ModifyExpressionValue(method = "updateRopeStrandExtension", at = @At(value = "INVOKE",
        target = "Ldev/simulated_team/simulated/content/blocks/rope/rope_winch/RopeWinchBlockEntity;getMovementSpeed()F"))
    private float firefighting$limitRetraction(float speed, @Local(argsOnly = true) ServerRopeStrand strand) {
        return ((RopeLatchPhysicsAccess) strand).firefighting$getHandle() instanceof PinnedRopeHandle pin
            ? pin.limitWinchSpeed(speed, strand.getExtension() + strand.getPoints().size() - 2) : speed;
    }
}
