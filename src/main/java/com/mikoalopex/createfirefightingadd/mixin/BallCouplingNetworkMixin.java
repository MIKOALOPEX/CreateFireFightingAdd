package com.mikoalopex.createfirefightingadd.mixin;

import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.CouplingKinetics;
import com.simibubi.create.content.kinetics.KineticNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KineticNetwork.class, remap = false)
public class BallCouplingNetworkMixin {
    @Inject(method = "calculateCapacity", at = @At("RETURN"), cancellable = true)
    private void ballCoupling$capacity(CallbackInfoReturnable<Float> result) {
        result.setReturnValue(result.getReturnValue() + CouplingKinetics.extra((KineticNetwork)(Object)this,true));
    }
    @Inject(method = "calculateStress", at = @At("RETURN"), cancellable = true)
    private void ballCoupling$stress(CallbackInfoReturnable<Float> result) {
        result.setReturnValue(result.getReturnValue() + CouplingKinetics.extra((KineticNetwork)(Object)this,false));
    }
}
