package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplingGeometry;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.CouplingJointKinematics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public final class RockerRenderer extends SmartBlockEntityRenderer<RockerBlockEntity> {
    private static final PartialModel SHAFT = model("shaft");
    private static final PartialModel AXIS = model("axis");
    private static final PartialModel[] SLIDERS = {model("slider_1"),model("slider_1.5"),model("slider_2"),model("slider_2.5"),model("slider_3"),model("slider_3.5"),model("slider_4")};
    private static PartialModel model(String name) { return PartialModel.of(CreateFireFightingAdd.path("block/rocker/"+name)); }
    public RockerRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(SHAFT.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(AXIS.modelLocation()));
        for(var model:SLIDERS) event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(model.modelLocation()));
    }
    @Override protected void renderSafe(RockerBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        Vec3 x=FluidCouplingGeometry.orient(new Vec3(1,0,0),be.facing()), y=Vec3.atLowerCornerOf(be.facing().getNormal());
        pose.pushPose(); pose.translate(0.5,0.5,0.5);
        pose.mulPose(new Quaternionf(CouplingJointKinematics.frame(new Vector3d(x.x,x.y,x.z),new Vector3d(y.x,y.y,y.z))));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-be.shaftAngle(partial)));
        pose.translate(-26/16f,-0.5,-0.5);
        CachedBuffers.partial(AXIS,be.getBlockState()).light(light).overlay(overlay).renderInto(pose,buffers.getBuffer(RenderType.cutoutMipped()));
        pose.popPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-be.angle(partial)));
        pose.translate(-26/16f,-0.5,-0.5);
        CachedBuffers.partial(SHAFT,be.getBlockState()).light(light).overlay(overlay).renderInto(pose,buffers.getBuffer(RenderType.cutoutMipped()));
        pose.pushPose();
        pose.translate((be.step() - be.sliderStep(partial)) * 5 / 16f, 0, 0);
        CachedBuffers.partial(SLIDERS[be.step()],be.getBlockState()).light(light).overlay(overlay).renderInto(pose,buffers.getBuffer(RenderType.cutoutMipped()));
        pose.popPose();
        if (RockerClient.isHovered(be)) {
            AABB box = RockerGeometry.INTERACTION_BOX;
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                box.minX/16, box.minY/16, box.minZ/16, box.maxX/16, box.maxY/16, box.maxZ/16,
                0, 0, 0, 0.4f);
        }
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(RockerBlockEntity be) { return be.getRenderBoundingBox(); }
}
