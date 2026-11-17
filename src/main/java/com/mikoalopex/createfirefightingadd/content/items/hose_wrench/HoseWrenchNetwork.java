package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import java.util.function.IntConsumer;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Mode synchronization only.
 *
 * <p>There is no custom size-action payload. The thickness action travels on
 * the native {@code ServerboundUseItemOnPacket} and is executed by
 * {@link HoseWrenchServer}'s {@code RightClickBlock} handler using the event's
 * own hit vector. This keeps Sable sub-level coordinates intact and guarantees
 * exactly one action per click.</p>
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class HoseWrenchNetwork {
    private static volatile IntConsumer clientModeSink = mode -> {
    };

    private HoseWrenchNetwork() {
    }

    public static void setClientModeSink(IntConsumer sink) {
        clientModeSink = sink == null ? mode -> {
        } : sink;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CreateFireFightingAdd.MODID).optional();

        registrar.playToServer(
            HoseWrenchModeC2SPacket.TYPE,
            HoseWrenchModeC2SPacket.STREAM_CODEC,
            (packet, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    HoseWrenchServer.onMode(player, packet.mode());
                }
            }));

        registrar.playToClient(
            HoseWrenchModeS2CPacket.TYPE,
            HoseWrenchModeS2CPacket.STREAM_CODEC,
            (packet, context) -> context.enqueueWork(() -> clientModeSink.accept(packet.mode())));
    }
}
