package com.mikoalopex.createfirefightingadd.api.fire_hose;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnections;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Public helper for tools and blocks that want to manage Fire Hose endpoints
 * without copying connector-specific logic.
 */
public final class FireHoseConnectionHelper {
	private FireHoseConnectionHelper() {
	}

	public static FireHoseConnections.Result tryConnect(Level level, BlockPos firstPos, BlockPos secondPos) {
		return FireHoseConnections.tryConnect(level, firstPos, secondPos);
	}

	public static FireHoseConnections.Result tryConnect(BlockEntity first, BlockEntity second) {
		if (first instanceof FireHoseBlockEntity firstHose && second instanceof FireHoseBlockEntity secondHose)
			return FireHoseConnections.tryConnect(firstHose, secondHose);
		return FireHoseConnections.Result.MISSING_ENDPOINT;
	}

	public static FireHoseConnections.ConnectionAttempt tryConnectFirstFreeEndpoint(BlockEntity origin) {
		if (origin instanceof FireHoseBlockEntity hose)
			return FireHoseConnections.tryConnectFirstFreeEndpoint(hose);
		return new FireHoseConnections.ConnectionAttempt(FireHoseConnections.Result.MISSING_ENDPOINT, null);
	}

	@Nullable
	public static BlockEntity findFirstFreeEndpoint(BlockEntity origin) {
		if (!(origin instanceof FireHoseBlockEntity hose))
			return null;
		return FireHoseConnections.findFirstFreeEndpoint(hose);
	}

	public static boolean isWithinRange(BlockEntity first, BlockEntity second) {
		if (first instanceof FireHoseBlockEntity firstHose && second instanceof FireHoseBlockEntity secondHose)
			return FireHoseConnections.isWithinRange(firstHose, secondHose);
		return false;
	}
}
