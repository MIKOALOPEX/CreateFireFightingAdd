package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayList;
import java.util.List;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseGeometry;
import net.minecraft.world.phys.Vec3;

public final class HoseTextureTest {
    public static void main(String[] args) {
        checkSpan(0, 0.3, 1);
        checkSpan(0, 1, 1);
        checkSpan(0, 2.25, 3);
        checkSpan(0.875, 1.125, 2);
        checkSpan(-0.2, 0.2, 2);
        checkSpan(1 - 1e-10, 1.3, 1);
        for (int width : new int[] {4, 6, 8}) {
            close((HoseTexture.u0(width) + HoseTexture.u1(width)) / 2, 0.25);
            close(HoseTexture.u1(width) - HoseTexture.u0(width), width / 16.0);
        }
        List<Vec3> points = List.of(Vec3.ZERO, new Vec3(0, 0, 0.2), new Vec3(0, 0, 1.2),
            new Vec3(0, 1, 1.2), new Vec3(0, 0, 1.2));
        var segments = PhysicalHoseGeometry.segments(points);
        close(segments.getFirst().middleLength(), 0.15);
        close(segments.get(1).middleLength(), 0.75);
        for (var segment : segments) {
            close(segment.normal().dot(segment.up()), 0);
            close(segment.up().length(), 1);
            close(segment.midStart().distanceTo(segment.midEnd()), segment.middleLength());
            if (segment.middleLength() > segment.length()) throw new AssertionError("Visual exceeds native segment");
        }
        if (points.size() != 5 || points.get(1).z != 0.2) throw new AssertionError("Physics points mutated");
        checkHeadAssignment();
        checkRenderedTransition();
        checkHeadFaces();
        System.out.println("Hose texture tiling and physical segment geometry passed");
    }

