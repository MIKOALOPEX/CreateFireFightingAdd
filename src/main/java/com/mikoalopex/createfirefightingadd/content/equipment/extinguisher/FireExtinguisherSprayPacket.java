package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

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
public record FireExtinguisherSprayPacket(boolean spraying) implements CustomPacketPayload {
	public static final Type<FireExtinguisherSprayPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("fire_extinguisher_spray"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FireExtinguisherSprayPacket> STREAM_CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, FireExtinguisherSprayPacket::spraying,
			FireExtinguisherSprayPacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToServer(TYPE, STREAM_CODEC, FireExtinguisherSprayPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(FireExtinguisherSprayPacket packet, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			FireExtinguisherSprayHandler.setSpraying(player, packet.spraying());
	}
}
