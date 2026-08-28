package com.mikoalopex.createfirefightingadd.api.fire_hose;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Registry for mutually-exclusive Fire Hose appearances.
 *
 * <p>Each appearance owns one dye item, one endpoint texture, and optionally one
 * hose texture. A missing hose texture means the hose tube itself is invisible,
 * as used by the built-in transparent appearance.</p>
 */
public final class FireHoseAppearances {
	public static final ResourceLocation DEFAULT = CreateFireFightingAdd.path("default");
	public static final ResourceLocation BLACK = CreateFireFightingAdd.path("black");
	public static final ResourceLocation TRANSPARENT = CreateFireFightingAdd.path("transparent");

	private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
	private static final Map<Item, ResourceLocation> DYE_ITEMS = new LinkedHashMap<>();

	static {
		register(DEFAULT, Items.WHITE_DYE,
			CreateFireFightingAdd.path("textures/block/fire_hose_end.png"),
			CreateFireFightingAdd.path("textures/block/fire_hose.png"));
		register(BLACK, Items.BLACK_DYE,
			CreateFireFightingAdd.path("textures/block/fire_hose_end_black.png"),
			CreateFireFightingAdd.path("textures/block/fire_hose_black.png"));
		register(TRANSPARENT, Items.PHANTOM_MEMBRANE,
			CreateFireFightingAdd.path("textures/block/fire_hose_end_transparent.png"),
			null);
	}

	private FireHoseAppearances() {
	}

	public static Entry register(ResourceLocation id, @Nullable ItemLike dyeItem,
			ResourceLocation endpointTexture, @Nullable ResourceLocation hoseTexture) {
		if (ENTRIES.containsKey(id))
			throw new IllegalArgumentException("Duplicate Fire Hose appearance: " + id);

		Entry entry = new Entry(id, endpointTexture, hoseTexture);
		ENTRIES.put(id, entry);
		if (dyeItem != null)
			DYE_ITEMS.put(dyeItem.asItem(), id);
		return entry;
	}

	public static Entry get(@Nullable ResourceLocation id) {
		return ENTRIES.getOrDefault(id, ENTRIES.get(DEFAULT));
	}

	public static ResourceLocation normalize(@Nullable ResourceLocation id) {
		return get(id).id();
	}

	public static Map<ResourceLocation, Entry> all() {
		return Collections.unmodifiableMap(ENTRIES);
	}

	public static Optional<ResourceLocation> fromDyeItem(ItemStack stack) {
		if (stack.isEmpty())
			return Optional.empty();
		return Optional.ofNullable(DYE_ITEMS.get(stack.getItem()));
	}

	public static ResourceLocation fromLegacy(boolean black, boolean transparent) {
		if (transparent)
			return TRANSPARENT;
		return black ? BLACK : DEFAULT;
	}

	public static ResourceLocation selectForConnection(@Nullable ResourceLocation first,
			@Nullable ResourceLocation second) {
		ResourceLocation normalizedFirst = normalize(first);
		if (!DEFAULT.equals(normalizedFirst))
			return normalizedFirst;
		return normalize(second);
	}

	public static boolean isBlack(@Nullable ResourceLocation id) {
		return BLACK.equals(normalize(id));
	}

	public static boolean isTransparent(@Nullable ResourceLocation id) {
		return TRANSPARENT.equals(normalize(id));
	}

	public static FireHoseEndpointModel endpointModel(@Nullable ResourceLocation id) {
		ResourceLocation normalized = normalize(id);
		if (BLACK.equals(normalized))
			return FireHoseEndpointModel.BLACK;
		if (TRANSPARENT.equals(normalized))
			return FireHoseEndpointModel.TRANSPARENT;
		return DEFAULT.equals(normalized) ? FireHoseEndpointModel.DEFAULT : FireHoseEndpointModel.CUSTOM;
	}

	public record Entry(ResourceLocation id, ResourceLocation endpointTexture,
			@Nullable ResourceLocation hoseTexture) {
		public boolean rendersHose() {
			return hoseTexture != null;
		}
	}
}
