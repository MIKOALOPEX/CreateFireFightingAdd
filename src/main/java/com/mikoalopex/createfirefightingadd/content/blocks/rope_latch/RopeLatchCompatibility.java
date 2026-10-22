package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import com.mikoalopex.createfirefightingadd.compatibility.Capability;
import com.mikoalopex.createfirefightingadd.compatibility.CompatibilityManager;
import com.mikoalopex.createfirefightingadd.compatibility.InertPhysicsBlock;
import com.mikoalopex.createfirefightingadd.compatibility.InertPhysicsBlockEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Keeps rope physics classes out of installations without Sable 2 and Simulated. */
public final class RopeLatchCompatibility {
    private RopeLatchCompatibility() {}

    public static boolean available() {
        return CompatibilityManager.available(Capability.ROPE_CAPTURE);
    }

    public static Block createBlock(BlockBehaviour.Properties properties) {
        return available() ? Loaded.block(properties) : InertPhysicsBlock.create(properties, true);
    }

    public static BlockEntity createEntity(BlockPos pos, BlockState state) {
        return available() ? Loaded.entity(pos, state)
            : new InertPhysicsBlockEntity(CreateFireFightingAdd.ROPE_LATCH_BE.get(), pos, state);
    }

    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        if (available()) Client.register(event);
    }

    private static final class Client {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer((BlockEntityType)
                CreateFireFightingAdd.ROPE_LATCH_BE.get(), RopeLatchRenderer::new);
        }
    }

    private static final class Loaded {
        static Block block(BlockBehaviour.Properties properties) { return new RopeLatchBlock(properties); }
        static BlockEntity entity(BlockPos pos, BlockState state) {
            return new RopeLatchBlockEntity(CreateFireFightingAdd.ROPE_LATCH_BE.get(), pos, state);
        }
    }

}
