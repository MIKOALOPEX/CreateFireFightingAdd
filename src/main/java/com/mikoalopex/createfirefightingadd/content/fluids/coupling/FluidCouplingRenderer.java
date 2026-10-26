package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import java.util.LinkedHashMap;
import java.util.Map;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseRenderer;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.CouplingJointKinematics;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
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

public final class FluidCouplingRenderer extends SmartBlockEntityRenderer<FluidCouplingBlockEntity> {
    private static final Map<String, PartialModel> MODELS = new LinkedHashMap<>();
    static {
        for (String name : new String[] {"base", "base_item", "ring", "boom_nozzle", "boom_nozzle_cog", "boom_nozzle_item",
                "receptacle_fixed", "receptacle_pitch", "receptacle_gear",
                "receptacle_fixed_powered", "receptacle_pitch_powered", "receptacle_gear_powered",
                "hose_probe", "hose_drogue_fixed"})
            MODELS.put(name, PartialModel.of(CreateFireFightingAdd.path("block/fluid_coupling/" + name)));
        for (var part : HoseDrogueParts.PARTS)
            MODELS.put(part.model(), PartialModel.of(CreateFireFightingAdd.path("block/fluid_coupling/" + part.model())));
    }
    private final FireHoseRenderer hose;
    public FluidCouplingRenderer(BlockEntityRendererProvider.Context context) { super(context); hose = new FireHoseRenderer(context); }
    public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        MODELS.values().forEach(model -> event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(model.modelLocation())));
    }
    @Override protected void renderSafe(FluidCouplingBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        Vec3 normal = Vec3.atLowerCornerOf(be.facing().getNormal());
        Vec3 edge = FluidCouplingGeometry.orient(new Vec3(1, 0, 0), be.facing());
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(new Quaternionf(CouplingJointKinematics.frame(vector(edge), vector(normal))));
        pose.translate(-0.5, -0.5, -0.5);
        switch (be.settings().type()) {
            case FREE -> draw(be.active() ? "base" : "base_item", pose, buffers, light, overlay);
            case RECEPTACLE -> {
                String power = be.getBlockState().getValue(FluidCouplingBlock.POWERED) ? "_powered" : "";
                draw("receptacle_fixed" + power, pose, buffers, light, overlay);
                pose.pushPose();
                pose.translate(0.5, 0.5, 0.5); pose.mulPose(Axis.YP.rotationDegrees(be.gearPhase(partial))); pose.translate(-0.5, -0.5, -0.5);
                draw("receptacle_gear" + power, pose, buffers, light, overlay); pose.popPose();
                pose.translate(0.5, 11 / 16f, 0.5); pose.mulPose(Axis.XP.rotationDegrees(be.pitch(partial))); pose.translate(-0.5, -11 / 16f, -0.5);
                draw("receptacle_pitch" + power, pose, buffers, light, overlay);
            }
            case BOOM_NOZZLE -> {
                pose.translate(0.5, 0.5, 0.5); pose.mulPose(Axis.XP.rotationDegrees(-90)); pose.translate(-0.5, -0.5, -0.5);
                draw("boom_nozzle", pose, buffers, light, overlay);
                pose.translate(0.5, 0.5, 10 / 16f);
                pose.mulPose(Axis.ZP.rotationDegrees(be.gearPhase(partial)));
                pose.translate(-0.5, -0.5, -10 / 16f);
                draw("boom_nozzle_cog", pose, buffers, light, overlay);
            }
            case HOSE_PROBE, HOSE_DROGUE -> {
                pose.translate(0.5, 0.5, 0.5); pose.mulPose(Axis.ZP.rotationDegrees(-90)); pose.translate(-0.5, -0.5, -0.5);
                if (be.settings().type() == FluidCouplingInterfaces.HOSE_PROBE) {
                    draw("hose_probe", pose, buffers, light, overlay);
                } else {
                    draw("hose_drogue_fixed", pose, buffers, light, overlay);
                    float progress = be.deployment(partial);
                    for (var part : HoseDrogueParts.PARTS) {
                        pose.pushPose();
                        pose.translate(part.px() / 16, part.py() / 16, part.pz() / 16);
                        Axis axis = switch (part.axis()) { case 'x' -> Axis.XP; case 'z' -> Axis.ZP; default -> Axis.YP; };
                        pose.mulPose(axis.rotationDegrees(part.angle() * progress));
                        pose.translate(-part.px() / 16, -part.py() / 16, -part.pz() / 16);
                        pose.translate(part.dx() * progress / 16, part.dy() * progress / 16, part.dz() * progress / 16);
                        draw(part.model(), pose, buffers, light, overlay);
                        pose.popPose();
                    }
                }
            }
        }
        pose.popPose();
        if (be.settings().type() != FluidCouplingInterfaces.FREE || !be.active() || be.partnerId() == null
                || be.renderPartnerAnchor() == null || be.renderPartnerNormal() == null
                || !SableStructureClientCompat.renderTargetAvailable(be.renderPartnerBody())) return;
        var transform = SableStructureClientCompat.transformFireHoseTarget(be, be.renderPartnerBody(),
            vector(be.renderPartnerAnchor()), vector(be.renderPartnerNormal()));
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 ownAnchor = be.localAnchor().subtract(origin);
        Vec3 otherAnchor = vec(transform.partnerCenter()).subtract(origin);
        Vec3 otherNormal = vec(transform.partnerNormal());
        boolean owner = be.endpointId().compareTo(be.partnerId()) < 0;
        var ring = owner ? FluidCouplingGeometry.ring(ownAnchor, otherAnchor, normal, otherNormal)
            : FluidCouplingGeometry.ring(otherAnchor, ownAnchor, otherNormal, normal);
        Vec3 reference = edge;
        if (!owner) {
            Direction facing = Direction.getNearest(be.renderPartnerNormal().x, be.renderPartnerNormal().y, be.renderPartnerNormal().z);
            Vector3d r = vector(FluidCouplingGeometry.orient(new Vec3(1, 0, 0), facing));
            transform.partnerOrientation().transform(r); transform.ownerOrientation().conjugate().transform(r);
            reference = vec(r);
        }
        if (reference.cross(ring.axis()).lengthSqr() < 1e-8)
            reference = Math.abs(ring.axis().y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        var frame = CouplingJointKinematics.frame(vector(reference), vector(ring.axis()));
        Vec3 ringEdge = vec(frame.transform(new Vector3d(1, 0, 0)));
        Vec3 outlet = FluidCouplingGeometry.modelPoint(FluidCouplingGeometry.BASE_OUTLET, be.facing());
        hose.renderConnection(pose, buffers, light, outlet, owner ? ring.firstFace() : ring.secondFace(),
            normal, ring.axis().scale(owner ? -1 : 1), edge, ringEdge);
        if (owner) {
            pose.pushPose(); pose.translate(ring.center().x, ring.center().y, ring.center().z);
            pose.mulPose(new Quaternionf(frame)); pose.translate(-0.5, -10 / 16f, -0.5);
            draw("ring", pose, buffers, light, overlay); pose.popPose();
        }
    }
    private static void draw(String model, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CachedBuffers.partial(MODELS.get(model), Blocks.AIR.defaultBlockState()).light(light).overlay(overlay)
            .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
    }
    private static Vector3d vector(Vec3 v) { return new Vector3d(v.x, v.y, v.z); }
    private static Vec3 vec(Vector3d v) { return new Vec3(v.x, v.y, v.z); }
    @Override public AABB getRenderBoundingBox(FluidCouplingBlockEntity be) { return be.getRenderBoundingBox(); }
}
