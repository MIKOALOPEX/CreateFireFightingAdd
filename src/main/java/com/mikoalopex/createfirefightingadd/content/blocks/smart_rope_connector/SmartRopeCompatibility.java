package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

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

/** Keeps optional Simulated classes behind the rope capability checks. */
public final class SmartRopeCompatibility {
    private SmartRopeCompatibility() {}

    public static boolean available() { return CompatibilityManager.available(Capability.ROPE_CONNECT); }

    public static void clientTick() {
        if (available()) SmartRopeSelection.tick();
    }

    public static boolean scroll(double delta) {
        return available() && SmartRopeSelection.scroll(delta);
    }

    public static boolean use() {
        return available() && SmartRopeSelection.use();
    }

    public static boolean selecting() {
        return available() && SmartRopeSelection.hasTarget();
    }

    public static void cut(net.minecraft.server.level.ServerPlayer player, SmartRopeCutPacket packet) {
        if (available()) Loaded.cut(player, packet);
    }

    public static Block createBlock(BlockBehaviour.Properties properties) {
        return available() ? Loaded.createBlock(properties) : InertPhysicsBlock.create(properties, false);
    }

    public static BlockEntity createEntity(BlockPos pos, BlockState state) {
        return available() ? Loaded.createEntity(pos, state)
            : new InertPhysicsBlockEntity(CreateFireFightingAdd.SMART_ROPE_CONNECTOR_BE.get(), pos, state);
    }

    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        if (available()) Client.register(event);
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

    private static final class Client {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer((BlockEntityType)
                CreateFireFightingAdd.SMART_ROPE_CONNECTOR_BE.get(), context -> new SmartRopeConnectorRenderer(context));
        }
    }
}
