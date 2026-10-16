package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CopycatDoorClient {
    private static final Object AXIS = new Object();

    @SubscribeEvent
    public static void models(ModelEvent.ModifyBakingResult event) {
        for (var block : new CopycatDoorBlock[] {CopycatDoors.TRAPDOOR.get(), CopycatDoors.SLIDING.get(), CopycatDoors.FOLDING.get()})
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var key = BlockModelShaper.stateToModelLocation(state);
                var model = event.getModels().get(key);
                if (model != null) event.getModels().put(key, new CopycatDoorModel(model));
            }
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register(CopycatBlock.wrappedColor(), CopycatDoors.TRAPDOOR.get(), CopycatDoors.SLIDING.get(), CopycatDoors.FOLDING.get());
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CopycatDoors.BLOCK_ENTITY.get(), CopycatDoorRenderer::new);
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null
                || !mc.player.getMainHandItem().is(AllItems.WRENCH.get())
                    && !mc.player.getOffhandItem().is(AllItems.WRENCH.get())
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof CopycatDoorBlockEntity entity)) {
            Outliner.getInstance().remove(AXIS);
            return;
        }
        var state = entity.getBlockState();
        Vec3[] axis = DoorGeometry.axis(state);
        Vec3 a = axis[0];
        Vec3 b = axis[1];
        Vec3 origin = Vec3.atLowerCornerOf(entity.getBlockPos());
        a = SableStructureCompat.transformPositionToWorld(entity, a.add(origin));
        b = SableStructureCompat.transformPositionToWorld(entity, b.add(origin));
        Outliner.getInstance().showLine(AXIS, a, b).colored(0x70FF33).lineWidth(1 / 16f).disableLineNormals();
    }

    private CopycatDoorClient() {}
}
