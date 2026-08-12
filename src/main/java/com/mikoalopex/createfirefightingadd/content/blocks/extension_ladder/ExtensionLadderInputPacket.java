package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

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
public record ExtensionLadderInputPacket(int forwardInput, boolean jumpPulse) implements CustomPacketPayload {
	public static final Type<ExtensionLadderInputPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("extension_ladder_input"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExtensionLadderInputPacket> STREAM_CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, ExtensionLadderInputPacket::forwardInput,
			ByteBufCodecs.BOOL, ExtensionLadderInputPacket::jumpPulse,
			ExtensionLadderInputPacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID).playToServer(TYPE, STREAM_CODEC, ExtensionLadderInputPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(ExtensionLadderInputPacket packet, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			context.enqueueWork(() ->
				ExtensionLadderClimbingController.setInput(player, packet.forwardInput(), packet.jumpPulse()));
	}
}
