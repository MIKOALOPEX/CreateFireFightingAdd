package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record BallCouplingDisconnectPacket(int containerId) implements CustomPacketPayload {
    public static final Type<BallCouplingDisconnectPacket> TYPE =
        new Type<>(CreateFireFightingAdd.path("coupling_gui_disconnect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BallCouplingDisconnectPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> buf.writeVarInt(packet.containerId),
            buf -> new BallCouplingDisconnectPacket(buf.readVarInt()));

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToServer(TYPE, STREAM_CODEC, BallCouplingDisconnectPacket::handle);
    }

    private static void handle(BallCouplingDisconnectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof BallCouplingMenu menu && menu.containerId == packet.containerId)
                menu.disconnectFromGui(context.player());
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
