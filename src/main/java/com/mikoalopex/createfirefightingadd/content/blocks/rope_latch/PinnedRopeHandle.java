package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnectorBlockEntity;
import com.mikoalopex.createfirefightingadd.mixin.RopeLatchPhysicsAccess;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Two solver ropes behind one native strand: ownership, UUID, packets and drops are unchanged. */
public final class PinnedRopeHandle implements RopeHandle {
    private static final Method STEEL_FLAG = findSteelFlag();
    private final RopeHandle first;
    private final RopeHandle second;
    private final List<Vector3d> firstPoints;
    private final List<Vector3d> secondPoints;
    private final Vector3dc pinAnchor;
    private final ServerSubLevel pinBody;
    private boolean removed;

    private PinnedRopeHandle(RopeHandle first, RopeHandle second, List<Vector3d> a, List<Vector3d> b,
            Vector3dc anchor, ServerSubLevel body) {
        this.first = first;
        this.second = second;
        firstPoints = a;
        secondPoints = b;
        pinAnchor = new Vector3d(anchor);
        pinBody = body;
    }

    public static boolean canCapture(ServerLevel level, ServerRopeStrand rope) {
        if (!rope.isActive() || rope.getClass() != ServerRopeStrand.class || rope.getPoints().size() < 3
                || rope.getPoints().size() > 1024
                || handle(rope) instanceof PinnedRopeHandle) return false;
        // Unknown cable metadata or endpoint behaviour must not be silently taken over.
        if (ModList.get().isLoaded("create_submarine")) {
            if (STEEL_FLAG == null) return false;
            try {
                if ((boolean) STEEL_FLAG.invoke(rope)) return false;
            } catch (ReflectiveOperationException exception) { return false; }
        }
        return endpoint(level, rope.getAttachment(RopeAttachmentPoint.START)) != null
            && endpoint(level, rope.getAttachment(RopeAttachmentPoint.END)) != null;
    }

    public static PinnedRopeHandle attach(ServerLevel level, ServerRopeStrand rope, int node,
            Vector3dc anchor, ServerSubLevel body) {
        if (!canCapture(level, rope) || node < 1 || node >= rope.getPoints().size() - 1) return null;
        Anchor start = endpoint(level, rope.getAttachment(RopeAttachmentPoint.START));
        Anchor end = endpoint(level, rope.getAttachment(RopeAttachmentPoint.END));
        if (start == null || end == null) return null;
        rope.updatePose();
        var points = rope.getPoints();
        List<Vector3d> a = copy(points.subList(0, node + 1));
        List<Vector3d> b = copy(points.subList(node, points.size()));
        PhysicsPipeline pipeline = SubLevelPhysicsSystem.get(level).getPipeline();
        RopeHandle first = null, second = null;
        try {
            first = pipeline.addRope(new RopePhysicsObject(a, rope.getCollisionRadius()));
            second = pipeline.addRope(new RopePhysicsObject(b, rope.getCollisionRadius()));
            first.setFirstSegmentLength(rope.getExtension());
            second.setFirstSegmentLength(ServerRopeStrand.SEGMENT_LENGTH);
            start.apply(first, AttachmentPoint.START);
            first.setAttachment(AttachmentPoint.END, anchor, body);
            second.setAttachment(AttachmentPoint.START, anchor, body);
            end.apply(second, AttachmentPoint.END);
        } catch (RuntimeException exception) {
            if (first != null) first.remove();
            if (second != null) second.remove();
            throw exception;
        }
        PinnedRopeHandle pinned = new PinnedRopeHandle(first, second, a, b, anchor, body);
        RopeHandle previous = handle(rope);
        ((RopeLatchPhysicsAccess) rope).firefighting$setHandle(pinned);
        previous.remove();
        pinned.wakeUp();
        return pinned;
    }

