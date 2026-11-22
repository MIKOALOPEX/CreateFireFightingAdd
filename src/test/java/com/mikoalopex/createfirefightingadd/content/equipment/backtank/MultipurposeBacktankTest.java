package com.mikoalopex.createfirefightingadd.content.equipment.backtank;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Exercises placed storage and component restoration without launching a world. */
public final class MultipurposeBacktankTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
            java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.server.Bootstrap.bootStrap();
        var register = AllConfigs.class.getDeclaredMethod("register", java.util.function.Supplier.class,
            net.neoforged.fml.config.ModConfig.Type.class);
        register.setAccessible(true);
        CServer config = (CServer) register.invoke(null, (java.util.function.Supplier<CServer>) CServer::new,
            net.neoforged.fml.config.ModConfig.Type.SERVER);
        var field = AllConfigs.class.getDeclaredField("server");
        field.setAccessible(true); field.set(null, config);
        var data = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        config.specification.correct(data);
        Class<?> loaded = Class.forName("net.neoforged.fml.config.IConfigSpec$ILoadedConfig");
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object proxy = constructor.newInstance(data, null, null);
        config.specification.getClass().getMethod("acceptConfig", loaded).invoke(config.specification, proxy);
        var registry = (net.minecraft.core.MappedRegistry<net.minecraft.core.component.DataComponentType<?>>)
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE;
        ((net.minecraft.core.MappedRegistry<?>) net.minecraft.core.registries.BuiltInRegistries.REGISTRY).unfreeze();
        registry.unfreeze();
        net.minecraft.core.Registry.register(registry, "create:banktank_air", AllDataComponents.BACKTANK_AIR);
        registry.freeze();
        var items = (net.minecraft.core.MappedRegistry<net.minecraft.world.item.Item>) net.minecraft.core.registries.BuiltInRegistries.ITEM;
        items.unfreeze();
        var item = new MultipurposeBacktankItem(net.minecraft.world.item.ArmorMaterials.NETHERITE,
            new net.minecraft.world.item.Item.Properties().stacksTo(1),
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("test_backtank"), () -> null);
        net.minecraft.core.Registry.register(items, "test:multipurpose_backtank", item);
        items.freeze();
        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var stack = new ItemStack(item);
        stack.enchant(registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Named tank"));
        stack.set(DataComponents.DAMAGE, 17);
        CompoundTag custom = new CompoundTag(); custom.putString("OtherMod", "preserved");
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
        stack.set(AllDataComponents.BACKTANK_AIR, 123);
        com.mikoalopex.createfirefightingadd.api.backtank.MultipurposeBacktankFluidApi.setFluid(stack, registries,
            new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1500));
        for (int level = 0; level <= 3; level++) {
            var tank = new Tank();
            tank.setCapacityEnchantLevel(level);
            tank.setComponentPatch(stack.getComponentsPatch());
            tank.setCustomName(stack.getHoverName());
            tank.setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 600));
            int max = config.equipment.airInBacktank.get() + config.equipment.enchantedBacktankCapacity.get() * level;
            tank.setAirLevel(max + 100);
            check(tank.getAirLevel() == max, "Enchanted capacity clamp");
            check(tank.getComparatorOutput() == 15, "Full comparator");
            CompoundTag saved = new CompoundTag(); tank.write(saved, registries, false);
            var restored = new Tank(); restored.read(saved, registries, false);
            check(restored.getAirLevel() == max && restored.getAirCapacity() == max, "Capacity survives reload");
            var result = new ItemStack(item);
            MultipurposeBacktankBlock.copyStorageToStack(result, restored, registries);
            check(result.getOrDefault(AllDataComponents.BACKTANK_AIR, 0) == max, "Live air overrides saved patch");
            for (var type : new net.minecraft.core.component.DataComponentType<?>[]{DataComponents.ENCHANTMENTS,
                    DataComponents.CUSTOM_NAME, DataComponents.DAMAGE})
                check(java.util.Objects.equals(stack.get(type), result.get(type)), "Component round trip: " + type);
            check(com.mikoalopex.createfirefightingadd.api.backtank.MultipurposeBacktankFluidApi.getFluid(result, registries).getAmount() == 600,
                "Live fluid overrides old component contents");
            check(result.get(DataComponents.CUSTOM_DATA).copyTag().getString("OtherMod").equals("preserved"), "Unrelated custom data retained");
            restored.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
            MultipurposeBacktankBlock.copyStorageToStack(result, restored, registries);
            check(com.mikoalopex.createfirefightingadd.api.backtank.MultipurposeBacktankFluidApi.getFluid(result, registries).isEmpty(),
                "Empty tank cannot resurrect stored fluid");
        }
        var legacy = new Tank();
        CompoundTag old = new CompoundTag(); old.putInt("Air", 100);
        legacy.read(old, registries, false);
        check(legacy.getAirLevel() == 100 && legacy.getComponentPatch().isEmpty(), "Legacy storage");
        System.out.println("Backtank components, Capacity 0-3, comparator and legacy reload passed");
    }

    private static class Tank extends MultipurposeBacktankBlockEntity {
        Tank() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
        @Override public void sendData() {}
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
