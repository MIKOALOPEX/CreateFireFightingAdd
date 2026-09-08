package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.sound.sampled.AudioFormat;

import com.mikoalopex.createfirefightingadd.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.FiniteAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class NozzleSprayClientSounds {
	private static final int FADE_TICKS = 20;
	private static final int STALE_TICKS = 8;
	private static final float MAX_VOLUME = 0.75f;
	private static final Map<String, FadingSpraySound> SOUNDS = new HashMap<>();
	private static final Map<SoundSource, Float> LAST_SOURCE_VOLUMES = new EnumMap<>(SoundSource.class);

	private NozzleSprayClientSounds() {
	}

	public static void keepAlive(String key, Vec3 pos, SoundSource source) {
		keepAlive(key, pos, source, SprayLoopSound.NOZZLE);
	}

	public static void keepAlive(String key, Vec3 pos, SoundSource source, SprayLoopSound loopSound) {
		if (key == null || key.isBlank() || pos == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		if (loopSound == null)
			loopSound = SprayLoopSound.NOZZLE;

		FadingSpraySound sound = SOUNDS.get(key);
		if (sound == null || sound.isStopped()) {
			sound = new FadingSpraySound(pos, source, loopSound);
			SOUNDS.put(key, sound);
			playIfAudible(minecraft, sound);
		} else if (sound.soundSource() != source) {
			sound = restart(minecraft, key, sound, source, loopSound);
		} else if (sound.loopSound() != loopSound) {
			sound = restart(minecraft, key, sound, source, loopSound);
		} else if (!minecraft.getSoundManager().isActive(sound) && isAudible(minecraft, sound.soundSource())) {
			sound = restart(minecraft, key, sound, sound.soundSource(), sound.loopSound());
		}
		sound.keepAlive(pos);
		rememberVolume(minecraft, source);
	}

	public static void stop(String key, Vec3 fallbackPos) {
		FadingSpraySound sound = SOUNDS.get(key);
		if (sound == null)
			return;
		if (fallbackPos != null)
			sound.moveTo(fallbackPos);
		sound.fadeOut();
		if (!Minecraft.getInstance().getSoundManager().isActive(sound)) {
			sound.stopNow();
			SOUNDS.remove(key);
		}
	}

	public static void clientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			SOUNDS.values().forEach(FadingSpraySound::stopNow);
			SOUNDS.clear();
			LAST_SOURCE_VOLUMES.clear();
			return;
		}

		Map<SoundSource, Boolean> restoredSources = updateSourceVolumes(minecraft);
		boolean masterRestored = Boolean.TRUE.equals(restoredSources.get(SoundSource.MASTER));
		Iterator<Map.Entry<String, FadingSpraySound>> iterator = SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, FadingSpraySound> entry = iterator.next();
			FadingSpraySound sound = entry.getValue();
			if (sound.isStopped())
				iterator.remove();
			else if ((masterRestored || Boolean.TRUE.equals(restoredSources.get(sound.soundSource())))
				&& sound.isRecentlyAlive())
				entry.setValue(restart(minecraft, entry.getKey(), sound, sound.soundSource(), sound.loopSound()));
		}
	}

	private static Map<SoundSource, Boolean> updateSourceVolumes(Minecraft minecraft) {
		Map<SoundSource, Boolean> restoredSources = new EnumMap<>(SoundSource.class);
		for (SoundSource source : SoundSource.values()) {
			float previous = LAST_SOURCE_VOLUMES.getOrDefault(source, getSourceVolume(minecraft, source));
			float current = getSourceVolume(minecraft, source);
			if (previous <= 0.0f && current > 0.0f)
				restoredSources.put(source, true);
			LAST_SOURCE_VOLUMES.put(source, current);
		}
		return restoredSources;
	}

	private static void rememberVolume(Minecraft minecraft, SoundSource source) {
		LAST_SOURCE_VOLUMES.put(source, getSourceVolume(minecraft, source));
	}

	private static FadingSpraySound restart(Minecraft minecraft, String key, FadingSpraySound previous,
		SoundSource source, SprayLoopSound loopSound) {
		minecraft.getSoundManager().stop(previous);
		previous.stopNow();

		FadingSpraySound replacement = new FadingSpraySound(previous.position(), source, loopSound);
		replacement.copyStateFrom(previous);
		SOUNDS.put(key, replacement);
		playIfAudible(minecraft, replacement);
		return replacement;
	}

	private static void playIfAudible(Minecraft minecraft, FadingSpraySound sound) {
		if (isAudible(minecraft, sound.soundSource()))
			minecraft.getSoundManager().play(sound);
	}

	private static boolean isAudible(Minecraft minecraft, SoundSource source) {
		float masterVolume = getSourceVolume(minecraft, SoundSource.MASTER);
		float sourceVolume = source == SoundSource.MASTER
			? masterVolume
			: getSourceVolume(minecraft, source);
		return masterVolume > 0.0f && sourceVolume > 0.0f;
	}

	private static float getSourceVolume(Minecraft minecraft, SoundSource source) {
		return minecraft.options.getSoundSourceVolume(source);
	}

	private static final class FadingSpraySound extends AbstractTickableSoundInstance {
		private final SoundSource soundSource;
		private final SprayLoopSound loopSound;
		private int fade;
		private int ticksSinceKeepAlive;
		private boolean active;

		private FadingSpraySound(Vec3 pos, SoundSource source, SprayLoopSound loopSound) {
			super(loopSound.soundEvent(), source, SoundInstance.createUnseededRandom());
			this.soundSource = source;
			this.loopSound = loopSound;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0f;
			this.pitch = 1.0f;
			moveTo(pos);
		}

		@Override
		public void tick() {
			if (Minecraft.getInstance().level == null) {
				stop();
				return;
			}

			ticksSinceKeepAlive++;
			if (ticksSinceKeepAlive > STALE_TICKS)
				active = false;

			fade += active ? 1 : -1;
			fade = Mth.clamp(fade, 0, FADE_TICKS);
			float configuredVolume = Mth.clamp(ClientConfig.nozzleSprayVolume, 0, 100) / 100.0f;
			volume = MAX_VOLUME * configuredVolume * fade / FADE_TICKS;

			if (!active && fade <= 0)
				stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
			if (!looping || loopSound.loopTrimStartSeconds() <= 0.0 && loopSound.loopTrimEndSeconds() <= 0.0)
				return soundBuffers.getStream(sound.getPath(), looping);

			// Loop only the steady section; runtime volume controls the audible fade-in and fade-out.
			return soundBuffers.getStream(sound.getPath(), false)
				.thenApply(stream -> {
					try {
						if (!(stream instanceof FiniteAudioStream finite))
							return stream;
						AudioFormat format = stream.getFormat();
						ByteBuffer pcm = finite.readAll();
						stream.close();
						return new TrimmedPcmLoopStream(format, pcm,
							loopSound.loopTrimStartSeconds(), loopSound.loopTrimEndSeconds());
					} catch (IOException e) {
						try {
							stream.close();
						} catch (IOException ignored) {
						}
						throw new CompletionException(e);
					}
				});
		}

		private void keepAlive(Vec3 pos) {
			moveTo(pos);
			active = true;
			ticksSinceKeepAlive = 0;
		}

		private void fadeOut() {
			active = false;
			ticksSinceKeepAlive = STALE_TICKS + 1;
		}

		private void moveTo(Vec3 pos) {
			x = pos.x;
			y = pos.y;
			z = pos.z;
		}

		private void stopNow() {
			stop();
		}

		private SoundSource soundSource() {
			return soundSource;
		}

		private SprayLoopSound loopSound() {
			return loopSound;
		}

		private Vec3 position() {
			return new Vec3(x, y, z);
		}

		private boolean isRecentlyAlive() {
			return active || ticksSinceKeepAlive <= STALE_TICKS;
		}

		private void copyStateFrom(FadingSpraySound previous) {
			fade = previous.fade;
			ticksSinceKeepAlive = previous.ticksSinceKeepAlive;
			active = previous.active;
			volume = previous.volume;
		}
	}

	private static final class TrimmedPcmLoopStream implements AudioStream {
		private final AudioFormat format;
		private final byte[] loopBytes;
		private int cursor;
		private boolean closed;

		private TrimmedPcmLoopStream(AudioFormat format, ByteBuffer pcm, double trimStartSeconds,
				double trimEndSeconds) {
			this.format = format;
			byte[] bytes = new byte[pcm.remaining()];
			pcm.get(bytes);

			int frameSize = Math.max(1, format.getFrameSize());
			int start = alignedBytes(trimStartSeconds, format, frameSize);
			int end = bytes.length - alignedBytes(trimEndSeconds, format, frameSize);
			if (end <= start + frameSize)
				this.loopBytes = bytes;
			else
				this.loopBytes = Arrays.copyOfRange(bytes, start, end);
		}

		@Override
		public AudioFormat getFormat() {
			return format;
		}

		@Override
		public ByteBuffer read(int capacity) {
			if (closed || loopBytes.length == 0 || capacity <= 0)
				return ByteBuffer.allocate(0);

			ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
			while (buffer.hasRemaining()) {
				int length = Math.min(buffer.remaining(), loopBytes.length - cursor);
				buffer.put(loopBytes, cursor, length);
				cursor = (cursor + length) % loopBytes.length;
			}
			buffer.flip();
			return buffer;
		}

		@Override
		public void close() {
			closed = true;
		}

		private static int alignedBytes(double seconds, AudioFormat format, int frameSize) {
			int bytes = (int) Math.round(seconds * format.getFrameRate()) * frameSize;
			bytes = Math.max(0, bytes);
			return bytes - bytes % frameSize;
		}
	}
}
