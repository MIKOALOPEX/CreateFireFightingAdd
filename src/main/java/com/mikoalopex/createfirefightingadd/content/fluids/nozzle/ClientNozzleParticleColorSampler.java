package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.FastColor;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public final class ClientNozzleParticleColorSampler {
	private static final int MAX_SAMPLES = 2048;
	private static final int MIN_ALPHA = 24;
	private static final int HUE_BUCKETS = 24;
	private static final double MIN_SATURATION_FOR_HUE = 0.12;
	private static final double MAX_SELECTED_HUE_SPREAD = 0.12;
	private static final Map<CacheKey, NozzleParticlePalette> CACHE = new HashMap<>();

	private ClientNozzleParticleColorSampler() {
	}

	public static NozzleSprayRule withSuggestedPalette(NozzleSprayRule rule) {
		if (rule == null || rule.locked() || rule.target().isEmpty())
			return rule;
		NozzleParticlePalette palette = suggestedPalette(rule.target());
		return palette == null ? rule : rule.withParticles(palette);
	}

	public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
			@Override
			protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
				return null;
			}

			@Override
			protected void apply(Void object, ResourceManager manager, ProfilerFiller profiler) {
				clearCache();
			}
		});
	}

	private static void clearCache() {
		CACHE.clear();
	}

	private static NozzleParticlePalette suggestedPalette(FluidStack stack) {
		try {
			IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
			int tint = extensions.getTintColor(stack);
			ResourceLocation stillTexture = extensions.getStillTexture(stack);
			ResourceLocation flowingTexture = extensions.getFlowingTexture(stack);
			CacheKey key = new CacheKey(BuiltInRegistries.FLUID.getKey(stack.getFluid()), stillTexture, flowingTexture, tint);
			NozzleParticlePalette cached = CACHE.get(key);
			if (cached != null)
				return cached;

			NozzleParticlePalette palette = sampleTexture(stack, extensions, stillTexture)
				.or(() -> sampleTexture(stack, extensions, flowingTexture))
				.orElseGet(() -> NozzleParticleColors.defaultPalette(stack));
			CACHE.put(key, palette);
			return palette;
		} catch (RuntimeException ignored) {
			return NozzleParticleColors.defaultPalette(stack);
		}
	}

	private static Optional<NozzleParticlePalette> sampleTexture(FluidStack stack,
			IClientFluidTypeExtensions extensions, ResourceLocation texture) {
		if (texture == null)
			return Optional.empty();
		Minecraft minecraft = Minecraft.getInstance();
		Function<ResourceLocation, TextureAtlasSprite> atlas = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
		if (atlas == null)
			return Optional.empty();
		TextureAtlasSprite sprite = atlas.apply(texture);
		if (sprite == null || sprite.contents() == null)
			return Optional.empty();

		int tint = extensions.getTintColor(stack);
		int width = sprite.contents().width();
		int height = sprite.contents().height();
		if (width <= 0 || height <= 0)
			return Optional.empty();

		Map<Integer, ColorBucket> buckets = new HashMap<>();
		int step = Math.max(1, (int) Math.ceil(Math.sqrt((width * height) / (double) MAX_SAMPLES)));
		for (int y = 0; y < height; y += step) {
			for (int x = 0; x < width; x += step) {
				int color = tintedPixel(sprite.getPixelRGBA(0, x, y), tint);
				if (color < 0)
					continue;
				int key = quantizedKey(color);
				buckets.computeIfAbsent(key, ignored -> new ColorBucket()).add(color);
			}
		}

		List<ColorSample> samples = buckets.values().stream()
			.map(ColorBucket::sample)
			.filter(sample -> sample.count() > 0)
			.sorted(Comparator.comparingInt(ColorSample::count).reversed())
			.toList();
		if (samples.isEmpty())
			return Optional.empty();
		return Optional.of(paletteFromSamples(samples));
	}

	private static NozzleParticlePalette paletteFromSamples(List<ColorSample> samples) {
		// Keep sampled colors in one hue family; synthesize variants when the texture cannot provide them.
		List<ColorSample> family = dominantHueFamily(samples);
		ColorSample base = family.isEmpty() ? samples.getFirst() : family.getFirst();
		ColorSample light = bestVariant(family, base, true).orElse(null);
		ColorSample dark = bestVariant(family, base, false).orElse(null);
		if (light == null || dark == null || hueSpreadTooWide(base, light, dark))
			return paletteFromBase(base.rgb());
		return new NozzleParticlePalette(
			new NozzleParticlePalette.Entry(base.rgb(), 60),
			new NozzleParticlePalette.Entry(light.rgb(), 25),
			new NozzleParticlePalette.Entry(dark.rgb(), 15));
	}

	private static List<ColorSample> dominantHueFamily(List<ColorSample> samples) {
		int[] weights = new int[HUE_BUCKETS];
		int coloredWeight = 0;
		for (ColorSample sample : samples) {
			if (sample.saturation() < MIN_SATURATION_FOR_HUE)
				continue;
			int bucket = hueBucket(sample.hue());
			weights[bucket] += sample.count();
			coloredWeight += sample.count();
		}
		if (coloredWeight <= 0)
			return samples;

		int dominant = 0;
		for (int i = 1; i < weights.length; i++)
			if (weights[i] > weights[dominant])
				dominant = i;

		List<ColorSample> family = new ArrayList<>();
		for (ColorSample sample : samples) {
			if (sample.saturation() >= MIN_SATURATION_FOR_HUE
				&& bucketDistance(hueBucket(sample.hue()), dominant) <= 2)
				family.add(sample);
		}
		return family.isEmpty() ? samples : family;
	}

	private static Optional<ColorSample> bestVariant(List<ColorSample> samples, ColorSample base, boolean lighter) {
		return samples.stream()
			.filter(sample -> sample != base)
			.filter(sample -> lighter ? sample.value() > base.value() + 0.08 : sample.value() < base.value() - 0.08)
			.min(Comparator.comparingDouble((ColorSample sample) -> hueDistance(sample.hue(), base.hue()) * 3.0)
				.thenComparingDouble(sample -> lighter ? -sample.value() : sample.value())
				.thenComparing(Comparator.comparingInt(ColorSample::count).reversed()));
	}

	private static boolean hueSpreadTooWide(ColorSample base, ColorSample light, ColorSample dark) {
		if (base.saturation() < MIN_SATURATION_FOR_HUE)
			return false;
		return hueDistance(base.hue(), light.hue()) > MAX_SELECTED_HUE_SPREAD
			|| hueDistance(base.hue(), dark.hue()) > MAX_SELECTED_HUE_SPREAD;
	}

	private static NozzleParticlePalette paletteFromBase(int base) {
		return new NozzleParticlePalette(
			new NozzleParticlePalette.Entry(base, 60),
			new NozzleParticlePalette.Entry(NozzleParticlePalette.mix(base, 0xFFFFFF, 0.35), 25),
			new NozzleParticlePalette.Entry(NozzleParticlePalette.mix(base, 0x000000, 0.25), 15));
	}

	private static int tintedPixel(int abgr, int argbTint) {
		int alpha = FastColor.ABGR32.alpha(abgr);
		if (alpha < MIN_ALPHA)
			return -1;
		int tintAlpha = FastColor.ARGB32.alpha(argbTint);
		if (argbTint != -1 && tintAlpha < MIN_ALPHA)
			return -1;

		int r = FastColor.ABGR32.red(abgr);
		int g = FastColor.ABGR32.green(abgr);
		int b = FastColor.ABGR32.blue(abgr);
		if (argbTint != -1) {
			r = r * FastColor.ARGB32.red(argbTint) / 255;
			g = g * FastColor.ARGB32.green(argbTint) / 255;
			b = b * FastColor.ARGB32.blue(argbTint) / 255;
		}
		return (r << 16) | (g << 8) | b;
	}

	private static int quantizedKey(int rgb) {
		return (((rgb >> 19) & 0x1F) << 10) | (((rgb >> 11) & 0x1F) << 5) | ((rgb >> 3) & 0x1F);
	}

	private static int hueBucket(double hue) {
		return Math.floorMod((int) Math.floor(hue * HUE_BUCKETS), HUE_BUCKETS);
	}

	private static int bucketDistance(int first, int second) {
		int distance = Math.abs(first - second);
		return Math.min(distance, HUE_BUCKETS - distance);
	}

	private static double hueDistance(double first, double second) {
		double distance = Math.abs(first - second);
		return Math.min(distance, 1.0 - distance);
	}

	private static Hsv rgbToHsv(int rgb) {
		double r = ((rgb >> 16) & 0xFF) / 255.0;
		double g = ((rgb >> 8) & 0xFF) / 255.0;
		double b = (rgb & 0xFF) / 255.0;
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		double delta = max - min;
		double hue;
		if (delta == 0.0)
			hue = 0.0;
		else if (max == r)
			hue = ((g - b) / delta) / 6.0;
		else if (max == g)
			hue = ((b - r) / delta + 2.0) / 6.0;
		else
			hue = ((r - g) / delta + 4.0) / 6.0;
		if (hue < 0.0)
			hue += 1.0;
		double saturation = max == 0.0 ? 0.0 : delta / max;
		return new Hsv(hue, saturation, max);
	}

	private record CacheKey(ResourceLocation fluid, ResourceLocation stillTexture,
			ResourceLocation flowingTexture, int tint) {
	}

	private record Hsv(double hue, double saturation, double value) {
	}

	private record ColorSample(int rgb, int count, double hue, double saturation, double value) {
	}

	private static final class ColorBucket {
		private int count;
		private int red;
		private int green;
		private int blue;

		void add(int rgb) {
			count++;
			red += (rgb >> 16) & 0xFF;
			green += (rgb >> 8) & 0xFF;
			blue += rgb & 0xFF;
		}

		ColorSample sample() {
			int rgb = ((red / count) << 16) | ((green / count) << 8) | (blue / count);
			Hsv hsv = rgbToHsv(rgb);
			return new ColorSample(rgb, count, hsv.hue(), hsv.saturation(), hsv.value());
		}
	}
}
