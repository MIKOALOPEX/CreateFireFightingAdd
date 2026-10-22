package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.util.List;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.api.rope.RopeHookMode;
import com.mikoalopex.createfirefightingadd.api.rope.RopeHookModeControl;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnectorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Search is explicitly armed; bound ropes are addressed by UUID instead of searched again. */
public class RopeLatchBlockEntity extends SmartBlockEntity implements RopeHookModeControl {
    private UUID ropeId;
    private int node;
    private int tailSegments = -1;
    private double tailLength = -1;
    private ScrollValueBehaviour modeSelector;
    private BlockPos ownerPos;
    private long searchUntil;
    private long cooldownUntil;
    private boolean lastPowered;
    private PinnedRopeHandle pin;
    private ServerRopeStrand boundRope;
    private Vec3 lastAnchor;
    private boolean moving;
    private float gateAngle;
    private float previousGateAngle;
    private float rodAngle;
    private float previousRodAngle;
    private float boundRodAngle = 70;
    private double rodDamping = RopeSlideSettings.DEFAULT.swingDamping();
    private double upperRodDamping = RopeSlideSettings.DEFAULT.upperSwingDamping();

    @Override public void initialize() {
        super.initialize();
        if (ropeId != null && level instanceof ServerLevel server) RopeHookAttachments.get(server).attach(ropeId);
    }

    public float getRodAngle(float partialTick) {
        return Mth.lerp(partialTick, previousRodAngle, rodAngle);
    }

    @Override
    public AABB getRenderBoundingBox() {
        var bounds = new AABB(worldPosition);
        return getMode() == RopeHookMode.ARRESTING ? bounds.inflate(3.1) : bounds;
    }

    public float getGateAngle(float partialTick) {
        return Mth.lerp(partialTick, previousGateAngle, gateAngle);
    }

