package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RopeMasterMenu extends AbstractContainerMenu {
    private final RopeMasterBlockEntity master;

    public RopeMasterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, inventory.player.level().getBlockEntity(buffer.readBlockPos())
            instanceof RopeMasterBlockEntity be ? be : null);
    }

    public RopeMasterMenu(int id, Inventory inventory, RopeMasterBlockEntity master) {
        super(CreateFireFightingAdd.ROPE_MASTER_MENU.get(), id);
        this.master = master;
        addSlot(new Slot(master == null ? new SimpleContainer(1) : master.inventory, 0, 80, 28) {
            @Override public boolean mayPlace(ItemStack stack) { return RopeMasterCompatibility.accepts(stack); }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 66 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 124));
    }

    @Override public boolean stillValid(Player player) {
        return master != null && !master.isRemoved() && player.level() == master.getLevel()
            && SableStructureCompat.projectToWorld(player.level(), master.getBlockPos().getCenter())
                .distanceToSqr(player.position()) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = stack.copy();
        if (index == 0 ? !moveItemStackTo(stack, 1, slots.size(), true)
                : !slotMayAccept(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    private boolean slotMayAccept(ItemStack stack) { return slots.getFirst().mayPlace(stack); }
}
