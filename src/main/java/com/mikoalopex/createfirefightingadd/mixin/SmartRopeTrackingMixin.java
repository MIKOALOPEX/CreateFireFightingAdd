package com.mikoalopex.createfirefightingadd.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnectorBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeStopPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.network.packets.rope.ClientboundRopeStoppedPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves the native tracking schedule while resolving the owner of each individual strand. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeTrackingSystem", remap = false)
public class SmartRopeTrackingMixin {
    @WrapOperation(method = "sendTrackingData", at = @At(value = "INVOKE",
        target = "Ldev/simulated_team/simulated/content/blocks/rope/RopeStrandHolderBehavior;makeStopPacket()Ldev/simulated_team/simulated/network/packets/rope/ClientboundRopeStoppedPacket;"))
    private ClientboundRopeStoppedPacket smartRope$stop(
            RopeStrandHolderBehavior holder,
            Operation<ClientboundRopeStoppedPacket> original,
            @Local ServerRopeStrand strand) {
        if (holder.blockEntity instanceof SmartRopeConnectorBlockEntity smart) {
            var packet = new SmartRopeStopPacket(
                smart.getBlockPos(), strand.getUUID());
            for (var player : holder.getStrandTrackingPlayers())
                PacketDistributor.sendToPlayer(player, packet);
        }
        return original.call(holder);
    }

    @WrapOperation(method = {"neededPlayers", "sendTrackingData"}, at = @At(value = "INVOKE",
        target = "Ldev/simulated_team/simulated/content/blocks/rope/RopeStrandHolderBehavior;get(Lnet/minecraft/world/level/block/entity/BlockEntity;Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;)Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;"))
    private BlockEntityBehaviour smartRope$owner(BlockEntity be, BehaviourType<?> type,
            Operation<BlockEntityBehaviour> original, @Local ServerRopeStrand strand) {
        return be instanceof SmartRopeConnectorBlockEntity smart ? smart.owner(strand.getUUID()) : original.call(be, type);
    }
}
