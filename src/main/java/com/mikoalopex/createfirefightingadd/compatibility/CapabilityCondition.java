package com.mikoalopex.createfirefightingadd.compatibility;

import com.mojang.serialization.MapCodec;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Gates recipes before their optional ingredients are decoded. */
public record CapabilityCondition(Capability capability) implements ICondition {
    public static final MapCodec<CapabilityCondition> CODEC = com.mojang.serialization.Codec.STRING
        .xmap(Capability::valueOf, Capability::name).fieldOf("capability")
        .xmap(CapabilityCondition::new, CapabilityCondition::capability);

    public static void register(IEventBus bus) {
        DeferredRegister<MapCodec<? extends ICondition>> register = DeferredRegister.create(
            NeoForgeRegistries.CONDITION_SERIALIZERS, CreateFireFightingAdd.MODID);
        register.register("capability", () -> CODEC);
        register.register(bus);
    }
    @Override public boolean test(IContext context) { return CompatibilityManager.available(capability); }
    @Override public MapCodec<? extends ICondition> codec() { return CODEC; }
}
