package com.mikoalopex.createfirefightingadd.compatibility;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class CompatibilityEvents {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var status = CompatibilityManager.status(Capability.SABLE_SPATIAL);
        CreateFireFightingAdd.LOGGER.info("Firefighting spatial support: {} ({})", status.state(), status.reason());
    }
    private CompatibilityEvents() {}
}
