package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.List;
import java.util.UUID;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlockEntity;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Computes segment selection and bracket reach from the hose material lengths.
 * Each fixed segment separates the available length on its two sides.
 */
public final class PhysicalHoseAnchors {
    // Placement tolerance does not increase the strand's rest length.
    private static final double PLACEMENT_TOLERANCE = 1;
    public record Binding(int segment, BlockPos pos, UUID node) {
        public HoseBracketBlockEntity resolve(Level level) {
            return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof HoseBracketBlockEntity bracket
                && node.equals(bracket.nodeId) ? bracket : null;
        }
        public CompoundTag write(Level level) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Segment", segment); tag.putUUID("Node", node);
            SableStructureCompat.writeLinkedBlock(tag, "Pos", "SubLevel", pos,
                level == null ? null : SableStructureCompat.containingSubLevelId(level, pos));
            return tag;
        }
        public static Binding read(CompoundTag tag) {
            return new Binding(tag.getInt("Segment"), SableStructureCompat.readLinkedBlock(tag, "Pos", "SubLevel").pos(), tag.getUUID("Node"));
        }
    }
    /** World-space rope points with the native first-segment rest length. */
    public record Snapshot(UUID rope, List<Vec3> points, double firstLength) {
        public boolean valid(int segment) { return rope != null && segment >= 0 && segment + 1 < points.size(); }
        public double material(int node) { return node == 0 ? 0 : firstLength + node - 1; }
        public double length(int segment) { return segment == 0 ? firstLength : 1; }
    }
    public record Placement(Vec3 start, Vec3 end, Vec3 left, Vec3 right, double leftLength, double rightLength) {
        public boolean leftReachable() { return left.distanceTo(start) <= leftLength + PLACEMENT_TOLERANCE + 1e-5; }
        public boolean rightReachable() { return right.distanceTo(end) <= rightLength + PLACEMENT_TOLERANCE + 1e-5; }
        public boolean reachable() { return leftReachable() && rightReachable(); }
    }
    public record Target(float angle, boolean reversed, Vec3 start, Vec3 end) {}
    /** Resolves the target ports in world space using the clicked surface's frame. */
    public static Target target(Snapshot snapshot, int segment, BlockPos pos, Direction face, Vec3 worldLook,
                                java.util.function.UnaryOperator<Vec3> toWorld) {
        Vec3 localCenter = pos.getCenter();
        Vec3 center = toWorld.apply(localCenter);
        Vec3 x = toWorld.apply(localCenter.add(1, 0, 0)).subtract(center);
        Vec3 y = toWorld.apply(localCenter.add(0, 1, 0)).subtract(center);
        Vec3 z = toWorld.apply(localCenter.add(0, 0, 1)).subtract(center);
        Vec3 look = new Vec3(worldLook.dot(x), worldLook.dot(y), worldLook.dot(z));
        float angle = Math.abs(look.dot(axis(face, 90, false))) > Math.abs(look.dot(axis(face, 0, false))) ? 90 : 0;
        Vec3 worldAxis = toWorld.apply(localCenter.add(axis(face, angle, false))).subtract(center);
        boolean reversed = worldAxis.dot(snapshot.points.get(segment + 1).subtract(snapshot.points.get(segment))) < -1e-6;
        double length = snapshot.length(segment);
        return new Target(angle, reversed, toWorld.apply(port(pos, face, angle, reversed, length, false)),
            toWorld.apply(port(pos, face, angle, reversed, length, true)));
    }
    public static Vec3 axis(Direction face, float angle, boolean reversed) {
        var rotation = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0),
            new org.joml.Vector3f(face.getStepX(), face.getStepY(), face.getStepZ())).rotateY((float) Math.toRadians(angle));
        var axis = rotation.transform(new org.joml.Vector3f(0, 0, reversed ? -1 : 1));
        return new Vec3(axis.x, axis.y, axis.z);
    }
    public static Vec3 port(BlockPos pos, Direction face, float angle, boolean reversed, double length, boolean end) {
        return pos.getCenter().add(axis(face, angle, reversed).scale(length * (end ? 0.5 : -0.5)));
    }
    public static Vec3 port(HoseBracketBlockEntity bracket, boolean end) {
        return port(bracket.getBlockPos(), bracket.getBlockState().getValue(HoseBracketBlock.FACING),
            bracket.angle(), bracket.reversed, bracket.physicalLength, end);
    }
    public static Placement placement(Snapshot snapshot, List<Binding> bindings, int segment, Vec3 start, Vec3 end) {
        int left = 0, right = snapshot.points.size() - 1;
        for (Binding binding : bindings) {
            if (binding.segment < segment) left = Math.max(left, binding.segment + 1);
            if (binding.segment > segment) right = Math.min(right, binding.segment);
        }
        return new Placement(start, end, snapshot.points.get(left), snapshot.points.get(right),
            snapshot.material(segment) - snapshot.material(left),
            snapshot.material(right) - snapshot.material(segment + 1));
    }
    public static boolean fixed(List<Binding> bindings, int segment) {
        return bindings.stream().anyMatch(binding -> binding.segment == segment);
    }
    /** Retains null slots for degenerate segments so saved binding indices remain stable. */
    public static List<PhysicalHoseGeometry.Segment> indexedSegments(List<Vec3> points) {
        var visible = PhysicalHoseGeometry.segments(points).iterator();
        List<PhysicalHoseGeometry.Segment> result = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < points.size(); i++) {
            double length = points.get(i).distanceTo(points.get(i + 1));
            result.add(length < 1e-6 || !Double.isFinite(length) ? null : visible.next());
        }
        return result;
    }
    public static double hit(Vec3 eye, Vec3 rayEnd, PhysicalHoseGeometry.Segment segment, int width) {
        Vec3 normal = segment.normal(), up = segment.up(), side = up.cross(normal);
        Vec3 a = eye.subtract(segment.center()), b = rayEnd.subtract(segment.center());
        Vec3 localA = new Vec3(a.dot(side), a.dot(up), a.dot(normal));
        Vec3 localB = new Vec3(b.dot(side), b.dot(up), b.dot(normal));
        double radius = width / 32.0;
        return new AABB(-radius, -radius, -radius, radius, radius, radius).clip(localA, localB)
            .map(point -> point.distanceTo(localA)).orElse(Double.POSITIVE_INFINITY);
    }
    private PhysicalHoseAnchors() {}
}
