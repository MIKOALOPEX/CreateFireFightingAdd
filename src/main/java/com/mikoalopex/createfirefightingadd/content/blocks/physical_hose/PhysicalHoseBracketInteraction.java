package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketInteraction;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Validates physical hose selection and fixes the selected segment to a bracket.
 */
public final class PhysicalHoseBracketInteraction {
    public static PhysicalHoseBlockEntity selected(Level level, CompoundTag selection) {
        if (!selection.hasUUID("PhysicalEndpoint") || !selection.getString("Dimension").equals(level.dimension().location().toString())) return null;
        BlockPos pos = BlockPos.of(selection.getLong("Owner"));
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PhysicalHoseBlockEntity hose
            && selection.getUUID("PhysicalEndpoint").equals(hose.getFireHoseEndpointId())
            && selection.hasUUID("Strand") && selection.getUUID("Strand").equals(hose.bracketStrand) ? hose : null;
    }
    public static void select(ServerPlayer player, PhysicalHoseBracketPacket packet) {
        if (!PhysicalHoses.enabled() || player.isSpectator() || !player.mayBuild()) return;
        var stack = player.getItemInHand(packet.hand());
        if (!HoseBracketInteraction.isBracket(stack)) return;
        if (packet.segment() < 0) {
            HoseBracketInteraction.selection(stack, new CompoundTag()); message(player, "cancelled"); return;
        }
        var level = player.serverLevel();
        if (!level.isLoaded(packet.owner()) || !(level.getBlockEntity(packet.owner()) instanceof PhysicalHoseBlockEntity hose)) return;
        var snapshot = hose.anchorSnapshot();
        if (snapshot == null || !snapshot.valid(packet.segment()) || !packet.rope().equals(snapshot.rope())
            || PhysicalHoseAnchors.fixed(hose.anchors, packet.segment())) return;
        var segments = PhysicalHoseAnchors.indexedSegments(snapshot.points());
        if (segments.get(packet.segment()) == null) return;
        double reach = player.blockInteractionRange() + 0.5;
        Vec3 eye = player.getEyePosition();
        if (PhysicalHoseAnchors.hit(eye, eye.add(player.getLookAngle().scale(reach)), segments.get(packet.segment()), hose.width()) > reach) return;
        CompoundTag selection = new CompoundTag();
        selection.putUUID("PhysicalEndpoint", hose.getFireHoseEndpointId()); selection.putUUID("Strand", packet.rope());
        selection.putLong("Owner", packet.owner().asLong()); selection.putInt("Segment", packet.segment());
        selection.putString("Dimension", level.dimension().location().toString());
        HoseBracketInteraction.selection(player, packet.hand(), selection);
    }
    public static boolean use(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        var selection = HoseBracketInteraction.selection(stack);
        if (!selection.hasUUID("PhysicalEndpoint")) return false;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) return true;
        if (!player.mayBuild() || player.isSpectator()) return true;
        if (player.isShiftKeyDown()) {
            HoseBracketInteraction.selection(stack, new CompoundTag()); message(player, "cancelled"); return true;
        }
        var level = player.serverLevel();
        var hose = selected(level, selection);
        int segment = selection.getInt("Segment");
        var snapshot = hose == null ? null : hose.anchorSnapshot();
        if (snapshot == null || !selection.getUUID("Strand").equals(snapshot.rope())
            || !snapshot.valid(segment) || PhysicalHoseAnchors.fixed(hose.anchors, segment)) {
            message(player, "unavailable"); return true;
        }
        BlockPos pos = event.getPos().relative(event.getFace());
        Direction face = event.getFace();
        var target = PhysicalHoseAnchors.target(snapshot, segment, pos, face, player.getLookAngle(),
            point -> SableStructureCompat.projectFromBlockToWorld(level, event.getPos(), point));
        double length = snapshot.length(segment);
        Vec3 a = target.start(), b = target.end();
        if (!PhysicalHoseAnchors.placement(snapshot, hose.anchors, segment, a, b).reachable()) {
            message(player, "physical_range"); return true;
        }
        var context = new BlockPlaceContext(player, event.getHand(), stack, event.getHitVec());
        var state = CreateFireFightingAdd.HOSE_BRACKET.get().defaultBlockState().setValue(HoseBracketBlock.FACING, face)
            .setValue(HoseBracketBlock.WOODEN, AllBlocks.WOODEN_BRACKET.isIn(stack));
        if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).canBeReplaced(context)
            || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, face, stack)
            || !level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.of(player))) {
            message(player, "blocked"); return true;
        }
        BlockSnapshot original = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, 3)) return true;
        if (EventHooks.onBlockPlace(player, original, face)) { original.restore(); return true; }
        if (!(level.getBlockEntity(pos) instanceof HoseBracketBlockEntity bracket)) { original.restore(); return true; }
        bracket.setAngle(target.angle()); bracket.reversed = target.reversed();
        bracket.physicalOwner = hose.getBlockPos(); bracket.physicalEndpoint = hose.getFireHoseEndpointId();
        bracket.physicalStrand = snapshot.rope(); bracket.physicalSegment = segment; bracket.physicalLength = length;
        hose.anchors.add(new PhysicalHoseAnchors.Binding(segment, pos, bracket.nodeId));
        if (!hose.rebuildAnchors()) {
            hose.anchors.removeIf(binding -> binding.node().equals(bracket.nodeId));
            original.restore(); message(player, "unavailable"); return true;
        }
        bracket.acceptPhysicalAngle(); bracket.notifyUpdate(); hose.notifyUpdate();
        HoseBracketInteraction.selection(stack, new CompoundTag());
        if (!player.isCreative()) stack.shrink(1);
        return true;
    }
    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("createfirefightingadd.hose_bracket." + key), true);
    }
    private PhysicalHoseBracketInteraction() {}
}
