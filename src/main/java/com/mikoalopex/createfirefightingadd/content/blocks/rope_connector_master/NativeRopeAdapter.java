package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import com.mikoalopex.createfirefightingadd.api.rope.ManagedRopeAdapter;
import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.PinnedRopeHandle;
import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.RopeHookAttachments;
import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnections;
import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnectorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Adapts native Simulated ropes; cable variants require their own material-preserving adapter. */
final class NativeRopeAdapter implements ManagedRopeAdapter {
    private static final String MATERIALS = "createfirefightingadd:managed_rope_materials";
    private static final ResourceLocation ROPE = ResourceLocation.fromNamespaceAndPath("simulated", "rope_coupling");
    private static final Method REMOVE = removeMethod();
    private static final boolean SUBMARINE = net.neoforged.fml.ModList.get().isLoaded("create_submarine");
    private static final Method STEEL_FLAG = steelFlag();

    private static Method steelFlag() {
        if (!SUBMARINE) return null;
        try {
            return Class.forName("com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor")
                .getMethod("createsubmarine$isSteelCable");
        } catch (ReflectiveOperationException ignored) { return null; }
    }

    private static boolean ordinary(ServerRopeStrand rope) {
        if (rope.getClass() != ServerRopeStrand.class) return false;
        if (!SUBMARINE) return true;
        if (STEEL_FLAG == null) return false;
        try { return !(boolean) STEEL_FLAG.invoke(rope); }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    static boolean supported() { return REMOVE != null; }

    private static Method removeMethod() {
        try { return RopeStrandHolderBehavior.class.getMethod("destroyRope", ServerPlayer.class, Vec3.class, boolean.class); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    @Override public ResourceLocation id() { return ROPE; }
    @Override public boolean accepts(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ROPE);
    }
    @Override public boolean supports(BlockEntity be) {
        return be != null && (be.getClass() == RopeConnectorBlockEntity.class || be instanceof SmartRopeConnectorBlockEntity);
    }
    private RopeStrandHolderBehavior holder(BlockEntity be) {
        return be instanceof SmartBlockEntity smart ? smart.getBehaviour(RopeStrandHolderBehavior.TYPE) : null;
    }
    @Override public boolean hasCapacity(BlockEntity be) {
        if (!supports(be)) return false;
        return be instanceof SmartRopeConnectorBlockEntity smart ? smart.hasCapacity()
            : holder(be) != null && !holder(be).isAttached();
    }
    @Override public double range() {
        return dev.simulated_team.simulated.service.SimConfigService.INSTANCE.server().blocks.maxRopeRange.get();
    }

    @Override public List<UUID> connections(BlockEntity be) {
        if (be instanceof SmartRopeConnectorBlockEntity smart) return smart.allConnectionIds();
        RopeStrandHolderBehavior holder = holder(be);
        ServerRopeStrand rope = holder == null ? null : holder.getAttachedStrand();
        return rope == null ? List.of() : List.of(rope.getUUID());
    }

    @Override public Link inspect(ServerLevel level, UUID id) {
        ServerRopeStrand rope = ServerLevelRopeManager.getOrCreate(level).getStrand(id);
        if (rope == null) return null;
        var start = rope.getAttachment(RopeAttachmentPoint.START);
        var end = rope.getAttachment(RopeAttachmentPoint.END);
        if (start == null || end == null) return null;
        BlockPos a = start.blockAttachment(), b = end.blockAttachment();
        Protection protection = Protection.NONE;
        if (!level.isLoaded(a) || !level.isLoaded(b)) protection = Protection.UNSUPPORTED;
        else if (level.getBlockEntity(a) instanceof RopeWinchBlockEntity || level.getBlockEntity(b) instanceof RopeWinchBlockEntity)
            protection = Protection.WINCH;
        else if (RopeHookAttachments.get(level).contains(id) || PinnedRopeHandle.handle(rope) instanceof PinnedRopeHandle)
            protection = Protection.HOOK;
        else if (!rope.isActive() || !supports(level.getBlockEntity(a)) || !supports(level.getBlockEntity(b))
                || !ordinary(rope)) protection = Protection.UNSUPPORTED;
        ItemStack material = new ItemStack(BuiltInRegistries.ITEM.get(ROPE));
        BlockEntity owner = level.isLoaded(a) ? level.getBlockEntity(a) : null;
        if (owner != null) {
            CompoundTag records = owner.getPersistentData().getCompound(MATERIALS);
            if (records.contains(id.toString()))
                material = ItemStack.parseOptional(level.registryAccess(), records.getCompound(id.toString()));
        }
        if (!accepts(material)) protection = Protection.UNSUPPORTED;
        return new Link(id, a.immutable(), b.immutable(), material.copyWithCount(1), protection);
    }

    @Override public Link create(ServerLevel level, BlockEntity first, BlockEntity second, ItemStack material) {
        if (!accepts(material) || !hasCapacity(first) || !hasCapacity(second)) return null;
        List<UUID> before = connections(first);
        boolean made = first instanceof SmartRopeConnectorBlockEntity || second instanceof SmartRopeConnectorBlockEntity
            ? SmartRopeConnections.connect(level, first.getBlockPos(), second.getBlockPos())
            : SmartRopeConnections.createRope(holder(first), holder(second));
        if (!made) return null;
        for (UUID id : connections(first)) {
            if (before.contains(id)) continue;
            ServerRopeStrand rope = ServerLevelRopeManager.getOrCreate(level).getStrand(id);
            if (rope == null || rope.getAttachment(RopeAttachmentPoint.START) == null)
                throw new IllegalStateException("Created rope has no registered owner");
            BlockEntity owner = level.getBlockEntity(rope.getAttachment(RopeAttachmentPoint.START).blockAttachment());
            CompoundTag records = owner.getPersistentData().getCompound(MATERIALS);
            List<UUID> live = connections(owner);
            for (String key : List.copyOf(records.getAllKeys()))
                if (live.stream().noneMatch(existing -> existing.toString().equals(key))) records.remove(key);
            records.put(id.toString(), material.copyWithCount(1).save(level.registryAccess()));
            owner.getPersistentData().put(MATERIALS, records);
            owner.setChanged();
            var end = rope.getAttachment(RopeAttachmentPoint.END);
            return new Link(id, owner.getBlockPos(), end.blockAttachment(), material.copyWithCount(1), Protection.NONE);
        }
        // Do not refund a material when an adapter cannot prove that creation failed.
        throw new IllegalStateException("Created rope could not be identified");
    }

    @Override public boolean remove(ServerLevel level, Link requested) {
        Link current = inspect(level, requested.id());
        if (current == null || current.protection() != Protection.NONE) return false;
        BlockEntity owner = level.getBlockEntity(current.first());
        RopeStrandHolderBehavior behavior = owner instanceof SmartRopeConnectorBlockEntity smart
            ? smart.owner(current.id()) : holder(owner);
        if (behavior == null || behavior.getOwnedStrand() == null
                || !behavior.getOwnedStrand().getUUID().equals(current.id())) return false;
        try { REMOVE.invoke(behavior, null, current.first().getCenter(), false); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot remove managed rope", error); }
        if (ServerLevelRopeManager.getOrCreate(level).getStrand(current.id()) != null) return false;
        for (BlockPos pos : List.of(current.first(), current.second())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SmartRopeConnectorBlockEntity smart) smart.forgetManagedConnection(current.id());
            if (be != null) {
                be.getPersistentData().getCompound(MATERIALS).remove(current.id().toString());
                be.setChanged();
            }
        }
        return true;
    }
}
