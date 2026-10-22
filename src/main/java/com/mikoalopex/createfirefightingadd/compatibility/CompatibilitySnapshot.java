package com.mikoalopex.createfirefightingadd.compatibility;

/** Intersects server and local capabilities for client-side visibility. */
public final class CompatibilitySnapshot {
    private static volatile int serverMask = -1;
    public static boolean visible(Capability capability) {
        return (serverMask == -1 || (serverMask & (1 << capability.ordinal())) != 0)
            && CompatibilityManager.available(capability);
    }
    public static void receive(int mask) { serverMask = mask; refreshRecipes(); }
    public static void reset() { serverMask = -1; refreshRecipes(); }
    private static void refreshRecipes() {
        if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
            try {
                Class.forName("com.mikoalopex.createfirefightingadd.compatibility.CompatibilityJeiPlugin")
                    .getMethod("refresh").invoke(null);
            } catch (ReflectiveOperationException | LinkageError error) {
                com.mojang.logging.LogUtils.getLogger().debug("Could not refresh optional JEI compatibility visibility", error);
            }
        }
    }
    private CompatibilitySnapshot() {}
}
