package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import net.createmod.catnip.outliner.LineOutline;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector4f;

/** Uses native interpolated world-space points without modifying their snapshots or physics. */
final class SmartRopeSelectionOutline extends LineOutline {
    private static final double RADIUS = 3.5 / 16.0;
    private final ClientRopeStrand strand;

    SmartRopeSelectionOutline(ClientRopeStrand strand) {
        this.strand = strand;
    }

    @Override
    protected void renderInner(PoseStack pose, VertexConsumer buffer, Vec3 camera, float partialTick,
            float width, Vector4f color, int light, boolean disableNormals) {
        var points = strand.getPoints();
        if (points.size() < 2) return;
        Vector3d[] previous = null;
        Vector3d tangent = new Vector3d(0, 1, 0);
        for (int i = 0; i < points.size(); i++) {
            Vector3d center = points.get(i).renderPos(partialTick, new Vector3d());
            if (i + 1 < points.size()) {
                Vector3d next = points.get(i + 1).renderPos(partialTick, new Vector3d()).sub(center);
                if (next.lengthSquared() > 1e-8) tangent.set(next).normalize();
            }
            Quaterniond rotation = new Quaterniond().rotationTo(0, 1, 0, tangent.x, tangent.y, tangent.z);
            Vector3d[] ring = new Vector3d[4];
            for (int corner = 0; corner < 4; corner++) {
                double x = corner < 2 ? -RADIUS : RADIUS;
                double z = corner == 0 || corner == 3 ? -RADIUS : RADIUS;
                ring[corner] = rotation.transform(new Vector3d(x, 0, z)).add(center);
            }
            for (int corner = 0; corner < 4; corner++) {
                if (i == 0 || i == points.size() - 1)
                    bufferCuboidLine(pose, buffer, camera, ring[corner], ring[(corner + 1) % 4],
                        width, color, light, disableNormals);
                if (previous != null && previous[corner].distanceSquared(ring[corner]) > 1e-8)
                    bufferCuboidLine(pose, buffer, camera, previous[corner], ring[corner],
                        width, color, light, disableNormals);
            }
            previous = ring;
        }
    }
}
