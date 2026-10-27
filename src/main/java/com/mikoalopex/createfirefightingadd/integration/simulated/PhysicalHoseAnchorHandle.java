package com.mikoalopex.createfirefightingadd.integration.simulated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseAnchors;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.mixin.RopeLatchPhysicsAccess;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Splits a physical hose into free solver intervals around fixed bracket segments.
 * The combined pose retains the native strand's point indices.
 */
final class PhysicalHoseAnchorHandle implements RopeHandle {
    private record Anchor(Vector3d local, ServerSubLevel body) {
        void apply(RopeHandle handle, AttachmentPoint end) { handle.setAttachment(end, local, body); }
        Vector3d world(ServerLevel level) {
            Vec3 point = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(local.x, local.y, local.z));
            return new Vector3d(point.x, point.y, point.z);
        }
    }
    private record Fixed(int segment, Anchor start, Anchor end) {}
    private record Free(int start, RopeHandle handle, List<Vector3d> points) {}
    private final ServerLevel level;
    private final List<Fixed> fixed = new ArrayList<>();
    private final List<Free> free = new ArrayList<>();
    private final int pointCount;
    private boolean removed;

    private PhysicalHoseAnchorHandle(ServerLevel level, int count) { this.level = level; pointCount = count; }
    static RopeHandle handle(ServerRopeStrand rope) { return ((RopeLatchPhysicsAccess) rope).firefighting$getHandle(); }
    static boolean install(PhysicalHoseBlockEntity hose, ServerRopeStrand rope) {
        if (!(hose.getLevel() instanceof ServerLevel level) || !rope.isActive()) return false;
        Anchor start = endpoint(level, rope, RopeAttachmentPoint.START);
        Anchor end = endpoint(level, rope, RopeAttachmentPoint.END);
        if (start == null || end == null) return false;
        rope.updatePose();
        List<Vector3d> points = copy(rope.getPoints());
        var result = new PhysicalHoseAnchorHandle(level, points.size());
        for (var binding : hose.anchors.stream().sorted(Comparator.comparingInt(PhysicalHoseAnchors.Binding::segment)).toList()) {
            var bracket = binding.resolve(level);
            if (bracket == null || binding.segment() < 0 || binding.segment() + 1 >= points.size()) return false;
            var containing = Sable.HELPER.getContaining(bracket);
            ServerSubLevel body = containing instanceof ServerSubLevel sub ? sub : null;
            result.fixed.add(new Fixed(binding.segment(), anchor(PhysicalHoseAnchors.port(bracket, false), body),
                anchor(PhysicalHoseAnchors.port(bracket, true), body)));
        }
        try {
            int cursor = 0;
            Anchor previous = start;
            for (Fixed fixed : result.fixed) {
                result.addFree(points, cursor, fixed.segment, previous, fixed.start, rope);
                points.get(fixed.segment).set(fixed.start.world(level));
                points.get(fixed.segment + 1).set(fixed.end.world(level));
                cursor = fixed.segment + 1;
                previous = fixed.end;
            }
            result.addFree(points, cursor, points.size() - 1, previous, end, rope);
        } catch (RuntimeException exception) {
            result.remove();
            throw exception;
        }
        // Replace the live handle only after every free interval has been created.
        RopeHandle old = handle(rope);
        ((RopeLatchPhysicsAccess) rope).firefighting$setHandle(result.fixed.isEmpty() ? result.free.getFirst().handle : result);
        old.remove();
        result.wakeUp();
        return true;
    }
    private void addFree(List<Vector3d> all, int from, int to, Anchor a, Anchor b, ServerRopeStrand rope) {
        if (from == to) return;
        var points = copy(all.subList(from, to + 1));
        points.getFirst().set(a.world(level));
        points.getLast().set(b.world(level));
        RopeHandle handle = SubLevelPhysicsSystem.get(level).getPipeline()
            .addRope(new RopePhysicsObject(points, rope.getCollisionRadius()));
        free.add(new Free(from, handle, points));
        handle.setFirstSegmentLength(from == 0 ? rope.getExtension() : 1);
        a.apply(handle, AttachmentPoint.START);
        b.apply(handle, AttachmentPoint.END);
    }
    private static Anchor endpoint(ServerLevel level, ServerRopeStrand rope, RopeAttachmentPoint end) {
        var attachment = rope.getAttachment(end);
        if (attachment == null || !level.isLoaded(attachment.blockAttachment())
            || !(level.getBlockEntity(attachment.blockAttachment()) instanceof SimulatedPhysicalHoseBlockEntity hose)) return null;
        var containing = Sable.HELPER.getContaining(hose);
        return anchor(hose.getBehavior().getAttachmentPoint(), containing instanceof ServerSubLevel body ? body : null);
    }
    private static Anchor anchor(Vec3 point, ServerSubLevel body) { return new Anchor(new Vector3d(point.x, point.y, point.z), body); }
    private static List<Vector3d> copy(List<Vector3d> points) {
        List<Vector3d> result = new ArrayList<>();
        for (var point : points) result.add(new Vector3d(point));
        return result;
    }
    @Override public void readPose(List<Vector3d> points) {
        while (points.size() < pointCount) points.add(new Vector3d());
        while (points.size() > pointCount) points.removeLast();
        for (Free run : free) {
            run.handle.readPose(run.points);
            for (int i = 0; i < run.points.size(); i++) points.get(run.start + i).set(run.points.get(i));
        }
        for (Fixed segment : fixed) {
            points.get(segment.segment).set(segment.start.world(level));
            points.get(segment.segment + 1).set(segment.end.world(level));
        }
    }
    @Override public void remove() { if (!removed) { removed = true; free.forEach(run -> run.handle.remove()); } }
    @Override public void wakeUp() { free.forEach(run -> run.handle.wakeUp()); }
    @Override public void setAttachment(AttachmentPoint end, Vector3dc point, ServerSubLevel body) {
        if (free.isEmpty()) return;
        Free run = end == AttachmentPoint.START ? free.getFirst() : free.getLast();
        if (end == AttachmentPoint.START && run.start == 0
            || end == AttachmentPoint.END && run.start + run.points.size() == pointCount)
            run.handle.setAttachment(end, point, body);
    }
    @Override public void setFirstSegmentLength(double length) {
        if (!free.isEmpty() && free.getFirst().start == 0) free.getFirst().handle.setFirstSegmentLength(length);
    }
    @Override public void addPoint(Vector3dc point) { throw new UnsupportedOperationException("Fixed hose length"); }
    @Override public void removeFirstPoint() { throw new UnsupportedOperationException("Fixed hose length"); }
}
