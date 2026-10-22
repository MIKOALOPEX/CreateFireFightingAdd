package com.mikoalopex.createfirefightingadd.content.items.configurator;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.RopeSlideSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Global rope tuning is authorized by the configurator menu, not a particular hook block. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record ConfiguratorRopeSettingsPacket(int containerId, RopeSlideSettings settings) implements CustomPacketPayload {
    public static final Type<ConfiguratorRopeSettingsPacket> TYPE = new Type<>(CreateFireFightingAdd.path("configurator_rope_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorRopeSettingsPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            var s = packet.settings;
            buf.writeDouble(s.friction()); buf.writeDouble(s.geometricFriction()); buf.writeDouble(s.responseGain());
            buf.writeDouble(s.maxSpeed()); buf.writeDouble(s.gravityAcceleration());
            buf.writeDouble(s.gravityRetention()); buf.writeDouble(s.tautRatio());
            buf.writeVarInt(s.scanIntervalTicks()); buf.writeVarInt(s.searchDurationSeconds());
            buf.writeDouble(s.swingDamping());
            buf.writeDouble(s.upperSwingDamping());
        }, buf -> new ConfiguratorRopeSettingsPacket(buf.readVarInt(), new RopeSlideSettings(buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble())));

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID).playToServer(TYPE, STREAM_CODEC, ConfiguratorRopeSettingsPacket::handle);
    }

    private static void handle(ConfiguratorRopeSettingsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!com.mikoalopex.createfirefightingadd.compatibility.CompatibilityManager
                    .available(com.mikoalopex.createfirefightingadd.compatibility.Capability.ROPE_CAPTURE)) return;
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof MultifunctionConfiguratorMenu menu)
                    || menu.containerId != packet.containerId || !menu.stillValid(player)) return;
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("rope_latch.settings.denied"), true);
                return;
            }
            boolean saved = Config.saveRopeSlideSettings(packet.settings);
            player.displayClientMessage(Component.translatable(saved ? "rope_latch.settings.saved" : "rope_latch.settings.invalid"), true);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
