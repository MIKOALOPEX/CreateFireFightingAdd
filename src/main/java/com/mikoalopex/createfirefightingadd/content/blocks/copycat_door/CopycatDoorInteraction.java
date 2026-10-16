package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class CopycatDoorInteraction extends MovingInteractionBehaviour {
    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos, AbstractContraptionEntity entity) {
        var info = entity.getContraption().getBlocks().get(pos);
        if (info == null || !(info.state().getBlock() instanceof CopycatDoorBlock)) return false;
        if (player.getItemInHand(hand).getItem() instanceof WrenchItem) return false;
        if (!entity.level().isClientSide) setOpen(entity, info, !info.state().getValue(CopycatDoorBlock.OPEN));
        return true;
    }

    static void setOpen(AbstractContraptionEntity entity, StructureBlockInfo info, boolean open) {
        var contraption = entity.getContraption();
        entity.setBlock(info.pos(), new StructureBlockInfo(info.pos(),
            info.state().setValue(CopycatDoorBlock.OPEN, open).setValue(CopycatDoorBlock.VISIBLE,
                info.state().getBlock() instanceof MultidirectionalTrapdoorBlock), info.nbt()));
        if (info.state().getBlock() instanceof ShortSlidingDoorBlock) {
            var pos = ShortSlidingDoorBlock.partnerPos(info.pos(), info.state());
            var other = contraption.getBlocks().get(pos);
            if (other != null && ShortSlidingDoorBlock.matches(info.state(), other.state()))
                entity.setBlock(pos, new StructureBlockInfo(pos,
                    other.state().setValue(CopycatDoorBlock.OPEN, open).setValue(CopycatDoorBlock.VISIBLE, false), other.nbt()));
        }
        contraption.invalidateColliders();
    }
}
