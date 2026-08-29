package com.mikoalopex.createfirefightingadd.content.items.firefighter;

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
public record FirefighterHandbookSyncPacket(FirefighterHandbookSnapshot snapshot) implements CustomPacketPayload {
	public static final Type<FirefighterHandbookSyncPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("firefighter_handbook_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FirefighterHandbookSyncPacket> STREAM_CODEC =
		StreamCodec.of(FirefighterHandbookSyncPacket::write, FirefighterHandbookSyncPacket::read);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, FirefighterHandbookSyncPacket::handle);
	}

	public static void send(ServerPlayer player, FirefighterHandbookSnapshot snapshot) {
		PacketDistributor.sendToPlayer(player, new FirefighterHandbookSyncPacket(snapshot));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void write(RegistryFriendlyByteBuf buf, FirefighterHandbookSyncPacket packet) {
		packet.snapshot.write(buf);
	}

	private static FirefighterHandbookSyncPacket read(RegistryFriendlyByteBuf buf) {
		return new FirefighterHandbookSyncPacket(FirefighterHandbookSnapshot.read(buf));
	}

	private static void handle(FirefighterHandbookSyncPacket packet, IPayloadContext context) {
		context.enqueueWork(() -> FirefighterHandbookScreen.applySnapshot(packet.snapshot()));
	}
}
