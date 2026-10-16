package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import java.util.List;
import java.util.UUID;

import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.index.SimTags;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only selection of one strand; connection ownership remains entirely server-side. */
final class SmartRopeSelection {
    private static final Object OUTLINE_KEY = new Object();
    private static ClientLevel selectedLevel;
    private static BlockPos selectedPos;
    private static UUID selected;
    private static ClientRopeStrand highlighted;
    private static boolean useHeld;

    private SmartRopeSelection() {}

    private static InteractionHand toolHand() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        for (InteractionHand hand : InteractionHand.values())
            if (player.getItemInHand(hand).is(SimTags.Items.DESTROYS_ROPE)) return hand;
        return null;
    }

    private static SmartRopeConnectorBlockEntity target() {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null || mc.player == null || mc.player.isSpectator()
            || toolHand() == null || !(mc.hitResult instanceof BlockHitResult hit)
            || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        return mc.level.getBlockEntity(hit.getBlockPos()) instanceof SmartRopeConnectorBlockEntity connector
            ? connector : null;
    }

    private static List<UUID> refresh(SmartRopeConnectorBlockEntity connector) {
        var mc = Minecraft.getInstance();
        if (selectedLevel != mc.level || !connector.getBlockPos().equals(selectedPos)) {
            clear();
            selectedLevel = mc.level;
            selectedPos = connector.getBlockPos().immutable();
        }
        List<UUID> ids = connector.allConnectionIds();
        if (selected == null || !ids.contains(selected)) {
            selected = ids.isEmpty() ? null : ids.getFirst();
            removeOutline();
        }
        return ids;
    }

    static boolean hasTarget() {
        return target() != null;
    }

    static void tick() {
        var mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isDown()) useHeld = false;
        SmartRopeConnectorBlockEntity connector = target();
        if (connector == null) {
            clear();
            return;
        }
        List<UUID> ids = refresh(connector);
        ClientRopeStrand strand = selected == null ? null
            : ClientLevelRopeManager.getOrCreate(mc.level).getStrand(selected);
        if (strand == null || strand.getPoints().size() < 2) {
            removeOutline();
            mc.player.displayClientMessage(Component.translatable(ids.isEmpty()
                ? "smart_rope.selection.empty" : "smart_rope.selection.loading"), true);
            return;
        }
        if (highlighted != strand) {
            removeOutline();
            highlighted = strand;
            Outliner.getInstance().showOutline(OUTLINE_KEY, new SmartRopeSelectionOutline(strand))
                .colored(0x70FF33).lineWidth(1 / 16f).disableLineNormals();
        } else {
            Outliner.getInstance().keep(OUTLINE_KEY);
        }
        mc.player.displayClientMessage(Component.translatable("smart_rope.selection",
            ids.indexOf(selected) + 1, ids.size()), true);
    }

    static boolean scroll(double delta) {
        SmartRopeConnectorBlockEntity connector = target();
        if (connector == null || delta == 0) return false;
        List<UUID> ids = refresh(connector);
        if (!ids.isEmpty()) {
            int index = Math.floorMod(ids.indexOf(selected) + (delta > 0 ? -1 : 1), ids.size());
            selected = ids.get(index);
            removeOutline();
            tick();
        }
        return true;
    }

    static boolean use() {
        var mc = Minecraft.getInstance();
        SmartRopeConnectorBlockEntity connector = target();
        if (connector == null) return false;
        // Holding use must not cut the next strand after a successful removal.
        if (useHeld) return true;
        useHeld = true;
        if (selectedLevel != mc.level || !connector.getBlockPos().equals(selectedPos)
            || selected == null || highlighted == null || !connector.allConnectionIds().contains(selected)) return true;
        PacketDistributor.sendToServer(new SmartRopeCutPacket(mc.level.dimension().location(),
            selectedPos, selected, toolHand()));
        return true;
    }

    private static void removeOutline() {
        Outliner.getInstance().remove(OUTLINE_KEY);
        highlighted = null;
    }

    private static void clear() {
        removeOutline();
        if (selectedPos != null && Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.empty(), true);
        selectedLevel = null;
        selectedPos = null;
        selected = null;
    }
}
