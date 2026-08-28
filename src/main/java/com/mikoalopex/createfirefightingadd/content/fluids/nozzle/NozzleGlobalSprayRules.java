package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.AllFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NozzleGlobalSprayRules {
	private static final Map<ResourceLocation, NozzleSprayRule> EXTERNAL_RULES = new LinkedHashMap<>();
	private static volatile List<NozzleSprayRule> CONFIG_RULES = List.of();

	private NozzleGlobalSprayRules() {
	}

	public static synchronized void registerLockedRule(ResourceLocation id, NozzleSprayRule rule) {
		if (id == null || rule == null || rule.target().isEmpty())
			return;
		EXTERNAL_RULES.put(id, rule.asLocked());
	}

	public static Optional<NozzleSprayRule> findGlobalRule(FluidStack stack) {
		if (stack == null || stack.isEmpty())
			return Optional.empty();
		for (NozzleSprayRule rule : CONFIG_RULES) {
			if (sameFluid(rule, stack))
				return Optional.of(rule);
		}
		synchronized (NozzleGlobalSprayRules.class) {
			for (NozzleSprayRule rule : EXTERNAL_RULES.values()) {
				if (sameFluid(rule, stack))
					return Optional.of(rule);
			}
		}
		return Optional.empty();
	}

	public static Optional<NozzleSprayRule> lockedDisplayRule(Level level, FluidStack stack) {
		Optional<NozzleSprayRule> global = findGlobalRule(stack);
		if (global.isPresent())
			return global;
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.classifyBuiltInFluidForSpray(level, stack);
		return behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED
			? Optional.empty()
			: Optional.of(builtInRule(stack, behavior));
	}

	public static void reloadFromConfig(List<? extends String> entries) {
		List<NozzleSprayRule> rules = new ArrayList<>();
		for (String entry : entries) {
			if (entry == null || entry.isBlank())
				continue;
			try {
				readConfigRule(entry).ifPresent(rules::add);
			} catch (RuntimeException e) {
				CreateFireFightingAdd.LOGGER.warn("Invalid nozzle global fluid rule: {}", entry, e);
			}
		}
		CONFIG_RULES = List.copyOf(rules);
	}

	private static Optional<NozzleSprayRule> readConfigRule(String entry) {
		JsonObject json = JsonParser.parseString(entry).getAsJsonObject();
		ResourceLocation fluidId = ResourceLocation.tryParse(requiredString(json, "fluid"));
		if (fluidId == null)
			return Optional.empty();
		Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
		if (fluid == Fluids.EMPTY)
			return Optional.empty();

		FluidStack stack = new FluidStack(fluid, 1);
		NozzleParticlePalette particles = readParticles(json, stack);
		boolean flammable = bool(json, "flammable", false);
		boolean extinguishing = bool(json, "extinguishing", false);
		boolean igniting = bool(json, "igniting", false);
		List<ResourceLocation> effects = readIdList(json, "effects");
		ResourceLocation fanProcessing = optionalId(json, "fan_processing");
		return Optional.of(new NozzleSprayRule(stack, particles, flammable, extinguishing, igniting,
			effects, fanProcessing, true));
	}

	private static NozzleParticlePalette readParticles(JsonObject json, FluidStack stack) {
		NozzleParticlePalette fallback = NozzleParticleColors.defaultPalette(stack);
		if (!json.has("colors") || !json.get("colors").isJsonArray())
			return fallback;
		JsonArray colors = json.getAsJsonArray("colors");
		NozzleParticlePalette.Entry[] entries = {
			fallback.first(), fallback.second(), fallback.third()
		};
		for (int i = 0; i < entries.length && i < colors.size(); i++) {
			JsonElement element = colors.get(i);
			if (!element.isJsonObject())
				continue;
			JsonObject color = element.getAsJsonObject();
			int rgb = color.has("rgb") ? readColor(color.get("rgb"), entries[i].color()) : entries[i].color();
			int weight = color.has("weight") ? color.get("weight").getAsInt() : entries[i].weight();
			entries[i] = new NozzleParticlePalette.Entry(rgb, weight);
		}
		return new NozzleParticlePalette(entries[0], entries[1], entries[2]);
	}

	private static NozzleSprayRule builtInRule(FluidStack stack,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior) {
		NozzleSprayRule rule = NozzleSprayRule.of(stack);
		return switch (behavior) {
			case WATER -> rule.withFlags(false, true, false)
				.withFanProcessing(ResourceLocation.fromNamespaceAndPath("create", "splashing")).asLocked();
			case LAVA -> rule.withFlags(false, false, true)
				.withFanProcessing(ResourceLocation.fromNamespaceAndPath("create", "blasting")).asLocked();
			case FLAMMABLE -> rule.withFlags(true, false, false).asLocked();
			case POTION -> rule.withEffects(potionEffects(stack)).asLocked();
			case MILK, DRAGON_BREATH -> rule.asLocked();
			default -> rule.asLocked();
		};
	}

	private static List<ResourceLocation> potionEffects(FluidStack stack) {
		if (AllFluids.POTION == null || !stack.getFluid().isSame(AllFluids.POTION.get()))
			return List.of();
		PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		List<ResourceLocation> ids = new ArrayList<>();
		for (var effect : contents.getAllEffects()) {
			effect.getEffect().unwrapKey()
				.ifPresent(key -> ids.add(key.location()));
		}
		return ids;
	}

	private static boolean sameFluid(NozzleSprayRule rule, FluidStack stack) {
		return !rule.target().isEmpty() && !stack.isEmpty()
			&& rule.target().getFluid().isSame(stack.getFluid());
	}

	private static String requiredString(JsonObject json, String key) {
		if (!json.has(key))
			throw new IllegalArgumentException("Missing '" + key + "'");
		return json.get(key).getAsString();
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		return json.has(key) ? json.get(key).getAsBoolean() : fallback;
	}

	private static List<ResourceLocation> readIdList(JsonObject json, String key) {
		if (!json.has(key) || !json.get(key).isJsonArray())
			return List.of();
		List<ResourceLocation> ids = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray(key)) {
			ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
			if (id != null)
				ids.add(id);
		}
		return ids;
	}

	private static @Nullable ResourceLocation optionalId(JsonObject json, String key) {
		if (!json.has(key))
			return null;
		String value = json.get(key).getAsString();
		return value.isBlank() ? null : ResourceLocation.tryParse(value);
	}

	private static int readColor(JsonElement element, int fallback) {
		if (element == null)
			return fallback;
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
			return element.getAsInt() & 0xFFFFFF;
		String value = element.getAsString().trim();
		if (value.startsWith("#"))
			value = value.substring(1);
		try {
			return Integer.parseInt(value, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
