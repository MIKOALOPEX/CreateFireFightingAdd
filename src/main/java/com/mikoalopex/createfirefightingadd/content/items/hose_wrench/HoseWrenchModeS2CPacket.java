package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Authoritative server echo used on login, respawn, and after every request. */
public record HoseWrenchModeS2CPacket(int mode) implements CustomPacketPayload {
    public static final Type<HoseWrenchModeS2CPacket> TYPE =
        new Type<>(CreateFireFightingAdd.path("hose_wrench_mode_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HoseWrenchModeS2CPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HoseWrenchModeS2CPacket::mode,
            HoseWrenchModeS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
