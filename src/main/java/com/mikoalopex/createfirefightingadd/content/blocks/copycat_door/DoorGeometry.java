package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.createmod.catnip.math.AngleHelper;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorShapes;

/** Model, collision and hinge indicator use the same local frame. */
public final class DoorGeometry {
    public static final float THICKNESS = 3 / 16f;
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    public record Part(AABB bounds, Matrix4f transform) {}

    private DoorGeometry() {}

    static Direction placementHinge(Direction normal, Vec3 offset, Direction playerFacing) {
        Direction best = normal.getAxis().isVertical() ? playerFacing : Direction.DOWN;
        double distance = 1e-6;
        for (Direction side : Direction.values()) {
            if (side.getAxis() == normal.getAxis()) continue;
            double projection = offset.x * side.getStepX() + offset.y * side.getStepY() + offset.z * side.getStepZ();
            if (projection > distance) { distance = projection; best = side; }
        }
        return best;
    }

    public static Matrix4f frame(BlockState state) {
        if (state.getBlock() instanceof ShortSlidingDoorBlock)
            return slidingFrame(state.getValue(DoorBlock.FACING));
        Direction normal = state.getBlock() instanceof MultidirectionalTrapdoorBlock
            ? state.getValue(MultidirectionalTrapdoorBlock.NORMAL) : state.getValue(DoorBlock.FACING);
        int edge = state.getBlock() instanceof MultidirectionalTrapdoorBlock ? state.getValue(MultidirectionalTrapdoorBlock.EDGE) : 0;
        boolean mirror = state.getBlock() instanceof ShortSlidingDoorBlock && state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;
        return frame(normal, edge, mirror);
    }

    static Matrix4f frame(Direction normal, int edge, boolean mirror) {
        Matrix4f matrix = new Matrix4f().translation(.5f, .5f, .5f);
        switch (normal) {
            case SOUTH -> matrix.rotateY((float) Math.PI);
            case WEST -> matrix.rotateY((float) Math.PI / 2);
            case EAST -> matrix.rotateY(-(float) Math.PI / 2);
            case UP -> matrix.rotateX((float) Math.PI / 2);
            case DOWN -> matrix.rotateX(-(float) Math.PI / 2);
            default -> { }
        }
        matrix.rotateZ(edge * (float) Math.PI / 2);
        matrix.translate(-.5f, -.5f, -.5f);
        if (mirror) matrix.translate(1, 0, 0).scale(-1, 1, 1);
        return matrix;
    }

    public static Direction hingeSide(BlockState state) {
        Vector3f vector = frame(state).transformDirection(new Vector3f(-1, 0, 0));
        return Direction.getNearest(vector.x, vector.y, vector.z);
    }

    public static BlockState orient(BlockState state, Direction normal, Direction hinge) {
        for (int edge = 0; edge < 4; edge++) {
            BlockState candidate = state.setValue(MultidirectionalTrapdoorBlock.NORMAL, normal)
                .setValue(MultidirectionalTrapdoorBlock.EDGE, edge);
            if (hingeSide(candidate) == hinge) return candidate;
        }
        return state;
    }

    public static List<Part> parts(BlockState state, float progress) {
        if (state.getBlock() instanceof ShortSlidingDoorBlock sliding)
            return slidingParts(state.getValue(DoorBlock.FACING), state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT,
                sliding.folds, progress);
        return parts(frame(state), progress);
    }

    static List<Part> parts(Matrix4f frame, float progress) {
        float pivot = THICKNESS / 2;
        return List.of(new Part(new AABB(0, 0, 0, 1, 1, THICKNESS), frame.translate(pivot, 0, pivot)
            .rotateY(-progress * (float) Math.PI / 2).translate(-pivot, 0, -pivot)));
    }

    static Matrix4f slidingFrame(Direction facing) {
        return new Matrix4f().translation(.5f, .5f, .5f)
            .rotateY((float) Math.toRadians(AngleHelper.horizontalAngle(facing.getClockWise())))
            .translate(-.5f, -.5f, -.5f);
    }

    // Door models run along X; Create's door partials run along Z.
    private static Matrix4f sourceFrame() {
        return new Matrix4f().m00(0).m02(1).m20(1).m22(0);
    }

