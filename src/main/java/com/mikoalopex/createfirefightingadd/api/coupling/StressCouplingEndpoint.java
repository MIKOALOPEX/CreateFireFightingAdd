package com.mikoalopex.createfirefightingadd.api.coupling;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Read and command contract implemented by stress coupling endpoints supplied by this mod.
 * External integrations should use {@link StressCouplingApi} instead of casting to the internal block entity.
 */
public interface StressCouplingEndpoint {
    UUID stressCouplingEndpointId();
    @Nullable UUID stressCouplingPartnerId();
    Level stressCouplingLevel();
    Vec3 stressCouplingAnchor();
    Vec3 stressCouplingNormal();
    StressCouplingApi.InterfaceMode stressCouplingInterfaceMode();
    StressCouplingApi.ConnectionState stressCouplingState();
    boolean stressCouplingPowered();
    void stressCouplingRequestSearch();
    void stressCouplingDisconnect();
}
