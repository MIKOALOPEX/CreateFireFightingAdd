package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Keeps optional Simulated classes behind a mod-presence check. */
public final class SmartRopeCompatibility {
    private SmartRopeCompatibility() {}

    public static void clientTick() {
        if (ModList.get().isLoaded("simulated")) SmartRopeSelection.tick();
    }

    public static boolean scroll(double delta) {
        return ModList.get().isLoaded("simulated") && SmartRopeSelection.scroll(delta);
    }

    public static boolean use() {
        return ModList.get().isLoaded("simulated") && SmartRopeSelection.use();
    }

    public static boolean selecting() {
        return ModList.get().isLoaded("simulated") && SmartRopeSelection.hasTarget();
    }

    public static void cut(net.minecraft.server.level.ServerPlayer player, SmartRopeCutPacket packet) {
        if (ModList.get().isLoaded("simulated")) Loaded.cut(player, packet);
    }

    public static Block createBlock(BlockBehaviour.Properties properties) {
        return ModList.get().isLoaded("simulated") ? Loaded.createBlock(properties) : new InertBlock(properties);
    }

    public static BlockEntity createEntity(BlockPos pos, BlockState state) {
        return ModList.get().isLoaded("simulated") ? Loaded.createEntity(pos, state) : null;
    }

    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        if (ModList.get().isLoaded("simulated")) Client.register(event);
    }

    private static final class Loaded {
        static void cut(net.minecraft.server.level.ServerPlayer player, SmartRopeCutPacket packet) {
            var level = player.serverLevel();
            if (!level.dimension().location().equals(packet.dimension()) || player.isSpectator()
                || !player.getAbilities().mayBuild || !level.isLoaded(packet.pos())
                || !level.mayInteract(player, packet.pos())
                || !player.getItemInHand(packet.hand()).is(dev.simulated_team.simulated.index.SimTags.Items.DESTROYS_ROPE))
                return;
            if (!(level.getBlockEntity(packet.pos()) instanceof SmartRopeConnectorBlockEntity connector)) return;
            var center = com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat
                .transformPositionToWorld(connector, packet.pos().getCenter());
            double range = player.blockInteractionRange() + 1.0;
            if (player.getEyePosition().distanceToSqr(center) > range * range) return;
            connector.cutConnection(packet.rope(), player);
        }

        static Block createBlock(BlockBehaviour.Properties properties) {
            return new SmartRopeConnectorBlock(properties);
        }

        static BlockEntity createEntity(BlockPos pos, BlockState state) {
            return new SmartRopeConnectorBlockEntity(CreateFireFightingAdd.SMART_ROPE_CONNECTOR_BE.get(), pos, state);
        }
    }

    private static final class InertBlock extends WrenchableDirectionalBlock {
        private static final BooleanProperty AXIS = BooleanProperty.create("axis_along_first");

        InertBlock(BlockBehaviour.Properties properties) { super(properties); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(AXIS);
        }
    }

    private static final class Client {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer((BlockEntityType)
                CreateFireFightingAdd.SMART_ROPE_CONNECTOR_BE.get(), context -> new SmartRopeConnectorRenderer(context));
        }
    }
}
