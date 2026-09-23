package com.mikoalopex.createfirefightingadd.mixin;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnections;

import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes only links involving a smart connector through the multi-rope service. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem", remap = false)
public class RopeItemMixin {
	@Inject(method = "isValidRopeAttachment", at = @At("HEAD"), cancellable = true, require = 0)
	private static void smartRopeConnector$capacity(Level level, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (SmartRopeConnections.isSmart(level, pos))
			cir.setReturnValue(SmartRopeConnections.canAttach(level, pos));
	}

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true, require = 0)
	private void smartRopeConnector$useOn(UseOnContext context,
			CallbackInfoReturnable<InteractionResult> cir) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		ItemStack stack = context.getItemInHand();
		Player player = context.getPlayer();
		if (player == null) return;
		boolean selectedSmart = stack.getOrDefault(CreateFireFightingAdd.SMART_ROPE_SELECTION.get(), false);
		boolean clickedSmart = SmartRopeConnections.isSmart(level, clicked);
		if (!selectedSmart && !clickedSmart)
			return;

		if (player != null && player.isShiftKeyDown()) {
			clearSelection(stack);
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		BlockPos first = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
		if (first == null) {
			if (!SmartRopeConnections.canAttach(level, clicked))
				return;
			stack.set(SimDataComponents.ROPE_FIRST_CONNECTION, clicked);
			stack.set(CreateFireFightingAdd.SMART_ROPE_SELECTION.get(), clickedSmart);
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		if (!SmartRopeConnections.canAttach(level, clicked)) {
			clearSelection(stack);
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		boolean connected = level.isClientSide || SmartRopeConnections.connect(level, first, clicked);
		clearSelection(stack);
		if (connected && !level.isClientSide && !player.hasInfiniteMaterials())
			stack.shrink(1);
		cir.setReturnValue(InteractionResult.SUCCESS);
	}

	@org.spongepowered.asm.mixin.Unique
	private static void clearSelection(ItemStack stack) {
		stack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
		stack.remove(CreateFireFightingAdd.SMART_ROPE_SELECTION.get());
	}
}
