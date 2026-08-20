package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record ExtensionLadderAdjustPacket(BlockPos pos, double x, double z, float moveOffsetPixels)
	implements CustomPacketPayload {
	public static final Type<ExtensionLadderAdjustPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("extension_ladder_adjust"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExtensionLadderAdjustPacket> STREAM_CODEC =
		StreamCodec.of(ExtensionLadderAdjustPacket::write, ExtensionLadderAdjustPacket::read);

	private static void write(RegistryFriendlyByteBuf buf, ExtensionLadderAdjustPacket packet) {
		BlockPos.STREAM_CODEC.encode(buf, packet.pos);
		buf.writeDouble(packet.x);
		buf.writeDouble(packet.z);
		buf.writeFloat(packet.moveOffsetPixels);
	}

	private static ExtensionLadderAdjustPacket read(RegistryFriendlyByteBuf buf) {
		return new ExtensionLadderAdjustPacket(BlockPos.STREAM_CODEC.decode(buf), buf.readDouble(), buf.readDouble(),
			buf.readFloat());
	}

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToServer(TYPE, STREAM_CODEC, ExtensionLadderAdjustPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(ExtensionLadderAdjustPacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			if (player.distanceToSqr(Vec3.atCenterOf(packet.pos)) > 256)
				return;
			if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())
				return;
			if (player.level().getBlockEntity(packet.pos) instanceof ExtensionLadderBlockEntity ladder) {
				Vec3 direction = new Vec3(packet.x, 0, packet.z);
				Vec3 localDirection = SableStructureCompat.transformNormalToLocal(player.level(), packet.pos.below(),
					direction);
				ladder.adjustWithSettings(localDirection, packet.moveOffsetPixels());
			}
		});
	}
}
