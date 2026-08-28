package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
	float potionB,
	NozzleParticlePalette particles
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
		buf.writeNbt(packet.particles == null ? new CompoundTag() : packet.particles.write());
	}

	private static ContraptionSprayStatePacket read(RegistryFriendlyByteBuf buf) {
		int entityId = buf.readVarInt();
		BlockPos localPos = BlockPos.STREAM_CODEC.decode(buf);
		int behaviorOrdinal = buf.readVarInt();
		boolean ignited = buf.readBoolean();
		String fuelPath = buf.readUtf(256);
		float potionR = buf.readFloat();
		float potionG = buf.readFloat();
		float potionB = buf.readFloat();
		CompoundTag particlesTag = buf.readNbt();
		int fallback = rgb(potionR, potionG, potionB);
		NozzleParticlePalette particles = particlesTag == null || particlesTag.isEmpty()
			? null
			: NozzleParticlePalette.read(particlesTag, fallback);
		return new ContraptionSprayStatePacket(
			entityId, localPos, behaviorOrdinal, ignited, fuelPath, potionR, potionG, potionB, particles);
	}

	private static int rgb(float r, float g, float b) {
		int red = Math.clamp((int) (r * 255), 0, 255);
		int green = Math.clamp((int) (g * 255), 0, 255);
		int blue = Math.clamp((int) (b * 255), 0, 255);
		return (red << 16) | (green << 8) | blue;
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
