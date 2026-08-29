package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record FirefighterInvitePacket(UUID inviter, String inviterName) implements CustomPacketPayload {
	public static final Type<FirefighterInvitePacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("firefighter_invite"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FirefighterInvitePacket> STREAM_CODEC =
		StreamCodec.of(FirefighterInvitePacket::write, FirefighterInvitePacket::read);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, FirefighterInvitePacket::handle);
	}

	public static void send(ServerPlayer player, UUID inviter, String inviterName) {
		PacketDistributor.sendToPlayer(player, new FirefighterInvitePacket(inviter, inviterName));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void write(RegistryFriendlyByteBuf buf, FirefighterInvitePacket packet) {
		buf.writeUUID(packet.inviter);
		buf.writeUtf(packet.inviterName);
	}

	private static FirefighterInvitePacket read(RegistryFriendlyByteBuf buf) {
		return new FirefighterInvitePacket(buf.readUUID(), buf.readUtf());
	}

	private static void handle(FirefighterInvitePacket packet, IPayloadContext context) {
		context.enqueueWork(() -> FirefighterInviteClientHandler.receive(packet.inviter(), packet.inviterName()));
	}
}
