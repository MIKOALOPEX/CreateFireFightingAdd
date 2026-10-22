package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class RockerBlockEntity extends HandCrankBlockEntity {
    final RockerMotion motion = new RockerMotion();
    private ScrollValueBehaviour ratio;
    private long lastUse = Long.MIN_VALUE / 2;
    private boolean heldReverse;
    private float previousAngle, visualAngle;
    private int pendingSide;
    private final RockerSliderAnimation sliderAnimation = new RockerSliderAnimation();
    private boolean recoverStoppedSource;
    private float recoverySpeed;
    public RockerBlockEntity(BlockPos pos, BlockState state) { super(Rockers.BLOCK_ENTITY.get(), pos, state); }
    public Direction facing() { return getBlockState().getValue(RockerBlock.FACING); }
    public int step() { return motion.step; }
    public float sliderStep(float partial) {
        return sliderAnimation.value(partial);
    }
    public float angle(float partial) { return previousAngle + (visualAngle - previousAngle) * partial; }
    public float shaftAngle(float partial) { return convertToDirection(getIndependentAngle(partial), facing()); }
    public boolean dial(Vec3 local) {
        if (ratio == null) return false;
        var transform = (ValueBoxTransform.Sided) ratio.getSlotPositioning();
        Direction previous = transform.getSide();
        try {
            transform.fromSide(facing());
            return ratio.testHit(Vec3.atLowerCornerOf(worldPosition).add(local));
        } finally { transform.fromSide(previous); }
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        ratio = new ScrollValueBehaviour(Component.translatable("rocker.ratio"), this, new ValueBoxTransform.Sided() {
            @Override protected Vec3 getSouthLocation() { return new Vec3(0.5, 0.5, 0.445); }
            @Override protected boolean isSideActive(BlockState state, Direction side) { return side == state.getValue(RockerBlock.FACING); }
        }) {
            @Override public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                return new ValueSettingsBoard(label, 6, 1, List.of(label),
                    new ValueSettingsFormatter(value -> Component.literal((1 + value.value() * 0.5) + "x")));
            }
        }.between(0,6).withFormatter(value -> (1 + value * 0.5) + "x");
        behaviours.add(ratio);
    }
    public void use(Player player) {
        if (level == null || level.isClientSide || player.isSpectator() || level.getGameTime() - lastUse < 4) return;
        lastUse = level.getGameTime(); heldReverse = player.isShiftKeyDown();
        if (motion.mode == RockerMotion.ROTARY) super.turn(heldReverse);
        else if (!motion.running) { motion.start(heldReverse); updateGeneratedRotation(); }
        if (!player.getMainHandItem().is(AllItems.EXTENDO_GRIP.get()))
            player.causeFoodExhaustion((float)(32 * motion.multiplier() * AllConfigs.server().kinetics.crankHungerMultiplier.getF()));
        if (player.getFoodData().getFoodLevel() == 0) AllAdvancements.HAND_CRANK.awardTo(player);
        notifyUpdate();
    }
    public void select(int side, Player player) {
        if (level.isClientSide) return;
        if (motion.running) pendingSide = side;
        else applySide(side);
        player.displayClientMessage(Component.translatable("rocker.mode." + (pendingSide == 0 ? motion.mode :
            side == motion.half ? (motion.mode+1)%3 : motion.mode)), true);
        notifyUpdate();
    }
    private void applySide(int side) {
        motion.select(side); inUse = 0; lastUse = Long.MIN_VALUE / 2;
        visualAngle = previousAngle = motion.half * 45;
        independentAngle = convertToDirection(visualAngle, facing());
        updateGeneratedRotation();
    }
    @Override protected Block getStressConfigKey() { return AllBlocks.HAND_CRANK.get(); }
    @Override public float calculateAddedStressCapacity() {
        float base = super.calculateAddedStressCapacity();
        // Create multiplies the capacity coefficient by absolute generated RPM.
        return lastCapacityProvided = motion == null ? base : (float)motion.capacityCoefficient(base);
    }
    @Override public float getGeneratedSpeed() {
        if (motion == null) return 0;
        if (recoverStoppedSource) return recoverySpeed;
        return motion.mode == RockerMotion.ROTARY ? (float)motion.outputSpeed(super.getGeneratedSpeed())
            : convertToDirection((float)motion.speed(), facing());
    }
    @Override public void tick() {
        previousAngle = visualAngle;
        float old = getGeneratedSpeed();
        int oldStep = motion.step;
        super.tick();
        // Register stale saved output before Create propagates the stop to the network.
        if (!level.isClientSide && recoverStoppedSource) {
            recoverStoppedSource = false;
        }
        if (motion.mode == RockerMotion.ROTARY) visualAngle = convertToDirection(independentAngle, facing());
        else {
            boolean wasRunning = motion.running;
            motion.tick();
            if (!level.isClientSide && wasRunning && !motion.running) {
                if (ratio != null) motion.step = ratio.getValue();
                if (pendingSide != 0) { applySide(pendingSide); pendingSide = 0; }
                else if (level.getGameTime() - lastUse <= 5) motion.start(heldReverse);
            }
            visualAngle = (float)motion.angle();
        }
        if (!motion.running && ratio != null && motion.step != ratio.getValue()) {
            motion.step = ratio.getValue();
            if (!level.isClientSide) notifyUpdate();
        }
        if (!level.isClientSide && (old != getGeneratedSpeed() || oldStep != motion.step)) {
            updateGeneratedRotation(); notifyUpdate();
        }
        if (!level.isClientSide && motion.running && level.getGameTime()%4 == 0) sendData();
        if (level.isClientSide) {
            // Interpolate the slider from the synchronized ratio.
            sliderAnimation.tick(motion.step);
        }
        RockerTargets.update(this);
    }
    @Override public void invalidate() { RockerTargets.remove(this); super.invalidate(); }
    @Override public void tickAudio() {
        super.tickAudio();
        if ((inUse > 0 || motion.running) && level.getGameTime() % 10 == 0)
            AllSoundEvents.CRANKING.playAt(level, worldPosition, (motion.running ? 10 : inUse) / 2.5f,
                0.65f + (10 - (motion.running ? 10 : inUse)) / 10f, true);
    }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(4); }
    @Override public void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag,registries,packet);
        motion.write(tag);
        tag.putInt("PendingSide", pendingSide);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        motion.read(tag);
        recoverStoppedSource = false;
        super.read(tag,registries,packet);
        pendingSide = Math.clamp(tag.getInt("PendingSide"), -1, 1);
        if (!packet) {
            // Preserve HandCrank's countdown and direction for native shutdown.
            lastUse = Long.MIN_VALUE / 2;
            heldReverse = false;
            recoverySpeed = getTheoreticalSpeed();
            recoverStoppedSource = !hasSource() && getGeneratedSpeed() == 0
                && Float.isFinite(recoverySpeed) && recoverySpeed != 0;
            visualAngle = previousAngle = (float)motion.angle();
        }
    }
}
