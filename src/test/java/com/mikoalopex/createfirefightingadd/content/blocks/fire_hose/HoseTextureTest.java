package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayList;
import java.util.List;
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
        checkRenderedTransition();
        System.out.println("Ordinary hose texture tiling and spline geometry passed");
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
