package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record HandheldNozzlePosePacket(int entityId, boolean spraying) implements CustomPacketPayload {
	public static final Type<HandheldNozzlePosePacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("handheld_nozzle_pose"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HandheldNozzlePosePacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HandheldNozzlePosePacket::entityId,
			ByteBufCodecs.BOOL, HandheldNozzlePosePacket::spraying,
			HandheldNozzlePosePacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, HandheldNozzlePosePacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(HandheldNozzlePosePacket packet, IPayloadContext context) {
		context.enqueueWork(() ->
			HandheldNozzleClientHandler.updateSyncedSpraying(packet.entityId(), packet.spraying()));
	}
}
