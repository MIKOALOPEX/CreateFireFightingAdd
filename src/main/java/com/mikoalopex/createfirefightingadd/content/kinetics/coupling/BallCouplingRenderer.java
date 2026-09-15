package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import java.util.LinkedHashMap;
import java.util.Map;

public class BallCouplingRenderer extends KineticBlockEntityRenderer<BallCouplingBlockEntity> {
    private static final Map<String, PartialModel> MODELS = new LinkedHashMap<>();
    static {
        for (String state : new String[] {"off", "on"}) {
            add("base_fixed_" + state);
            add("top_0_fixed_" + state);
            for (int i = 0; i < 5; i++) {
                add("top_" + i + "_move_" + state);
                for (String part : new String[] {"hooke/shaft_", "hooke/hand_", "hooke/pin_", "ball/receiver_", "ball/receiver_shaft_"})
                    add(part + i + "_" + state);
            }
        }
    }

    private static void add(String path) {
        MODELS.put(path, PartialModel.of(CreateFireFightingAdd.path("block/ball_coupling/" + path)));
    }

    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        MODELS.values().forEach(model -> event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(model.modelLocation())));
    }

    public BallCouplingRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override protected void renderSafe(BallCouplingBlockEntity be, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(BallCouplingBlock.FACING);
        int length = be.getBlockState().getValue(BallCouplingBlock.LENGTH);
        String state = be.getBlockState().getValue(BallCouplingBlock.POWERED) ? "on" : "off";
        boolean small = be.interfaceMode() == CouplingInterfaceMode.PASSIVE;
        pose.pushPose();
        orient(pose, facing);
        draw((small ? "top_0_fixed_" : "base_fixed_") + state, pose, buffers, light, overlay);
        float phase = getAngleForBe(be, be.getBlockPos(), facing.getAxis()) * facing.getAxisDirection().getStep();
        if (be.interfaceMode() != CouplingInterfaceMode.FREE) {
            if (small) {
                rotateShaft(pose, phase);
                draw("top_" + length + "_move_" + state, pose, buffers, light, overlay);
            } else {
                draw("ball/receiver_" + length + "_" + state, pose, buffers, light, overlay);
                rotateShaft(pose, phase);
                draw("ball/receiver_shaft_" + length + "_" + state, pose, buffers, light, overlay);
            }
            pose.popPose();
            return;
        }
        pose.pushPose();
        rotateShaft(pose, phase);
        draw("hooke/shaft_" + length + "_" + state, pose, buffers, light, overlay);
        pose.popPose();
        pose.popPose();

        Vector3d axis = vector(Vec3.atLowerCornerOf(facing.getNormal()));
        Vector3d ownReference = vector(BallCouplingBlock.orient(new Vec3(1, 0, 0), facing));
        Vector3d pin = new Vector3d(ownReference);
        CouplingJointKinematics.Pins pins = null;
        boolean owner = be.partnerId() != null && be.endpointId().compareTo(be.partnerId()) < 0;
        Vec3 otherAnchor = be.renderPartnerAnchor(), otherNormal = be.renderPartnerNormal();
        if (be.active() && otherAnchor != null && otherNormal != null
                && SableStructureClientCompat.renderTargetAvailable(be.renderPartnerBody())) {
            var transform = SableStructureClientCompat.transformFireHoseTarget(be, be.renderPartnerBody(), vector(otherAnchor), vector(otherNormal));
            Direction otherFacing = Direction.getNearest(otherNormal.x, otherNormal.y, otherNormal.z);
            Vector3d reference = owner ? ownReference : vector(BallCouplingBlock.orient(new Vec3(1, 0, 0), otherFacing));
            if (!owner) {
                transform.partnerOrientation().transform(reference);
                transform.ownerOrientation().conjugate().transform(reference);
            }
            Vector3d a = owner ? axis : transform.partnerNormal();
            Vector3d b = owner ? transform.partnerNormal() : axis;
            // Both halves solve in endpoint A's frame; the opposing shaft has the opposite local sign.
            float jointSpeed = be.getSpeed() * facing.getAxisDirection().getStep() * (owner ? 1 : -1);
            double jointPhase = Math.toRadians(AnimationTickHolder.getRenderTime(be.getLevel()) * jointSpeed * 3.0 / 10.0);
            pins = CouplingJointKinematics.solve(a, b, reference, jointPhase);
            pin = owner ? pins.a() : pins.b();
        }
        Vec3 center = be.localAnchor().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(new Quaternionf(CouplingJointKinematics.frame(pin, axis)));
        pose.translate(-0.5, -(8 + length * 4) / 16.0, -0.5);
        draw("hooke/hand_" + length + "_" + state, pose, buffers, light, overlay);
        if (pins == null) draw("hooke/pin_" + length + "_" + state, pose, buffers, light, overlay);
        pose.popPose();
        if (pins != null && owner) {
            Vector3d normal = new Vector3d(pins.b()).cross(pins.a()).normalize();
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            pose.mulPose(new Quaternionf(CouplingJointKinematics.frame(pins.a(), normal)));
            pose.translate(-0.5, -0.5, -0.5);
            draw("hooke/pin_0_" + state, pose, buffers, light, overlay);
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.translate(-0.5, -0.5, -0.5);
            draw("hooke/pin_0_" + state, pose, buffers, light, overlay);
            pose.popPose();
        }
    }

    private static Vector3d vector(Vec3 p) { return new Vector3d(p.x, p.y, p.z); }

    private static void rotateShaft(PoseStack pose, float phase) {
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotation(phase));
        pose.translate(-0.5, -0.5, -0.5);
    }

    private static void orient(PoseStack pose, Direction facing) {
        var axis = vector(BallCouplingBlock.orient(new Vec3(0, 1, 0), facing));
        var pin = vector(BallCouplingBlock.orient(new Vec3(1, 0, 0), facing));
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(new Quaternionf(CouplingJointKinematics.frame(pin, axis)));
        pose.translate(-0.5, -0.5, -0.5);
    }

    private static void draw(String path, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CachedBuffers.partial(MODELS.get(path), Blocks.AIR.defaultBlockState()).light(light).overlay(overlay)
            .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    @Override public AABB getRenderBoundingBox(BallCouplingBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2);
    }
}
