package com.mikoalopex.createfirefightingadd.api.rope;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Server-thread contract. Creation and removal must be synchronous and must never emit item drops. */
public interface ManagedRopeAdapter {
    ResourceLocation id();
    boolean accepts(ItemStack stack);
    boolean supports(BlockEntity endpoint);
    boolean hasCapacity(BlockEntity endpoint);
    double range();
    List<UUID> connections(BlockEntity endpoint);
    Link inspect(ServerLevel level, UUID id);
    Link create(ServerLevel level, BlockEntity first, BlockEntity second, ItemStack material);
    boolean remove(ServerLevel level, Link link);

    enum Protection { NONE, WINCH, HOOK, UNSUPPORTED }

    /** Material is the exact single-item refund; adapters must preserve it across save/load. */
    record Link(UUID id, BlockPos first, BlockPos second, ItemStack material, Protection protection) {
        public BlockPos other(BlockPos endpoint) {
            return first.equals(endpoint) ? second : second.equals(endpoint) ? first : null;
        }
    }
}
