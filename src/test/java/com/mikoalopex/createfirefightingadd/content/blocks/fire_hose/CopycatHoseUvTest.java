package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/**
 * Offline regression checks for the copycat material UV bridge. The synthetic
 * quads use non-square, asymmetric UV gradients so a min/max atlas rectangle or
 * a fixed middle-half crop cannot pass.
 */
public final class CopycatHoseUvTest {
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.WEST, Direction.EAST};
    private static final float U0 = 0.10f;
    private static final float U1 = 0.70f;
    private static final float V0 = 0.20f;
    private static final float V1 = 0.80f;

    public static void main(String[] args) {
        checkDefaultCubeOrientation();
        checkRotatedAndFlippedFaces();
        checkAsymmetricAffineFace();
        checkWidthDensity();
        checkPartialFaceDensity();
        checkAlongRepeat();
        checkPortMapping();
        System.out.println("Copycat material UV orientation, width density and repeat checks passed");
    }

    private static void checkDefaultCubeOrientation() {
        for (Direction side : SIDES) {
            BakedQuad quad = defaultQuad(side);
            CopycatHoseUv.FaceUv face = CopycatHoseUv.of(quad, side);
            close(face.acrossExtent(), 1f);
            float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            for (int i = 0; i < 4; i++) {
                int stride = quad.getVertices().length / 4;
                float expectedU = Float.intBitsToFloat(quad.getVertices()[i * stride + 4]);
                float expectedV = Float.intBitsToFloat(quad.getVertices()[i * stride + 5]);
                close(face.u(corners[i][0], corners[i][1]), expectedU);
                close(face.v(corners[i][0], corners[i][1]), expectedV);
            }
            for (float p : new float[]{0.1f, 0.25f, 0.5f, 0.75f, 0.9f}) {
                close(face.u(p, 0.3f), U0 + (U1 - U0) * p);
                close(face.v(p, 0.3f), V0 + (V1 - V0) * 0.3f);
            }
        }
    }

    private static void checkRotatedAndFlippedFaces() {
        // U follows the face's V axis and V runs backwards along its U axis.
        for (Direction side : SIDES) {
            float[] us = new float[4];
            float[] vs = new float[4];
            float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            for (int i = 0; i < 4; i++) {
                us[i] = U0 + (U1 - U0) * corners[i][1];
                vs[i] = V0 + (V1 - V0) * (1 - corners[i][0]);
            }
            CopycatHoseUv.FaceUv face = CopycatHoseUv.of(quad(side, us, vs), side);
            for (float p : new float[]{0.15f, 0.4f, 0.65f, 0.9f}) {
                for (float q : new float[]{0.2f, 0.55f, 0.85f}) {
                    close(face.u(p, q), U0 + (U1 - U0) * q);
                    close(face.v(p, q), V0 + (V1 - V0) * (1 - p));
                }
            }
        }
    }

    private static void checkAsymmetricAffineFace() {
        // A skewed, non-square UV gradient in block space. It cannot be rebuilt
        // from the atlas bounding box alone.
        for (Direction side : SIDES) {
            float[][] positions = corners(side);
            float[] us = new float[4];
            float[] vs = new float[4];
            for (int i = 0; i < 4; i++) {
                us[i] = blockU(positions[i][0], positions[i][2]);
                vs[i] = blockV(positions[i][1], positions[i][2]);
            }
            CopycatHoseUv.FaceUv face = CopycatHoseUv.of(quad(side, us, vs), side);
            for (float p : new float[]{0.05f, 0.3f, 0.5f, 0.8f, 0.95f}) {
                for (float q : new float[]{0.05f, 0.3f, 0.5f, 0.8f, 0.95f}) {
                    float[] point = point(side, p, q);
                    close(face.u(p, q), blockU(point[0], point[2]));
                    close(face.v(p, q), blockV(point[1], point[2]));
                }
            }
        }
    }

    private static void checkWidthDensity() {
        for (Direction side : SIDES) {
            CopycatHoseUv.FaceUv face = CopycatHoseUv.of(defaultQuad(side), side);
            for (int width : new int[]{4, 6, 8}) {
                float start = CopycatHoseUv.across(0f, width, face.acrossExtent());
                float end = CopycatHoseUv.across(1f, width, face.acrossExtent());
                close(start, 0.5f - width / 32f);
                close(end, 0.5f + width / 32f);
                close(end - start, width / 16f);
                // The emitted atlas span must scale with the real width, never
                // stay at a fixed half-face.
                float expected = (width / 16f) * (U1 - U0);
                close(Math.abs(face.u(end, 0.4f) - face.u(start, 0.4f)), expected);
            }
            // The same density rule must survive a rotated face, where the
            // width now advances the material's V axis.
            float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            float[] us = new float[4];
            float[] vs = new float[4];
            for (int i = 0; i < 4; i++) {
                us[i] = U0 + (U1 - U0) * corners[i][1];
                vs[i] = V0 + (V1 - V0) * (1 - corners[i][0]);
            }
            CopycatHoseUv.FaceUv rotated = CopycatHoseUv.of(quad(side, us, vs), side);
            for (int width : new int[]{4, 6, 8}) {
                float start = CopycatHoseUv.across(0f, width, rotated.acrossExtent());
                float end = CopycatHoseUv.across(1f, width, rotated.acrossExtent());
                close(Math.abs(rotated.u(end, 0.4f) - rotated.u(start, 0.4f)), 0f);
                close(Math.abs(rotated.v(end, 0.4f) - rotated.v(start, 0.4f)),
                    (width / 16f) * (V1 - V0));
            }
        }
    }

    private static void checkPartialFaceDensity() {
        // A material face that is only half a block wide along its texture U.
        float[][] positions = {
            {0, 1, 0.25f}, {0, 0, 0.25f}, {0, 0, 0.75f}, {0, 1, 0.75f}
        };
        BakedQuad quad = quad(Direction.WEST, new float[]{U0, U0, U1, U1}, new float[]{V0, V1, V1, V0},
            positions);
        CopycatHoseUv.FaceUv face = CopycatHoseUv.of(quad, Direction.WEST);
        close(face.acrossExtent(), 0.5f);
        for (int width : new int[]{4, 6, 8}) {
            float start = CopycatHoseUv.across(0f, width, face.acrossExtent());
            float end = CopycatHoseUv.across(1f, width, face.acrossExtent());
            // Material texels per drawn block stay identical to the source face.
            close((end - start) * face.acrossExtent(), width / 16f);
        }
    }

    private static void checkAlongRepeat() {
        for (Direction side : SIDES) {
            CopycatHoseUv.FaceUv face = CopycatHoseUv.of(defaultQuad(side), side);
            float base = face.v(0.5f, 0f);
            float full = face.v(0.5f, 1f) - base;
            close(full, V1 - V0);
            for (int width : new int[]{4, 6, 8}) {
                // Length repetition is independent of the drawn width.
                float p = CopycatHoseUv.across(0.3f, width, face.acrossExtent());
                close(face.v(p, 1f) - face.v(p, 0f), full);
            }
            // A rotated face advances its U axis along the hose, but a full
            // material face still covers one repeat per unit of drawn length.
            float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            float[] us = new float[4];
            float[] vs = new float[4];
            for (int i = 0; i < 4; i++) {
                us[i] = U0 + (U1 - U0) * corners[i][1];
                vs[i] = V0 + (V1 - V0) * (1 - corners[i][0]);
            }
            CopycatHoseUv.FaceUv rotated = CopycatHoseUv.of(quad(side, us, vs), side);
            close(rotated.u(0.4f, 1f) - rotated.u(0.4f, 0f), U1 - U0);
            close(rotated.v(0.4f, 1f) - rotated.v(0.4f, 0f), 0f);
        }
    }

    private static void checkPortMapping() {
        for (Direction side : SIDES) {
            float[][] positions = corners(side);
            float[] us = new float[4];
            float[] vs = new float[4];
            for (int i = 0; i < 4; i++) {
                us[i] = blockU(positions[i][0], positions[i][2]);
                vs[i] = blockV(positions[i][1], positions[i][2]);
            }
            BakedQuad material = quad(side, us, vs);
            BakedQuad template = templateQuad(side);
            BakedQuad mapped = CopycatFireHoseTemplateRendering.map(template, material);
            int stride = mapped.getVertices().length / 4;
            for (int i = 0; i < 4; i++) {
                float x = Float.intBitsToFloat(mapped.getVertices()[i * stride]);
                float y = Float.intBitsToFloat(mapped.getVertices()[i * stride + 1]);
                float z = Float.intBitsToFloat(mapped.getVertices()[i * stride + 2]);
                close(Float.intBitsToFloat(mapped.getVertices()[i * stride + 4]), blockU(x, z));
                close(Float.intBitsToFloat(mapped.getVertices()[i * stride + 5]), blockV(y, z));
            }

            // Swapped U/V: the port transfer must also preserve a rotated face.
            float[] rotatedUs = new float[4];
            float[] rotatedVs = new float[4];
            for (int i = 0; i < 4; i++) {
                rotatedUs[i] = blockV(positions[i][1], positions[i][2]);
                rotatedVs[i] = blockU(positions[i][0], positions[i][2]);
            }
            BakedQuad rotated = CopycatFireHoseTemplateRendering.map(template, quad(side, rotatedUs, rotatedVs));
            for (int i = 0; i < 4; i++) {
                float x = Float.intBitsToFloat(rotated.getVertices()[i * stride]);
                float y = Float.intBitsToFloat(rotated.getVertices()[i * stride + 1]);
                float z = Float.intBitsToFloat(rotated.getVertices()[i * stride + 2]);
                close(Float.intBitsToFloat(rotated.getVertices()[i * stride + 4]), blockV(y, z));
                close(Float.intBitsToFloat(rotated.getVertices()[i * stride + 5]), blockU(x, z));
            }
        }
    }

    private static BakedQuad defaultQuad(Direction side) {
        return quad(side, new float[]{U0, U0, U1, U1}, new float[]{V0, V1, V1, V0});
    }

    private static BakedQuad templateQuad(Direction side) {
        float[][] canonical = {{0.3f, 0.3f}, {0.3f, 0.7f}, {0.7f, 0.7f}, {0.7f, 0.3f}};
        float[][] positions = new float[4][];
        for (int i = 0; i < 4; i++)
            positions[i] = point(side, canonical[i][0], canonical[i][1]);
        return quad(side, new float[4], new float[4], positions);
    }

    private static BakedQuad quad(Direction side, float[] us, float[] vs) {
        return quad(side, us, vs, corners(side));
    }

    private static BakedQuad quad(Direction side, float[] us, float[] vs, float[][] positions) {
        int[] vertices = new int[32];
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            vertices[offset] = Float.floatToRawIntBits(positions[i][0]);
            vertices[offset + 1] = Float.floatToRawIntBits(positions[i][1]);
            vertices[offset + 2] = Float.floatToRawIntBits(positions[i][2]);
            vertices[offset + 3] = -1;
            vertices[offset + 4] = Float.floatToRawIntBits(us[i]);
            vertices[offset + 5] = Float.floatToRawIntBits(vs[i]);
        }
        return new BakedQuad(vertices, -1, side, null, true);
    }

    /** Vanilla FaceInfo vertex order for the four tube sides. */
    private static float[][] corners(Direction side) {
        return switch (side) {
            case DOWN -> new float[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
            case UP -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case WEST -> new float[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
            case EAST -> new float[][]{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}};
            default -> throw new IllegalArgumentException("Not a tube side: " + side);
        };
    }

    /** Block-space point for a default cube face's (U, V) parameters. */
    private static float[] point(Direction side, float p, float q) {
        return switch (side) {
            case UP -> new float[]{p, 1, q};
            case DOWN -> new float[]{p, 0, 1 - q};
            case WEST -> new float[]{0, 1 - q, p};
            case EAST -> new float[]{1, 1 - q, 1 - p};
            default -> throw new IllegalArgumentException("Not a tube side: " + side);
        };
    }

    private static float blockU(float x, float z) {
        return 0.05f + 0.30f * x + 0.10f * z;
    }

    private static float blockV(float y, float z) {
        return 0.15f + 0.25f * y + 0.20f * z;
    }

    private static void close(float actual, float expected) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 1e-5f)
            throw new AssertionError(actual + " != " + expected);
    }
}