    static List<Part> slidingParts(Direction facing, boolean flip, boolean folds, float progress) {
        float outward = Math.min(1, progress * 10) / 32f;
        Matrix4f world = new Matrix4f().translation(facing.getStepX() * outward, 0, facing.getStepZ() * outward);
        if (!folds) {
            Direction movement = flip ? facing.getClockWise() : facing.getCounterClockWise();
            float distance = progress * progress * 13 / 16f;
            world.translate(movement.getStepX() * distance, 0, movement.getStepZ() * distance);
            return List.of(new Part(new AABB(0, 0, 0, 1, 1, THICKNESS), world.mul(slidingFrame(facing)).mul(sourceFrame())));
        }
        world.translate(0, -1 / 512f, 0).mul(slidingFrame(facing));
        float sign = flip ? -1 : 1;
        if (flip) world.translate(0, 0, 1);
        world.rotateY((float) Math.toRadians(91 * sign * progress * progress));
        Matrix4f root = new Matrix4f(world);
        Matrix4f fold = new Matrix4f(world).translate(0, 0, sign / 2)
            .rotateY((float) Math.toRadians(-181 * sign * progress * progress));
        if (flip) { root.translate(0, 0, -.5f); fold.translate(0, 0, -.5f); }
        float rootStart = flip ? .5f : 0;
        float foldStart = flip ? 0 : .5f;
        root.mul(sourceFrame()).translate(-rootStart, 0, 0);
        fold.mul(sourceFrame()).translate(-foldStart, 0, 0);
        return List.of(new Part(new AABB(rootStart, 0, 0, rootStart + .5, 1, THICKNESS), root),
            new Part(new AABB(foldStart, 0, 0, foldStart + .5, 1, THICKNESS), fold));
    }

    public static Vec3[] axis(BlockState state) {
        if (state.getBlock() instanceof ShortSlidingDoorBlock door) {
            Matrix4f frame = slidingFrame(state.getValue(DoorBlock.FACING));
            float hinge = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT ? 1 : 0;
            return door.folds ? new Vec3[] {point(frame, 0, 0, hinge), point(frame, 0, 1, hinge)}
                : new Vec3[] {point(frame, 0, .5, 0), point(frame, 0, .5, 1)};
        }
        float pivot = THICKNESS / 2;
        return new Vec3[] {point(frame(state), pivot, 0, pivot), point(frame(state), pivot, 1, pivot)};
    }

    public static Vec3 point(Matrix4f matrix, double x, double y, double z) {
        Vector3f p = matrix.transformPosition(new Vector3f((float) x, (float) y, (float) z));
        return new Vec3(p.x, p.y, p.z);
    }

    public static VoxelShape shape(BlockState state) {
        if (state.getBlock() instanceof ShortSlidingDoorBlock door) {
            if (state.getValue(CopycatDoorBlock.OPEN))
                return SlidingDoorShapes.get(state.getValue(DoorBlock.FACING), state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT, door.folds);
            return switch (state.getValue(DoorBlock.FACING)) {
                case EAST -> Shapes.box(0, 0, 0, THICKNESS, 1, 1);
                case WEST -> Shapes.box(1 - THICKNESS, 0, 0, 1, 1, 1);
                case SOUTH -> Shapes.box(0, 0, 0, 1, 1, THICKNESS);
                default -> Shapes.box(0, 0, 1 - THICKNESS, 1, 1, 1);
            };
        }
        return SHAPES.computeIfAbsent(state, current -> {
            VoxelShape shape = Shapes.empty();
            for (Part part : parts(current, current.getValue(CopycatDoorBlock.OPEN) ? 1 : 0)) {
                AABB box = part.bounds();
                double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
                double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
                for (int corner = 0; corner < 8; corner++) {
                    Vec3 p = point(part.transform(), (corner & 1) == 0 ? box.minX : box.maxX,
                        (corner & 2) == 0 ? box.minY : box.maxY, (corner & 4) == 0 ? box.minZ : box.maxZ);
                    minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
                    maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
                }
                shape = Shapes.or(shape, Shapes.box(snap(minX), snap(minY), snap(minZ), snap(maxX), snap(maxY), snap(maxZ)));
            }
            return shape;
        });
    }

    private static double snap(double value) { return Math.rint(value * 32) / 32; }
}
