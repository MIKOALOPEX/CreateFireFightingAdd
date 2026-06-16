package com.createfireworkadd.createfirefightingadd;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Createfirefightingadd.MODID)
public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue PARTICLE_DENSITY = BUILDER
        .comment("Particle density multiplier for firefighting spray effects (0.0-1.0). 1.0 = full particles, 0.5 = half, 0.0 = none. Client-side only.")
        .defineInRange("particleDensity", 1.0, 0.0, 1.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static double particleDensity;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC)
            return;

        particleDensity = PARTICLE_DENSITY.get();
    }
}
