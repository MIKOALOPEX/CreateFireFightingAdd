package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.mikoalopex.createfirefightingadd.Config;

/**
 * Shared server-side throttle for lightweight spray projectiles.
 */
final class SprayProjectileBudget {
	private static final Map<Object, Integer> SERVER_PROJECTILES =
		Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Object, Double> SERVER_SPAWN_CREDIT =
		Collections.synchronizedMap(new WeakHashMap<>());

	private SprayProjectileBudget() {
	}

	static int requestServerProjectiles(Object owner, int currentProjectiles, int requestedProjectiles) {
		if (requestedProjectiles <= 0)
			return 0;

		synchronized (SERVER_PROJECTILES) {
			updateLocked(owner, currentProjectiles);
			int softLimit = Math.max(1, Config.serverMaxActiveSprayProjectiles);
			int hardLimit = Math.max(softLimit, Config.serverHardMaxActiveSprayProjectiles);
			int active = totalLocked();
			int remaining = hardLimit - active;
			if (remaining <= 0) {
				SERVER_SPAWN_CREDIT.put(owner, 0.0);
				return 0;
			}

			double spawnRate = spawnRate(active, softLimit, hardLimit);
			double credit = SERVER_SPAWN_CREDIT.getOrDefault(owner, 0.0)
				+ requestedProjectiles * spawnRate;
			int allowed = Math.min(requestedProjectiles, (int) Math.floor(credit));
			if (active < softLimit && allowed == 0)
				allowed = 1;
			allowed = Math.min(allowed, remaining);
			if (allowed > 0)
				credit -= allowed;
			SERVER_SPAWN_CREDIT.put(owner, Math.min(credit, requestedProjectiles));
			return allowed;
		}
	}

	static void updateServerProjectiles(Object owner, int currentProjectiles) {
		synchronized (SERVER_PROJECTILES) {
			updateLocked(owner, currentProjectiles);
		}
	}

	static void clearServerProjectiles(Object owner) {
		synchronized (SERVER_PROJECTILES) {
			SERVER_PROJECTILES.remove(owner);
			SERVER_SPAWN_CREDIT.remove(owner);
		}
	}

	private static double spawnRate(int active, int softLimit, int hardLimit) {
		if (active < softLimit)
			return 1.0;
		double overload = (active - softLimit) / (double) Math.max(1, hardLimit - softLimit);
		return Math.max(0.05, 1.0 - overload) * 0.35;
	}

	private static void updateLocked(Object owner, int currentProjectiles) {
		if (currentProjectiles <= 0)
			SERVER_PROJECTILES.remove(owner);
		else
			SERVER_PROJECTILES.put(owner, currentProjectiles);
	}

	private static int totalLocked() {
		int total = 0;
		for (int count : SERVER_PROJECTILES.values())
			total += Math.max(0, count);
		return total;
	}
}
