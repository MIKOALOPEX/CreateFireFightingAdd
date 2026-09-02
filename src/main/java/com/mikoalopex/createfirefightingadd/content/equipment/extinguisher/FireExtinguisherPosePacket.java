package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

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
public record FireExtinguisherPosePacket(int entityId, boolean spraying) implements CustomPacketPayload {
	public static final Type<FireExtinguisherPosePacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("fire_extinguisher_pose"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FireExtinguisherPosePacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FireExtinguisherPosePacket::entityId,
			ByteBufCodecs.BOOL, FireExtinguisherPosePacket::spraying,
			FireExtinguisherPosePacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, FireExtinguisherPosePacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(FireExtinguisherPosePacket packet, IPayloadContext context) {
		context.enqueueWork(() ->
			FireExtinguisherClientHandler.updateSyncedSpraying(packet.entityId(), packet.spraying()));
	}
}
