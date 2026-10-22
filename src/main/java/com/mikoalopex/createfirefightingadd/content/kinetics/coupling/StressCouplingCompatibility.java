package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.lang.reflect.Constructor;
import com.mikoalopex.createfirefightingadd.compatibility.Capability;
import com.mikoalopex.createfirefightingadd.compatibility.CompatibilityManager;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingAlignment;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingPhysics;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/** Selects the coupling feature tier before optional physics classes are loaded. */
public final class StressCouplingCompatibility {
    public enum Tier { HIDDEN, INERT, FULL }

    private static Tier tier;
    private static Boolean versionsSupported;
    private static Constructor<? extends BallCouplingBlockEntity> factory;

    private StressCouplingCompatibility() {}

    public static boolean supportedVersions() {
        if (versionsSupported == null)
            versionsSupported = CompatibilityManager.versions(Capability.COUPLING).available();
        return versionsSupported;
    }

    public static synchronized Tier tier() {
        if (tier != null) return tier;
        tier = ModList.get().isLoaded("sable") && ModList.get().isLoaded("aeronautics")
            ? Tier.INERT : Tier.HIDDEN;
        if (tier == Tier.INERT && supportedVersions()) {
            try {
                if (SableStructureCompat.available() && CouplingPhysics.available() && CouplingAlignment.available()) {
                    factory = Class.forName("com.mikoalopex.createfirefightingadd.integration.sable.SableBallCouplingBlockEntity")
                        .asSubclass(BallCouplingBlockEntity.class).getConstructor(BlockPos.class, BlockState.class);
                    tier = Tier.FULL;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                LogUtils.getLogger().debug("Stress coupling compatibility probe failed", error);
            }
        }
        return tier;
    }

    public static boolean enabled() { return tier() == Tier.FULL; }
    public static boolean visible() { return enabled(); }

    public static BallCouplingBlockEntity create(BlockPos pos, BlockState state) {
        if (enabled()) {
            try {
                return factory.newInstance(pos, state);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                tier = Tier.INERT;
                LogUtils.getLogger().warn("Stress Coupling disabled: its Sable adapter could not load. Check the installed dependency versions.");
                LogUtils.getLogger().debug("Stress coupling adapter failure", error);
            }
        }
        return new BallCouplingBlockEntity(pos, state);
    }

    public static Component hint() {
        return Component.translatable(tier() != Tier.HIDDEN ? "ball_coupling.dependencies_incompatible" : "ball_coupling.dependencies_missing");
    }

    public static void notifyPlayer(Player player) {
        if (player != null && !player.level().isClientSide)
            player.displayClientMessage(hint(), false);
    }
}
