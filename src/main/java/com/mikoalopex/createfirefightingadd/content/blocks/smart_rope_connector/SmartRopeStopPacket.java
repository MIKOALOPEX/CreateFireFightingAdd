package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import java.util.UUID;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Native stop packets contain only a position; smart connectors also need the strand identity. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record SmartRopeStopPacket(BlockPos pos, UUID id) implements CustomPacketPayload {
    public static final Type<SmartRopeStopPacket> TYPE = new Type<>(CreateFireFightingAdd.path("smart_rope_stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SmartRopeStopPacket> CODEC = StreamCodec.of(
        (buf, packet) -> { buf.writeBlockPos(packet.pos); buf.writeUUID(packet.id); },
        buf -> new SmartRopeStopPacket(buf.readBlockPos(), buf.readUUID()));

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToClient(TYPE, CODEC, (packet, context) ->
            context.enqueueWork(() -> {
                if (context.player().level().getBlockEntity(packet.pos) instanceof SmartRopeConnectorBlockEntity smart)
                    smart.stopClientRope(packet.id);
            }));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
