package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.compatibility.Capability;
import com.mikoalopex.createfirefightingadd.compatibility.CompatibilityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registers persistent endpoints without loading the optional physics backend. */
public final class PhysicalHoses {
    public static final DeferredBlock<PhysicalHoseBlock> BLOCK = block("physical_fire_hose", 8);
    public static final DeferredItem<PhysicalHoseItem> ITEM = CreateFireFightingAdd.ITEMS.register("physical_fire_hose",
        () -> new PhysicalHoseItem(BLOCK.get(), new Item.Properties()));
    // Retain the prototype IDs for existing worlds; only the adjustable item is exposed.
    public static final DeferredBlock<PhysicalHoseBlock> SMALL = block(4), MEDIUM = block(6), LARGE = block(8);
    public static final DeferredItem<PhysicalHoseItem> SMALL_ITEM = item(SMALL, 4), MEDIUM_ITEM = item(MEDIUM, 6), LARGE_ITEM = item(LARGE, 8);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhysicalHoseBlockEntity>> BLOCK_ENTITY =
        CreateFireFightingAdd.BLOCK_ENTITY_TYPES.register("physical_fire_hose", () -> BlockEntityType.Builder
            .of(PhysicalHoses::create, BLOCK.get(), SMALL.get(), MEDIUM.get(), LARGE.get()).build(null));
    private static java.lang.reflect.Constructor<? extends PhysicalHoseBlockEntity> factory;

    private static DeferredBlock<PhysicalHoseBlock> block(int width) {
        return block("physical_fire_hose_" + width, width);
    }

    private static DeferredBlock<PhysicalHoseBlock> block(String name, int width) {
        return CreateFireFightingAdd.BLOCKS.register(name, () -> {
            var properties = BlockBehaviour.Properties.of().strength(2).sound(SoundType.METAL).noOcclusion();
            if (enabled()) {
                try {
                    return (PhysicalHoseBlock) Class.forName("com.mikoalopex.createfirefightingadd.integration.simulated.SimulatedPhysicalHoseBlock")
                        .getConstructor(BlockBehaviour.Properties.class, int.class).newInstance(properties, width);
                } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                    CompatibilityManager.disable(Capability.PHYSICAL_HOSE, "Physical hose block adapter unavailable");
                    CreateFireFightingAdd.LOGGER.debug("Physical hose block adapter failure", error);
                }
            }
            return new PhysicalHoseBlock(properties, width);
        });
    }

    private static DeferredItem<PhysicalHoseItem> item(DeferredBlock<PhysicalHoseBlock> block, int width) {
        return CreateFireFightingAdd.ITEMS.register("physical_fire_hose_" + width,
            () -> new PhysicalHoseItem(block.get(), new Item.Properties()));
    }

    public static void init() {}
    public static boolean enabled() { return CompatibilityManager.available(Capability.PHYSICAL_HOSE); }

    public static boolean adapterAvailable() throws ReflectiveOperationException {
        Class<?> adapter = Class.forName("com.mikoalopex.createfirefightingadd.integration.simulated.SimulatedPhysicalHoseBlockEntity");
        adapter.getMethod("probe").invoke(null);
        factory = adapter.asSubclass(PhysicalHoseBlockEntity.class).getConstructor(BlockPos.class, BlockState.class);
        return true;
    }

    private static PhysicalHoseBlockEntity create(BlockPos pos, BlockState state) {
        if (enabled()) {
            try {
                return factory.newInstance(pos, state);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                CompatibilityManager.disable(Capability.PHYSICAL_HOSE, "Physical hose adapter could not be constructed");
                CreateFireFightingAdd.LOGGER.debug("Physical hose adapter failure", error);
            }
        }
        return new PhysicalHoseBlockEntity(pos, state);
    }

    private PhysicalHoses() {}
}
