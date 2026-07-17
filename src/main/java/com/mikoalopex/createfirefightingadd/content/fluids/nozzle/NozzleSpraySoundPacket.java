package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record NozzleSpraySoundPacket(
	String key,
	boolean active,
	int sourceOrdinal,
	double x,
	double y,
	double z
) implements CustomPacketPayload {
	public static final Type<NozzleSpraySoundPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("nozzle_spray_sound"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NozzleSpraySoundPacket> STREAM_CODEC =
		StreamCodec.of(NozzleSpraySoundPacket::write, NozzleSpraySoundPacket::read);

	private static void write(RegistryFriendlyByteBuf buf, NozzleSpraySoundPacket packet) {
		buf.writeUtf(packet.key, 128);
		buf.writeBoolean(packet.active);
		buf.writeVarInt(packet.sourceOrdinal);
		buf.writeDouble(packet.x);
		buf.writeDouble(packet.y);
		buf.writeDouble(packet.z);
	}

	private static NozzleSpraySoundPacket read(RegistryFriendlyByteBuf buf) {
		return new NozzleSpraySoundPacket(
			buf.readUtf(128),
			buf.readBoolean(),
			buf.readVarInt(),
			buf.readDouble(),
			buf.readDouble(),
			buf.readDouble());
	}

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, NozzleSpraySoundPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(NozzleSpraySoundPacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			SoundSource source = source(packet.sourceOrdinal);
			Vec3 pos = new Vec3(packet.x, packet.y, packet.z);
			if (packet.active)
				NozzleSprayClientSounds.keepAlive(packet.key, pos, source);
			else
				NozzleSprayClientSounds.stop(packet.key, pos);
		});
	}

	private static SoundSource source(int ordinal) {
		SoundSource[] values = SoundSource.values();
		if (ordinal < 0 || ordinal >= values.length)
			return SoundSource.BLOCKS;
		return values[ordinal];
	}
}
