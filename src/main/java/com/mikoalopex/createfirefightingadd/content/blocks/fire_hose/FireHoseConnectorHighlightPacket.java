package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

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
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record FireHoseConnectorHighlightPacket(BlockPos pos, int color) implements CustomPacketPayload {
	public static final Type<FireHoseConnectorHighlightPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("fire_hose_connector_highlight"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FireHoseConnectorHighlightPacket> STREAM_CODEC =
		StreamCodec.composite(
			BlockPos.STREAM_CODEC, FireHoseConnectorHighlightPacket::pos,
			ByteBufCodecs.VAR_INT, FireHoseConnectorHighlightPacket::color,
			FireHoseConnectorHighlightPacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, FireHoseConnectorHighlightPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(FireHoseConnectorHighlightPacket packet, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (Minecraft.getInstance().level == null)
				return;
			Outliner.getInstance().showAABB("fire_hose_connector:" + packet.pos.asLong(),
					new AABB(packet.pos).inflate(-0.2))
				.colored(packet.color)
				.lineWidth(1 / 12f);
		});
	}
}
