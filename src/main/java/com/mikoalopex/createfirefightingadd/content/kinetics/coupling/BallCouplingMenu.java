package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplingBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplings;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class BallCouplingMenu extends AbstractContainerMenu {
    private final BallCouplingBlockEntity owner;
    private final BlockPos pos;
    private final ContainerData values;
    private FluidCouplingBlockEntity fluidOwner;
    private final boolean fluid;

    public BallCouplingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null);
    }

    public BallCouplingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer, boolean fluid) {
        this(id, inventory, buffer.readBlockPos(), null, null, fluid);
    }

    public BallCouplingMenu(int id, Inventory inventory, FluidCouplingBlockEntity owner) {
        this(id, inventory, owner.getBlockPos(), null, owner, true);
    }

    public BallCouplingMenu(int id, Inventory inventory, BallCouplingBlockEntity owner) {
        this(id, inventory, owner.getBlockPos(), owner);
    }

    private BallCouplingMenu(int id, Inventory inventory, BlockPos pos, BallCouplingBlockEntity owner) {
        this(id, inventory, pos, owner, null, false);
    }

    private BallCouplingMenu(int id, Inventory inventory, BlockPos pos, BallCouplingBlockEntity owner,
            FluidCouplingBlockEntity fluidOwner, boolean fluid) {
        super(fluid ? FluidCouplings.MENU.get() : BallCouplings.MENU.get(), id);
        this.fluid = fluid;
        this.fluidOwner = fluidOwner;
        this.owner = owner;
        this.pos = pos;
        values = owner == null && fluidOwner == null ? new SimpleContainerData(8) : new ContainerData() {
            @Override
            public int get(int index) {
                if (fluidOwner != null) return switch (index) {
                    case 0 -> fluidOwner.settings().freeMode();
                    case 1 -> fluidOwner.status();
                    case 2 -> fluidOwner.settings().type().ordinal();
                    case 3 -> fluidOwner.settings().lower();
                    case 4 -> fluidOwner.settings().upper();
                    case 6 -> fluidOwner.partnerId() != null ? 1 : 0;
                    case 7 -> fluidOwner.settings().flipRange() ? 1 : 0;
                    default -> 1;
                };
                return switch (index) {
                    case 0 -> owner.mode();
                    case 1 -> owner.status();
                    case 2 -> owner.interfaceMode().ordinal();
                    case 3 -> owner.lowerAngle();
                    case 4 -> owner.upperAngle();
                    case 6 -> owner.partnerId() != null ? 1 : 0;
                    case 7 -> owner.flipRange() ? 1 : 0;
                    default -> 1;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return 8;
            }
        };
        addDataSlots(values);

    }

    public int mode() {
        return values.get(0);
    }

    public boolean fluid() { return fluid; }

    public int status() {
        return values.get(1);
    }

    public int interfaceMode() { return values.get(2); }
    public int lowerAngle() { return values.get(3); }
    public int upperAngle() { return values.get(4); }
    public boolean synchronizedSettings() { return values.get(5) == 1; }
    public boolean connected() { return values.get(6) == 1; }
    public boolean flipRange() { return values.get(7) == 1; }

    public void applySettings(Player player, int mode, int role, int lower, int upper, boolean flip) {
        if (fluidOwner != null) {
            if (stillValid(player) && !player.isSpectator()) fluidOwner.applySettings(mode, role, lower, upper, flip);
            broadcastChanges(); return;
        }
        if (owner == null || !stillValid(player) || player.isSpectator()) return;
        owner.applySettings(mode, role, lower, upper, flip);
        broadcastChanges();
    }

    public void disconnectFromGui(Player player) {
        if (fluidOwner != null) {
            if (stillValid(player) && !player.isSpectator() && fluidOwner.partnerId() != null) fluidOwner.disconnectFromGui();
            broadcastChanges(); return;
        }
        if (owner == null || !stillValid(player) || player.isSpectator() || owner.partnerId() == null)
            return;
        owner.disconnectFromGui();
        broadcastChanges();
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
        if (fluid ? !FluidCouplings.enabled() : !StressCouplingCompatibility.enabled()) return false;
        if (fluidOwner != null) return !fluidOwner.isRemoved() && player.level() == fluidOwner.worldLevel()
            && player.position().distanceToSqr(fluidOwner.worldAnchor()) <= 64;
        if (owner == null)
            return true;
        return !owner.isRemoved() && player.level() == owner.worldLevel()
            && player.position().distanceToSqr(owner.worldAnchor()) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
