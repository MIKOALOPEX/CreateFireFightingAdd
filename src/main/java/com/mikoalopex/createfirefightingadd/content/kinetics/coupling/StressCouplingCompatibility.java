package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.lang.reflect.Constructor;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingAlignment;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingPhysics;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/** Selects the coupling feature tier before optional physics classes are loaded. */
public final class StressCouplingCompatibility {
    public enum Tier { HIDDEN, INERT, FULL }

    private static Tier tier;
    private static Boolean versionsSupported;
    private static Constructor<? extends BallCouplingBlockEntity> factory;

    private StressCouplingCompatibility() {}

    public static boolean supportedVersions() {
        if (versionsSupported == null)
            versionsSupported = atLeast("sable", "2.0.0") && !atLeast("sable", "3.0.0")
                && atLeast("aeronautics", "1.3.0") && atLeast("simulated", "1.3.0")
                && atLeast("synaxis", "1.4.0") && atLeast("sable_schematic_api", "0.4.1")
                && atLeast("ldlib2", "2.2.17");
        return versionsSupported;
    }

    private static boolean atLeast(String id, String minimum) {
        return ModList.get().getModContainerById(id)
            .map(mod -> mod.getModInfo().getVersion().compareTo(new DefaultArtifactVersion(minimum)) >= 0)
            .orElse(false);
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
        if (tier == Tier.INERT)
            LogUtils.getLogger().warn("Stress Coupling disabled: requires Sable 2.x, Aeronautics/Simulated 1.3+, Synaxis 1.4+, Sable Photomancy API 0.4.1+ and LDLib2 2.2.17+ with compatible physics APIs. Other features are unchanged.");
        return tier;
    }

    public static boolean enabled() { return tier() == Tier.FULL; }
    public static boolean visible() { return tier() != Tier.HIDDEN; }

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
        return Component.translatable(visible() ? "ball_coupling.dependencies_incompatible" : "ball_coupling.dependencies_missing");
    }

    public static void notifyPlayer(Player player) {
        if (player != null && !player.level().isClientSide)
            player.displayClientMessage(hint(), false);
    }
}
