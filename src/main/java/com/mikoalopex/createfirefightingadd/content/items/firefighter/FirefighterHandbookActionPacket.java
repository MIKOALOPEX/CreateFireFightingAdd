package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.Optional;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record FirefighterHandbookActionPacket(InteractionHand hand, Action action, String target)
	implements CustomPacketPayload {
	public static final Type<FirefighterHandbookActionPacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("firefighter_handbook_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FirefighterHandbookActionPacket> STREAM_CODEC =
		StreamCodec.of(FirefighterHandbookActionPacket::write, FirefighterHandbookActionPacket::read);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToServer(TYPE, STREAM_CODEC, FirefighterHandbookActionPacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void write(RegistryFriendlyByteBuf buf, FirefighterHandbookActionPacket packet) {
		buf.writeVarInt(packet.hand.ordinal());
		buf.writeVarInt(packet.action.ordinal());
		buf.writeUtf(packet.target);
	}

	private static FirefighterHandbookActionPacket read(RegistryFriendlyByteBuf buf) {
		int handIndex = buf.readVarInt();
		InteractionHand[] hands = InteractionHand.values();
		InteractionHand hand = handIndex >= 0 && handIndex < hands.length ? hands[handIndex] : InteractionHand.MAIN_HAND;
		int actionIndex = buf.readVarInt();
		Action[] actions = Action.values();
		Action action = actionIndex >= 0 && actionIndex < actions.length ? actions[actionIndex] : Action.SYNC;
		return new FirefighterHandbookActionPacket(hand, action, buf.readUtf());
	}

	private static void handle(FirefighterHandbookActionPacket packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		if (packet.action == Action.INVITE_ACCEPT || packet.action == Action.INVITE_DECLINE) {
			parseUuid(packet.target).ifPresent(inviter -> FirefighterRecordStore.respondInvite(player, inviter,
				packet.action == Action.INVITE_ACCEPT));
			return;
		}
		ItemStack stack = player.getItemInHand(packet.hand);
		if (!(stack.getItem() instanceof FirefighterHandbookItem))
			return;

		switch (packet.action) {
			case REGISTER -> {
				var record = FirefighterRecordStore.register(player);
				FirefighterHandbookData.bind(stack, player, record.extinguished());
			}
			case MANUAL_ADD_10 -> manual(stack, 10);
			case MANUAL_ADD_1 -> manual(stack, 1);
			case MANUAL_SUB_10 -> manual(stack, -10);
			case MANUAL_SUB_1 -> manual(stack, -1);
			case MANUAL_FILL -> manualFill(stack);
			case INVITE -> parseUuid(packet.target)
				.filter(target -> !target.equals(FirefighterHandbookData.owner(stack).orElse(null)))
				.ifPresent(target -> FirefighterRecordStore.invite(player, target));
			case KICK_MEMBER -> parseUuid(packet.target).ifPresent(target -> FirefighterRecordStore.kickMember(player, target));
			case INVITE_ACCEPT, INVITE_DECLINE -> {
			}
			case SYNC -> {
			}
		}

		UUID owner = FirefighterHandbookData.owner(stack).orElse(null);
		FirefighterHandbookSnapshot snapshot = owner == null
			? FirefighterHandbookData.snapshotFromStack(stack, Config.firefighterExtinguishRecordsEnabled)
			: FirefighterRecordStore.snapshot(player, owner, FirefighterHandbookData.count(stack),
				Config.firefighterExtinguishRecordsEnabled);
		FirefighterHandbookSyncPacket.send(player, snapshot);
	}

	private static void manual(ItemStack stack, int delta) {
		if (Config.firefighterExtinguishRecordsEnabled)
			return;
		FirefighterHandbookData.setCount(stack, FirefighterHandbookData.count(stack) + delta);
	}

	private static void manualFill(ItemStack stack) {
		if (Config.firefighterExtinguishRecordsEnabled)
			return;
		FirefighterHandbookData.setCount(stack, FirefighterHandbookSnapshot.GOAL);
	}

	private static Optional<UUID> parseUuid(String value) {
		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public enum Action {
		SYNC,
		REGISTER,
		MANUAL_ADD_10,
		MANUAL_ADD_1,
		MANUAL_SUB_10,
		MANUAL_SUB_1,
		MANUAL_FILL,
		INVITE,
		KICK_MEMBER,
		INVITE_ACCEPT,
		INVITE_DECLINE
	}
}
