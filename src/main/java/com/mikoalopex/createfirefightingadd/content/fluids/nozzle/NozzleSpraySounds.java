package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class NozzleSpraySounds {
	private static final int KEEP_ALIVE_INTERVAL_TICKS = 5;
	private static final int STALE_TICKS = 12;
	private static final double SOUND_RADIUS = 64.0;
	private static final double POSITION_RESEND_DISTANCE_SQR = 0.25;
	private static final Map<Level, Map<String, ActiveSound>> ACTIVE = new WeakHashMap<>();

	private NozzleSpraySounds() {
	}

	static String blockKey(BlockPos pos) {
		return "block:" + pos.asLong();
	}

	static String contraptionKey(int entityId, BlockPos localPos) {
		return "contraption:" + entityId + ":" + localPos.asLong();
	}

	static String handheldKey(UUID playerId) {
		return "handheld:" + playerId;
	}

	static void tick(Level level, String key, Vec3 pos, SoundSource source) {
		if (!(level instanceof ServerLevel serverLevel) || key == null || pos == null)
			return;

		long gameTime = level.getGameTime();
		Map<String, ActiveSound> sounds = sounds(level);
		cleanup(serverLevel, sounds, gameTime);

		ActiveSound sound = sounds.get(key);
		boolean firstSync = sound == null;
		if (sound == null) {
			sound = new ActiveSound(gameTime, Long.MIN_VALUE, pos, source);
			sounds.put(key, sound);
		}

		boolean sourceChanged = sound.source != source;
		boolean moved = sound.lastSentPos == null || sound.lastSentPos.distanceToSqr(pos) > POSITION_RESEND_DISTANCE_SQR;
		if (firstSync || sourceChanged || moved || gameTime - sound.lastSentTick >= KEEP_ALIVE_INTERVAL_TICKS) {
			send(serverLevel, new NozzleSpraySoundPacket(key, true, source.ordinal(), pos.x, pos.y, pos.z), pos);
			sound.lastSentTick = gameTime;
			sound.lastSentPos = pos;
		}

		sound.lastSeenTick = gameTime;
		sound.lastPos = pos;
		sound.source = source;
	}

	static void stop(Level level, String key, Vec3 fallbackPos, SoundSource source) {
		if (!(level instanceof ServerLevel serverLevel) || key == null)
			return;

		Map<String, ActiveSound> sounds = ACTIVE.get(level);
		ActiveSound sound = sounds == null ? null : sounds.remove(key);
		Vec3 pos = sound != null && sound.lastPos != null ? sound.lastPos : fallbackPos;
		SoundSource soundSource = sound != null && sound.source != null ? sound.source : source;
		if (pos != null)
			send(serverLevel, new NozzleSpraySoundPacket(key, false, soundSource.ordinal(), pos.x, pos.y, pos.z), pos);
	}

	private static Map<String, ActiveSound> sounds(Level level) {
		return ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>());
	}

	private static void cleanup(ServerLevel level, Map<String, ActiveSound> sounds, long gameTime) {
		if (gameTime % 20 != 0)
			return;
		sounds.entrySet().removeIf(entry -> {
			ActiveSound sound = entry.getValue();
			if (gameTime - sound.lastSeenTick <= STALE_TICKS)
				return false;
			if (sound.lastPos != null)
				send(level, new NozzleSpraySoundPacket(entry.getKey(), false, sound.source.ordinal(),
					sound.lastPos.x, sound.lastPos.y, sound.lastPos.z), sound.lastPos);
			return true;
		});
	}

	private static void send(ServerLevel level, NozzleSpraySoundPacket packet, Vec3 pos) {
		double radiusSqr = SOUND_RADIUS * SOUND_RADIUS;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos) <= radiusSqr)
				PacketDistributor.sendToPlayer(player, packet);
		}
	}

	private static final class ActiveSound {
		private long lastSeenTick;
		private long lastSentTick;
		private Vec3 lastSentPos;
		private Vec3 lastPos;
		private SoundSource source;

		private ActiveSound(long lastSeenTick, long lastSentTick, Vec3 lastPos, SoundSource source) {
			this.lastSeenTick = lastSeenTick;
			this.lastSentTick = lastSentTick;
			this.lastPos = lastPos;
			this.source = source;
		}
	}
}
