package com.mikoalopex.createfirefightingadd.content.items.configurator;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayRuleSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
public record MultifunctionConfiguratorSavePacket(InteractionHand hand, CompoundTag nozzleRules)
	implements CustomPacketPayload {
	public static final Type<MultifunctionConfiguratorSavePacket> TYPE =
		new Type<>(CreateFireFightingAdd.path("save_multifunction_configurator"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MultifunctionConfiguratorSavePacket> STREAM_CODEC =
		StreamCodec.of(MultifunctionConfiguratorSavePacket::write, MultifunctionConfiguratorSavePacket::read);

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(CreateFireFightingAdd.MODID)
			.playToServer(TYPE, STREAM_CODEC, MultifunctionConfiguratorSavePacket::handle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void write(RegistryFriendlyByteBuf buf, MultifunctionConfiguratorSavePacket packet) {
		buf.writeVarInt(packet.hand.ordinal());
		buf.writeNbt(packet.nozzleRules);
	}

	private static MultifunctionConfiguratorSavePacket read(RegistryFriendlyByteBuf buf) {
		int index = buf.readVarInt();
		InteractionHand[] hands = InteractionHand.values();
		InteractionHand hand = index >= 0 && index < hands.length ? hands[index] : InteractionHand.MAIN_HAND;
		CompoundTag tag = buf.readNbt();
		return new MultifunctionConfiguratorSavePacket(hand, tag == null ? new CompoundTag() : tag);
	}

	private static void handle(MultifunctionConfiguratorSavePacket packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		if (!player.getAbilities().instabuild) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.creative_only"), true);
			return;
		}
		ItemStack stack = player.getItemInHand(packet.hand);
		if (!(stack.getItem() instanceof MultifunctionConfiguratorItem))
			return;
		NozzleSprayRuleSet rules = NozzleSprayRuleSet.read(player.registryAccess(), packet.nozzleRules);
		NozzleSprayRuleSet.setOnStack(stack, player.registryAccess(), rules);
		player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.saved"), true);
	}
}
