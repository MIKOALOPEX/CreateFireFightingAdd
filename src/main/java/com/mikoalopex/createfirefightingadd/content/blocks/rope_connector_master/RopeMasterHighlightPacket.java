package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Sends hose-style Outliner feedback without requiring a client block-entity ticker. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record RopeMasterHighlightPacket(BlockPos endpoint, int color) implements CustomPacketPayload {
    public static final Type<RopeMasterHighlightPacket> TYPE = new Type<>(CreateFireFightingAdd.path("rope_master_highlight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeMasterHighlightPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RopeMasterHighlightPacket::endpoint,
        ByteBufCodecs.VAR_INT, RopeMasterHighlightPacket::color, RopeMasterHighlightPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @SubscribeEvent static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToClient(TYPE, CODEC,
            (packet, context) -> context.enqueueWork(() -> Client.show(packet)));
    }

    public static final class Client {
        static void show(RopeMasterHighlightPacket packet) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            BlockPos pos = packet.endpoint();
            String key = "rope_master:" + pos.asLong();
            // Sable transforms Catnip outlines from plot coordinates during rendering.
            // Keep the local coordinates here, and let Catnip retain the feedback for two seconds.
            Outliner.getInstance().showAABB(key, new AABB(pos).deflate(3 / 16d), 40)
                .colored(packet.color()).lineWidth(1 / 12f);
        }
    }
}