    public RopeLatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        modeSelector = new ScrollValueBehaviour(Component.translatable("createfirefightingadd.rope_hook.mode"),
            this, new ValueBoxTransform.Sided() {
                @Override protected Vec3 getSouthLocation() { return new Vec3(0.5, 0.5, 0.5); }
                @Override protected boolean isSideActive(BlockState state, Direction side) {
                    return side == state.getValue(RopeLatchBlock.FACING);
                }
            }) {
                @Override public boolean bypassesInput(ItemStack stack) {
                    return !stack.isEmpty();
                }
                @Override public boolean mayInteract(Player player) {
                    return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
                }
                @Override public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                    return new ValueSettingsBoard(label, RopeHookMode.values().length - 1, 1, List.of(label),
                        new ValueSettingsFormatter(settings -> modeLabel(settings.value()).copy()));
                }
            }.between(0, RopeHookMode.values().length - 1)
                .withFormatter(value -> modeLabel(value).getString()).withCallback(value -> applyMode());
        modeSelector.value = (Config.ropeLatchSlidingEnabled ? RopeHookMode.SLIDING : RopeHookMode.LOCKED).ordinal();
        behaviours.add(modeSelector);
    }

    private static Component modeLabel(int value) {
        var mode = RopeHookMode.values()[Math.clamp(value, 0, RopeHookMode.values().length - 1)];
        return Component.translatable("createfirefightingadd.rope_hook.mode." + mode.id());
    }

    @Override public RopeHookMode getMode() {
        return modeSelector == null ? RopeHookMode.LOCKED
            : RopeHookMode.values()[Math.clamp(modeSelector.getValue(), 0, RopeHookMode.values().length - 1)];
    }

    @Override public boolean setMode(RopeHookMode mode) {
        if (mode == null || modeSelector == null || !(level instanceof ServerLevel) || isRemoved()) return false;
        modeSelector.setValue(mode.ordinal());
        applyMode();
        return true;
    }

    private void applyMode() {
        if (level == null || level.isClientSide) return;
        if (pin instanceof SlidingRopeHandle handle) handle.setMode(getMode());
    }

    public boolean isMoving() { return moving; }
    public void beginMove() { moving = true; detachPhysics(); }
    public void finishMove() { moving = false; }

    public void sampleSignal() {
        if (!(level instanceof ServerLevel server) || moving) return;
        boolean powered = server.hasNeighborSignal(worldPosition);
        boolean rising = powered && !lastPowered;
        if (lastPowered != powered) {
            lastPowered = powered;
            setChanged();
        }
        if (getBlockState().getValue(RopeLatchBlock.POWERED) != powered)
            server.setBlock(worldPosition, getBlockState().setValue(RopeLatchBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        long now = server.getGameTime();
        if (!rising || now < cooldownUntil) return;
        if (ropeId != null) {
            release();
            cooldownUntil = now + 100;
        } else if (now >= searchUntil) {
            searchUntil = now + Config.ropeLatchSlideSettings.searchDurationSeconds() * 20L;
            RopeLatchIndex.request(server);
        }
        notifyUpdate();
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide && (rodDamping != Config.ropeLatchSlideSettings.swingDamping()
                || upperRodDamping != Config.ropeLatchSlideSettings.upperSwingDamping())) {
            rodDamping = Config.ropeLatchSlideSettings.swingDamping();
            upperRodDamping = Config.ropeLatchSlideSettings.upperSwingDamping();
            sendData();
        }
        if (level != null && !moving) {
            previousRodAngle = rodAngle;
            float target = getMode() != RopeHookMode.ARRESTING ? 0
                : ropeId != null ? boundRodAngle : level.getGameTime() < searchUntil ? 70 : 0;
            // Dampen only attached tracking; deployment and retraction keep their original speed.
            boolean tracking = getMode() == RopeHookMode.ARRESTING && ropeId != null;
            double damping = rodAngle > 30 || (rodAngle == 30 && target > 30) ? upperRodDamping : rodDamping;
            float step = tracking ? (float) Math.min(7, Math.abs(target - rodAngle) * (1 - damping)) : 7;
            // End a crossing step at the boundary so the next segment uses its own damping.
            if (tracking && ((rodAngle > 30 && target < 30) || (rodAngle < 30 && target > 30)))
                step = Math.min(step, Math.abs(rodAngle - 30));
            rodAngle = Mth.approach(rodAngle, target, step);
            if (!level.isClientSide && rodAngle != previousRodAngle) setChanged();
        }
        if (level != null && level.isClientSide) {
            previousGateAngle = gateAngle;
            long now = level.getGameTime();
            boolean open = ropeId == null && (now < searchUntil || now < cooldownUntil);
            // Rotate inward around the supplied lower hinge; the baked model retains its own angle.
            gateAngle = Mth.approach(gateAngle, open ? 45 : 0, 11.25f);
            return;
        }
        if (!(level instanceof ServerLevel server) || moving) return;
        // Sampling also handles redstone providers that omit neighbour notifications.
        sampleSignal();
        if (Config.ropeLatchDebugSliding && Math.floorMod(server.getGameTime() + worldPosition.asLong(), 100) == 0) {
            String state = pin instanceof SlidingRopeHandle slide ? slide.debugState()
                : pin != null ? "fixed" : "no active physics handle";
            CreateFireFightingAdd.LOGGER.info("Rope latch {}: rope={}, mode={}, defaultSliding={}, {}",
                worldPosition, ropeId, getMode(), Config.ropeLatchSlidingEnabled, state);
        }
        if (ropeId != null) {
            maintain(server);
            if (getMode() == RopeHookMode.ARRESTING && server.getGameTime() % 5 == 0)
                updateRodDirection();
            return;
        }
        long now = server.getGameTime();
        if (now >= searchUntil || now < cooldownUntil) return;
        RopeLatchIndex.request(server);
        if (Math.floorMod(now + worldPosition.asLong(), Config.ropeLatchSlideSettings.scanIntervalTicks()) != 0) return;
        Vec3 world = SableStructureCompat.transformPositionToWorld(this, anchor());
        RopeCaptureArea area = getMode() == RopeHookMode.ARRESTING
            ? TailHookGeometry.captureArea(this, rodAngle) : RopeCaptureArea.sphere(world);
        RopeLatchIndex.Hit hit = RopeLatchIndex.search(server, area);
        if (hit == null) return;
        try {
            tailLength = -1;
            hit.rope().updatePose();
            if (!area.contains(hit.rope().getPoints().get(hit.point()))) return;
            if (attach(server, hit.rope(), hit.point())) {
                ropeId = hit.rope().getUUID();
                RopeHookAttachments.get(server).attach(ropeId);
                if (getMode() == RopeHookMode.ARRESTING) boundRodAngle = rodAngle;
                node = hit.point();
                tailSegments = pin.tailSegments();
                rememberPin();
                searchUntil = 0;
                notifyUpdate();
            }
        } catch (RuntimeException exception) {
            searchUntil = 0;
            notifyUpdate();
            CreateFireFightingAdd.LOGGER.warn("Rope latch at {} could not capture a rope; search stopped.", worldPosition);
            CreateFireFightingAdd.LOGGER.debug("Rope latch capture failure", exception);
        }
    }

    private void maintain(ServerLevel server) {
        rememberPin();
        if (pin instanceof SlidingRopeHandle slide && slide.hasFailed()) {
            release();
            return;
        }
        if (boundRope != null) rememberOwner(boundRope);
        if (pin != null && !pin.isRemoved() && boundRope != null
                && PinnedRopeHandle.handle(boundRope) == pin) {
            Vec3 anchor = anchor();
            // Assembly handles body changes; pitch updates only move the attachment on the existing body.
            if (!anchor.equals(lastAnchor)) {
                if (pin instanceof SlidingRopeHandle sliding) {
                    sliding.moveAnchor(new Vector3d(anchor.x, anchor.y, anchor.z));
                    lastAnchor = anchor;
                    return;
                }
                detachPhysics();
            } else return;
        }
        if (Math.floorMod(server.getGameTime() + worldPosition.asLong(), 10) != 0) return;
        ServerRopeStrand rope = ServerLevelRopeManager.getOrCreate(server).getStrand(ropeId);
        if (rope == null) {
            if (ownerPos != null && server.isLoaded(ownerPos) && !ownerRetainsRope(server)) release();
            return;
        }
        if (!rope.isActive()) return;
        if (PinnedRopeHandle.handle(rope) instanceof PinnedRopeHandle other && other != pin) {
            release();
            return;
        }
        // Sliding captures persist a fractional allocation; old fixed captures keep their node offset.
        if (tailLength >= RopeSlideState.MIN_SIDE) {
            double total = rope.getPoints().size() - 2 + rope.getExtension();
            if (total < 2 * RopeSlideState.MIN_SIDE || rope.getPoints().size() < 3) {
                release();
                return;
            }
            tailLength = Math.clamp(tailLength, RopeSlideState.MIN_SIDE, total - RopeSlideState.MIN_SIDE);
            node = Math.clamp(1 + (int) Math.round(total - tailLength - rope.getExtension()), 1, rope.getPoints().size() - 2);
        } else if (tailSegments >= 1) node = rope.getPoints().size() - 1 - tailSegments;
        if (node < 1 || node >= rope.getPoints().size() - 1) {
            release();
            return;
        }
        try {
            attach(server, rope, node);
        } catch (RuntimeException exception) {
            release();
            CreateFireFightingAdd.LOGGER.warn("Rope latch at {} could not restore its attachment; released.", worldPosition);
            CreateFireFightingAdd.LOGGER.debug("Rope latch attachment restore failure", exception);
        }
    }

    private boolean attach(ServerLevel server, ServerRopeStrand rope, int point) {
        UUID bodyId = SableStructureCompat.containingSubLevelId(this);
        ServerSubLevel body = null;
        if (bodyId != null) {
            var container = SubLevelContainer.getContainer(server);
            if (container == null || !(container.getSubLevel(bodyId) instanceof ServerSubLevel sublevel)) return false;
            body = sublevel;
        }
        Vec3 local = anchor();
        Vector3d localPoint = new Vector3d(local.x, local.y, local.z);
        SlidingRopeHandle created = SlidingRopeHandle.attach(server, rope, point, localPoint, body, tailLength);
        if (created == null) return false;
        created.setMode(getMode());
        pin = created;
        tailSegments = created.tailSegments();
        boundRope = rope;
        rememberOwner(rope);
        lastAnchor = local;
        return true;
    }

    private void rememberPin() {
        if (pin == null) return;
        int currentNode = pin.pinnedNode();
        double currentTail = pin instanceof SlidingRopeHandle slide ? slide.tailLength() : -1;
        if (node != currentNode || Double.compare(tailLength, currentTail) != 0) {
            node = currentNode;
            tailSegments = pin.tailSegments();
            tailLength = currentTail;
            setChanged();
        }
    }

    private void rememberOwner(ServerRopeStrand rope) {
        var start = rope.getAttachment(RopeAttachmentPoint.START);
        if (start != null && !start.blockAttachment().equals(ownerPos)) {
            ownerPos = start.blockAttachment().immutable();
            setChanged();
        }
    }

    private boolean ownerRetainsRope(ServerLevel server) {
        var be = server.getBlockEntity(ownerPos);
        if (be instanceof SmartRopeConnectorBlockEntity smart) return smart.owner(ropeId) != null;
        if (!(be instanceof SmartBlockEntity smart)) return false;
        var holder = smart.getBehaviour(RopeStrandHolderBehavior.TYPE);
        var rope = holder == null ? null : holder.getOwnedStrand();
        return rope != null && ropeId.equals(rope.getUUID());
    }

    private Vec3 anchor() {
        if (getMode() == RopeHookMode.ARRESTING)
            return Vec3.atLowerCornerOf(worldPosition).add(TailHookGeometry.anchor(getBlockState(), rodAngle));
        return worldPosition.getCenter().add(Vec3.atLowerCornerOf(getBlockState()
            .getValue(RopeLatchBlock.FACING).getNormal()).scale(-3.0 / 16.0));
    }

    private void updateRodDirection() {
        // Two adjacent rope samples provide a pitch target without searching the surrounding world.
        if (boundRope == null || pin == null) return;
        var points = boundRope.getPoints();
        int index = pin.pinnedNode();
        if (index < 1 || index >= points.size() - 1) return;
        Vec3 origin = SableStructureCompat.transformPositionToWorld(this, anchor());
        Vec3 pull = Vec3.ZERO;
        for (int neighbor : new int[] {index - 1, index + 1}) {
            var point = points.get(neighbor);
            pull = pull.add(new Vec3(point.x, point.y, point.z).subtract(origin).normalize());
        }
        if (pull.lengthSqr() < 1e-6) return;
        var rotation = TailHookGeometry.rotation(getBlockState());
        var forward = rotation.transform(new Vector3f(0, 0, 1));
        var up = rotation.transform(new Vector3f(0, 1, 0));
        Vec3 worldForward = SableStructureCompat.transformPositionToWorld(this,
            anchor().add(forward.x, forward.y, forward.z)).subtract(origin).normalize();
        Vec3 worldUp = SableStructureCompat.transformPositionToWorld(this,
            anchor().add(up.x, up.y, up.z)).subtract(origin).normalize();
        float angle = (float) Math.toDegrees(Math.atan2(pull.dot(worldUp), pull.dot(worldForward)));
        angle = Mth.clamp(angle, 0, 70);
        if (Math.abs(angle - boundRodAngle) >= 1) {
            boundRodAngle = angle;
            sendData();
        }
    }

    public void release() {
        if (level instanceof ServerLevel server) RopeHookAttachments.get(server).release(ropeId);
        detachPhysics();
        ropeId = null;
        tailSegments = -1;
        tailLength = -1;
        ownerPos = null;
        searchUntil = 0;
        notifyUpdate();
    }

    private void detachPhysics() {
        rememberPin();
        if (pin != null && boundRope != null && level instanceof ServerLevel server) {
            try {
                pin.release(server, boundRope);
            } catch (RuntimeException exception) {
                // Never leave a live solver attachment pointing at a removed block.
                if (boundRope.isActive()) SubLevelPhysicsSystem.get(server).removeObject(boundRope);
                CreateFireFightingAdd.LOGGER.warn("Rope latch at {} released its physics object after a restore failure.", worldPosition);
                CreateFireFightingAdd.LOGGER.debug("Rope latch physics restore failure", exception);
            }
        }
        pin = null;
        boundRope = null;
    }

    @Override
    public void invalidate() {
        detachPhysics();
        super.invalidate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        rememberPin();
        super.write(tag, registries, clientPacket);
        if (ropeId != null) tag.putUUID("PinnedRope", ropeId);
        else tag.remove("PinnedRope");
        tag.putInt("PinnedNode", pin != null && !pin.isRemoved() ? pin.pinnedNode() : node);
        tag.putInt("TailSegments", tailSegments);
        tag.putDouble("SlidingTailLength", tailLength);
        tag.putString("HookMode", getMode().id());
        if (ownerPos != null) tag.putLong("RopeOwner", ownerPos.asLong());
        else tag.remove("RopeOwner");
        tag.putLong("SearchUntil", searchUntil);
        tag.putLong("CooldownUntil", cooldownUntil);
        tag.putBoolean("LastPowered", lastPowered);
        tag.putFloat("VisualRodAngle", boundRodAngle);
        tag.putFloat("RodAngle", rodAngle);
        tag.putDouble("RodDamping", rodDamping);
        tag.putDouble("UpperRodDamping", upperRodDamping);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ropeId = tag.hasUUID("PinnedRope") ? tag.getUUID("PinnedRope") : null;
        node = tag.getInt("PinnedNode");
        tailSegments = tag.contains("TailSegments") ? tag.getInt("TailSegments") : -1;
        tailLength = tag.contains("SlidingTailLength") ? tag.getDouble("SlidingTailLength") : -1;
        if (!Double.isFinite(tailLength)) tailLength = -1;
        // Legacy attachments retain their fixed/sliding choice; new captures use the block's dial.
        RopeHookMode restored = tag.contains("HookMode") ? RopeHookMode.fromId(tag.getString("HookMode"))
            : (tag.hasUUID("PinnedRope") ? tag.getBoolean("SlidingCapture") : Config.ropeLatchSlidingEnabled)
                ? RopeHookMode.SLIDING : RopeHookMode.LOCKED;
        if (modeSelector != null) modeSelector.value = restored.ordinal();
        applyMode();
        ownerPos = tag.contains("RopeOwner") ? BlockPos.of(tag.getLong("RopeOwner")) : null;
        searchUntil = tag.getLong("SearchUntil");
        cooldownUntil = tag.getLong("CooldownUntil");
        lastPowered = tag.getBoolean("LastPowered");
        boundRodAngle = tag.contains("VisualRodAngle") ? tag.getFloat("VisualRodAngle") : 70;
        boundRodAngle = Float.isFinite(boundRodAngle) ? Mth.clamp(boundRodAngle, 0, 70) : 70;
        if (tag.contains("RodAngle")) {
            float restoredAngle = tag.getFloat("RodAngle");
            rodAngle = Float.isFinite(restoredAngle) ? Mth.clamp(restoredAngle, 0, 70) : 0;
            previousRodAngle = rodAngle;
        }
        if (tag.contains("RodDamping")) {
            double damping = tag.getDouble("RodDamping");
            rodDamping = Double.isFinite(damping) ? Math.clamp(damping, 0, 0.99)
                : RopeSlideSettings.DEFAULT.swingDamping();
        }
        if (tag.contains("UpperRodDamping")) {
            double damping = tag.getDouble("UpperRodDamping");
            upperRodDamping = Double.isFinite(damping) ? Math.clamp(damping, 0, 0.99)
                : RopeSlideSettings.DEFAULT.upperSwingDamping();
        }
    }
}
