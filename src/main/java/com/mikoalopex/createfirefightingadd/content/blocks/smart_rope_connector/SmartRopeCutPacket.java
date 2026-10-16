package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import java.util.UUID;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Identifies the selected strand explicitly so stale requests cannot cut another connection. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record SmartRopeCutPacket(ResourceLocation dimension, BlockPos pos, UUID rope, InteractionHand hand)
    implements CustomPacketPayload {
    public static final Type<SmartRopeCutPacket> TYPE = new Type<>(CreateFireFightingAdd.path("smart_rope_cut"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SmartRopeCutPacket> CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeResourceLocation(packet.dimension);
            buf.writeBlockPos(packet.pos);
            buf.writeUUID(packet.rope);
            buf.writeEnum(packet.hand);
        }, buf -> new SmartRopeCutPacket(buf.readResourceLocation(), buf.readBlockPos(), buf.readUUID(),
            buf.readEnum(InteractionHand.class)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToServer(TYPE, CODEC, (packet, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player)
                    SmartRopeCompatibility.cut(player, packet);
            }));
    }
}
