/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Portions of this software use code from Simulated (dev.simulated_team.simulated),
 * licensed under the MIT License.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */

package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.Config;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_ITEM;

public class FireHoseItemHandler implements InteractCallback {

    public static final FireHoseItemHandler INSTANCE = new FireHoseItemHandler();

    private static final int SUCCESS_LIME = 0x70FF33;
    private static final int NUH_UH_RED = 0xFF5555;

    public BlockPos linkPos;
    public Direction linkDirection;
    private int placementCooldown;

    public boolean tryStartPlacement(UseOnContext context) {
        if (placementCooldown > 0) return false;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        Level level = player.level();
        Direction dir = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        BlockPos relative = pos.relative(dir);

        if (this.linkPos != null)
            return false;

        if (!testPlacementAndSendError(level, relative, pos, dir))
            return false;

        this.linkPos = pos;
        this.linkDirection = dir;
        return true;
    }

    @Override
    public Result onUse(int modifiers, int action, KeyMapping rightKey) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return Result.empty();
        Level level = player.level();

        if (action == GLFW.GLFW_PRESS) {
            InteractionHand hand = getHandOrNull(player);
            if (hand == null) {
                reset(true);
                return Result.empty();
            }

            if (this.linkPos != null) {
                if (player.isShiftKeyDown()) {
                    player.swing(hand);
                    reset(true);
                    return new Result(true);
                }
            }

            HitResult clientHit = Minecraft.getInstance().hitResult;
            if (clientHit instanceof BlockHitResult hit
                    && hit.getType() != HitResult.Type.MISS
                    && this.linkPos != null) {
                Direction dir = hit.getDirection();
                BlockPos pos = hit.getBlockPos();

                BlockPos childCenter = pos.relative(dir);
                BlockPos parentCenter = this.linkPos.relative(this.linkDirection);

                if (testExceedsRange(level, childCenter, parentCenter)) {
                    sendMessage("out_of_range", NUH_UH_RED);
                    return Result.empty();
                }

                if (parentCenter.equals(childCenter)) {
                    sendMessage("same_block", NUH_UH_RED);
                    return Result.empty();
                }

                if (!testPlacementAndSendError(level, childCenter, pos, dir))
                    return Result.empty();

                player.swing(hand);
                PacketDistributor.sendToServer(new PlaceFireHosePacket(
                        this.linkPos, pos, this.linkDirection, dir, hand));
                reset(false);
                return new Result(true);
            }
        }

        return Result.empty();
    }

    private boolean testExceedsRange(Level level, BlockPos childPos, BlockPos parentPos) {
        return Sable.HELPER.distanceSquaredWithSubLevels(
                level,
                childPos.getX() + 0.5, childPos.getY() + 0.5, childPos.getZ() + 0.5,
                parentPos.getX() + 0.5, parentPos.getY() + 0.5, parentPos.getZ() + 0.5)
                > (long) Config.hoseMaxLength * Config.hoseMaxLength;
    }

    private boolean testPlacementAndSendError(Level level, BlockPos relative, BlockPos pos, Direction dir) {
        if (!level.getBlockState(relative).canBeReplaced()) {
            sendMessage("block_exists", NUH_UH_RED);
            return false;
        }
        return true;
    }

    @Nullable
    public InteractionHand getHandOrNull(LocalPlayer player) {
        ItemStack mainItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHandItem = player.getItemInHand(InteractionHand.OFF_HAND);

        if (mainItem.getItem() instanceof FireHoseItem)
            return InteractionHand.MAIN_HAND;
        if (offHandItem.getItem() instanceof FireHoseItem)
            return InteractionHand.OFF_HAND;
        return null;
    }

    public void reset(boolean sayMessage) {
        if (sayMessage && this.linkPos != null)
            sendMessage("connection_terminated", NUH_UH_RED);

        this.linkPos = null;
        this.linkDirection = null;

        placementCooldown = 2;
    }

    public void sendMessage(String message, int color) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("createfirefightingadd.fire_hose." + message)
                            .withColor(color), true);
        }
    }

    @Override
    public void clientTick(Level level, LocalPlayer player) {
        if (placementCooldown > 0) placementCooldown--;

        if (!player.getMainHandItem().is(FIRE_HOSE_ITEM.get())
                && !player.getOffhandItem().is(FIRE_HOSE_ITEM.get())) {
            reset(true);
            return;
        }

        if (this.linkPos != null) {
            Vec3 linkVec = new Vec3(
                    this.linkDirection.getStepX(),
                    this.linkDirection.getStepY(),
                    this.linkDirection.getStepZ());
            AABB linkAABB = new AABB(this.linkPos).inflate(-0.3).move(linkVec.scale(0.65));
            Outliner.getInstance().showAABB(this.linkPos + "FireHose", linkAABB)
                    .colored(SUCCESS_LIME)
                    .lineWidth(1 / 16f);

            HitResult clientHit = Minecraft.getInstance().hitResult;
            if (clientHit != null
                    && clientHit.getType() != HitResult.Type.MISS
                    && clientHit instanceof BlockHitResult hit) {
                BlockPos pos = hit.getBlockPos();
                Direction dir = hit.getDirection();

                BlockPos childCenter = pos.relative(dir);
                BlockPos parentCenter = this.linkPos.relative(this.linkDirection);

                int color = SUCCESS_LIME;
                if (!level.getBlockState(pos.relative(dir)).canBeReplaced()
                        || this.linkPos.relative(this.linkDirection).equals(pos.relative(dir))
                        || testExceedsRange(level, childCenter, parentCenter)) {
                    color = NUH_UH_RED;
                }

                AABB hitAABB = new AABB(pos).inflate(-0.3).move(
                        new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ()).scale(0.65));

                Vec3 globalFirstPoint = Sable.HELPER.projectOutOfSubLevel(level, linkAABB.getCenter());
                Vec3 globalTarget = Sable.HELPER.projectOutOfSubLevel(level, hitAABB.getCenter());

                DustParticleOptions data = new DustParticleOptions(
                        new net.createmod.catnip.theme.Color(color).asVectorF(), 1);
                double totalFlyingTicks = 10;
                int segments = (((int) totalFlyingTicks) / 3) + 1;

                for (int i = 0; i < segments; i++) {
                    Vec3 vec = globalFirstPoint.lerp(globalTarget, level.getRandom().nextFloat());
                    level.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
                }

                Outliner.getInstance().showAABB(this.linkPos + " FireHose Selection", hitAABB)
                        .colored(color)
                        .lineWidth(1 / 16f);
            }
        }
    }
}
