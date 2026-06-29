package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record ContraptionSprayStatePacket(
	int entityId,
	BlockPos localPos,
	int behaviorOrdinal,
	boolean ignited,
	String fuelPath,
	float potionR,
	float potionG,
	float potionB
) implements CustomPacketPayload {
	public static final Type<ContraptionSprayStatePacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("contraption_spray_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionSprayStatePacket> STREAM_CODEC =
		StreamCodec.of(ContraptionSprayStatePacket::write, ContraptionSprayStatePacket::read);

	private static void write(RegistryFriendlyByteBuf buf, ContraptionSprayStatePacket packet) {
		buf.writeVarInt(packet.entityId);
		BlockPos.STREAM_CODEC.encode(buf, packet.localPos);
		buf.writeVarInt(packet.behaviorOrdinal);
		buf.writeBoolean(packet.ignited);
		buf.writeUtf(packet.fuelPath, 256);
		buf.writeFloat(packet.potionR);
		buf.writeFloat(packet.potionG);
		buf.writeFloat(packet.potionB);
	}

	private static ContraptionSprayStatePacket read(RegistryFriendlyByteBuf buf) {
		return new ContraptionSprayStatePacket(
			buf.readVarInt(),
			BlockPos.STREAM_CODEC.decode(buf),
			buf.readVarInt(),
			buf.readBoolean(),
			buf.readUtf(256),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat());
	}

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, ContraptionSprayStatePacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(ContraptionSprayStatePacket packet, IPayloadContext ctx) {
		ctx.enqueueWork(() -> SprayDeviceMovementBehaviour.handleClientSprayState(
			packet,
			ctx.player().level().getGameTime()));
	}

	AbstractSprayDeviceBlockEntity.FluidBehavior behavior() {
		AbstractSprayDeviceBlockEntity.FluidBehavior[] values =
			AbstractSprayDeviceBlockEntity.FluidBehavior.values();
		if (behaviorOrdinal < 0 || behaviorOrdinal >= values.length)
			return AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED;
		return values[behaviorOrdinal];
	}
}