    private static void checkRenderedTransition() {
        var points = FireHoseDynamicRenderer.attachedSpline(Vec3.ZERO, new Vec3(0, -0.025, 0),
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0), new Vec3(0, 0, -1), true);
        Capture output = new Capture();
        float[] phases = new float[4];
        for (int i = 0; i + 1 < points.size(); i++) {
            var a = points.get(i); var b = points.get(i + 1);
            FireHoseDynamicRenderer.renderSurfaceSegment(new com.mojang.blaze3d.vertex.PoseStack(), a.normal(), b.normal(),
                a.up(), b.up(), a.point(), b.point(), phases, 0, -1, output.sink, 8, 8);
        }
        output.checkDensity();
    }

    private static final class Capture {
        final List<org.joml.Vector3d> positions = new ArrayList<>();
        final List<float[]> uv = new ArrayList<>();
        final com.mojang.blaze3d.vertex.VertexConsumer sink = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
            HoseTextureTest.class.getClassLoader(), new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
            (proxy, method, args) -> {
                if (method.getName().equals("addVertex") && args.length == 4)
                    positions.add(new org.joml.Vector3d((float) args[1], (float) args[2], (float) args[3]));
                if (method.getName().equals("setUv")) uv.add(new float[]{(float) args[0], (float) args[1]});
                return method.getReturnType() == void.class ? null : proxy;
            });
        void checkDensity() {
            if (positions.isEmpty() || positions.size() != uv.size()) throw new AssertionError("Missing hose faces");
            for (int i = 0; i < positions.size(); i += 4) {
                double drawnLength = (positions.get(i).distance(positions.get(i + 1))
                    + positions.get(i + 3).distance(positions.get(i + 2))) / 2;
                double textureLength = uv.get(i + 1)[1] - uv.get(i)[1];
                close(drawnLength, textureLength);
                if (uv.get(i)[1] < 0 || uv.get(i + 1)[1] > 1) throw new AssertionError("Unsplit texture repeat");
            }
        }

        void contains(org.joml.Vector3d corner) {
            if (positions.stream().noneMatch(vertex -> vertex.distance(corner) < 1e-6))
                throw new AssertionError("Transition misses a model corner: " + corner);
        }
    }

    private static void checkHeadAssignment() {
        Vec3 owner = new Vec3(5, 2, -3), partner = new Vec3(9, 2, -3);
        List<Vec3> path = List.of(owner, owner.add(0, -0.2, 0), new Vec3(7, 1, -3), partner.add(0, -1, 0), partner);
        var forward = PhysicalHoseGeometry.segments(path);
        var reverse = PhysicalHoseGeometry.segments(path.reversed());
        if (!PhysicalHoseGeometry.ownerAtFirst(owner, partner, forward)
                || PhysicalHoseGeometry.ownerAtFirst(owner, partner, reverse))
            throw new AssertionError("Head connected across the strand to the remote terminal segment");
        for (int i = 0; i < forward.size(); i++) {
            close(forward.get(i).start().distanceTo(path.get(i)), 0);
            close(forward.get(i).end().distanceTo(path.get(i + 1)), 0);
        }
    }

    private static void checkHeadFaces() {
        Vec3[] normals = {new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0),
            new Vec3(0, -1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)};
        for (int width : new int[]{4, 6, 8})
            for (Vec3 normalA : normals)
                for (Vec3 normalB : normals) {
                    Vec3 upA = Math.abs(normalA.y) == 1 ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0);
                    Vec3 upB = Math.abs(normalB.y) == 1 ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0);
                    Vec3 aligned = PhysicalHoseGeometry.alignedUp(normalA, upA, normalB, upB);
                    var model = new org.joml.Matrix3d(PhysicalHoseGeometry.vector(upB.cross(normalB)),
                        PhysicalHoseGeometry.vector(upB), PhysicalHoseGeometry.vector(normalB));
                    Vec3 mid = normalB.scale(0.025);
                    Capture output = new Capture();
                    FireHoseDynamicRenderer.renderSurfaceSegment(new com.mojang.blaze3d.vertex.PoseStack(),
                        PhysicalHoseGeometry.vector(normalA), PhysicalHoseGeometry.vector(normalB),
                        PhysicalHoseGeometry.vector(upA), PhysicalHoseGeometry.vector(aligned),
                        new org.joml.Vector3d(), PhysicalHoseGeometry.vector(mid), new float[4], 0, -1, output.sink, width, width);
                    output.checkDensity();
                    double radius = width / 32.0;
                    for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
                        var corner = model.transform(new org.joml.Vector3d(x * radius, y * radius, 0));
                        output.contains(corner.add(PhysicalHoseGeometry.vector(mid)));
                        output.contains(PhysicalHoseGeometry.vector(upA.scale(x * radius)
                            .add(upA.cross(normalA).scale(y * radius))));
                    }
                }
        Capture longSpan = new Capture();
        FireHoseDynamicRenderer.renderSurfaceSegment(new com.mojang.blaze3d.vertex.PoseStack(),
            new org.joml.Vector3d(0, 0, 1), new org.joml.Vector3d(0, 0, 1),
            new org.joml.Vector3d(0, 1, 0), new org.joml.Vector3d(0, 1, 0),
            new org.joml.Vector3d(), new org.joml.Vector3d(0, 0, 2.25), new float[4], 0, -1, longSpan.sink, 8, 8);
        longSpan.checkDensity();
        if (longSpan.positions.size() != 48) throw new AssertionError("Expected three texture tiles on four faces");
    }

    private static void checkSpan(double start, double end, int count) {
        List<double[]> slices = new ArrayList<>();
        HoseTexture.split(start, end, (a, b, u, v) -> slices.add(new double[]{a, b, u, v}));
        if (slices.size() != count) throw new AssertionError("Unexpected tile count: " + slices.size());
        double cursor = 0;
        for (double[] slice : slices) {
            close(cursor, slice[0]);
            close((slice[1] - slice[0]) * (end - start), slice[3] - slice[2]);
            if (slice[2] < 0 || slice[3] > 1 || slice[3] < slice[2]) throw new AssertionError("UV crosses atlas boundary");
            cursor = slice[1];
        }
        close(cursor, 1);
    }

    private static void close(double a, double b) {
        if (!Double.isFinite(a) || Math.abs(a - b) > 1e-6) throw new AssertionError(a + " != " + b);
    }
}
