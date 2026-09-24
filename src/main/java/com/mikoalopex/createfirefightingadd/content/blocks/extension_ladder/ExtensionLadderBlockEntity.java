package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ExtensionLadderBlockEntity extends SmartBlockEntity {
	private static final int DROP_TICKS = 7;
	private static final int DROP_LAND_TICK = 2;
	private static final int EXTEND_TICKS = 16;
	private static final int TILT_TICKS = 12;
	private static final int CLIENT_PLACEMENT_TICKS = DROP_TICKS + EXTEND_TICKS + TILT_TICKS;
	private static final int CLIENT_PLACEMENT_REPLAY_WINDOW = 100;
	private static final int SUPPORT_CHECK_INTERVAL = 10;
	private static final int ADJUST_TIMEOUT_TICKS = 3;
	private static final double SUPPORT_SAMPLE_RADIUS = 0.08;
	private static final double ANCHOR_CLEARANCE = 0.26;
	private static final String TAG_MOVE_OFFSET = "MoveOffset";
	private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
	private static final String TAG_ACCELERATED_PLACEMENT_ANIMATION = "AcceleratedPlacementAnimation";
	private static final String TAG_LEGACY_SKIP_EXTEND_ANIMATION = "SkipExtendAnimation";
	private static final int TEAM_PLACEMENT_ANIMATION_STEP = 2;

	private Vec3 anchorOffset = new Vec3(0.5, 0, 0.5);
	private Vec3 fallDirection = new Vec3(0, 0, 1);
	private BlockPos anchorSupport;
	@Nullable
	private ExtensionLadderSupportRef support;
	private final ExtensionLadderSearchTask searchTask = new ExtensionLadderSearchTask();

	private Phase phase = Phase.DROP;
	private int animationTick;
	private int previousAnimationTick;
	private int clientPlacementTick = CLIENT_PLACEMENT_TICKS;
	private int previousClientPlacementTick = CLIENT_PLACEMENT_TICKS;
	private int supportCheckTimer;
	private float currentPitch;
	private float startPitch;
	private float targetPitch;
	private float targetMoveOffsetPixels = ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS;
	private long createdGameTime = Long.MIN_VALUE;
	private long clientAnimatedCreatedGameTime = Long.MIN_VALUE;
	private long lastAdjustTick;
	private boolean acceleratedPlacementAnimation;
	@Nullable
	private Predicate<Vec3> searchCollisionQuery;
	@Nullable
	private Map<BlockPos, Boolean> searchLocalCollisions;

	public ExtensionLadderBlockEntity(BlockPos pos, BlockState state) {
		super(CreateFireFightingAdd.EXTENSION_LADDER_BE.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ExtensionLadderIndex.add(this);
	}

	@Override
	public void remove() {
		ExtensionLadderIndex.remove(this);
		if (level != null && !level.isClientSide)
			ExtensionLadderSearchBudget.cancel(this);
		super.remove();
	}

	@Override
	public void onChunkUnloaded() {
		ExtensionLadderIndex.remove(this);
		if (level != null && !level.isClientSide)
			ExtensionLadderSearchBudget.cancel(this);
		super.onChunkUnloaded();
	}

	public void initialize(Vec3 anchor, Vec3 fallDirection, BlockPos anchorSupport) {
		initialize(anchor, fallDirection, anchorSupport, false);
	}

	public void initialize(Vec3 anchor, Vec3 fallDirection, BlockPos anchorSupport, boolean acceleratedPlacementAnimation) {
		this.anchorOffset = anchor.subtract(Vec3.atLowerCornerOf(worldPosition));
		this.fallDirection = ExtensionLadderGeometry.normalizeHorizontal(fallDirection);
		if (this.fallDirection.lengthSqr() < 1.0E-6)
			this.fallDirection = new Vec3(0, 0, 1);
		this.anchorSupport = anchorSupport;
		this.support = null;
		this.currentPitch = 0;
		this.startPitch = 0;
		this.targetPitch = 0;
		this.targetMoveOffsetPixels = ExtensionLadderPlacement.initialMoveOffsetPixels(level, anchorSupport);
		this.phase = Phase.DROP;
		this.animationTick = 0;
		this.previousAnimationTick = 0;
		this.clientPlacementTick = CLIENT_PLACEMENT_TICKS;
		this.previousClientPlacementTick = CLIENT_PLACEMENT_TICKS;
		this.supportCheckTimer = 0;
		this.createdGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
		this.lastAdjustTick = 0;
		this.acceleratedPlacementAnimation = acceleratedPlacementAnimation;
		this.searchTask.restart(0);
		markDirtyAndSync();
	}

	@Override
	public void tick() {
		super.tick();
		previousAnimationTick = animationTick;
		previousClientPlacementTick = clientPlacementTick;
		if (level == null)
			return;
		if (level.isClientSide) {
			startClientPlacementAnimationIfRecent();
			tickClientPlacementAnimation();
			return;
		}

		if (phase != Phase.SEARCHING)
			ExtensionLadderSearchBudget.cancel(this);
		if (!hasAnchorSupport()) {
			breakSelf();
			return;
		}

		switch (phase) {
			case DROP -> tickTimedPhase(DROP_TICKS, Phase.EXTENDING);
			case EXTENDING -> tickTimedPhase(EXTEND_TICKS, Phase.SEARCHING);
			case SEARCHING -> tickSearch();
			case TILTING -> tickTilt();
			case SUPPORTED -> tickSupported();
			case ADJUSTING -> tickAdjusting();
			case FALLEN -> {
			}
		}
	}

	private void tickTimedPhase(int duration, Phase nextPhase) {
		animationTick += placementAnimationStep();
		if (animationTick >= duration) {
			animationTick = 0;
			previousAnimationTick = 0;
			phase = nextPhase;
			if (nextPhase == Phase.SEARCHING)
				searchTask.restart(currentPitch);
		}
		markDirtyAndSync();
	}

	private void tickClientPlacementAnimation() {
		if (clientPlacementTick >= CLIENT_PLACEMENT_TICKS)
			return;
		// The placement preview waits after extension until the server sends the tilt target.
		if (clientPlacementTick >= DROP_TICKS + EXTEND_TICKS && phase == Phase.SEARCHING)
			return;
		clientPlacementTick += placementAnimationStep();
	}

	private void tickSearch() {
		if (!ExtensionLadderSearchBudget.acquire(this))
			return;
		// Reuse nearby structures and block results only within this search batch.
		Vec3 center = SableStructureCompat.transformPositionToWorld(this, Vec3.atCenterOf(worldPosition));
		searchCollisionQuery = SableStructureCompat.collisionQuery(this,
			new AABB(center, center).inflate(ExtensionLadderGeometry.MAX_LENGTH + 3));
		searchLocalCollisions = new HashMap<>();
		ExtensionLadderSearchTask.SearchResult result;
		try {
			result = searchTask.tick(this);
		} finally {
			searchCollisionQuery = null;
			searchLocalCollisions = null;
		}
		if (result.state() == ExtensionLadderSearchTask.State.SEARCHING)
			return;
		if (result.state() == ExtensionLadderSearchTask.State.EXHAUSTED) {
			startTilt(ExtensionLadderGeometry.MAX_PITCH, null);
			markDirtyAndSync();
			return;
		}

		startTilt(result.pitch(), result.support());
		markDirtyAndSync();
	}

	private void startTilt(float targetPitch, @Nullable ExtensionLadderSupportRef support) {
		this.startPitch = currentPitch;
		this.targetPitch = targetPitch;
		this.support = support;
		this.phase = Phase.TILTING;
		this.animationTick = 0;
		this.previousAnimationTick = 0;
	}

	private void tickTilt() {
		animationTick += placementAnimationStep();
		if (animationTick >= TILT_TICKS) {
			currentPitch = targetPitch;
			phase = support == null ? Phase.FALLEN : Phase.SUPPORTED;
			if (support != null && !support.isStillPresent(this)) {
				support = null;
				phase = Phase.SEARCHING;
				searchTask.restart(currentPitch);
			}
			animationTick = 0;
			previousAnimationTick = 0;
			supportCheckTimer = 0;
		}
		markDirtyAndSync();
	}

	private void tickAdjusting() {
		currentPitch = 0;
		if (level != null && level.getGameTime() - lastAdjustTick <= ADJUST_TIMEOUT_TICKS)
			return;

		// Releasing the use input ends adjustment; the ladder searches again with the chosen yaw and length.
		support = null;
		phase = Phase.SEARCHING;
		animationTick = 0;
		previousAnimationTick = 0;
		searchTask.restart(0);
		markDirtyAndSync();
	}

	private void tickSupported() {
		if (++supportCheckTimer < SUPPORT_CHECK_INTERVAL)
			return;
		supportCheckTimer = 0;
		if (support != null && support.isStillPresent(this))
			return;
		support = null;
		phase = Phase.SEARCHING;
		searchTask.restart(currentPitch);
		markDirtyAndSync();
	}

	public void adjustWithPlayer(Player player) {
		BlockPos supportPos = anchorSupport != null ? anchorSupport : worldPosition.below();
		Vec3 localDirection = SableStructureCompat.transformNormalToLocal(level, supportPos,
			player.getViewVector(1.0f));
		adjustWithSettings(localDirection, targetMoveOffsetPixels);
	}

	public void adjustWithSettings(Vec3 direction, float moveOffsetPixels) {
		if (level == null || level.isClientSide)
			return;
		Vec3 normalized = ExtensionLadderGeometry.normalizeHorizontal(direction);
		if (normalized.lengthSqr() < 1.0E-6)
			return;
		float clampedMoveOffset = Mth.clamp(moveOffsetPixels, 0, ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS);
		if (anchorSupport != null)
			clampedMoveOffset = Math.min(clampedMoveOffset,
				ExtensionLadderPlacement.initialMoveOffsetPixels(level, anchorSupport));

		boolean changed = phase != Phase.ADJUSTING || currentPitch != 0
			|| normalized.distanceToSqr(fallDirection) > 1.0E-4
			|| Math.abs(clampedMoveOffset - targetMoveOffsetPixels) > 1.0E-4;
		fallDirection = normalized;
		targetMoveOffsetPixels = clampedMoveOffset;
		lastAdjustTick = level.getGameTime();
		support = null;
		currentPitch = 0;
		startPitch = 0;
		targetPitch = 0;
		phase = Phase.ADJUSTING;
		animationTick = 0;
		previousAnimationTick = 0;
		supportCheckTimer = 0;
		if (changed)
			markDirtyAndSync();
	}

	public void reinitializeAfterStructureMove() {
		Vec3 anchor = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
		Vec3 direction = fallDirection.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : fallDirection;
		float moveOffset = targetMoveOffsetPixels;
		initialize(anchor, direction, worldPosition.below());
		targetMoveOffsetPixels = moveOffset;
		markDirtyAndSync();
	}

	private boolean hasAnchorSupport() {
		return anchorSupport != null && hasLocalCollision(anchorSupport);
	}

	private boolean hasLocalCollision(BlockPos pos) {
		if (searchLocalCollisions != null)
			return searchLocalCollisions.computeIfAbsent(pos.immutable(), this::hasLoadedLocalCollision);
		return hasLoadedLocalCollision(pos);
	}

	private boolean hasLoadedLocalCollision(BlockPos pos) {
		if (level == null || !level.isLoaded(pos))
			return false;
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	public boolean hasCollisionNearLocal(Vec3 localPoint) {
		Vec3 center = SableStructureCompat.transformPositionToWorld(this, localPoint);
		searchCollisionQuery = SableStructureCompat.collisionQuery(this, new AABB(center, center).inflate(0.25));
		try {
			return findSupportNearLocal(localPoint, Vec3.ZERO) != null;
		} finally {
			searchCollisionQuery = null;
		}
	}

	@Nullable
	public ExtensionLadderSupportRef findSupportNearLocal(Vec3 localPoint, Vec3 localNormal) {
		Vec3 normal = localNormal.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : localNormal.normalize();
		double[] offsets = {-SUPPORT_SAMPLE_RADIUS, 0, SUPPORT_SAMPLE_RADIUS};
		for (double offset : offsets) {
			Vec3 sample = localPoint.add(normal.scale(offset));
			ExtensionLadderSupportRef support = findSupportAtLocalSample(sample);
			if (support != null)
				return support;
		}
		return null;
	}

	@Nullable
	private ExtensionLadderSupportRef findSupportAtLocalSample(Vec3 localSample) {
		if (level == null)
			return null;
		BlockPos localPos = BlockPos.containing(localSample);
		if (isIgnoredLocalSupport(localPos))
			return null;
		if (hasLocalCollision(localPos))
			return ExtensionLadderSupportRef.at(this, localSample,
				SableStructureCompat.transformPositionToWorld(this, localSample));

		Vec3 worldSample = SableStructureCompat.transformPositionToWorld(this, localSample);
		BlockPos worldPos = BlockPos.containing(worldSample);
		if (isIgnoredWorldSupport(worldPos))
			return null;
		if (searchCollisionQuery != null ? searchCollisionQuery.test(worldSample)
			: SableStructureCompat.hasCollisionAtWorld(this, worldSample))
			return ExtensionLadderSupportRef.at(this, localSample, worldSample);
		return null;
	}

	private boolean isIgnoredLocalSupport(BlockPos pos) {
		if (pos.equals(worldPosition) || pos.equals(anchorSupport))
			return true;
		if (anchorSupport != null && pos.getY() == anchorSupport.getY())
			return true;
		return Vec3.atCenterOf(pos).distanceToSqr(localAnchor()) < ANCHOR_CLEARANCE * ANCHOR_CLEARANCE;
	}

	private boolean isIgnoredWorldSupport(BlockPos pos) {
		BlockPos worldBlock = SableStructureCompat.worldBlockPos(this);
		if (pos.equals(worldBlock))
			return true;
		if (!SableStructureCompat.isInSubLevel(this) && anchorSupport != null) {
			if (pos.equals(anchorSupport) || pos.getY() == anchorSupport.getY())
				return true;
		}
		return false;
	}

	private void breakSelf() {
		phase = Phase.FALLEN;
		if (level != null && level.getBlockState(worldPosition).is(CreateFireFightingAdd.EXTENSION_LADDER.get()))
			level.destroyBlock(worldPosition, true);
	}

	private void markDirtyAndSync() {
		setChanged();
		sendData();
	}

	public Vec3 localAnchor() {
		return Vec3.atLowerCornerOf(worldPosition).add(anchorOffset);
	}

	public Vec3 getAnchorOffset() {
		return anchorOffset;
	}

	public Vec3 getFallDirection() {
		return fallDirection;
	}

	public Phase getPhase() {
		return phase;
	}

	public boolean isClimbable() {
		return phase == Phase.SUPPORTED || phase == Phase.FALLEN;
	}

	public float getTargetMoveOffsetPixels() {
		return targetMoveOffsetPixels;
	}

	public double getClimbLength() {
		return ExtensionLadderGeometry.climbLength(targetMoveOffsetPixels);
	}

	public float getPitch(float partialTick) {
		if (isClientPlacementAnimationActive()) {
			float tick = clientPlacementTick(partialTick);
			if (tick < DROP_TICKS + EXTEND_TICKS)
				return 0;
			float tiltTick = tick - DROP_TICKS - EXTEND_TICKS;
			return Mth.lerp(ease(tiltTick / TILT_TICKS), 0, clientPlacementTargetPitch());
		}
		if (phase != Phase.TILTING)
			return currentPitch;
		float tick = Mth.lerp(partialTick, previousAnimationTick, animationTick);
		return Mth.lerp(ease(tick / TILT_TICKS), startPitch, targetPitch);
	}

	public float getMoveOffsetPixels(float partialTick) {
		if (isClientPlacementAnimationActive()) {
			float tick = clientPlacementTick(partialTick);
			if (tick < DROP_TICKS)
				return 0;
			if (tick < DROP_TICKS + EXTEND_TICKS)
				return targetMoveOffsetPixels * ease((tick - DROP_TICKS) / EXTEND_TICKS);
			return targetMoveOffsetPixels;
		}
		if (phase == Phase.DROP)
			return 0;
		if (phase == Phase.EXTENDING) {
			float tick = Mth.lerp(partialTick, previousAnimationTick, animationTick);
			return targetMoveOffsetPixels * ease(tick / EXTEND_TICKS);
		}
		return targetMoveOffsetPixels;
	}

	public float getDropOffsetPixels(float partialTick) {
		if (isClientPlacementAnimationActive()) {
			float tick = Mth.clamp(clientPlacementTick(partialTick), 0, DROP_TICKS);
			if (tick <= DROP_LAND_TICK)
				return Mth.lerp(tick / DROP_LAND_TICK, 4, 0);
			return 0;
		}
		if (phase != Phase.DROP)
			return 0;
		float tick = Mth.clamp(Mth.lerp(partialTick, previousAnimationTick, animationTick), 0, DROP_TICKS);
		if (tick <= DROP_LAND_TICK)
			return Mth.lerp(tick / DROP_LAND_TICK, 4, 0);
		return 0;
	}

	public float getDropWobbleDegrees(float partialTick) {
		if (isClientPlacementAnimationActive())
			return dropWobbleFromTick(clientPlacementTick(partialTick));
		if (phase != Phase.DROP)
			return 0;
		return dropWobbleFromTick(Mth.lerp(partialTick, previousAnimationTick, animationTick));
	}

	private float dropWobbleFromTick(float tick) {
		tick = Mth.clamp(tick, 0, DROP_TICKS);
		if (tick <= DROP_LAND_TICK)
			return 0;
		float t = (tick - DROP_LAND_TICK) / (DROP_TICKS - DROP_LAND_TICK);
		float decay = 1 - t;
		return Mth.sin(t * Mth.TWO_PI) * decay * 4.0f;
	}

	private boolean isClientPlacementAnimationActive() {
		return level != null && level.isClientSide && clientPlacementTick < CLIENT_PLACEMENT_TICKS;
	}

	private float clientPlacementTick(float partialTick) {
		return Mth.lerp(partialTick, previousClientPlacementTick, clientPlacementTick);
	}

	private int placementAnimationStep() {
		return acceleratedPlacementAnimation ? TEAM_PLACEMENT_ANIMATION_STEP : 1;
	}

	private float clientPlacementTargetPitch() {
		if (phase == Phase.TILTING)
			return targetPitch;
		return currentPitch != 0 ? currentPitch : targetPitch;
	}

	public ExtensionLadderGeometry.LocalFrame localPhysicsFrame(float pitch) {
		return ExtensionLadderGeometry.localFrame(localAnchor(), fallDirection, pitch, false);
	}

	public ExtensionLadderGeometry.WorldFrame worldPhysicsFrame(float pitch) {
		ExtensionLadderGeometry.LocalFrame localFrame = localPhysicsFrame(pitch);
		Vec3 anchor = SableStructureCompat.transformPositionToWorld(this, localFrame.anchor());
		Vec3 right = SableStructureCompat.transformNormalToWorld(this, localFrame.right()).normalize();
		Vec3 longAxis = SableStructureCompat.transformNormalToWorld(this, localFrame.longAxis()).normalize();
		Vec3 normal = SableStructureCompat.transformNormalToWorld(this, localFrame.normal()).normalize();
		return new ExtensionLadderGeometry.WorldFrame(anchor, right, longAxis, normal);
	}

	private static float ease(float value) {
		float t = Mth.clamp(value, 0, 1);
		return t * t * (3 - 2 * t);
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).inflate(7);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putDouble("AnchorX", anchorOffset.x);
		tag.putDouble("AnchorY", anchorOffset.y);
		tag.putDouble("AnchorZ", anchorOffset.z);
		tag.putDouble("FallX", fallDirection.x);
		tag.putDouble("FallZ", fallDirection.z);
		if (anchorSupport != null)
			tag.putLong("AnchorSupport", anchorSupport.asLong());
		tag.putInt("Phase", phase.ordinal());
		tag.putInt("AnimationTick", animationTick);
		tag.putFloat("CurrentPitch", currentPitch);
		tag.putFloat("StartPitch", startPitch);
		tag.putFloat("TargetPitch", targetPitch);
		tag.putFloat(TAG_MOVE_OFFSET, targetMoveOffsetPixels);
		tag.putLong(TAG_CREATED_GAME_TIME, createdGameTime);
		tag.putBoolean(TAG_ACCELERATED_PLACEMENT_ANIMATION, acceleratedPlacementAnimation);
		tag.putFloat("SearchPitch", searchTask.nextPitch());
		if (support != null)
			support.write(tag, "Support");
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		anchorOffset = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
		fallDirection = ExtensionLadderGeometry.normalizeHorizontal(new Vec3(tag.getDouble("FallX"), 0,
			tag.getDouble("FallZ")));
		if (fallDirection.lengthSqr() < 1.0E-6)
			fallDirection = new Vec3(0, 0, 1);
		anchorSupport = tag.contains("AnchorSupport") ? BlockPos.of(tag.getLong("AnchorSupport")) : null;
		int phaseOrdinal = Mth.clamp(tag.getInt("Phase"), 0, Phase.values().length - 1);
		phase = Phase.values()[phaseOrdinal];
		animationTick = tag.getInt("AnimationTick");
		previousAnimationTick = animationTick;
		currentPitch = tag.getFloat("CurrentPitch");
		startPitch = tag.getFloat("StartPitch");
		targetPitch = tag.getFloat("TargetPitch");
		targetMoveOffsetPixels = tag.contains(TAG_MOVE_OFFSET)
			? Mth.clamp(tag.getFloat(TAG_MOVE_OFFSET), 0, ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS)
			: ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS;
		createdGameTime = tag.contains(TAG_CREATED_GAME_TIME) ? tag.getLong(TAG_CREATED_GAME_TIME) : Long.MIN_VALUE;
		acceleratedPlacementAnimation = tag.getBoolean(TAG_ACCELERATED_PLACEMENT_ANIMATION)
			|| tag.getBoolean(TAG_LEGACY_SKIP_EXTEND_ANIMATION);
		lastAdjustTick = 0;
		searchTask.setNextPitch(tag.contains("SearchPitch") ? tag.getFloat("SearchPitch") : currentPitch);
		support = ExtensionLadderSupportRef.read(tag, "Support");
		startClientPlacementAnimationIfRecent();
	}

	private void startClientPlacementAnimationIfRecent() {
		if (level == null || !level.isClientSide || createdGameTime == Long.MIN_VALUE
			|| clientAnimatedCreatedGameTime == createdGameTime)
			return;
		long age = level.getGameTime() - createdGameTime;
		if (age < 0 || age > CLIENT_PLACEMENT_REPLAY_WINDOW)
			return;
		clientAnimatedCreatedGameTime = createdGameTime;
		clientPlacementTick = 0;
		previousClientPlacementTick = 0;
	}

	public enum Phase {
		DROP,
		EXTENDING,
		SEARCHING,
		TILTING,
		SUPPORTED,
		FALLEN,
		ADJUSTING
	}
}
