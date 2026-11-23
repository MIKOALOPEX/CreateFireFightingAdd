package com.mikoalopex.createfirefightingadd.integration.dragonsplus;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Keeps the optional dye API outside the nozzle's class-loading path. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class DragonsPlusCompat {

	private DragonsPlusCompat() {
	}

	private static boolean loaded() {
		return ModList.get() != null && ModList.get().isLoaded("create_dragons_plus");
	}

	public static @Nullable ResourceLocation color(FluidStack fluid) {
		return loaded() && !fluid.isEmpty() ? DragonsPlusColoring.color(fluid) : null;
	}

	public static int tint(FluidStack fluid) {
		return loaded() && !fluid.isEmpty() ? DragonsPlusColoring.tint(fluid) : 0xFFFFFF;
	}

	public static Vector3f tintVector(FluidStack fluid) {
		int rgb = tint(fluid);
		return new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
	}

	public static @Nullable BlockState recolor(BlockState state, FluidStack fluid) {
		if (state.isAir())
			return null;
		ResourceLocation color = color(fluid);
		if (color == null)
			return null;
		BlockState special = NozzleSpecialColoring.recolor(state, color);
		return special != null ? special : NozzleColoringIndex.recolor(state, color);
	}

	@SubscribeEvent
	public static void serverStarted(ServerStartedEvent event) {
		if (loaded())
			DragonsPlusColoring.rebuild(event.getServer());
	}

	@SubscribeEvent
	public static void datapackSync(OnDatapackSyncEvent event) {
		// A joining player receives existing data; only a global reload changes recipes.
		if (loaded() && event.getPlayer() == null)
			DragonsPlusColoring.rebuild(event.getPlayerList().getServer());
	}

	@SubscribeEvent
	public static void serverStopped(ServerStoppedEvent event) {
		if (loaded())
			NozzleColoringIndex.clear();
	}
}
