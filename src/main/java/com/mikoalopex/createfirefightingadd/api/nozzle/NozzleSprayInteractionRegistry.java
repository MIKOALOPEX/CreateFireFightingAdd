package com.mikoalopex.createfirefightingadd.api.nozzle;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Common-side registry for server block interactions caused by nozzle spray
 * samples. Register callbacks during common setup or mod construction.
 */
public final class NozzleSprayInteractionRegistry {
	private static final List<NozzleSprayBlockInteraction> INTERACTIONS = new CopyOnWriteArrayList<>();
	private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

	private NozzleSprayInteractionRegistry() {
	}

	public static void register(NozzleSprayBlockInteraction interaction) {
		INTERACTIONS.add(Objects.requireNonNull(interaction, "interaction"));
	}

	public static boolean shouldNotify(NozzleSprayHitContext context) {
		if (targetShouldReceive(context))
			return true;
		for (NozzleSprayBlockInteraction interaction : INTERACTIONS) {
			if (safeShouldReceive(interaction, context))
				return true;
		}
		return false;
	}

	public static void notifyHit(NozzleSprayHitContext context) {
		notifyTarget(context);
		for (NozzleSprayBlockInteraction interaction : INTERACTIONS) {
			if (safeShouldReceive(interaction, context))
				safeOnHit(interaction, context);
		}
	}

	private static boolean targetShouldReceive(NozzleSprayHitContext context) {
		if (context.state().getBlock() instanceof NozzleSprayBlockTarget target
			&& safeShouldReceive(target, context))
			return true;
		BlockEntity be = context.level().getBlockEntity(context.pos());
		return be instanceof NozzleSprayBlockTarget target && safeShouldReceive(target, context);
	}

	private static void notifyTarget(NozzleSprayHitContext context) {
		if (context.state().getBlock() instanceof NozzleSprayBlockTarget target
			&& safeShouldReceive(target, context))
			safeOnHit(target, context);
		BlockEntity be = context.level().getBlockEntity(context.pos());
		if (be instanceof NozzleSprayBlockTarget target
			&& safeShouldReceive(target, context))
			safeOnHit(target, context);
	}

	private static boolean safeShouldReceive(NozzleSprayBlockTarget target, NozzleSprayHitContext context) {
		try {
			return target.shouldReceiveNozzleSprayHit(context);
		} catch (RuntimeException e) {
			warnOnce("target predicate", target, context, e);
			return false;
		}
	}

	private static void safeOnHit(NozzleSprayBlockTarget target, NozzleSprayHitContext context) {
		try {
			target.onNozzleSprayHit(context);
		} catch (RuntimeException e) {
			warnOnce("target callback", target, context, e);
		}
	}

	private static boolean safeShouldReceive(NozzleSprayBlockInteraction interaction, NozzleSprayHitContext context) {
		try {
			return interaction.shouldReceive(context);
		} catch (RuntimeException e) {
			warnOnce("interaction predicate", interaction, context, e);
			return false;
		}
	}

	private static void safeOnHit(NozzleSprayBlockInteraction interaction, NozzleSprayHitContext context) {
		try {
			interaction.onHit(context);
		} catch (RuntimeException e) {
			warnOnce("interaction callback", interaction, context, e);
		}
	}

	private static void warnOnce(String phase, Object handler, NozzleSprayHitContext context, RuntimeException error) {
		String handlerName = handler.getClass().getName();
		if (!REPORTED_FAILURES.add(phase + ':' + handlerName))
			return;
		CreateFireFightingAdd.LOGGER.warn(
			"Nozzle spray {} failed for {} at {}; further matching errors are suppressed: {}",
			phase, handlerName, context.pos(), error.toString());
		CreateFireFightingAdd.LOGGER.debug("Nozzle spray integration failure", error);
	}
}
