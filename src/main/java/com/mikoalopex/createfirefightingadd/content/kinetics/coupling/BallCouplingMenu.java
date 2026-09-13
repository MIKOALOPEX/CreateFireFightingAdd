package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BallCouplingMenu extends AbstractContainerMenu {
    private final BallCouplingBlockEntity owner;
    private final BlockPos pos;
    private final ContainerData values;

    public BallCouplingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null);
    }

    public BallCouplingMenu(int id, Inventory inventory, BallCouplingBlockEntity owner) {
        this(id, inventory, owner.getBlockPos(), owner);
    }

    private BallCouplingMenu(int id, Inventory inventory, BlockPos pos, BallCouplingBlockEntity owner) {
        super(BallCouplings.MENU.get(), id);
        this.owner = owner;
        this.pos = pos;
        values = owner == null ? new SimpleContainerData(2) : new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? owner.mode() : owner.status();
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return 2;
            }
        };
        addDataSlots(values);

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 112 + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, 8 + column * 18, 170));
    }

    public int mode() {
        return values.get(0);
    }

    public int status() {
        return values.get(1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (owner == null || !stillValid(player) || id < 0 || id > 2)
            return false;
        owner.setMode(id);
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner == null)
            return true;
        return !owner.isRemoved() && player.level() == owner.worldLevel()
            && player.position().distanceToSqr(owner.worldAnchor()) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size())
            return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false))
            return ItemStack.EMPTY;
        if (stack.isEmpty())
            slot.setByPlayer(ItemStack.EMPTY);
        else
            slot.setChanged();
        return copy;
    }
}
