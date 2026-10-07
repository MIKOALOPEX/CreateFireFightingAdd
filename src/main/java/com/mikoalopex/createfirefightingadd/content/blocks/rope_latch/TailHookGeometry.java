package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared model-marker geometry for rendering, capture bounds and the physical rope attachment. */
final class TailHookGeometry {
    static final Vec3 BASE_PITCH = new Vec3(8, 3.5, 8).scale(1.0 / 16);
    static final Vec3 ROD_PITCH = new Vec3(8, 2, -14).scale(1.0 / 16);
    private static final Vec3 ROD_ANCHOR = new Vec3(8, 3.75, 29.5).scale(1.0 / 16);

    private TailHookGeometry() {}

    static Quaternionf rotation(BlockState state) {
        // The supplied tailhook extends along Z; the rope hook's opening is aligned with X.
        return RopeHookOrientation.rotation(state).rotateY((float) Math.PI / 2);
    }

    static RopeCaptureArea captureArea(RopeLatchBlockEntity be, float angle) {
        Vec3 block = Vec3.atLowerCornerOf(be.getBlockPos());
        var rotation = rotation(be.getBlockState());
        var pivotOffset = rotation.transform(new Vector3f((float) BASE_PITCH.x - .5f,
            (float) BASE_PITCH.y - .5f, (float) BASE_PITCH.z - .5f));
        Vec3 pivot = block.add(.5 + pivotOffset.x, .5 + pivotOffset.y, .5 + pivotOffset.z);
        Vec3 tip = block.add(anchor(be.getBlockState(), angle));
        Vec3 worldPivot = SableStructureCompat.transformPositionToWorld(be, pivot);
        Vec3 worldTip = SableStructureCompat.transformPositionToWorld(be, tip);
        var localRight = rotation.transform(new Vector3f(1, 0, 0));
        Vec3 right = SableStructureCompat.transformPositionToWorld(be,
            pivot.add(localRight.x, localRight.y, localRight.z)).subtract(worldPivot).normalize();
        double radians = Math.toRadians(angle);
        var localForward = rotation.transform(new Vector3f(0, (float) Math.sin(radians), (float) Math.cos(radians)));
        Vec3 forward = SableStructureCompat.transformPositionToWorld(be,
            pivot.add(localForward.x, localForward.y, localForward.z)).subtract(worldPivot).normalize();
        Vec3 up = forward.cross(right).normalize();
        return RopeCaptureArea.rod(worldPivot.add(worldTip).scale(.5), right, up, forward);
    }

    static Vec3 anchor(BlockState state, float angle) {
        Vec3 offset = ROD_ANCHOR.subtract(ROD_PITCH);
        double radians = Math.toRadians(-angle), sin = Math.sin(radians), cos = Math.cos(radians);
        Vec3 local = BASE_PITCH.add(offset.x, offset.y * cos - offset.z * sin,
            offset.y * sin + offset.z * cos).subtract(0.5, 0.5, 0.5);
        Vector3f rotated = rotation(state)
            .transform(new Vector3f((float) local.x, (float) local.y, (float) local.z));
        return new Vec3(rotated.x + 0.5, rotated.y + 0.5, rotated.z + 0.5);
    }
}