    public boolean release(ServerLevel level, ServerRopeStrand rope) {
        if (removed || !rope.isActive() || handle(rope) != this) return true;
        Anchor start = endpoint(level, rope.getAttachment(RopeAttachmentPoint.START));
        Anchor end = endpoint(level, rope.getAttachment(RopeAttachmentPoint.END));
        if (start == null || end == null) {
            // Unloading the native object lets its original owner reactivate it when both ends return.
            SubLevelPhysicsSystem.get(level).removeObject(rope);
            return true;
        }
        rope.updatePose();
        RopeHandle replacement = SubLevelPhysicsSystem.get(level).getPipeline()
            .addRope(new RopePhysicsObject(copy(rope.getPoints()), rope.getCollisionRadius()));
        try {
            replacement.setFirstSegmentLength(rope.getExtension());
            start.apply(replacement, AttachmentPoint.START);
            end.apply(replacement, AttachmentPoint.END);
        } catch (RuntimeException exception) {
            replacement.remove();
            throw exception;
        }
        ((RopeLatchPhysicsAccess) rope).firefighting$setHandle(replacement);
        remove();
        replacement.wakeUp();
        return true;
    }

    public boolean isRemoved() { return removed; }

    public int pinnedNode() { return firstPoints.size() - 1; }
    public int tailSegments() { return secondPoints.size() - 1; }

    /** Leave at least one solver segment between the winch and the fixed material point. */
    public float limitWinchSpeed(float speed) {
        return firstPoints.size() <= 2 ? Math.max(0, speed) : speed;
    }

    public static RopeHandle handle(ServerRopeStrand rope) {
        return ((RopeLatchPhysicsAccess) rope).firefighting$getHandle();
    }

    @Override
    public void readPose(List<Vector3d> points) {
        first.readPose(firstPoints);
        second.readPose(secondPoints);
        int size = firstPoints.size() + secondPoints.size() - 1;
        while (points.size() < size) points.add(new Vector3d());
        while (points.size() > size) points.removeLast();
        for (int i = 0; i < firstPoints.size(); i++) points.get(i).set(firstPoints.get(i));
        for (int i = 1; i < secondPoints.size(); i++)
            points.get(firstPoints.size() - 1 + i).set(secondPoints.get(i));
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        first.remove();
        second.remove();
    }

    @Override public void wakeUp() { first.wakeUp(); second.wakeUp(); }
    @Override public void setFirstSegmentLength(double length) { first.setFirstSegmentLength(length); }
    @Override
    public void addPoint(Vector3dc point) {
        first.addPoint(point);
        firstPoints.addFirst(new Vector3d(point));
        first.setAttachment(AttachmentPoint.END, pinAnchor, pinBody);
    }
    @Override
    public void removeFirstPoint() {
        first.removeFirstPoint();
        firstPoints.removeFirst();
        first.setAttachment(AttachmentPoint.END, pinAnchor, pinBody);
    }
    @Override
    public void setAttachment(AttachmentPoint point, Vector3dc position, ServerSubLevel body) {
        (point == AttachmentPoint.START ? first : second).setAttachment(point, position, body);
    }

    private static List<Vector3d> copy(List<Vector3d> points) {
        List<Vector3d> result = new ArrayList<>(points.size());
        for (Vector3d point : points) result.add(new Vector3d(point));
        return result;
    }

    private static Anchor endpoint(ServerLevel level, RopeAttachment attachment) {
        if (attachment == null || !level.isLoaded(attachment.blockAttachment())) return null;
        var be = level.getBlockEntity(attachment.blockAttachment());
        if (!(be instanceof RopeConnectorBlockEntity) && !(be instanceof SmartRopeConnectorBlockEntity)
                && !(be instanceof RopeWinchBlockEntity)) return null;
        var holder = ((SmartBlockEntity) be).getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (holder == null) return null;
        ServerSubLevel body = null;
        if (attachment.subLevelID() != null) {
            var container = SubLevelContainer.getContainer(level);
            if (container == null || !(container.getSubLevel(attachment.subLevelID()) instanceof ServerSubLevel sublevel))
                return null;
            body = sublevel;
        }
        var point = holder.getAttachmentPoint();
        return new Anchor(new Vector3d(point.x, point.y, point.z), body);
    }

    private static Method findSteelFlag() {
        if (!ModList.get().isLoaded("create_submarine")) return null;
        try {
            return Class.forName("com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor")
                .getMethod("createsubmarine$isSteelCable");
        } catch (ReflectiveOperationException exception) { return null; }
    }

    private record Anchor(Vector3dc point, ServerSubLevel body) {
        void apply(RopeHandle handle, AttachmentPoint end) { handle.setAttachment(end, point, body); }
    }
}
