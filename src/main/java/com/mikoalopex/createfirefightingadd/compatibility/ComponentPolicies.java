package com.mikoalopex.createfirefightingadd.compatibility;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Maps physics-dependent components to their required capabilities. */
public final class ComponentPolicies {
    private static final Map<String, Capability> COMPONENTS = Map.of(
        "smart_rope_connector", Capability.ROPE_CONNECT,
        "rope_hook", Capability.ROPE_CAPTURE, "rope_latch", Capability.ROPE_CAPTURE,
        "rope_connector_master", Capability.ROPE_MANAGE,
        "stress_coupling", Capability.COUPLING, "ball_coupling_base", Capability.COUPLING,
        "ball_coupling_top", Capability.COUPLING, "fluid_coupling", Capability.COUPLING);

    public static Capability requirement(ResourceLocation id) {
        return id.getNamespace().equals("createfirefightingadd") ? COMPONENTS.get(id.getPath()) : null;
    }
    public static boolean enabled(ResourceLocation id) {
        Capability capability = requirement(id);
        return capability == null || CompatibilityManager.available(capability);
    }
    public static boolean enabled(ItemStack stack) { return stack.isEmpty() || enabled(BuiltInRegistries.ITEM.getKey(stack.getItem())); }
    public static boolean enabled(Block block) { return enabled(BuiltInRegistries.BLOCK.getKey(block)); }
    public static boolean visible(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.equals(ResourceLocation.fromNamespaceAndPath("createfirefightingadd", "rope_latch"))) return false;
        Capability capability = requirement(id);
        return capability == null || CompatibilitySnapshot.visible(capability);
    }
    public static String impact(Capability capability) {
        return switch (capability) {
            case SABLE_SPATIAL -> "Sable projection/interaction disabled; native-world firefighting, rocker and copycat doors remain available.";
            case ROPE_CONNECT -> "Smart Rope Connector hidden and disabled.";
            case ROPE_CAPTURE -> "Rope Hook (including tail-hook mode) hidden and disabled.";
            case ROPE_MANAGE -> "Rope Connector Master hidden; transactions disabled, stored items remain recoverable.";
            case COUPLING -> "Stress Coupling and Fluid Coupling hidden and disabled.";
        };
    }
    private ComponentPolicies() {}
}
