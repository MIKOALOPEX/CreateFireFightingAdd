package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record HandheldNozzleControllerStatePacket(
	int entityId,
	boolean present,
	boolean stowed,
	boolean leftHand,
	boolean bound,
	BlockPos pos,
	String dimension,
	UUID hydrantId,
	int nozzleType
) implements CustomPacketPayload {
	public static final Type<HandheldNozzleControllerStatePacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("handheld_nozzle_controller_state"));
	private static final UUID EMPTY_ID = new UUID(0L, 0L);

	public static final StreamCodec<RegistryFriendlyByteBuf, HandheldNozzleControllerStatePacket> STREAM_CODEC =
		StreamCodec.of(HandheldNozzleControllerStatePacket::write, HandheldNozzleControllerStatePacket::read);

	public static HandheldNozzleControllerStatePacket clear(int entityId) {
		return new HandheldNozzleControllerStatePacket(entityId, false, false, false, false,
			BlockPos.ZERO, "", EMPTY_ID, HandheldNozzleType.NONE.ordinal());
	}

	public HandheldNozzleClientHandler.SyncedControllerState toClientState(long expiresAt) {
		HandheldNozzleControllerItem.Binding binding = null;
		if (bound) {
			ResourceLocation location = ResourceLocation.tryParse(dimension);
			HandheldNozzleType[] values = HandheldNozzleType.values();
			HandheldNozzleType type = nozzleType >= 0 && nozzleType < values.length
				? values[nozzleType]
				: HandheldNozzleType.NONE;
			if (location != null) {
				binding = new HandheldNozzleControllerItem.Binding(pos,
					ResourceKey.create(Registries.DIMENSION, location), hydrantId, type);
			}
		}
		return new HandheldNozzleClientHandler.SyncedControllerState(present, stowed, leftHand, binding, expiresAt);
	}

	private static void write(RegistryFriendlyByteBuf buf, HandheldNozzleControllerStatePacket packet) {
		buf.writeVarInt(packet.entityId);
		buf.writeBoolean(packet.present);
		buf.writeBoolean(packet.stowed);
		buf.writeBoolean(packet.leftHand);
		buf.writeBoolean(packet.bound);
		BlockPos.STREAM_CODEC.encode(buf, packet.pos);
		buf.writeUtf(packet.dimension, 256);
		buf.writeUUID(packet.hydrantId);
		buf.writeVarInt(packet.nozzleType);
	}

	private static HandheldNozzleControllerStatePacket read(RegistryFriendlyByteBuf buf) {
		return new HandheldNozzleControllerStatePacket(
			buf.readVarInt(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			BlockPos.STREAM_CODEC.decode(buf),
			buf.readUtf(256),
			buf.readUUID(),
			buf.readVarInt());
	}

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToClient(TYPE, STREAM_CODEC, HandheldNozzleControllerStatePacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(HandheldNozzleControllerStatePacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			long now = context.player().level().getGameTime();
			HandheldNozzleClientHandler.updateSyncedControllerState(packet.entityId(),
				packet.toClientState(now + 60));
		});
	}
}
