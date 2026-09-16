package com.mikoalopex.createfirefightingadd.api.coupling;

import java.util.Optional;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Stable public facade for inspecting and requesting operations on stress coupling endpoints.
 * Pair ownership and constraint changes remain server-authoritative.
 */
public final class StressCouplingApi {
    public static final ResourceLocation ITEM_ID = CreateFireFightingAdd.path("stress_coupling");

    public enum InterfaceMode { FREE, ACTIVE, PASSIVE }
    public enum ConnectionState { IDLE, SEARCHING, ALIGNING, CONNECTED, UNAVAILABLE }

    public record EndpointSnapshot(ResourceKey<Level> dimension, UUID endpointId, Optional<UUID> partnerId,
            Vec3 anchor, Vec3 normal, InterfaceMode interfaceMode, ConnectionState state, boolean powered) {}

    private StressCouplingApi() {}

    public static Optional<EndpointSnapshot> endpoint(BlockEntity blockEntity) {
        if (!(blockEntity instanceof StressCouplingEndpoint endpoint) || blockEntity.getLevel() == null)
            return Optional.empty();
        return Optional.of(new EndpointSnapshot(endpoint.stressCouplingLevel().dimension(),
            endpoint.stressCouplingEndpointId(), Optional.ofNullable(endpoint.stressCouplingPartnerId()),
            endpoint.stressCouplingAnchor(), endpoint.stressCouplingNormal(), endpoint.stressCouplingInterfaceMode(),
            endpoint.stressCouplingState(), endpoint.stressCouplingPowered()));
    }

    public static boolean requestConnection(BlockEntity blockEntity) {
        if (!(blockEntity instanceof StressCouplingEndpoint endpoint) || blockEntity.getLevel() == null
                || blockEntity.getLevel().isClientSide)
            return false;
        endpoint.stressCouplingRequestSearch();
        return true;
    }

    public static boolean disconnect(BlockEntity blockEntity) {
        if (!(blockEntity instanceof StressCouplingEndpoint endpoint) || blockEntity.getLevel() == null
                || blockEntity.getLevel().isClientSide)
            return false;
        endpoint.stressCouplingDisconnect();
        return true;
    }
}
