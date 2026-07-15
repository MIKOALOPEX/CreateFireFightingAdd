package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record HandheldNozzleSprayPacket(boolean spraying) implements CustomPacketPayload {
	public static final Type<HandheldNozzleSprayPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("handheld_nozzle_spray"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HandheldNozzleSprayPacket> STREAM_CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, HandheldNozzleSprayPacket::spraying,
			HandheldNozzleSprayPacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToServer(TYPE, STREAM_CODEC, HandheldNozzleSprayPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(HandheldNozzleSprayPacket packet, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			HandheldNozzleSprayHandler.setSpraying(player, packet.spraying());
	}
}
