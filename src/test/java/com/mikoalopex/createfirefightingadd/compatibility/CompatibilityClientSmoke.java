package com.mikoalopex.createfirefightingadd.compatibility;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in client checks for transformed creative tabs and JEI visibility changes. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class CompatibilityClientSmoke {
    private static boolean ran;
    private static long started;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (ran || !Boolean.getBoolean("createfirefightingadd.compatibilityClientSmoke")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (started == 0) {
            if (minecraft.screen == null || minecraft.getOverlay() != null) return;
            started = System.nanoTime();
            minecraft.createWorldOpenFlows().createFreshLevel("compatibility-client-" + System.currentTimeMillis(),
                new LevelSettings("Compatibility client smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                    true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(0, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                    .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), minecraft.screen);
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            if (System.nanoTime() - started < java.util.concurrent.TimeUnit.MINUTES.toNanos(2)) return;
            ran = true;
            CreateFireFightingAdd.LOGGER.error("Compatibility client smoke: FAILED (world load timed out)");
            minecraft.stop();
            return;
        }
        try {
            if (!com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.PhysicalHoseClientChecks.nativeReady()) return;
            ran = true;
            com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.PhysicalHoseClientChecks.check();
            creativeCache();
            jeiVisibility();
            CreateFireFightingAdd.LOGGER.info("Compatibility client smoke: PASSED");
        } catch (Throwable error) {
            ran = true;
            CreateFireFightingAdd.LOGGER.error("Compatibility client smoke: FAILED", error);
        } finally {
            if (ran) minecraft.stop();
        }
    }

    private static void creativeCache() {
        var registries = Minecraft.getInstance().level.registryAccess();
        int available = CompatibilityManager.mask();
        CompatibilitySnapshot.reset();
        check(CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, registries), "Initial build");
        check(!CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, registries), "Stable cache");
        CompatibilitySnapshot.receive(0);
        check(CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, registries), "Server mask invalidates cache");
        check(CreateFireFightingAdd.CREATIVE_TAB.get().getDisplayItems().stream()
            .noneMatch(stack -> !ComponentPolicies.visible(stack)), "Disabled items remain in display");
        check(CreateFireFightingAdd.CREATIVE_TAB.get().getSearchTabDisplayItems().stream()
            .noneMatch(stack -> !ComponentPolicies.visible(stack)), "Disabled items remain in search");
        CompatibilitySnapshot.receive(0);
        check(!CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, registries), "Duplicate mask rebuilt tabs");
        CompatibilitySnapshot.receive(available);
        if (available != 0)
            check(CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, registries), "Capability restore rebuilds tabs");
        CompatibilitySnapshot.reset();
        check(CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, registries), "Logout invalidates cache");
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (ComponentPolicies.requirement(id) != null && ComponentPolicies.visible(new ItemStack(item))
                    && !id.getPath().startsWith("ball_coupling_"))
                check(CreateFireFightingAdd.CREATIVE_TAB.get().getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(item)),
                    "Missing restored search item: " + id);
        }
    }

    private static void jeiVisibility() {
        Set<net.minecraft.world.item.Item> hidden = new HashSet<>();
        int[] mutations = {0};
        var manager = (IIngredientManager) Proxy.newProxyInstance(CompatibilityClientSmoke.class.getClassLoader(),
            new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> {
                boolean removing = method.getName().equals("removeIngredientsAtRuntime");
                if (removing || method.getName().equals("addIngredientsAtRuntime")) {
                    mutations[0]++;
                    for (Object value : (Collection<?>) args[1]) {
                        var item = ((ItemStack)value).getItem();
                        if (removing) hidden.add(item); else hidden.remove(item);
                    }
                    return null;
                }
                throw new AssertionError("Unexpected JEI call: " + method.getName());
            });
        var runtime = (IJeiRuntime) Proxy.newProxyInstance(CompatibilityClientSmoke.class.getClassLoader(),
            new Class<?>[]{IJeiRuntime.class}, (proxy, method, args) -> {
                if (method.getName().equals("getIngredientManager")) return manager;
                throw new AssertionError("Unexpected runtime call: " + method.getName());
            });
        var plugin = new CompatibilityJeiPlugin();
        try {
            CompatibilitySnapshot.receive(0);
            plugin.onRuntimeAvailable(runtime);
            check(hidden.contains(CreateFireFightingAdd.ROPE_HOOK_ITEM.get()), "Disabled hook visible in JEI");
            int before = mutations[0];
            CompatibilityJeiPlugin.refresh();
            check(before == mutations[0], "Unchanged visibility mutates JEI");
            CompatibilitySnapshot.receive(CompatibilityManager.mask());
            for (var item : BuiltInRegistries.ITEM)
                check(hidden.contains(item) == !ComponentPolicies.visible(new ItemStack(item)), "JEI restore mismatch: " + item);
            check(hidden.contains(CreateFireFightingAdd.ROPE_LATCH_ITEM.get()), "Legacy hook restored");
            plugin.onRuntimeUnavailable();
            CompatibilitySnapshot.reset();
            hidden.clear();
            plugin.onRuntimeAvailable(runtime);
            check(hidden.contains(CreateFireFightingAdd.ROPE_LATCH_ITEM.get()), "Legacy hook visible after reconnect");
        } finally {
            plugin.onRuntimeUnavailable();
            CompatibilitySnapshot.reset();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private CompatibilityClientSmoke() {}
}
