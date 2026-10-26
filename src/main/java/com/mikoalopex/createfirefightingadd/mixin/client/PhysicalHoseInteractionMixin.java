package com.mikoalopex.createfirefightingadd.mixin.client;

import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives hose heads their block interactions instead of the native wrench-to-cut action. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager", remap = false)
public abstract class PhysicalHoseInteractionMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void physicalHose$use(int modifiers, int action, KeyMapping key,
            CallbackInfoReturnable<InteractCallback.Result> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof PhysicalHoseBlockEntity) {
            cir.setReturnValue(InteractCallback.Result.empty());
            return;
        }
        if (ZiplineClientManager.hoveringRope == null || mc.player.getMainHandItem().is(Items.SHEARS)) return;
        var strand = ClientLevelRopeManager.getOrCreate(mc.level).getStrand(ZiplineClientManager.hoveringRope);
        if (strand != null && (physicalHose$isHead(mc, strand.startAttachment) || physicalHose$isHead(mc, strand.endAttachment)))
            cir.setReturnValue(InteractCallback.Result.empty());
    }

    @Unique
    private static boolean physicalHose$isHead(Minecraft mc, Vec3 attachment) {
        return attachment != null && mc.level.getBlockEntity(BlockPos.containing(attachment)) instanceof PhysicalHoseBlockEntity;
    }
}
