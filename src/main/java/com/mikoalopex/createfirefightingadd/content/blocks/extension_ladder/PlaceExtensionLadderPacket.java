package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record PlaceExtensionLadderPacket(InteractionHand hand) implements CustomPacketPayload {
	private static final double RANGE = 16.0;
	public static final Type<PlaceExtensionLadderPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("place_extension_ladder"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlaceExtensionLadderPacket> STREAM_CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal),
			PlaceExtensionLadderPacket::hand, PlaceExtensionLadderPacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID).playToServer(TYPE, STREAM_CODEC, PlaceExtensionLadderPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(PlaceExtensionLadderPacket packet, IPayloadContext context) {
		ServerPlayer player = (ServerPlayer) context.player();
		ItemStack held = player.getItemInHand(packet.hand());
		if (!(held.getItem() instanceof ExtensionLadderItem))
			return;

		BlockHitResult hit = rayTrace(player);
		if (hit == null) {
			message(player, "no_target");
			return;
		}
		if (hit.getDirection() != Direction.UP) {
			message(player, "first_point_floor");
			return;
		}

		Vec3 fallDirection = ExtensionLadderGeometry.normalizeHorizontal(player.getViewVector(1.0f));
		if (fallDirection.lengthSqr() < 1.0E-6) {
			message(player, "invalid_direction");
			return;
		}

		BlockPos support = hit.getBlockPos();
		BlockPos anchor = support.above();
		if (!player.level().getBlockState(anchor).canBeReplaced()) {
			message(player, "anchor_blocked");
			return;
		}

		if (!player.level().setBlockAndUpdate(anchor, CreateFireFightingAdd.EXTENSION_LADDER.get().defaultBlockState())) {
			message(player, "anchor_failed");
			return;
		}
		if (!(player.level().getBlockEntity(anchor) instanceof ExtensionLadderBlockEntity ladder)) {
			player.level().removeBlock(anchor, false);
			message(player, "anchor_failed");
			return;
		}

		Vec3 anchorPoint = new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
		ladder.initialize(anchorPoint, fallDirection, support);
		if (!player.getAbilities().instabuild)
			held.shrink(1);
	}

	private static BlockHitResult rayTrace(ServerPlayer player) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getViewVector(1.0f).scale(RANGE));
		BlockHitResult result = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		return result.getType() == HitResult.Type.BLOCK ? result : null;
	}

	private static void message(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable("createfirefightingadd.extension_ladder." + key), true);
	}
}
