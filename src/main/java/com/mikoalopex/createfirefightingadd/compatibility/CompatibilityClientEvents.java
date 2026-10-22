package com.mikoalopex.createfirefightingadd.compatibility;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class CompatibilityClientEvents {
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        event.getParentEntries().removeIf(stack -> !ComponentPolicies.visible(stack));
        event.getSearchEntries().removeIf(stack -> !ComponentPolicies.visible(stack));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { CompatibilitySnapshot.reset(); }
    private CompatibilityClientEvents() {}
}
