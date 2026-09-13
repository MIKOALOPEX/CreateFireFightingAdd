package com.mikoalopex.createfirefightingadd.mixin;

import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.CouplingKinetics;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KineticBlockEntity.class, remap = false)
public class BallCouplingKineticSaveMixin {
    @Inject(method = "write", at = @At("RETURN"))
    private void ballCoupling$localTotals(CompoundTag tag, HolderLookup.Provider registries, boolean packet, CallbackInfo ci) {
        if (!packet) CouplingKinetics.writeLocalNetwork((KineticBlockEntity)(Object)this, tag);
    }
}
