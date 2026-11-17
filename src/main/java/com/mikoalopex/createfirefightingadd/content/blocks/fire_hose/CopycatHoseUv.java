package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Maps one baked copycat material face onto the hose's across/along axes.
 *
 * <p>The material face keeps its own UV orientation. Model face rotations and
 * mirrored UV rectangles are recovered from the baked vertex/UV pairs instead
 * of being flattened into a min/max atlas rectangle. The tube's across axis
 * always advances the face's default texture U, and its length always advances
 * the face's default texture V, so all four tube faces share one convention.
 * The across extent is retained so a partial material face still uses the same
 * texels-per-block density as the block it was copied from.</p>
 */
final class CopycatHoseUv {
    private static final double EPSILON = 1e-6;

    record FaceUv(float originU, float originV, float uAcross, float vAcross,
                  float uAlong, float vAlong, float acrossExtent) {
        float u(float across, float along) {
            return originU + uAcross * across + uAlong * along;
        }

        float v(float across, float along) {
            return originV + vAcross * across + vAlong * along;
        }
    }

    static FaceUv of(BakedQuad quad, Direction side) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        double minAcross = Double.POSITIVE_INFINITY;
        double maxAcross = Double.NEGATIVE_INFINITY;
        double minAlong = Double.POSITIVE_INFINITY;
        double maxAlong = Double.NEGATIVE_INFINITY;
        double[] across = new double[4];
        double[] along = new double[4];
        for (int i = 0; i < 4; i++) {
            int offset = i * stride;
            double x = Float.intBitsToFloat(vertices[offset]);
            double y = Float.intBitsToFloat(vertices[offset + 1]);
            double z = Float.intBitsToFloat(vertices[offset + 2]);
            double a = defaultAcross(side, x, y, z);
            double t = defaultAlong(side, y, z);
            across[i] = a;
            along[i] = t;
            minAcross = Math.min(minAcross, a);
            maxAcross = Math.max(maxAcross, a);
            minAlong = Math.min(minAlong, t);
            maxAlong = Math.max(maxAlong, t);
        }
        double acrossExtent = maxAcross - minAcross;
        double alongExtent = maxAlong - minAlong;
        if (acrossExtent < EPSILON || alongExtent < EPSILON)
            return region(vertices);
        for (int i = 0; i < 4; i++) {
            across[i] = (across[i] - minAcross) / acrossExtent;
            along[i] = (along[i] - minAlong) / alongExtent;
        }
        return affine(vertices, across, along, (float) acrossExtent);
    }

    static FaceUv sprite(TextureAtlasSprite sprite) {
        return new FaceUv(sprite.getU0(), sprite.getV0(),
            sprite.getU1() - sprite.getU0(), 0f,
            0f, sprite.getV1() - sprite.getV0(), 1f);
    }

    /** Native width density: the tube shows width/16 of the face at the block's own scale. */
    static float across(float across, int tubeWidth, float acrossExtent) {
        float span = (tubeWidth / 16f) / Math.max(acrossExtent, (float) EPSILON);
        return 0.5f + (across - 0.5f) * Math.clamp(span, 0f, 1f);
    }

    private static FaceUv affine(int[] vertices, double[] across, double[] along,
            float acrossExtent) {
        int stride = vertices.length / 4;
        double da1 = across[1] - across[0];
        double dt1 = along[1] - along[0];
        double da3 = across[3] - across[0];
        double dt3 = along[3] - along[0];
        double determinant = da1 * dt3 - dt1 * da3;
        if (Math.abs(determinant) < EPSILON)
            return region(vertices);
        double du1 = u(vertices, stride, 1) - u(vertices, stride, 0);
        double du3 = u(vertices, stride, 3) - u(vertices, stride, 0);
        double dv1 = v(vertices, stride, 1) - v(vertices, stride, 0);
        double dv3 = v(vertices, stride, 3) - v(vertices, stride, 0);
        float uAcross = (float) ((du1 * dt3 - du3 * dt1) / determinant);
        float uAlong = (float) ((da1 * du3 - da3 * du1) / determinant);
        float vAcross = (float) ((dv1 * dt3 - dv3 * dt1) / determinant);
        float vAlong = (float) ((da1 * dv3 - da3 * dv1) / determinant);
        float originU = (float) (u(vertices, stride, 0) - uAcross * across[0] - uAlong * along[0]);
        float originV = (float) (v(vertices, stride, 0) - vAcross * across[0] - vAlong * along[0]);
        return new FaceUv(originU, originV, uAcross, vAcross, uAlong, vAlong, acrossExtent);
    }

    private static FaceUv region(int[] vertices) {
        int stride = vertices.length / 4;
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float u = Float.intBitsToFloat(vertices[i * stride + 4]);
            float v = Float.intBitsToFloat(vertices[i * stride + 5]);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        return new FaceUv(minU, minV, maxU - minU, 0f, 0f, maxV - minV, 1f);
    }

    /**
     * Default cube-face UV axes for a tube side. These are the axes the vanilla
     * baker uses before a model's face rotation is applied, so solving against
     * them keeps the material's appearance and still honours face rotation.
     */
    private static double defaultAcross(Direction side, double x, double y, double z) {
        return switch (side) {
            case UP, DOWN -> x;
            case WEST -> z;
            case EAST -> 1.0 - z;
            default -> throw new IllegalArgumentException("Not a tube side: " + side);
        };
    }

    private static double defaultAlong(Direction side, double y, double z) {
        return switch (side) {
            case UP -> z;
            case DOWN -> 1.0 - z;
            case WEST, EAST -> 1.0 - y;
            default -> throw new IllegalArgumentException("Not a tube side: " + side);
        };
    }

    private static float u(int[] vertices, int stride, int vertex) {
        return Float.intBitsToFloat(vertices[vertex * stride + 4]);
    }

    private static float v(int[] vertices, int stride, int vertex) {
        return Float.intBitsToFloat(vertices[vertex * stride + 5]);
    }

    private CopycatHoseUv() {
    }
}
