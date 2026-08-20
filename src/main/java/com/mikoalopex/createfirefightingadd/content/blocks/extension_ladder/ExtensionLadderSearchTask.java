package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ExtensionLadderSearchTask {
	private static final float PITCH_STEP = (float) Math.toRadians(2);
	private static final int PITCHES_PER_TICK = 2;
	private static final int ALONG_SAMPLES = 14;
	private static final double[] WIDTH_SAMPLES = {-0.46, -0.23, 0, 0.23, 0.46};
	private static final double[] NORMAL_SAMPLES = {-ExtensionLadderGeometry.PROBE_RADIUS, 0,
		ExtensionLadderGeometry.PROBE_RADIUS};

	private float nextPitch;
	private boolean exhausted;

	public void restart(float currentPitch) {
		nextPitch = Math.min(ExtensionLadderGeometry.MAX_PITCH, currentPitch + PITCH_STEP);
		exhausted = false;
	}

	public SearchResult tick(ExtensionLadderBlockEntity ladder) {
		if (exhausted)
			return SearchResult.exhausted();

		for (int i = 0; i < PITCHES_PER_TICK; i++) {
			if (nextPitch > ExtensionLadderGeometry.MAX_PITCH + 1.0E-5) {
				exhausted = true;
				return SearchResult.exhausted();
			}
			ExtensionLadderSupportRef support = findSupportAt(ladder, nextPitch);
			if (support != null)
				return SearchResult.found(nextPitch, support);
			nextPitch += PITCH_STEP;
		}
		return SearchResult.searching();
	}

	public float nextPitch() {
		return nextPitch;
	}

	public void setNextPitch(float nextPitch) {
		this.nextPitch = nextPitch;
		this.exhausted = false;
	}

	@Nullable
	private static ExtensionLadderSupportRef findSupportAt(ExtensionLadderBlockEntity ladder, float pitch) {
		ExtensionLadderGeometry.LocalFrame frame = ladder.localPhysicsFrame(pitch);
		double length = ladder.getClimbLength();
		for (int alongIndex = 1; alongIndex <= ALONG_SAMPLES; alongIndex++) {
			double along = length * alongIndex / ALONG_SAMPLES;
			if (along < 0.28)
				continue;
			for (double width : WIDTH_SAMPLES) {
				double clampedWidth = Mth.clamp(width, -ExtensionLadderGeometry.HALF_WIDTH,
					ExtensionLadderGeometry.HALF_WIDTH);
				for (double normal : NORMAL_SAMPLES) {
					Vec3 localPoint = frame.point(along, clampedWidth, normal);
					ExtensionLadderSupportRef support = ladder.findSupportNearLocal(localPoint, frame.normal());
					if (support != null)
						return support;
				}
			}
		}
		return null;
	}

	public record SearchResult(State state, float pitch, @Nullable ExtensionLadderSupportRef support) {
		public static SearchResult searching() {
			return new SearchResult(State.SEARCHING, 0, null);
		}

		public static SearchResult exhausted() {
			return new SearchResult(State.EXHAUSTED, ExtensionLadderGeometry.MAX_PITCH, null);
		}

		public static SearchResult found(float pitch, ExtensionLadderSupportRef support) {
			return new SearchResult(State.FOUND, pitch, support);
		}
	}

	public enum State {
		SEARCHING,
		FOUND,
		EXHAUSTED
	}
}
