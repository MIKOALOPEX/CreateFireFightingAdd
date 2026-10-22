package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class Rockers {
    public static final DeferredBlock<RockerBlock> BLOCK = CreateFireFightingAdd.BLOCKS.register("rocker",
        () -> new RockerBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> ITEM = CreateFireFightingAdd.ITEMS.register("rocker", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RockerBlockEntity>> BLOCK_ENTITY =
        CreateFireFightingAdd.BLOCK_ENTITY_TYPES.register("rocker", () -> BlockEntityType.Builder.of(RockerBlockEntity::new, BLOCK.get()).build(null));
    public static void init() {}
    private Rockers() {}
}
