package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class CopycatDoors {
    public static final DeferredBlock<MultidirectionalTrapdoorBlock> TRAPDOOR = CreateFireFightingAdd.BLOCKS.register(
        "copycat_multidirectional_trapdoor", () -> new MultidirectionalTrapdoorBlock(properties()));
    public static final DeferredBlock<ShortSlidingDoorBlock> SLIDING = CreateFireFightingAdd.BLOCKS.register(
        "copycat_short_sliding_door", () -> new ShortSlidingDoorBlock(properties(), false));
    public static final DeferredBlock<ShortSlidingDoorBlock> FOLDING = CreateFireFightingAdd.BLOCKS.register(
        "copycat_short_folding_door", () -> new ShortSlidingDoorBlock(properties(), true));
    public static final DeferredItem<BlockItem> TRAPDOOR_ITEM = CreateFireFightingAdd.ITEMS.register(
        "copycat_multidirectional_trapdoor", () -> new BlockItem(TRAPDOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SLIDING_ITEM = CreateFireFightingAdd.ITEMS.register(
        "copycat_short_sliding_door", () -> new BlockItem(SLIDING.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> FOLDING_ITEM = CreateFireFightingAdd.ITEMS.register(
        "copycat_short_folding_door", () -> new BlockItem(FOLDING.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopycatDoorBlockEntity>> BLOCK_ENTITY =
        CreateFireFightingAdd.BLOCK_ENTITY_TYPES.register("copycat_door", () -> BlockEntityType.Builder.of(
            CopycatDoorBlockEntity::new, TRAPDOOR.get(), SLIDING.get(), FOLDING.get()).build(null));

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion().dynamicShape();
    }

    public static void init() {}

    public static void registerBehaviours() {
        for (var block : new CopycatDoorBlock[] {SLIDING.get(), FOLDING.get()})
            MovementBehaviour.REGISTRY.register(block, new ShortDoorMovementBehaviour());
        for (var block : new CopycatDoorBlock[] {TRAPDOOR.get(), SLIDING.get(), FOLDING.get()})
            MovingInteractionBehaviour.REGISTRY.register(block, new CopycatDoorInteraction());
    }

    private CopycatDoors() {}
}
