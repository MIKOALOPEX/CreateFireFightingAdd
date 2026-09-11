package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.List;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Stores a bracket's route identity and its rotation around the attachment axis.
 * The bracket only shapes the rendered hose; fluid remains connected between
 * the route's two endpoint block entities.
 */
public class HoseBracketBlockEntity extends SmartBlockEntity {
    public UUID nodeId = UUID.randomUUID();
    public HoseRoute route;
    public boolean reversed;
    public boolean assembling;
    private ScrollValueBehaviour rotation;

    public HoseBracketBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFireFightingAdd.HOSE_BRACKET_BE.get(), pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        rotation = new ScrollValueBehaviour(Component.translatable("createfirefightingadd.hose_bracket.angle"),
            this, new ValueBoxTransform.Sided() {
                @Override
                protected Vec3 getSouthLocation() {
                    return new Vec3(0.5, 0.5, 0.8);
                }

                @Override
                protected boolean isSideActive(BlockState state, Direction side) {
                    return side == state.getValue(HoseBracketBlock.FACING);
                }
            }) {
                @Override
                public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                    return new ValueSettingsBoard(label, 180, 10,
                        List.of(Component.translatable("createfirefightingadd.hose_bracket.angle")),
                        new ValueSettingsFormatter(
                            settings -> Component.literal((settings.value() - 90) + "\u00b0")));
                }
            }.between(0, 180).requiresWrench().withFormatter(value -> (value - 90) + "\u00b0")
            .withCallback(value -> refreshRoute());
        rotation.value = 90;
        behaviours.add(rotation);
    }

    public float angle() {
        return rotation == null ? 0 : rotation.getValue() - 90;
    }

    public void setAngle(float angle) {
        rotation.setValue(Math.round(angle) + 90);
    }

    public void refreshRoute() {
        if (level != null && !level.isClientSide && route != null)
            HoseRoutes.get(level).relocate(level, route, HoseRoute.Node.of(this));
    }

    @Override
    public void initialize() {
        super.initialize();
        refreshRoute();
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide)
            HoseRouteRenderer.track(this, route);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide || assembling || route == null)
            return;
        HoseRoutes data = HoseRoutes.get(level);
        HoseRoute current = data.find(route.id);
        if (current == null || current.index(nodeId) < 0) {
            level.destroyBlock(worldPosition, true);
            return;
        }
        refreshRoute();
        if (route.revision != current.revision) {
            route = HoseRoute.read(current.write());
            notifyUpdate();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("HoseNodeId", nodeId);
        tag.putBoolean("HoseReversed", reversed);
        if (route != null)
            tag.put("HoseRoute", route.write());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        nodeId = tag.hasUUID("HoseNodeId") ? tag.getUUID("HoseNodeId") : UUID.randomUUID();
        reversed = tag.getBoolean("HoseReversed");
        route = HoseRoute.read(tag.getCompound("HoseRoute"));
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }
}
