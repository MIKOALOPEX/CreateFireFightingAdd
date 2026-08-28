package com.mikoalopex.createfirefightingadd.api.fire_hose;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * Stable access point for tools that need to preserve Fire Hose endpoint state
 * through custom structure, schematic, or contraption serialization.
 */
public interface FireHoseConnectionAccess {
	String CONNECTION_TAG = "FireHoseConnection";

	void writeFireHoseConnection(CompoundTag tag, HolderLookup.Provider registries);

	void readFireHoseConnection(CompoundTag tag, HolderLookup.Provider registries);

	@Nullable
	BlockPos getFireHosePartnerPos();

	@Nullable
	UUID getFireHosePartnerSubLevel();

	UUID getFireHoseEndpointId();

	@Nullable
	UUID getFireHosePartnerEndpointId();

	default boolean isFireHosePartnerMoving() {
		return false;
	}

	boolean isFireHoseController();

	boolean isFireHoseBlack();

	default ResourceLocation getFireHoseAppearance() {
		if (isFireHoseTransparent())
			return FireHoseAppearances.TRANSPARENT;
		return isFireHoseBlack() ? FireHoseAppearances.BLACK : FireHoseAppearances.DEFAULT;
	}

	default boolean isFireHoseTransparent() {
		return false;
	}

	void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel, boolean blackHose);

	default void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel, boolean blackHose, boolean transparentHose) {
		setFireHoseConnection(controller, partnerPos, partnerSubLevel, blackHose);
	}

	default void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel, ResourceLocation appearance) {
		setFireHoseConnection(controller, partnerPos, partnerSubLevel, null, false, appearance);
	}

	default void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel,
			@Nullable UUID partnerEndpointId, boolean blackHose) {
		setFireHoseConnection(controller, partnerPos, partnerSubLevel, blackHose);
	}

	default void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel,
			@Nullable UUID partnerEndpointId,
			boolean partnerMoving, boolean blackHose) {
		setFireHoseConnection(controller, partnerPos, partnerSubLevel, partnerEndpointId, blackHose);
	}

	default void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel,
			@Nullable UUID partnerEndpointId,
			boolean partnerMoving, boolean blackHose, boolean transparentHose) {
		setFireHoseConnection(controller, partnerPos, partnerSubLevel, blackHose, transparentHose);
	}

	default void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel,
			@Nullable UUID partnerEndpointId,
			boolean partnerMoving, ResourceLocation appearance) {
		setFireHoseConnection(controller, partnerPos, partnerSubLevel, partnerEndpointId,
			partnerMoving, FireHoseAppearances.isBlack(appearance), FireHoseAppearances.isTransparent(appearance));
	}
}
