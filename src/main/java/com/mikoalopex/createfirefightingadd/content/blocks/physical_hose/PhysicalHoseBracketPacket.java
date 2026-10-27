package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.UUID;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Sends segment selection or cancellation to the server.
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record PhysicalHoseBracketPacket(BlockPos owner, UUID rope, int segment, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<PhysicalHoseBracketPacket> TYPE = new Type<>(CreateFireFightingAdd.path("physical_hose_bracket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicalHoseBracketPacket> CODEC = StreamCodec.of(
        (buf, packet) -> { buf.writeBlockPos(packet.owner); buf.writeUUID(packet.rope); buf.writeVarInt(packet.segment); buf.writeEnum(packet.hand); },
        buf -> new PhysicalHoseBracketPacket(buf.readBlockPos(), buf.readUUID(), buf.readVarInt(), buf.readEnum(InteractionHand.class)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    @SubscribeEvent static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToServer(TYPE, CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PhysicalHoseBracketInteraction.select(player, packet);
        }));
    }
}
