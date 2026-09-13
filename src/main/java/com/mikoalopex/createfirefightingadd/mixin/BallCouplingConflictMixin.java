package com.mikoalopex.createfirefightingadd.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.CouplingKinetics;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RotationPropagator.class, remap = false)
public class BallCouplingConflictMixin {
    @WrapOperation(method = "propagateNewSource", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private static boolean ballCoupling$suspend(Level level, BlockPos pos, boolean drops, Operation<Boolean> original) {
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity be && CouplingKinetics.protectConflict(be)) return false;
        return original.call(level,pos,drops);
    }
}
