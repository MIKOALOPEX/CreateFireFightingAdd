package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.items.firefighter.FirefighterRecordStore;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record PlaceExtensionLadderPacket(InteractionHand hand, BlockPos support) implements CustomPacketPayload {
	public static final Type<PlaceExtensionLadderPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("place_extension_ladder"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlaceExtensionLadderPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal),
			PlaceExtensionLadderPacket::hand,
			BlockPos.STREAM_CODEC,
			PlaceExtensionLadderPacket::support,
			PlaceExtensionLadderPacket::new);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToServer(TYPE, STREAM_CODEC, PlaceExtensionLadderPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void handle(PlaceExtensionLadderPacket packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		ItemStack held = player.getItemInHand(packet.hand());
		if (!(held.getItem() instanceof ExtensionLadderItem))
			return;

		BlockPos support = packet.support();
		Vec3 fallDirection = localFallDirection(player, support);
		if (fallDirection.lengthSqr() < 1.0E-6) {
			reject(player, support, "invalid_direction");
			return;
		}

		if (!isSupportInRange(player, support)) {
			reject(player, support, "no_target");
			return;
		}
		if (player.level().getBlockState(support).getCollisionShape(player.level(), support).isEmpty()) {
			reject(player, support, "first_point_floor");
			return;
		}

		BlockPos anchor = support.above();
		if (!ExtensionLadderPlacement.canPlaceAt(player.level(), support)) {
			reject(player, support, "anchor_blocked");
			return;
		}

		if (!player.level().setBlockAndUpdate(anchor, CreateFireFightingAdd.EXTENSION_LADDER.get().defaultBlockState())) {
			reject(player, support, "anchor_failed");
			return;
		}
		if (!(player.level().getBlockEntity(anchor) instanceof ExtensionLadderBlockEntity ladder)) {
			player.level().removeBlock(anchor, false);
			reject(player, support, "anchor_failed");
			return;
		}

		Vec3 anchorPoint = new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
		boolean acceleratedPlacementAnimation = player.level() instanceof ServerLevel serverLevel
			&& FirefighterRecordStore.hasTeamPairNear(serverLevel, anchor);
		ladder.initialize(anchorPoint, fallDirection, support, acceleratedPlacementAnimation);
		PacketDistributor.sendToPlayer(player, new ExtensionLadderPlacementFeedbackPacket(support, true));
		if (!player.getAbilities().instabuild)
			held.shrink(1);
	}

	private static boolean isSupportInRange(ServerPlayer player, BlockPos support) {
		double range = player.blockInteractionRange() + 1.0;
		Vec3 supportCenter = SableStructureCompat.projectToWorld(player.level(), Vec3.atCenterOf(support));
		return player.getEyePosition().distanceToSqr(supportCenter) <= range * range;
	}

	private static Vec3 localFallDirection(ServerPlayer player, BlockPos support) {
		Vec3 worldDirection = ExtensionLadderGeometry.normalizeHorizontal(player.getViewVector(1.0f));
		Vec3 localDirection = SableStructureCompat.transformNormalToLocal(player.level(), support, worldDirection);
		return ExtensionLadderGeometry.normalizeHorizontal(localDirection);
	}

	private static void message(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable("createfirefightingadd.extension_ladder." + key)
			.withStyle(ChatFormatting.RED), true);
	}

	private static void reject(ServerPlayer player, BlockPos support, String key) {
		PacketDistributor.sendToPlayer(player, new ExtensionLadderPlacementFeedbackPacket(support, false));
		message(player, key);
	}
}
