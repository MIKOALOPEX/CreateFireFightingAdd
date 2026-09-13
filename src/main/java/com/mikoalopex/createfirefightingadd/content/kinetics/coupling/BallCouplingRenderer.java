package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public class BallCouplingRenderer extends KineticBlockEntityRenderer<BallCouplingBlockEntity> {
    private static final PartialModel[][] MODELS=new PartialModel[6][2];
    static {
        for(int part=0;part<6;part++) for(int active=0;active<2;active++)
            MODELS[part][active]=PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID,
                "block/ball_coupling/"+(part==0?"base_move":"top_"+(part-1)+"_move")+(active==0?"_off":"_on")));
    }
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        for (var states : MODELS) for (var model : states)
            event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(model.modelLocation()));
    }
    public BallCouplingRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    @Override protected void renderSafe(BallCouplingBlockEntity be, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        // No Flywheel visual is registered for this compound model; always render its moving part.
        renderRotatingBuffer(be, getRotatedModel(be, be.getBlockState()), pose, buffers.getBuffer(RenderType.solid()), light);
    }
    @Override protected SuperByteBuffer getRotatedModel(BallCouplingBlockEntity be,BlockState state) {
        int model=be.isTop()?state.getValue(BallCouplingBlock.LENGTH)+1:0;
        var facing = state.getValue(BallCouplingBlock.FACING);
        return CachedBuffers.partialDirectional(MODELS[model][state.getValue(BallCouplingBlock.POWERED)?1:0],state,facing, () -> {
            PoseStack pose = new PoseStack();
            int x = facing == net.minecraft.core.Direction.UP ? 0 : facing == net.minecraft.core.Direction.DOWN ? 180 : 90;
            int y = switch (facing) { case SOUTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; };
            pose.translate(0.5,0.5,0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-y));
            pose.mulPose(Axis.XP.rotationDegrees(-x));
            pose.translate(-0.5,-0.5,-0.5);
            return pose;
        });
    }
    @Override public AABB getRenderBoundingBox(BallCouplingBlockEntity be) { return new AABB(be.getBlockPos()).inflate(1); }
}
