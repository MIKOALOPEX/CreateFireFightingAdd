package com.mikoalopex.createfirefightingadd;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public class ClientConfig {
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	private static final ModConfigSpec.DoubleValue PARTICLE_DENSITY = BUILDER
		.comment("Particle density multiplier for firefighting spray effects (0.0-1.0). 1.0 = full particles, 0.5 = half, 0.0 = none. Client-side only.")
		.defineInRange("particleDensity", 1.0, 0.0, 1.0);

	private static final ModConfigSpec.IntValue SPRAY_PARTICLE_VIEW_DISTANCE = BUILDER
		.comment("Maximum distance in blocks at which firefighting spray particles can spawn (16-1024). Client-side only; fog and render distance may hide them sooner.")
		.translation("createfirefightingadd.config.sprayParticleViewDistance")
		.defineInRange("sprayParticleViewDistance", 48, 16, 1024);

	private static final ModConfigSpec.IntValue NOZZLE_SPRAY_VOLUME = BUILDER
		.comment("Base volume percentage for nozzle spray loop sounds on this client (0-100). Minecraft sound category sliders are still applied on top of this value.")
		.translation("createfirefightingadd.config.nozzleSprayVolume")
		.defineInRange("nozzleSprayVolume", 100, 0, 100);

	private static final ModConfigSpec.BooleanValue RENDER_HANDHELD_HOSE_FIRST_PERSON = BUILDER
		.comment("Render the hose between a bound fire hydrant cabinet and the handheld nozzle in first person. Disable this if a camera or first-person animation mod causes visual issues.")
		.translation("createfirefightingadd.config.renderHandheldHoseFirstPerson")
		.define("renderHandheldHoseFirstPerson", true);

	private static final ModConfigSpec.BooleanValue RENDER_HANDHELD_HOSE_THIRD_PERSON = BUILDER
		.comment("Render the hose between a bound fire hydrant cabinet and the handheld nozzle in third person or external camera views. Disable this if a camera mod causes visual issues.")
		.translation("createfirefightingadd.config.renderHandheldHoseThirdPerson")
		.define("renderHandheldHoseThirdPerson", true);

	static final ModConfigSpec SPEC = BUILDER.build();

	public static double particleDensity;
	public static int sprayParticleViewDistance = 48;
	public static int nozzleSprayVolume = 100;
	public static boolean renderHandheldHoseFirstPerson = true;
	public static boolean renderHandheldHoseThirdPerson = true;

	@SubscribeEvent
	static void onLoad(final ModConfigEvent event) {
		if (event.getConfig().getSpec() != SPEC)
			return;

		particleDensity = PARTICLE_DENSITY.get();
		sprayParticleViewDistance = SPRAY_PARTICLE_VIEW_DISTANCE.get();
		nozzleSprayVolume = NOZZLE_SPRAY_VOLUME.get();
		renderHandheldHoseFirstPerson = RENDER_HANDHELD_HOSE_FIRST_PERSON.get();
		renderHandheldHoseThirdPerson = RENDER_HANDHELD_HOSE_THIRD_PERSON.get();
	}
}
