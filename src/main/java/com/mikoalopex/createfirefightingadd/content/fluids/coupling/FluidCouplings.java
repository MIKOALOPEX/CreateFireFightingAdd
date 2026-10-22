package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.BallCouplingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class FluidCouplings {
    @net.neoforged.bus.api.SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        FluidCouplingIndex.clear();
    }
    public static final DeferredBlock<FluidCouplingBlock> BLOCK = CreateFireFightingAdd.BLOCKS.register("fluid_coupling",
        () -> new FluidCouplingBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.METAL).noOcclusion().dynamicShape()));
    public static final DeferredItem<BlockItem> ITEM = CreateFireFightingAdd.ITEMS.register("fluid_coupling",
        () -> new com.mikoalopex.createfirefightingadd.compatibility.CompatibleBlockItem(BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidCouplingBlockEntity>> BLOCK_ENTITY =
        CreateFireFightingAdd.BLOCK_ENTITY_TYPES.register("fluid_coupling",
            () -> BlockEntityType.Builder.of(FluidCouplings::create, BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BallCouplingMenu>> MENU = CreateFireFightingAdd.MENU_TYPES.register(
        "fluid_coupling", () -> IMenuTypeExtension.create((id, inventory, buffer) -> new BallCouplingMenu(id, inventory, buffer, true)));
    public static void init() {}
    private static java.lang.reflect.Constructor<? extends FluidCouplingBlockEntity> factory;
    private static boolean failed;
    private static FluidCouplingBlockEntity create(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!failed && com.mikoalopex.createfirefightingadd.content.kinetics.coupling.StressCouplingCompatibility.enabled()) {
            try {
                if (factory == null) factory = Class.forName("com.mikoalopex.createfirefightingadd.integration.sable.SableFluidCouplingBlockEntity")
                    .asSubclass(FluidCouplingBlockEntity.class).getConstructor(net.minecraft.core.BlockPos.class, net.minecraft.world.level.block.state.BlockState.class);
                return factory.newInstance(pos, state);
            } catch (ReflectiveOperationException | LinkageError error) {
                failed = true;
                CreateFireFightingAdd.LOGGER.warn("Fluid coupling physics unavailable; check the installed Sable/Synaxis dependency versions.");
                CreateFireFightingAdd.LOGGER.debug("Fluid coupling Sable adapter failure", error);
            }
        }
        return new FluidCouplingBlockEntity(pos, state);
    }
    private FluidCouplings() {}
}
