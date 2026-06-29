package com.mikoalopex.createfirefightingadd;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Function;

/**
 * Reads {@code data/createfirefightingadd/block_remap.json} and registers legacy
 * aliases so old worlds with renamed blocks won't crash on load.
 * <p>
 * Each legacy alias gets its own {@link Block} instance, which is required by NeoForge's
 * IntrusiveHolder system. The {@code blockFactory} parameter determines which
 * Java class and properties to use for each replacement name.
 */
public class RemapManager {

	private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
	private static final String CONFIG_PATH = "/data/createfirefightingadd/block_remap.json";

	private static final List<DeferredBlock<?>> allLegacyBlocks = new ArrayList<>();
	private static final List<DeferredItem<? extends BlockItem>> allLegacyItems = new ArrayList<>();
	private static final Map<String, List<DeferredBlock<?>>> blocksByReplacement = new LinkedHashMap<>();

	/**
	 * Read the JSON remap file and register every entry on the mod's
	 * {@code BLOCKS} and {@code ITEMS} deferred registers.
	 * <p>
	 * Must be called <em>before</em> the registers are submitted to the event
	 * bus so the legacy suppliers resolve in the same registry cycle as their
	 * replacements.
	 *
	 * @param blockFactory given a replacement block name (e.g. "cone_nozzle"),
	 *                     returns a new {@link Block} instance of the correct
	 *                     class with appropriate properties
	 */
	public static void registerAll(Function<String, Block> blockFactory) {
		JsonObject root;
		try (InputStreamReader reader = new InputStreamReader(
			RemapManager.class.getResourceAsStream(CONFIG_PATH))) {
			root = new Gson().fromJson(reader, JsonObject.class);
		} catch (Exception e) {
			LOGGER.warn("[CreateFireFightingAdd] block_remap.json not found; no block migrations loaded.");
			return;
		}

		if (root == null) return;

		JsonArray mappings = root.getAsJsonArray("mappings");
		if (mappings == null || mappings.isEmpty()) return;

		for (JsonElement element : mappings) {
			JsonObject entry = element.getAsJsonObject();
			String oldName = entry.get("old_name").getAsString();
			String newName = entry.get("new_name").getAsString();
			String note = entry.has("note") ? entry.get("note").getAsString() : "";

			DeferredBlock<?> legacyBlock = CreateFireFightingAdd.BLOCKS.register(oldName,
				() -> {
					Block b = blockFactory.apply(newName);
					if (b == null)
						throw new IllegalStateException(
							"[CreateFireFightingAdd] Block factory returned null for '" + newName + "' (legacy '" + oldName + "')");
					return b;
				});

			DeferredItem<BlockItem> legacyItem = CreateFireFightingAdd.ITEMS
				.registerSimpleBlockItem(oldName, legacyBlock);

			allLegacyBlocks.add(legacyBlock);
			allLegacyItems.add(legacyItem);
			blocksByReplacement.computeIfAbsent(newName, k -> new ArrayList<>()).add(legacyBlock);

			LOGGER.info("[CreateFireFightingAdd] Registered legacy alias: '{}' -> '{}'{}",
				oldName, newName, note.isEmpty() ? "" : " (" + note + ")");
		}
	}

	/**
	 * Returns all legacy {@link DeferredBlock}s that alias the given replacement
	 * name. Call from BE-type builder lambdas (resolves at registry time).
	 */
	public static List<DeferredBlock<?>> getLegacyBlocksFor(String replacementName) {
		List<DeferredBlock<?>> list = blocksByReplacement.get(replacementName);
		return list == null ? List.of() : Collections.unmodifiableList(list);
	}

	/** All legacy block holders used in render-layer setup. */
	public static Collection<DeferredBlock<?>> getAllLegacyBlocks() {
		return Collections.unmodifiableList(allLegacyBlocks);
	}

	/** All legacy item holders used in creative-tab population. */
	public static Collection<DeferredItem<? extends BlockItem>> getAllLegacyItems() {
		return Collections.unmodifiableList(allLegacyItems);
	}
}
