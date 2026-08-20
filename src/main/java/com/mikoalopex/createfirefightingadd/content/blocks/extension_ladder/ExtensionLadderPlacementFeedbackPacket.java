package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record ExtensionLadderPlacementFeedbackPacket(BlockPos support, boolean canPlace)
	implements CustomPacketPayload {
	public static final Type<ExtensionLadderPlacementFeedbackPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("extension_ladder_placement_feedback"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExtensionLadderPlacementFeedbackPacket> STREAM_CODEC =
		StreamCodec.composite(BlockPos.STREAM_CODEC, ExtensionLadderPlacementFeedbackPacket::support,
			ByteBufCodecs.BOOL, ExtensionLadderPlacementFeedbackPacket::canPlace,
			ExtensionLadderPlacementFeedbackPacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, ExtensionLadderPlacementFeedbackPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(ExtensionLadderPlacementFeedbackPacket packet, IPayloadContext context) {
		context.enqueueWork(() ->
			ExtensionLadderItemHandler.INSTANCE.acceptPlacementFeedback(packet.support(), packet.canPlace()));
	}
}
