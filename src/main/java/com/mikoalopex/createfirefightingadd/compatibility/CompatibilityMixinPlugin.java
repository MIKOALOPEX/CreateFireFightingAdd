package com.mikoalopex.createfirefightingadd.compatibility;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Early version checks must not initialize ModList, Minecraft or optional physics classes. */
public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        Capability capability;
        if (mixin.contains("RopeLatch")) capability = Capability.ROPE_CAPTURE;
        else if (mixin.contains("SmartRope") || mixin.endsWith("RopeItemMixin")) capability = Capability.ROPE_CONNECT;
        else return true;
        var loading = FMLLoader.getLoadingModList();
        if (loading == null) return false;
        return CompatibilityRules.evaluate(capability, id -> loading.getMods().stream()
            .filter(mod -> mod.getModId().equals(id)).map(mod -> mod.getVersion().toString())
            .findFirst().orElse(null)).available();
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
