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
public record BallCouplingSettingsPacket(int containerId, int mode, int role, int lower, int upper)
        implements CustomPacketPayload {
    public static final Type<BallCouplingSettingsPacket> TYPE = new Type<>(CreateFireFightingAdd.path("coupling_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BallCouplingSettingsPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId); buf.writeVarInt(packet.mode); buf.writeVarInt(packet.role);
            buf.writeVarInt(packet.lower); buf.writeVarInt(packet.upper);
        }, buf -> new BallCouplingSettingsPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToServer(TYPE, STREAM_CODEC, BallCouplingSettingsPacket::handle);
    }

    private static void handle(BallCouplingSettingsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof BallCouplingMenu menu && menu.containerId == packet.containerId)
                menu.applySettings(context.player(), packet.mode, packet.role, packet.lower, packet.upper);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
