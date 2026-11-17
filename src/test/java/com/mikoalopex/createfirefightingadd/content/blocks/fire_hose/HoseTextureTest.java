package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class HoseTextureTest {
    private static final float WIDTH = 8f;

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
        checkRenderedTransition();
        checkAttachedRuns();
        checkCurvedPhases();
        System.out.println("Ordinary hose texture tiling, surface alignment and spline geometry passed");
    }

    private static void checkRenderedTransition() {
        var points = FireHoseDynamicRenderer.attachedSpline(Vec3.ZERO, new Vec3(0, -0.025, 0),
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0), new Vec3(0, 0, -1), true);
        Capture output = new Capture();
        float[] phases = new float[4];
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        for (int i = 0; i + 1 < points.size(); i++) {
            var a = points.get(i);
            var b = points.get(i + 1);
            FireHoseDynamicRenderer.renderSurfaceSegment(pose, a.normal(), b.normal(),
                a.up(), b.up(), a.point(), b.point(), phases, 0, -1, output.sink, 8, 8);
            assertAligned(phases);
        }
        output.checkMeanDensity();
    }

    private static void checkAttachedRuns() {
        var points = FireHoseDynamicRenderer.attachedSpline(Vec3.ZERO, new Vec3(2.4, 1.2, 0),
            new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0));
        var sink = sink();
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        float[] phases = new float[4];
        for (int i = 0; i + 1 < points.size(); i++) {
            var a = points.get(i);
            var b = points.get(i + 1);
            float before = phases[0];
            FireHoseDynamicRenderer.renderSurfaceSegment(pose, a.normal(), b.normal(),
                a.up(), b.up(), a.point(), b.point(), phases, 0, -1, sink, WIDTH, WIDTH);
            assertAligned(phases);
            close(phases[0] - before, faceLengths(a, b, WIDTH));
        }
        float[] first = phases.clone();
        FireHoseDynamicRenderer.renderAttachedSpline(pose, sink, points, 0, phases);
        for (int face = 0; face < 4; face++)
            close(phases[face], first[face] * 2);
    }

    private static void checkCurvedPhases() {
        checkCurve("straight", new Vec3(0, 0, 0), new Vec3(0, 0, 3),
            new Vec3(0, 0, -1), new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(0, 1, 0));
        checkCurve("ninety", new Vec3(0, 0, 0), new Vec3(2, 0, 2),
            new Vec3(0, 0, -1), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0));
        checkCurve("tight", new Vec3(0, 0, 0), new Vec3(0.3, 0.3, 0.3),
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0), new Vec3(0, 0, -1));
        checkCurve("twist", new Vec3(0, 0, 0), new Vec3(2.5, 0.4, 2),
            new Vec3(0, 0, -1), new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 0, 0));
    }

    private static void checkCurve(String name, Vec3 start, Vec3 end, Vec3 startNormal, Vec3 endNormal,
            Vec3 startUp, Vec3 endUp) {
        var points = FireHoseDynamicRenderer.attachedSpline(start, end, startNormal, endNormal, startUp, endUp);
        if (points.size() < 2)
            throw new AssertionError("Curve produced no surface: " + name);
        var sink = sink();
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        float[] phases = new float[4];
        for (int i = 0; i + 1 < points.size(); i++) {
            var a = points.get(i);
            var b = points.get(i + 1);
            float before = phases[0];
            FireHoseDynamicRenderer.renderSurfaceSegment(pose, a.normal(), b.normal(),
                a.up(), b.up(), a.point(), b.point(), phases, 0, -1, sink, WIDTH, WIDTH);
            assertAligned(phases);
            close(phases[0] - before, faceLengths(a, b, WIDTH));
        }
    }

    private static void assertAligned(float[] phases) {
        for (int face = 1; face < 4; face++)
            if (Math.abs(phases[face] - phases[0]) > 1e-5f)
                throw new AssertionError("Surface phases drifted apart: " + java.util.Arrays.toString(phases));
    }

    /** Mean of the four face lengths, the renderer's shared longitudinal step. */
    private static double faceLengths(FireHoseDynamicRenderer.AttachedPoint a,
            FireHoseDynamicRenderer.AttachedPoint b, float width) {
        double scale = width / 16.0 / 2.0;
        Vector3d leftA = new Vector3d(a.up()).cross(a.normal()).normalize().mul(scale);
        Vector3d leftB = new Vector3d(b.up()).cross(b.normal()).normalize().mul(scale);
        Vector3d upA = new Vector3d(a.up()).mul(scale);
        Vector3d upB = new Vector3d(b.up()).mul(scale);
        Vector3d start = new Vector3d(a.point());
        Vector3d end = new Vector3d(b.point());
        double down = (faceLength(start, leftA, upA, end, leftB, upB, 1, -1)
            + faceLength(start, leftA, upA, end, leftB, upB, -1, -1)) / 2;
        double up = (faceLength(start, leftA, upA, end, leftB, upB, -1, 1)
            + faceLength(start, leftA, upA, end, leftB, upB, 1, 1)) / 2;
        double west = (faceLength(start, leftA, upA, end, leftB, upB, -1, -1)
            + faceLength(start, leftA, upA, end, leftB, upB, -1, 1)) / 2;
        double east = (faceLength(start, leftA, upA, end, leftB, upB, 1, 1)
            + faceLength(start, leftA, upA, end, leftB, upB, 1, -1)) / 2;
        return (down + up + west + east) / 4;
    }

    private static double faceLength(Vector3d start, Vector3d leftA, Vector3d upA,
            Vector3d end, Vector3d leftB, Vector3d upB, int leftSign, int upSign) {
        return corner(start, leftA, upA, leftSign, upSign)
            .distance(corner(end, leftB, upB, leftSign, upSign));
    }

    private static Vector3d corner(Vector3d point, Vector3d left, Vector3d up, int leftSign, int upSign) {
        return new Vector3d(point)
            .add(new Vector3d(left).mul(leftSign))
            .add(new Vector3d(up).mul(upSign));
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer sink() {
        return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
            HoseTextureTest.class.getClassLoader(), new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
            (proxy, method, args) -> method.getReturnType() == void.class ? null : proxy);
    }

    private static final class Capture {
        final List<Vector3d> positions = new ArrayList<>();
        final List<float[]> uv = new ArrayList<>();
        final com.mojang.blaze3d.vertex.VertexConsumer sink = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
            HoseTextureTest.class.getClassLoader(), new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
            (proxy, method, args) -> {
                if (method.getName().equals("addVertex") && args.length == 4)
                    positions.add(new Vector3d((float) args[1], (float) args[2], (float) args[3]));
                if (method.getName().equals("setUv")) uv.add(new float[]{(float) args[0], (float) args[1]});
                return method.getReturnType() == void.class ? null : proxy;
            });

        /**
         * Each surface must use the shared step, so the four face lengths average
         * to the texture span. Straight transitions have no split, giving exactly
         * one quad per face.
         */
        void checkMeanDensity() {
            if (positions.isEmpty() || positions.size() != uv.size() || positions.size() % 16 != 0)
                throw new AssertionError("Unexpected transition geometry");
            for (int segment = 0; segment < positions.size(); segment += 16) {
                double[] lengths = new double[4];
                float span = -1;
                for (int face = 0; face < 4; face++) {
                    int i = segment + face * 4;
                    lengths[face] = (positions.get(i).distance(positions.get(i + 1))
                        + positions.get(i + 3).distance(positions.get(i + 2))) / 2;
                    float low = uv.get(i)[1];
                    float high = uv.get(i + 1)[1];
                    if (span < 0) span = high - low;
                    close(high - low, span);
                    if (low < 0 || high > 1)
                        throw new AssertionError("Unsplit texture repeat");
                }
                close((lengths[0] + lengths[1] + lengths[2] + lengths[3]) / 4, span);
            }
        }
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
        if (!Double.isFinite(a) || Math.abs(a - b) > 1e-5) throw new AssertionError(a + " != " + b);
    }
}
