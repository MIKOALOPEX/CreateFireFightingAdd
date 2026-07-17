package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class NozzleSprayClientSounds {
	private static final int FADE_TICKS = 20;
	private static final int STALE_TICKS = 8;
	private static final float MAX_VOLUME = 0.75f;
	private static final Map<String, FadingSpraySound> SOUNDS = new HashMap<>();

	private NozzleSprayClientSounds() {
	}

	public static void keepAlive(String key, Vec3 pos, SoundSource source) {
		if (key == null || key.isBlank() || pos == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;

		FadingSpraySound sound = SOUNDS.get(key);
		if (sound == null || sound.isStopped()) {
			sound = new FadingSpraySound(pos, source);
			SOUNDS.put(key, sound);
			minecraft.getSoundManager().play(sound);
		}
		sound.keepAlive(pos);
	}

	public static void stop(String key, Vec3 fallbackPos) {
		FadingSpraySound sound = SOUNDS.get(key);
		if (sound == null)
			return;
		if (fallbackPos != null)
			sound.moveTo(fallbackPos);
		sound.fadeOut();
	}

	public static void clientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			SOUNDS.values().forEach(FadingSpraySound::stopNow);
			SOUNDS.clear();
			return;
		}

		Iterator<Map.Entry<String, FadingSpraySound>> iterator = SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			FadingSpraySound sound = iterator.next().getValue();
			if (sound.isStopped())
				iterator.remove();
		}
	}

	private static final class FadingSpraySound extends AbstractTickableSoundInstance {
		private int fade;
		private int ticksSinceKeepAlive;
		private boolean active;

		private FadingSpraySound(Vec3 pos, SoundSource source) {
			super(CreateFireFightingAdd.NOZZLE_SPRAY_SOUND.get(), source, SoundInstance.createUnseededRandom());
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
			volume = MAX_VOLUME * fade / FADE_TICKS;

			if (!active && fade <= 0)
				stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
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
	}
}
