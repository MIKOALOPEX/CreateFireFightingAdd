package com.mikoalopex.createfirefightingadd.compatibility;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Synchronizes server capabilities when a player logs in. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record CompatibilitySyncPacket(int mask) implements CustomPacketPayload {
    public static final Type<CompatibilitySyncPacket> TYPE = new Type<>(CreateFireFightingAdd.path("compatibility"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompatibilitySyncPacket> CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeVarInt(packet.mask), buffer -> new CompatibilitySyncPacket(buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    @SubscribeEvent static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToClient(TYPE, CODEC,
            (packet, context) -> context.enqueueWork(() -> CompatibilitySnapshot.receive(packet.mask)));
    }
}
