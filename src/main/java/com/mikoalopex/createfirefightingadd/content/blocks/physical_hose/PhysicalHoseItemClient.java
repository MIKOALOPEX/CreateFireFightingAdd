package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseItemHandler;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Renders endpoint selection and connection particles using the fire-hose style.
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class PhysicalHoseItemClient {
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !PhysicalHoses.enabled()) return;
        ItemStack stack = ItemStack.EMPTY;
        for (var hand : InteractionHand.values()) {
            var candidate = mc.player.getItemInHand(hand);
            if (candidate.getItem() instanceof PhysicalHoseItem) { stack = candidate; break; }
        }
        if (stack.isEmpty()) return;
        var selected = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            .getCompound(PhysicalHoseItem.SELECTION);
        if (!selected.hasUUID("Id") || !selected.getString("Dimension").equals(mc.level.dimension().location().toString())) return;
        BlockPos firstPos = BlockPos.of(selected.getLong("Pos"));
        if (!mc.level.hasChunkAt(firstPos) || !(mc.level.getBlockEntity(firstPos) instanceof PhysicalHoseBlockEntity first)
            || !first.getFireHoseEndpointId().equals(selected.getUUID("Id")) || first.attached()) return;
        Vec3 firstLocal = port(firstPos, first.getBlockState().getValue(FireHoseBlock.FACING));
        Vec3 start = SableStructureCompat.projectFromBlockToWorld(mc.level, firstPos, firstLocal);
        outline("physical_hose_item_first", firstPos, firstLocal, FireHoseItemHandler.SUCCESS_LIME);
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var target = mc.level.getBlockEntity(hit.getBlockPos()) instanceof PhysicalHoseBlockEntity hose ? hose : null;
        BlockPos targetPos = target == null ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
        Direction facing = target == null ? hit.getDirection() : target.getBlockState().getValue(FireHoseBlock.FACING);
        Vec3 targetLocal = port(targetPos, facing);
        Vec3 end = SableStructureCompat.projectFromBlockToWorld(mc.level, hit.getBlockPos(), targetLocal);
        double distance = start.distanceTo(end);
        boolean valid = distance >= 0.25 && distance <= Config.hoseMaxLength && !targetPos.equals(firstPos)
            && (target == null ? mc.level.getBlockState(targetPos).canBeReplaced()
                : !target.attached() && target.width() == first.width());
        int color = valid ? FireHoseItemHandler.SUCCESS_LIME : FireHoseItemHandler.NUH_UH_RED;
        outline("physical_hose_item_target", hit.getBlockPos(), targetLocal, color);
        var particle = new DustParticleOptions(new net.createmod.catnip.theme.Color(color).asVectorF(), 1);
        for (int i = 0; i < 4; i++) {
            Vec3 point = start.lerp(end, mc.level.random.nextFloat());
            mc.level.addParticle(particle, point.x, point.y, point.z, 0, 0, 0);
        }
    }
    private static Vec3 port(BlockPos pos, Direction facing) {
        return pos.getCenter().add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-0.25));
    }
    private static void outline(String key, BlockPos reference, Vec3 center, int color) {
        var level = Minecraft.getInstance().level;
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) corners[i] = SableStructureCompat.projectFromBlockToWorld(level, reference,
            center.add((i & 1) == 0 ? -0.2 : 0.2, (i & 2) == 0 ? -0.2 : 0.2, (i & 4) == 0 ? -0.2 : 0.2));
        for (int i = 0; i < 8; i++) for (int axis = 1; axis <= 4; axis *= 2)
            if ((i & axis) == 0) Outliner.getInstance().showLine(key + "_" + i + "_" + axis, corners[i], corners[i | axis])
                .colored(color).lineWidth(1 / 16f);
    }
    private PhysicalHoseItemClient() {}
}
